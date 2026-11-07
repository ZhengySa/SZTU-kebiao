package com.classflow.timetable.domain.imports

import com.classflow.timetable.domain.model.ImportSourceType

/** 导入解析出来的一个上课时段。周次是具体周号集合，写入时再转成位图。 */
data class ImportedSession(
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val weeks: Set<Int>,
)

/** 导入解析出来的一门课（同名课程的多个时段已经聚合到一起）。 */
data class ImportedCourse(
    val name: String,
    val teacher: String? = null,
    val location: String? = null,
    val note: String? = null,
    val sessions: List<ImportedSession> = emptyList(),
)

/** 与现有课表的冲突提示（不阻塞导入，只提示）。 */
data class ImportConflict(
    val courseName: String,
    val detail: String,
)

/**
 * 导入预览：解析完成但还没写库的状态。
 * 只有用户确认后才会走 [ApplyImportUseCase]，这样「导入前预览」是真的预览，
 * 不会出现「先写进去再回滚」这种危险实现。
 */
data class ImportPreview(
    val sourceType: ImportSourceType,
    val courses: List<ImportedCourse>,
    val conflicts: List<ImportConflict> = emptyList(),
    val warnings: List<String> = emptyList(),
    val skippedCourses: Int = 0,
) {
    val sessionCount: Int get() = courses.sumOf { it.sessions.size }
    val isEmpty: Boolean get() = courses.isEmpty()
}

/** 导入结果。 */
data class ImportOutcome(
    val insertedCourses: Int,
    val insertedSessions: Int,
    val skippedCourses: Int,
    val mode: com.classflow.timetable.domain.model.ImportMode,
    val warnings: List<String> = emptyList(),
)

/** 解析器的原始产出（还没做冲突检测）。 */
data class ParseResult(
    val courses: List<ImportedCourse>,
    val warnings: List<String> = emptyList(),
    val skippedItems: Int = 0,
)
