package com.classflow.timetable.widget

import android.content.Context
import com.classflow.timetable.domain.model.DayCourse
import com.classflow.timetable.domain.model.dayOfWeekLabel
import com.classflow.timetable.domain.usecase.ScheduleBuilder
import com.classflow.timetable.domain.usecase.WeekResolver
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 小组件上的一行（已把「第几节 / 几点到几点 / 教室」全部算好，渲染层不做业务）。 */
internal data class WidgetCourse(
    val name: String,
    /** "1-2"，紧凑模式用，比 "第 1-2 节" 省 4 个字符 —— 窄卡片里这 4 个字符就是教室名能不能显示的关键。 */
    val shortSlot: String,
    val fullSlot: String,
    val timeLabel: String,
    /** "08:30"，只放开始时间。 */
    val startLabel: String,
    val location: String?,
    val colorArgb: Long,
    val startMinute: Int,
    val endMinute: Int,
)

/** 小组件一次渲染需要的全部数据。 */
internal data class WidgetSnapshot(
    val dateText: String,
    val weekText: String,
    val courses: List<WidgetCourse>,
    /** 今天还没开始的下一节；全上完了就是 null。 */
    val next: WidgetCourse?,
    val semesterMissing: Boolean = false,
    val week: Int = 0,
    val totalWeeks: Int = 0,
) {
    /** 学期进度 0~1，用于底部进度条。 */
    val progress: Float
        get() = if (totalWeeks <= 0) 0f else (week.toFloat() / totalWeeks).coerceIn(0f, 1f)

    val progressLabel: String
        get() = if (totalWeeks <= 0) "" else "$week / $totalWeeks 周"

    val progressPercent: String
        get() = if (totalWeeks <= 0) "" else "${(progress * 100).toInt()}%"
}

/**
 * 小组件取数。
 *
 * 关键点：这里和 App 内用的是同一套 WeekResolver / ScheduleBuilder，
 * 所以「单双周过滤」「当前周」「节次时间」三件事在小组件上不需要任何第二份实现，
 * 也就不会出现「App 里没有这节课，小组件上有」这种不一致。
 */
internal object WidgetDataLoader {

    private val dateFormatter = DateTimeFormatter.ofPattern("M/d")

    suspend fun load(
        context: Context,
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now(),
    ): WidgetSnapshot {
        val repository = widgetRepository(context)
        val semester = repository.getActiveSemester()
            ?: return WidgetSnapshot(
                dateText = dateLabel(today),
                weekText = "未设置学期",
                courses = emptyList(),
                next = null,
                semesterMissing = true,
            )

        val weekState = WeekResolver.resolve(semester, today)
        val sessions = repository.observeSchedule(semester.id).first()
        val slots = repository.getTimeSlots()

        val courses = ScheduleBuilder
            .dayCourses(sessions, slots, today.dayOfWeek.value, weekState.week)
            .map { it.toWidgetCourse() }

        val nowMinute = now.hour * 60 + now.minute

        return WidgetSnapshot(
            dateText = dateLabel(today),
            weekText = "第 ${weekState.week} 周" +
                if (weekState.beforeTerm) " · 未开学" else if (weekState.afterTerm) " · 已结课" else "",
            courses = courses,
            next = courses.firstOrNull { it.startMinute > nowMinute },
            week = weekState.week,
            totalWeeks = semester.totalWeeks,
        )
    }

    private fun dateLabel(date: LocalDate): String =
        dayOfWeekLabel(date.dayOfWeek.value) + " · " + date.format(dateFormatter)

    private fun DayCourse.toWidgetCourse() = WidgetCourse(
        name = name,
        shortSlot = if (startSlot == endSlot) "$startSlot" else "$startSlot-$endSlot",
        fullSlot = slotLabel,
        timeLabel = timeLabel,
        startLabel = TimeSlotFormat.of(startTime),
        location = location?.takeIf { it.isNotBlank() },
        colorArgb = colorArgb,
        startMinute = startTime.hour * 60 + startTime.minute,
        endMinute = endTime.hour * 60 + endTime.minute,
    )
}

/** 小组件里只要 "08:30" 这种格式，独立出来避免依赖 domain 层的格式化实现细节。 */
private object TimeSlotFormat {
    fun of(time: LocalTime): String =
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
