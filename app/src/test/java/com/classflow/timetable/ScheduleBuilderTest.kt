package com.classflow.timetable

import com.classflow.timetable.domain.model.SlotSection
import com.classflow.timetable.domain.model.ScheduledSession
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.domain.model.WeekMask
import com.classflow.timetable.domain.model.WeekRuleType
import com.classflow.timetable.domain.usecase.ScheduleBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

class ScheduleBuilderTest {

    private val slots = listOf(
        slot(1, 8, 0, 8, 45),
        slot(2, 8, 55, 9, 40),
        slot(3, 10, 0, 10, 45),
        slot(4, 10, 55, 11, 40),
    )

    @Test
    fun `filters out courses not in target week`() {
        val oddOnly = session(id = 1, day = 1, start = 1, end = 2, rule = WeekRuleType.ODD)
        val everyWeek = session(id = 2, day = 1, start = 3, end = 4, rule = WeekRuleType.ALL)

        val week2 = ScheduleBuilder.dayCourses(listOf(oddOnly, everyWeek), slots, dayOfWeek = 1, week = 2)
        assertThat(week2.map { it.courseId }).containsExactly(2L)

        val week3 = ScheduleBuilder.dayCourses(listOf(oddOnly, everyWeek), slots, dayOfWeek = 1, week = 3)
        assertThat(week3.map { it.courseId }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `sorts by start slot`() {
        val late = session(id = 1, day = 3, start = 3, end = 4)
        val early = session(id = 2, day = 3, start = 1, end = 2)

        val result = ScheduleBuilder.dayCourses(listOf(late, early), slots, dayOfWeek = 3, week = 1)
        assertThat(result.map { it.courseId }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `resolves start and end time from slot table`() {
        val single = session(id = 1, day = 2, start = 2, end = 3)
        val result = ScheduleBuilder.dayCourses(listOf(single), slots, dayOfWeek = 2, week = 1).single()

        assertThat(result.startTime).isEqualTo(LocalTime.of(8, 55))
        assertThat(result.endTime).isEqualTo(LocalTime.of(10, 45))
        assertThat(result.slotLabel).isEqualTo("第 2-3 节")
        assertThat(result.timeLabel).isEqualTo("08:55-10:45")
    }

    @Test
    fun `week map always has seven days`() {
        val onlyMonday = session(id = 1, day = 1, start = 1, end = 2)
        val week = ScheduleBuilder.weekCourses(listOf(onlyMonday), slots, week = 1)

        assertThat(week.keys).containsExactly(1, 2, 3, 4, 5, 6, 7)
        assertThat(week[1]).hasSize(1)
        assertThat(week[2]).isEmpty()
    }

    private fun slot(index: Int, sh: Int, sm: Int, eh: Int, em: Int) = TimeSlot(
        id = index.toLong(),
        index = index,
        start = LocalTime.of(sh, sm),
        end = LocalTime.of(eh, em),
        section = SlotSection.MORNING,
    )

    private fun session(
        id: Long,
        day: Int,
        start: Int,
        end: Int,
        rule: WeekRuleType = WeekRuleType.ALL,
    ) = ScheduledSession(
        sessionId = id,
        courseId = id,
        courseName = "课程$id",
        teacher = "老师",
        colorArgb = 0xFF3A5BF0,
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        weekRule = rule,
        weekMask = WeekMask.of(rule, emptySet(), 16),
        location = "教三-201",
        note = null,
    )
}
