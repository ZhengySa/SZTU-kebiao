package com.classflow.timetable

import com.classflow.timetable.domain.pomodoro.PomodoroPhase
import com.classflow.timetable.domain.pomodoro.PomodoroRules
import com.classflow.timetable.domain.pomodoro.PomodoroSettings
import com.classflow.timetable.domain.pomodoro.PomodoroState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 番茄钟的计时数学。
 *
 * 这里测的全是「不依赖当前时间」的纯函数：一段该多长、走完进哪一段、
 * 长休息什么时候来、跨天计数清不清零。计时器最怕的不是算错秒，是**轮次搞错**——
 * 该长休息的时候给了 5 分钟，用户连着做四个小时才发现。
 */
class PomodoroRulesTest {

    private val settings = PomodoroSettings(
        focusMinutes = 25,
        breakMinutes = 5,
        longBreakMinutes = 15,
        roundsPerCycle = 4,
    )

    private fun state(
        phase: PomodoroPhase = PomodoroPhase.FOCUS,
        rounds: Int = 0,
        todayCount: Int = 0,
        todayFocusMillis: Long = 0L,
    ) = PomodoroState(
        phase = phase,
        roundsDone = rounds,
        todayCount = todayCount,
        todayFocusMillis = todayFocusMillis,
        remainingMillis = PomodoroRules.durationMillis(phase, settings, rounds),
    )

    @Test
    fun `专注 25 分钟，短休息 5 分钟`() {
        assertEquals(25 * 60_000L, PomodoroRules.durationMillis(PomodoroPhase.FOCUS, settings, 0))
        assertEquals(5 * 60_000L, PomodoroRules.durationMillis(PomodoroPhase.BREAK, settings, 1))
        assertEquals(5 * 60_000L, PomodoroRules.durationMillis(PomodoroPhase.BREAK, settings, 3))
    }

    @Test
    fun `做满四个之后休息变成 15 分钟`() {
        assertFalse(PomodoroRules.isLongBreak(3, settings))
        assertTrue(PomodoroRules.isLongBreak(4, settings))
        assertEquals(15 * 60_000L, PomodoroRules.durationMillis(PomodoroPhase.BREAK, settings, 4))
    }

    @Test
    fun `第四段专注走完进的是长休息`() {
        val completion = PomodoroRules.complete(state(rounds = 3), settings)
        assertEquals(PomodoroPhase.BREAK, completion.next)
        assertTrue(completion.nextIsLongBreak)
        assertEquals(15 * 60_000L, completion.state.remainingMillis)
    }

    @Test
    fun `长休息结束后轮次清零，从头再来一轮`() {
        val afterFourth = PomodoroRules.complete(state(rounds = 3), settings).state
        assertEquals(4, afterFourth.roundsDone)

        val afterLongBreak = PomodoroRules.complete(afterFourth, settings).state
        assertEquals(PomodoroPhase.FOCUS, afterLongBreak.phase)
        assertEquals(0, afterLongBreak.roundsDone)
        assertEquals(25 * 60_000L, afterLongBreak.remainingMillis)
    }

    @Test
    fun `短休息结束不清零，轮次接着往上走`() {
        val afterFirst = PomodoroRules.complete(state(rounds = 0), settings).state
        assertEquals(1, afterFirst.roundsDone)

        val afterBreak = PomodoroRules.complete(afterFirst, settings).state
        assertEquals(1, afterBreak.roundsDone)
        assertEquals(PomodoroPhase.FOCUS, afterBreak.phase)
    }

    @Test
    fun `只有专注段计入今日统计`() {
        val focusDone = PomodoroRules.complete(state(todayCount = 2, todayFocusMillis = 50 * 60_000L), settings)
        assertEquals(3, focusDone.state.todayCount)
        assertEquals(75 * 60_000L, focusDone.state.todayFocusMillis)

        val breakDone = PomodoroRules.complete(focusDone.state, settings)
        assertEquals(3, breakDone.state.todayCount)
        assertEquals(75 * 60_000L, breakDone.state.todayFocusMillis)
    }

    @Test
    fun `走完一段之后停在待开始，不会自己往前跑`() {
        val completion = PomodoroRules.complete(state(), settings)
        assertFalse(completion.state.running)
        assertEquals(0L, completion.state.endAtMillis)
    }

    @Test
    fun `跳过不计入统计，但照样进入下一段`() {
        val skipped = PomodoroRules.skip(state(rounds = 1, todayCount = 1), settings)
        assertEquals(PomodoroPhase.BREAK, skipped.phase)
        assertEquals(2, skipped.roundsDone)
        assertEquals(1, skipped.todayCount)
        assertFalse(skipped.running)
    }

    @Test
    fun `运行中按结束时刻算剩余，暂停时读存下来的值`() {
        val running = state().copy(running = true, endAtMillis = 1_000_000L, remainingMillis = 999L)
        assertEquals(400_000L, PomodoroRules.remainingMillis(running, nowMillis = 600_000L))
        // 过点了也不能给负数，圆环会画反
        assertEquals(0L, PomodoroRules.remainingMillis(running, nowMillis = 1_200_000L))

        val paused = running.copy(running = false, remainingMillis = 123_000L)
        assertEquals(123_000L, PomodoroRules.remainingMillis(paused, nowMillis = 9_999_999L))
    }

    @Test
    fun `秒表文本向上取整，开始那一刻是完整的 25_00`() {
        assertEquals("25:00", PomodoroRules.clockText(25 * 60_000L))
        assertEquals("25:00", PomodoroRules.clockText(24 * 60_000L + 59_600L))
        assertEquals("24:59", PomodoroRules.clockText(24 * 60_000L + 59_000L))
        assertEquals("0:07", PomodoroRules.clockText(6_200L))
        assertEquals("0:00", PomodoroRules.clockText(0L))
        assertEquals("0:00", PomodoroRules.clockText(-5L))
        // 90 分钟要显示成 90:00，不能进位成 1:30:00
        assertEquals("90:00", PomodoroRules.clockText(90 * 60_000L))
    }

    @Test
    fun `圆环进度从 0 走到 1，不会越界`() {
        assertEquals(0f, PomodoroRules.progress(1_500_000L, 1_500_000L), 0.0001f)
        assertEquals(0.5f, PomodoroRules.progress(750_000L, 1_500_000L), 0.0001f)
        assertEquals(1f, PomodoroRules.progress(0L, 1_500_000L), 0.0001f)
        assertEquals(0f, PomodoroRules.progress(0L, 0L), 0.0001f)
    }

    @Test
    fun `跨天把今日清零，同一天不动`() {
        val today = LocalDate.of(2026, 11, 12)
        val before = state(todayCount = 3, todayFocusMillis = 75 * 60_000L)
            .copy(todayKey = today.toString())

        val sameDay = PomodoroRules.rollover(before, today)
        assertEquals(3, sameDay.todayCount)

        val nextDay = PomodoroRules.rollover(before, today.plusDays(1))
        assertEquals(0, nextDay.todayCount)
        assertEquals(0L, nextDay.todayFocusMillis)
        assertEquals(today.plusDays(1).toString(), nextDay.todayKey)
    }

    @Test
    fun `改时长不影响已经数过的轮次`() {
        val longer = settings.copy(focusMinutes = 45)
        val completion = PomodoroRules.complete(state(rounds = 2), longer)
        assertEquals(2 + 1, completion.state.roundsDone)
        // 下一段是休息，时长跟专注时长无关
        assertEquals(5 * 60_000L, completion.state.remainingMillis)
    }
}
