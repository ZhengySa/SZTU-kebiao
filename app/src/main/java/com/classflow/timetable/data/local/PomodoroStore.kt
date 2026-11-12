package com.classflow.timetable.data.local

import android.content.Context
import com.classflow.timetable.domain.pomodoro.PomodoroPhase
import com.classflow.timetable.domain.pomodoro.PomodoroRules
import com.classflow.timetable.domain.pomodoro.PomodoroSettings
import com.classflow.timetable.domain.pomodoro.PomodoroState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 番茄钟的落盘。
 *
 * 用 SharedPreferences 而不是 DataStore：这里的读写全在用户明确操作时（开始/暂停/结束），
 * 一次几毫秒，不需要 Flow 也不需要异步；反而是「进程被杀后回来要把状态读对」这件事更要紧，
 * SharedPreferences 的 commit 能写在主线程之前，语义更直白。
 *
 * 没有敏感信息：只有几个时长和计数，和课表、账号都无关。
 */
@Singleton
class PomodoroStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("pomodoro", Context.MODE_PRIVATE)

    fun loadSettings(): PomodoroSettings = PomodoroSettings(
        focusMinutes = prefs.getInt(KEY_FOCUS_MIN, 25).coerceIn(MIN_MINUTES, MAX_MINUTES),
        breakMinutes = prefs.getInt(KEY_BREAK_MIN, 5).coerceIn(MIN_MINUTES, MAX_MINUTES),
        longBreakMinutes = prefs.getInt(KEY_LONG_BREAK_MIN, 15).coerceIn(MIN_MINUTES, MAX_MINUTES),
        roundsPerCycle = prefs.getInt(KEY_ROUNDS, 4).coerceIn(2, 8),
        autoStartNext = prefs.getBoolean(KEY_AUTO_NEXT, false),
    )

    fun saveSettings(settings: PomodoroSettings) {
        prefs.edit()
            .putInt(KEY_FOCUS_MIN, settings.focusMinutes)
            .putInt(KEY_BREAK_MIN, settings.breakMinutes)
            .putInt(KEY_LONG_BREAK_MIN, settings.longBreakMinutes)
            .putInt(KEY_ROUNDS, settings.roundsPerCycle)
            .putBoolean(KEY_AUTO_NEXT, settings.autoStartNext)
            .apply()
    }

    /** 没有存档时给一个「专注段待开始」的初值，避免首屏显示 0:00。 */
    fun loadState(defaults: PomodoroSettings): PomodoroState {
        val phase = PomodoroPhase.entries.firstOrNull { it.name == prefs.getString(KEY_PHASE, null) }
            ?: PomodoroPhase.FOCUS
        val rounds = prefs.getInt(KEY_ROUNDS_DONE, 0).coerceAtLeast(0)
        val fallbackDuration = PomodoroRules.durationMillis(phase, defaults, rounds)
        return PomodoroState(
            phase = phase,
            running = prefs.getBoolean(KEY_RUNNING, false),
            endAtMillis = prefs.getLong(KEY_END_AT, 0L),
            remainingMillis = prefs.getLong(KEY_REMAINING, fallbackDuration),
            roundsDone = rounds,
            todayCount = prefs.getInt(KEY_TODAY_COUNT, 0).coerceAtLeast(0),
            todayFocusMillis = prefs.getLong(KEY_TODAY_FOCUS, 0L).coerceAtLeast(0L),
            todayKey = prefs.getString(KEY_TODAY_KEY, "").orEmpty(),
        )
    }

    /** 计时状态的变化不频繁，用 commit 保证「按下开始之后立刻被杀进程」也不会丢。 */
    fun saveState(state: PomodoroState) {
        prefs.edit()
            .putString(KEY_PHASE, state.phase.name)
            .putBoolean(KEY_RUNNING, state.running)
            .putLong(KEY_END_AT, state.endAtMillis)
            .putLong(KEY_REMAINING, state.remainingMillis)
            .putInt(KEY_ROUNDS_DONE, state.roundsDone)
            .putInt(KEY_TODAY_COUNT, state.todayCount)
            .putLong(KEY_TODAY_FOCUS, state.todayFocusMillis)
            .putString(KEY_TODAY_KEY, state.todayKey)
            .commit()
    }

    private companion object {
        const val KEY_PHASE = "phase"
        const val KEY_RUNNING = "running"
        const val KEY_END_AT = "end_at"
        const val KEY_REMAINING = "remaining"
        const val KEY_ROUNDS_DONE = "rounds_done"
        const val KEY_TODAY_COUNT = "today_count"
        const val KEY_TODAY_FOCUS = "today_focus"
        const val KEY_TODAY_KEY = "today_key"
        const val KEY_FOCUS_MIN = "focus_min"
        const val KEY_BREAK_MIN = "break_min"
        const val KEY_LONG_BREAK_MIN = "long_break_min"
        const val KEY_ROUNDS = "rounds"
        const val KEY_AUTO_NEXT = "auto_next"
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
    }
}
