package com.classflow.timetable.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** 学期。当前周由 [Semester.weekOverride] + 开学日期共同决定，不单独存字段。 */
data class Semester(
    val id: Long = 0L,
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val parityEnabled: Boolean = true,
    /** 手动指定的当前周；null 表示按日期自动推导。 */
    val weekOverride: Int? = null,
    val isActive: Boolean = false,
)

enum class SlotSection { MORNING, AFTERNOON, EVENING }

/** 节次。节次编号固定 1..N，用户只能改时间，不能改编号，保证历史数据不失效。 */
data class TimeSlot(
    val id: Long = 0L,
    val index: Int,
    val start: LocalTime,
    val end: LocalTime,
    val section: SlotSection,
) {
    val startMinute: Int get() = start.hour * 60 + start.minute
    val endMinute: Int get() = end.hour * 60 + end.minute

    val timeLabel: String get() = "${format(start)}-${format(end)}"

    companion object {
        fun format(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
    }
}

/** 课程（一门课可以有多个上课时段）。 */
data class Course(
    val id: Long = 0L,
    val semesterId: Long,
    val name: String,
    val teacher: String? = null,
    val defaultLocation: String? = null,
    val colorArgb: Long = DEFAULT_COLOR,
    val note: String? = null,
    val importSourceId: Long? = null,
    val dedupeKey: String? = null,
) {
    companion object {
        const val DEFAULT_COLOR = 0xFF3A5BF0
    }
}

/** 上课时段：星期 + 节次区间 + 周次规则。 */
data class CourseSession(
    val id: Long = 0L,
    val courseId: Long = 0L,
    /** 1 = 周一 ... 7 = 周日 */
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val weekRule: WeekRuleType = WeekRuleType.ALL,
    val weekMask: WeekMask = WeekMask.EMPTY,
    val locationOverride: String? = null,
    val note: String? = null,
) {
    val slotLabel: String
        get() = if (startSlot == endSlot) "第 $startSlot 节" else "第 $startSlot-$endSlot 节"
}

enum class ImportSourceType { MANUAL, ICS, CSV, PROVIDER }

enum class ImportMode { OVERWRITE, MERGE }
