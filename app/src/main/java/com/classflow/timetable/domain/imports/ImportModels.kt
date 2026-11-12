package com.classflow.timetable.domain.imports

import com.classflow.timetable.domain.model.ImportSourceType

/**
 * 导入解析出来的一个上课时段。周次是具体周号集合，写入时再转成位图。
 *
 * [location] 是这个时段**自己**的教室，不是课程级的。
 * 一门课常常周一在 C-5-215、周四在 D-3-321，
 * 把教室只挂在课程上（拼成 "C-5-215 / D-3-321"）就等于把对应关系丢了，
 * 学生看到三个时间三个教室，根本不知道哪个时间去哪儿。
 */
data class ImportedSession(
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val weeks: Set<Int>,
    val location: String? = null,
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
    /** 合并模式里被就地更新的已有课程数（不为 0 说明这次真的补到了东西）。 */
    val updatedCourses: Int = 0,
    /** 这次补回教室的时段数 —— 老数据只有课程级地点、时段是空的那种。 */
    val repairedSessions: Int = 0,
)

/** 解析器的原始产出（还没做冲突检测）。 */
data class ParseResult(
    val courses: List<ImportedCourse>,
    val warnings: List<String> = emptyList(),
    val skippedItems: Int = 0,
)
