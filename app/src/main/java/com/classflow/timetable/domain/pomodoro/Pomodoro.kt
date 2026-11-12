package com.classflow.timetable.domain.pomodoro

import java.time.LocalDate
import java.util.Locale

/** 番茄钟的两个阶段。休息里长/短不是第三个阶段，而是由 [PomodoroRules.isLongBreak] 推出来的。 */
enum class PomodoroPhase {
    FOCUS,
    BREAK,
    ;

    val label: String
        get() = when (this) {
            FOCUS -> "专注"
            BREAK -> "休息"
        }
}

/**
 * 用户可调的时长。
 *
 * 25/5 是番茄工作法的经典值，但写论文和背单词需要的时间段根本不一样，
 * 所以三个数字都开放：只给一个固定 25 分钟，用户迟早会自己找别的计时器。
 */
data class PomodoroSettings(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    /** 做几个番茄算一轮（轮末给长休息）。 */
    val roundsPerCycle: Int = 4,
    /** 一段结束是否自动开始下一段。默认关：多数人需要那一下「我该起来了」的动作。 */
    val autoStartNext: Boolean = false,
)

/**
 * 计时器的全部持久状态。
 *
 * 关键设计：**只存结束时刻，不存剩余秒数**。
 * 存剩余秒数就得在切后台、锁屏、进程被杀时不停地扣，任何一次没扣准都会漂；
 * 存「这一段什么时候结束」，回来拿当前时间一减就是真实剩余，误差永远是 0。
 * [remainingMillis] 只在暂停时有意义（也是从 endAt 换算出来落盘的）。
 */
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val running: Boolean = false,
    /** 运行中：这一段的结束时刻（epoch millis）。暂停时无意义。 */
    val endAtMillis: Long = 0L,
    /** 暂停 / 待开始：还剩多少毫秒。 */
    val remainingMillis: Long = 0L,
    /** 本轮已经完成的专注数（0..roundsPerCycle）。长休息结束后清零。 */
    val roundsDone: Int = 0,
    /** 今日完成的番茄数，跨天自动归零。 */
    val todayCount: Int = 0,
    val todayFocusMillis: Long = 0L,
    /** 今日统计的归属日期（ISO），用来判断要不要清零。 */
    val todayKey: String = "",
)

/** 一段计时结束后的结果：新状态 + 谁来通知。 */
data class PomodoroCompletion(
    val state: PomodoroState,
    /** 刚结束的是哪一段。 */
    val finished: PomodoroPhase,
    /** 接下来是哪一段。 */
    val next: PomodoroPhase,
    /** 接下来这段是不是长休息（决定文案说 5 分钟还是 15 分钟）。 */
    val nextIsLongBreak: Boolean,
)

/** 纯计算，不碰 Android。全部可单测，见 PomodoroRulesTest。 */
object PomodoroRules {

    const val MINUTE_MILLIS = 60_000L

    fun isLongBreak(roundsDone: Int, settings: PomodoroSettings): Boolean =
        roundsDone > 0 && roundsDone % settings.roundsPerCycle == 0

    /** 某一段该有多长。休息段的长度取决于「本轮已经做了几个」。 */
    fun durationMillis(
        phase: PomodoroPhase,
        settings: PomodoroSettings,
        roundsDone: Int,
    ): Long = when (phase) {
        PomodoroPhase.FOCUS -> settings.focusMinutes * MINUTE_MILLIS
        PomodoroPhase.BREAK ->
            if (isLongBreak(roundsDone, settings)) {
                settings.longBreakMinutes * MINUTE_MILLIS
            } else {
                settings.breakMinutes * MINUTE_MILLIS
            }
    }

    /** 当前还剩多少（运行中按结束时刻算，暂停时读存下来的值）。 */
    fun remainingMillis(state: PomodoroState, nowMillis: Long): Long =
        if (state.running) (state.endAtMillis - nowMillis).coerceAtLeast(0L)
        else state.remainingMillis.coerceAtLeast(0L)

    /**
     * 秒表文本。向上取整——剩 24:59.4 时显示 25:00，
     * 这样按下开始的那一瞬间看到的就是完整的「25:00」，而不是「24:59」。
     */
    fun clockText(millis: Long): String {
        val totalSeconds = ((millis.coerceAtLeast(0L) + 999L) / 1000L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    /** 已走过的比例，给圆环用。 */
    fun progress(remainingMillis: Long, totalMillis: Long): Float {
        if (totalMillis <= 0L) return 0f
        return (1f - remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }

    /** 跨天把「今日」清零。只在计时器真正被看到时才需要，别在后台定时跑。 */
    fun rollover(state: PomodoroState, today: LocalDate): PomodoroState {
        val key = today.toString()
        if (state.todayKey == key) return state
        return state.copy(todayKey = key, todayCount = 0, todayFocusMillis = 0L)
    }

    /**
     * 一段走完。
     *
     * 只有专注段计入今日统计；休息段结束不算一个番茄。
     * 下一段从「完整的时长」开始而不是从 0 开始 —— 结束后停在待开始状态，
     * 让用户自己按一下再进去，比自动窜进下一段更不容易被打断思路。
     */
    fun complete(
        state: PomodoroState,
        settings: PomodoroSettings,
    ): PomodoroCompletion {
        val finished = state.phase
        val (next, rounds) = nextPhase(finished, state.roundsDone, settings)
        val counted = finished == PomodoroPhase.FOCUS
        val todayCount = if (counted) state.todayCount + 1 else state.todayCount
        val focusMillis = if (counted) {
            state.todayFocusMillis + durationMillis(PomodoroPhase.FOCUS, settings, state.roundsDone)
        } else {
            state.todayFocusMillis
        }
        val entered = state.copy(
            phase = next,
            running = false,
            endAtMillis = 0L,
            remainingMillis = durationMillis(next, settings, rounds),
            roundsDone = rounds,
            todayCount = todayCount,
            todayFocusMillis = focusMillis,
        )
        return PomodoroCompletion(
            state = entered,
            finished = finished,
            next = next,
            nextIsLongBreak = next == PomodoroPhase.BREAK && isLongBreak(rounds, settings),
        )
    }

    /** 跳过本段：不计入统计，直接进入下一段（跳过专注也要给休息，否则会连着硬撑）。 */
    fun skip(state: PomodoroState, settings: PomodoroSettings): PomodoroState {
        val (next, rounds) = nextPhase(state.phase, state.roundsDone, settings)
        return state.copy(
            phase = next,
            running = false,
            endAtMillis = 0L,
            remainingMillis = durationMillis(next, settings, rounds),
            roundsDone = rounds,
        )
    }

    private fun nextPhase(
        phase: PomodoroPhase,
        roundsDone: Int,
        settings: PomodoroSettings,
    ): Pair<PomodoroPhase, Int> = when (phase) {
        // 专注结束 → 休息。roundsDone 在这里 +1，长短休息由它决定
        PomodoroPhase.FOCUS -> PomodoroPhase.BREAK to (roundsDone + 1)
        // 长休息结束 → 新的一轮，计数清零；短休息结束 → 计数保留
        PomodoroPhase.BREAK ->
            if (isLongBreak(roundsDone, settings)) {
                PomodoroPhase.FOCUS to 0
            } else {
                PomodoroPhase.FOCUS to roundsDone
            }
    }
}
