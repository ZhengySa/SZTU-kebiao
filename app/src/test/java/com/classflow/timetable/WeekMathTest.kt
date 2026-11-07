package com.classflow.timetable

import com.classflow.timetable.core.time.WeekMath
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.usecase.WeekResolver
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class WeekMathTest {

    private val start = LocalDate.of(2026, 9, 7) // 周一

    @Test
    fun `start day is week one`() {
        assertThat(WeekMath.weekOf(start, start)).isEqualTo(1)
    }

    @Test
    fun `day before start is week zero`() {
        // 关键：必须用 floorDiv，否则会得到 0 或 -1 造成「第 0 周」
        assertThat(WeekMath.weekOf(start, LocalDate.of(2026, 9, 6))).isEqualTo(0)
    }

    @Test
    fun `same week is still week one`() {
        assertThat(WeekMath.weekOf(start, LocalDate.of(2026, 9, 13))).isEqualTo(1)
    }

    @Test
    fun `next monday starts week two`() {
        assertThat(WeekMath.weekOf(start, LocalDate.of(2026, 9, 14))).isEqualTo(2)
    }

    @Test
    fun `monday of week is computed from start date`() {
        assertThat(WeekMath.mondayOfWeek(start, 3)).isEqualTo(LocalDate.of(2026, 9, 21))
        assertThat(WeekMath.dateOf(start, 3, 5)).isEqualTo(LocalDate.of(2026, 9, 25))
    }

    @Test
    fun `resolver clamps before term`() {
        val semester = Semester(
            id = 1,
            name = "测试学期",
            startDate = start,
            totalWeeks = 20,
        )
        val state = WeekResolver.resolve(semester, LocalDate.of(2026, 8, 20))
        assertThat(state.week).isEqualTo(1)
        assertThat(state.beforeTerm).isTrue()
    }

    @Test
    fun `resolver clamps after term`() {
        val semester = Semester(id = 1, name = "测试学期", startDate = start, totalWeeks = 20)
        val state = WeekResolver.resolve(semester, LocalDate.of(2027, 6, 1))
        assertThat(state.week).isEqualTo(20)
        assertThat(state.afterTerm).isTrue()
    }

    @Test
    fun `manual override wins over date`() {
        val semester = Semester(
            id = 1,
            name = "测试学期",
            startDate = start,
            totalWeeks = 20,
            weekOverride = 7,
        )
        val state = WeekResolver.resolve(semester, start)
        assertThat(state.week).isEqualTo(7)
        assertThat(state.isOverridden).isTrue()
    }
}
