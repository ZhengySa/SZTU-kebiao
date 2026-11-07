package com.classflow.timetable.domain.usecase

import com.classflow.timetable.domain.model.DayCourse
import com.classflow.timetable.domain.model.ScheduledSession
import com.classflow.timetable.domain.model.TimeSlot
import java.time.LocalTime

/**
 * 把「时段 + 节次表 + 目标周」组装成可直接渲染的课程块。
 *
 * 时间一律现算：用户在设置里把第 3 节从 10:00 改成 10:30，周课表、今日页、小组件会同时变化，
 * 因为这里从来没有把时间字符串写死在数据里。
 */
object ScheduleBuilder {

    fun dayCourses(
        sessions: List<ScheduledSession>,
        slots: List<TimeSlot>,
        dayOfWeek: Int,
        week: Int,
    ): List<DayCourse> {
        val slotMap = slots.associateBy { it.index }
        return sessions
            .asSequence()
            .filter { it.dayOfWeek == dayOfWeek && week in it.weekMask }
            .sortedBy { it.startSlot }
            .map { it.toDayCourse(slotMap) }
            .toList()
    }

    /** 一整周，按 1..7 分组，每组内部按起始节次排序。 */
    fun weekCourses(
        sessions: List<ScheduledSession>,
        slots: List<TimeSlot>,
        week: Int,
    ): Map<Int, List<DayCourse>> {
        val slotMap = slots.associateBy { it.index }
        return (1..7).associateWith { day ->
            sessions
                .asSequence()
                .filter { it.dayOfWeek == day && week in it.weekMask }
                .sortedBy { it.startSlot }
                .map { it.toDayCourse(slotMap) }
                .toList()
        }
    }

    private fun ScheduledSession.toDayCourse(slotMap: Map<Int, TimeSlot>) = DayCourse(
        sessionId = sessionId,
        courseId = courseId,
        name = courseName,
        teacher = teacher,
        location = location,
        colorArgb = colorArgb,
        dayOfWeek = dayOfWeek,
        startSlot = startSlot,
        endSlot = endSlot,
        startTime = startTimeOf(slotMap, startSlot),
        endTime = endTimeOf(slotMap, endSlot),
        weekMask = weekMask,
        note = note,
    )

    /** 节次表被删改时的兜底：按 45 分钟一节从 08:00 推，保证不崩、不显示空白。 */
    private fun startTimeOf(slotMap: Map<Int, TimeSlot>, index: Int): LocalTime =
        slotMap[index]?.start ?: LocalTime.of(8, 0).plusMinutes((index - 1).toLong() * 45)

    private fun endTimeOf(slotMap: Map<Int, TimeSlot>, index: Int): LocalTime =
        slotMap[index]?.end ?: LocalTime.of(8, 45).plusMinutes((index - 1).toLong() * 45)
}
