package com.classflow.timetable.domain.model

import java.time.LocalTime

/** 当前周的计算结果。 */
data class WeekState(
    val week: Int,
    val isOverridden: Boolean = false,
    val beforeTerm: Boolean = false,
    val afterTerm: Boolean = false,
)

/**
 * 某个具体日期上的一节课（已把节次时间换算好），
 * 今日页、周课表、小组件共用同一个模型，避免三处各写一套时间逻辑。
 */
data class DayCourse(
    val sessionId: Long,
    val courseId: Long,
    val name: String,
    val teacher: String?,
    val location: String?,
    val colorArgb: Long,
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val weekMask: WeekMask,
    val note: String?,
) {
    val slotLabel: String
        get() = if (startSlot == endSlot) "第 $startSlot 节" else "第 $startSlot-$endSlot 节"

    val timeLabel: String
        get() = "${TimeSlot.format(startTime)}-${TimeSlot.format(endTime)}"
}

/**
 * 一节「时段」的完整信息（课程 + 时段 join 之后的结果），尚未换算时间。
 * 仓库层只吐这个模型，时间换算在 [com.classflow.timetable.domain.usecase.ScheduleBuilder] 里做，
 * 这样「改节次时间 → 全 App 立刻正确」是自动成立的。
 */
data class ScheduledSession(
    val sessionId: Long,
    val courseId: Long,
    val courseName: String,
    val teacher: String?,
    val colorArgb: Long,
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val weekRule: WeekRuleType,
    val weekMask: WeekMask,
    val location: String?,
    val note: String?,
)

/** 冲突提示（不是异常，只是 UI 上的黄色提醒）。 */
data class Conflict(
    val existingCourseName: String,
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val weeksDescription: String,
) {
    val message: String
        get() {
            val slot = if (startSlot == endSlot) "第 $startSlot 节" else "第 $startSlot-$endSlot 节"
            return "与「$existingCourseName」${dayOfWeekLabel(dayOfWeek)} $slot（$weeksDescription）冲突"
        }
}

/** 课程 + 它的全部时段，详情页与编辑页使用。 */
data class CourseDetail(
    val course: Course,
    val sessions: List<CourseSession>,
    val semester: Semester,
    val timeSlots: List<TimeSlot>,
)

fun dayOfWeekLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    7 -> "周日"
    else -> "周?"
}

val DAY_LABELS: List<String> = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
