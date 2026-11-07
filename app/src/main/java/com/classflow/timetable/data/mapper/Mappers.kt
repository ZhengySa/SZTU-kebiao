package com.classflow.timetable.data.mapper

import com.classflow.timetable.data.local.CourseEntity
import com.classflow.timetable.data.local.CourseSessionEntity
import com.classflow.timetable.data.local.ScheduleRow
import com.classflow.timetable.data.local.SemesterEntity
import com.classflow.timetable.data.local.TimeSlotEntity
import com.classflow.timetable.domain.model.Course
import com.classflow.timetable.domain.model.CourseSession
import com.classflow.timetable.domain.model.ScheduledSession
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.model.SlotSection
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.domain.model.WeekMask
import com.classflow.timetable.domain.model.WeekRuleType
import java.time.LocalTime

// ---------- 学期 ----------
fun SemesterEntity.toDomain() = Semester(
    id = id,
    name = name,
    startDate = startDate,
    totalWeeks = totalWeeks,
    parityEnabled = parityEnabled,
    weekOverride = weekOverride,
    isActive = isActive,
)

fun Semester.toEntity(now: Long = System.currentTimeMillis()) = SemesterEntity(
    id = id,
    name = name,
    startDate = startDate,
    totalWeeks = totalWeeks,
    parityEnabled = parityEnabled,
    weekOverride = weekOverride,
    isActive = isActive,
    createdAt = now,
    updatedAt = now,
)

// ---------- 节次 ----------
fun TimeSlotEntity.toDomain() = TimeSlot(
    id = id,
    index = slotIndex,
    start = minutesToTime(startMinute),
    end = minutesToTime(endMinute),
    section = SlotSection.entries.firstOrNull { it.name == section } ?: SlotSection.MORNING,
)

fun TimeSlot.toEntity() = TimeSlotEntity(
    id = id,
    slotIndex = index,
    startMinute = startMinute,
    endMinute = endMinute,
    section = section.name,
)

private fun minutesToTime(minutes: Int): LocalTime =
    LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

// ---------- 课程 ----------
fun CourseEntity.toDomain() = Course(
    id = id,
    semesterId = semesterId,
    name = name,
    teacher = teacher,
    defaultLocation = defaultLocation,
    colorArgb = colorArgb,
    note = note,
    importSourceId = importSourceId,
    dedupeKey = dedupeKey,
)

fun Course.toEntity(now: Long = System.currentTimeMillis()) = CourseEntity(
    id = id,
    semesterId = semesterId,
    name = name,
    teacher = teacher,
    defaultLocation = defaultLocation,
    colorArgb = colorArgb,
    note = note,
    importSourceId = importSourceId,
    dedupeKey = dedupeKey,
    createdAt = now,
    updatedAt = now,
)

// ---------- 上课时段 ----------
fun CourseSessionEntity.toDomain() = CourseSession(
    id = id,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startSlot = startSlot,
    endSlot = endSlot,
    weekRule = WeekRuleType.safeOf(weekRule),
    weekMask = WeekMask(weekMask),
    locationOverride = locationOverride,
    note = note,
)

fun CourseSession.toEntity(courseId: Long, now: Long = System.currentTimeMillis()) = CourseSessionEntity(
    id = id,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startSlot = startSlot,
    endSlot = endSlot,
    weekRule = weekRule.name,
    weekMask = weekMask.bits,
    locationOverride = locationOverride,
    note = note,
    createdAt = now,
    updatedAt = now,
)

// ---------- 课表 join 行 ----------
fun ScheduleRow.toDomain() = ScheduledSession(
    sessionId = sessionId,
    courseId = courseId,
    courseName = courseName,
    teacher = teacher,
    colorArgb = colorArgb,
    dayOfWeek = dayOfWeek,
    startSlot = startSlot,
    endSlot = endSlot,
    weekRule = WeekRuleType.safeOf(weekRule),
    weekMask = WeekMask(weekMask),
    location = location,
    note = note,
)
