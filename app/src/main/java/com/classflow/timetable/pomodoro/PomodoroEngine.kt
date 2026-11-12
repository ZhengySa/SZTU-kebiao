package com.classflow.timetable.pomodoro

import android.content.Context
import com.classflow.timetable.core.time.AppClock
import com.classflow.timetable.data.local.PomodoroStore
import com.classflow.timetable.domain.pomodoro.PomodoroCompletion
import com.classflow.timetable.domain.pomodoro.PomodoroPhase
import com.classflow.timetable.domain.pomodoro.PomodoroRules
import com.classflow.timetable.domain.pomodoro.PomodoroSettings
import com.classflow.timetable.domain.pomodoro.PomodoroState
import com.classflow.timetable.reminder.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 页面要显示的一切。由 [PomodoroEngine] 计算好，界面不再自己算时间。 */
data class PomodoroUiState(
    val settings: PomodoroSettings = PomodoroSettings(),
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val running: Boolean = false,
    val remainingMillis: Long = 25 * PomodoroRules.MINUTE_MILLIS,
    val totalMillis: Long = 25 * PomodoroRules.MINUTE_MILLIS,
    val roundsDone: Int = 0,
    val todayCount: Int = 0,
    val todayFocusMinutes: Int = 0,
    /** 当前休息是不是长休息。 */
    val isLongBreak: Boolean = false,
    /** 上一段刚刚结束（仅内存态，用于界面上那句"专注结束，休息一下"）。 */
    val justFinished: PomodoroPhase? = null,
) {
    val progress: Float get() = PomodoroRules.progress(remainingMillis, totalMillis)
    val clockText: String get() = PomodoroRules.clockText(remainingMillis)
    /** 满时长待开始 / 中途停 / 正在走：文案不同，因为「暂停」和「还没开始」是两件事。 */
    val idle: Boolean get() = !running && remainingMillis >= totalMillis
}

/**
 * 番茄钟的计时内核。
 *
 * 刻意做成 @Singleton 而不是挂在 ViewModel 上：用户按下开始之后会切出去看课件、回消息，
 * 那时页面早被销毁了，计时不能跟着一起死。进程还活着就一直走；
 * 进程被系统杀了，下次打开按落盘的结束时刻重新算剩余，一样准。
 *
 * 这里唯一不保证的是「进程被杀之后还能准时叫醒你」—— 那需要前台服务或精确闹钟，
 * 对一个小工具来说代价太大（常驻通知 / 额外权限）。所以结束时通知只在进程活着时发。
 */
@Singleton
class PomodoroEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PomodoroStore,
    private val clock: AppClock,
) {

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 计时 tick 在别的线程上读它，所以这两个必须 @Volatile：
    // 否则「按下暂停」之后 tick 还可能读到旧的 running=true，继续往下走一秒
    @Volatile private var settings: PomodoroSettings = store.loadSettings()
    @Volatile private var snapshot: PomodoroState = store.loadState(settings)
    private var ticker: Job? = null
    private var justFinished: PomodoroPhase? = null
    private var justFinishedAt: Long = 0L

    private val _state = MutableStateFlow(buildUi())
    val state: StateFlow<PomodoroUiState> = _state.asStateFlow()

    init {
        recover()
    }

    /**
     * 冷启动恢复。
     *
     * 三种情况：没在跑（什么都不做）、在跑而且还没到点（继续 tick）、
     * 在跑但已经过点了（说明进程在后台被杀，这段其实早就结束了）——
     * 补一次状态推进，且只在「刚过去不久」时打扰用户，避免第二天打开 App
     * 突然跳出一条昨天的「专注结束」。
     */
    private fun recover() {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            snapshot = PomodoroRules.rollover(snapshot, clock.today())
            if (!snapshot.running) {
                store.saveState(snapshot)
                return@synchronized
            }
            if (snapshot.endAtMillis > now) {
                store.saveState(snapshot)
                startTickerLocked()
                return@synchronized
            }
            val lateBy = now - snapshot.endAtMillis
            val completion = PomodoroRules.complete(snapshot, settings)
            snapshot = completion.state
            store.saveState(snapshot)
            if (lateBy <= STALE_NOTIFY_WINDOW_MILLIS) notifyFinished(completion)
        }
        publish()
    }

    fun toggle() {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            justFinished = null
            justFinishedAt = 0L
            snapshot = if (snapshot.running) {
                ticker?.cancel()
                ticker = null
                // 暂停时把剩余时间固定下来，之后就不再看 endAt 了
                snapshot.copy(
                    running = false,
                    endAtMillis = 0L,
                    remainingMillis = PomodoroRules.remainingMillis(snapshot, now),
                )
            } else {
                val remaining = if (snapshot.remainingMillis <= 0L) {
                    PomodoroRules.durationMillis(snapshot.phase, settings, snapshot.roundsDone)
                } else {
                    snapshot.remainingMillis
                }
                val started = snapshot.copy(
                    running = true,
                    remainingMillis = remaining,
                    endAtMillis = now + remaining,
                )
                startTickerLocked()
                started
            }
            store.saveState(snapshot)
        }
        publish()
    }

    /** 回到本段的开头。已完成的番茄数不受影响——重置的是计时，不是战绩。 */
    fun reset() {
        synchronized(lock) {
            justFinished = null
            justFinishedAt = 0L
            snapshot = snapshot.copy(
                running = false,
                endAtMillis = 0L,
                remainingMillis = PomodoroRules.durationMillis(snapshot.phase, settings, snapshot.roundsDone),
            )
            ticker?.cancel()
            ticker = null
            store.saveState(snapshot)
        }
        publish()
    }

    /** 跳过本段。不计入今日统计，但照样进入下一段（跳过专注也得休息）。 */
    fun skip() {
        synchronized(lock) {
            justFinished = null
            justFinishedAt = 0L
            ticker?.cancel()
            ticker = null
            snapshot = PomodoroRules.skip(snapshot, settings)
            store.saveState(snapshot)
        }
        publish()
    }

    fun setAutoStartNext(enabled: Boolean) {
        updateSettings(settings.copy(autoStartNext = enabled))
    }

    /**
     * 改时长。
     *
     * 正在计时时只改「下一段用多长」，不动手里这一段 —— 用户改设置时看到数字跳变会以为计时坏了。
     * 没在计时就立刻按新时长重排，这样改完马上按开始就是新时长。
     */
    fun setFocusMinutes(minutes: Int) = updateSettings(settings.copy(focusMinutes = minutes.coerceIn(1, 180)))

    fun setBreakMinutes(minutes: Int) = updateSettings(settings.copy(breakMinutes = minutes.coerceIn(1, 180)))

    fun setLongBreakMinutes(minutes: Int) =
        updateSettings(settings.copy(longBreakMinutes = minutes.coerceIn(1, 180)))

    private fun updateSettings(next: PomodoroSettings) {
        synchronized(lock) {
            settings = next
            store.saveSettings(next)
            if (!snapshot.running) {
                snapshot = snapshot.copy(
                    remainingMillis = PomodoroRules.durationMillis(snapshot.phase, settings, snapshot.roundsDone),
                )
                store.saveState(snapshot)
            }
        }
        publish()
    }

    private fun startTickerLocked() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                val remain = snapshot.endAtMillis - System.currentTimeMillis()
                if (!snapshot.running) return@launch
                if (remain <= 0L) {
                    finish()
                    return@launch
                }
                publish()
            }
        }
    }

    private fun finish() {
        val completion = synchronized(lock) {
            val done = PomodoroRules.complete(snapshot, settings)
            val autoNext = settings.autoStartNext
            snapshot = if (autoNext) {
                done.state.copy(
                    running = true,
                    endAtMillis = System.currentTimeMillis() + done.state.remainingMillis,
                )
            } else {
                done.state
            }
            justFinished = done.finished
            justFinishedAt = System.currentTimeMillis()
            store.saveState(snapshot)
            if (autoNext) startTickerLocked() else ticker = null
            done
        }
        notifyFinished(completion)
        publish()
    }

    private fun notifyFinished(completion: PomodoroCompletion) {
        val title: String
        val body: String
        if (completion.finished == PomodoroPhase.FOCUS) {
            title = if (completion.nextIsLongBreak) "一轮 ${settings.roundsPerCycle} 个番茄做完了" else "专注结束"
            body = if (completion.nextIsLongBreak) {
                "去休息 ${settings.longBreakMinutes} 分钟，起来走两步"
            } else {
                "休息 ${settings.breakMinutes} 分钟，眼睛从屏幕上挪开"
            }
        } else {
            title = "休息结束"
            body = "回来开始下一个 ${settings.focusMinutes} 分钟"
        }
        NotificationHelper.notifyPomodoro(context, title, body)
    }

    private fun publish() {
        _state.value = buildUi()
    }

    /**
     * 页面每次打开都调一次。
     *
     * 两件事：跨天了把「今日」清零（引擎可能整夜都在内存里待着），
     * 以及别把昨天那句「专注结束了」一直挂在屏幕上——见 [rememberedFinished]。
     */
    fun refresh() {
        synchronized(lock) {
            val rolled = PomodoroRules.rollover(snapshot, clock.today())
            if (rolled != snapshot) {
                snapshot = rolled
                store.saveState(snapshot)
            }
        }
        publish()
    }

    /** 「刚结束一段」只在对得上号的这段时间里说，超过就当没说过。 */
    private fun rememberedFinished(nowMillis: Long): PomodoroPhase? {
        val at = justFinishedAt
        if (at == 0L) return null
        return if (nowMillis - at in 0..JUST_FINISHED_WINDOW_MILLIS) justFinished else null
    }

    private fun buildUi(): PomodoroUiState {
        val now = System.currentTimeMillis()
        val current = snapshot
        val total = PomodoroRules.durationMillis(current.phase, settings, current.roundsDone)
        return PomodoroUiState(
            settings = settings,
            phase = current.phase,
            running = current.running,
            remainingMillis = PomodoroRules.remainingMillis(current, now),
            totalMillis = total,
            roundsDone = current.roundsDone,
            todayCount = current.todayCount,
            todayFocusMinutes = (current.todayFocusMillis / PomodoroRules.MINUTE_MILLIS).toInt(),
            isLongBreak = current.phase == PomodoroPhase.BREAK && PomodoroRules.isLongBreak(current.roundsDone, settings),
            justFinished = rememberedFinished(now),
        )
    }

    private companion object {
        /** 4 次/秒：秒数跳变的视觉够顺，又不至于让整页每秒重组几十次。 */
        const val TICK_MILLIS = 250L

        /** 进程被杀后补通知的时间窗口：超过这个点就不打扰了，只把状态推进好。 */
        const val STALE_NOTIFY_WINDOW_MILLIS = 10 * 60 * 1000L

        /** 「刚结束」那句话挂多久：够用户从别的 App 切回来，又不至于变成陈年旧账。 */
        const val JUST_FINISHED_WINDOW_MILLIS = 3 * 60 * 1000L
    }
}
