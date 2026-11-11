package com.classflow.timetable.domain.usecase

import com.classflow.timetable.data.remote.sztu.ZfKbParser
import com.classflow.timetable.domain.imports.ImportedCourse
import com.classflow.timetable.domain.imports.ImportedSession

/**
 * 把教务解析出来的一条条「格子」聚合成 App 的课程模型。
 *
 * 单独拿出来是因为这里是出过错的地方，必须能单测：
 *
 * 一门课在教务里常常是好几条独立记录 —— 周二晚上在 D-3-321，周三下午在 C-5-215，
 * 甚至同一节换个周次就换教室（「智能制造实践」第 3 周在 D-3-203A、第 4~13 周在 D-3-115、
 * 第 14 周在 C-5-304）。这些都靠 [ZfKbParser.ZfEntry] 自己带着 room 区分。
 *
 * 曾经的写法是把所有教室 `distinct().joinToString(" / ")` 挂在课程上，
 * 于是三个时段共享同一串地点，学生看到「三个时间三个教室」却对不上号。
 * 现在的规则：**地点属于时段**，课程级地点只在所有时段都相同时才填。
 */
internal object SztuCourseAggregator {

    fun aggregate(entries: List<ZfKbParser.ZfEntry>): List<ImportedCourse> =
        entries
            .groupBy { it.name }
            .map { (name, group) ->
                val sessions = group
                    .map { entry ->
                        ImportedSession(
                            dayOfWeek = entry.dayOfWeek,
                            startSlot = entry.startSlot,
                            endSlot = entry.endSlot,
                            weeks = entry.weeks,
                            location = entry.room?.trim()?.takeIf { it.isNotEmpty() }
                                ?: entry.building?.trim()?.takeIf { it.isNotEmpty() },
                        )
                    }
                    // 地点也进 key：同一天同一节但换了教室（临时调课）不该被当成重复丢掉
                    .distinctBy { session ->
                        "${session.dayOfWeek}-${session.startSlot}-${session.endSlot}-" +
                            "${session.weeks.sorted()}-${session.location}"
                    }

                // 所有时段同地点才写课程级地点；有分歧就留空，让每个时段自己说
                val sharedLocation = sessions
                    .map { it.location }
                    .distinct()
                    .singleOrNull()
                    ?.takeIf { sessions.all { session -> session.location != null } }

                ImportedCourse(
                    name = name,
                    teacher = group.mapNotNull { it.teacher }.distinct()
                        .joinToString("/").ifBlank { null },
                    location = sharedLocation,
                    note = group.mapNotNull { it.className }.distinct()
                        .joinToString("、", prefix = "教学班：")
                        .takeIf { group.any { it.className != null } },
                    sessions = sessions,
                )
            }
            .filter { it.sessions.isNotEmpty() }
}
