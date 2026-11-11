package com.classflow.timetable.domain.usecase

import com.classflow.timetable.domain.imports.CsvParser
import com.classflow.timetable.domain.imports.IcsParser
import com.classflow.timetable.domain.imports.ImportConflict
import com.classflow.timetable.domain.imports.ImportPreview
import com.classflow.timetable.domain.imports.ImportedCourse
import com.classflow.timetable.domain.imports.ParseResult
import com.classflow.timetable.domain.imports.ImportSupport
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.ImportSourceType
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.model.ScheduledSession
import com.classflow.timetable.domain.model.dayOfWeekLabel
import com.classflow.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 解析导入内容并做冲突检测，结果只用于预览，不写库。
 *
 * 冲突规则：同一天、节次区间有重叠、周次有交集 ⇒ 冲突。
 * 周次比较走位图 `intersects`，一次位与就出结果。
 */
class PrepareImportUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {

    suspend operator fun invoke(text: String, type: ImportSourceType): ImportPreview {
        val semester = repository.getActiveSemester()
            ?: return ImportPreview(
                sourceType = type,
                courses = emptyList(),
                warnings = listOf("还没有学期，请先到「设置 → 学期」设置开学日期"),
            )

        val parsed: ParseResult = when (type) {
            ImportSourceType.ICS -> IcsParser.parse(text, semester, repository.getTimeSlots())
            ImportSourceType.CSV -> CsvParser.parse(text, semester)
            // 手动录入与教务适配器不走文本解析
            ImportSourceType.MANUAL, ImportSourceType.PROVIDER -> ParseResult(emptyList())
        }

        val existing = repository.observeSchedule(semester.id).first()
        val conflicts = detectConflicts(parsed, existing)

        return ImportPreview(
            sourceType = type,
            courses = parsed.courses,
            conflicts = conflicts,
            warnings = parsed.warnings,
            skippedCourses = parsed.skippedItems,
        )
    }

    /**
     * 教务系统同步专用：课程已经是结构化的，不需要再解析文本，
     * 但仍然要走同一套冲突检测，保证两条路径给出的提示一致。
     */
    suspend fun fromCourses(
        courses: List<ImportedCourse>,
        type: ImportSourceType = ImportSourceType.PROVIDER,
        warnings: List<String> = emptyList(),
    ): ImportPreview {
        val semester = repository.getActiveSemester()
            ?: return ImportPreview(
                sourceType = type,
                courses = emptyList(),
                warnings = listOf("还没有学期，请先到「设置 → 学期」设置开学日期"),
            )
        val existing = repository.observeSchedule(semester.id).first()
        return ImportPreview(
            sourceType = type,
            courses = courses,
            conflicts = detectConflicts(ParseResult(courses), existing),
            warnings = warnings,
        )
    }

    private fun detectConflicts(
        parsed: ParseResult,
        existing: List<ScheduledSession>,
    ): List<ImportConflict> {
        if (existing.isEmpty()) return emptyList()

        val conflicts = mutableListOf<ImportConflict>()
        parsed.courses.forEach { course ->
            course.sessions.forEach { session ->
                val importedMask = com.classflow.timetable.domain.model.WeekMask.fromWeeks(session.weeks)
                existing.forEach { current ->
                    // 同名就是同一门课：重新同步 / 重新导入时，自己和自己必然重叠，
                    // 报出来只会让人以为课表冲突了，实际上什么都没变
                    if (current.courseName == course.name) return@forEach
                    val slotOverlap = session.startSlot <= current.endSlot && current.startSlot <= session.endSlot
                    if (current.dayOfWeek == session.dayOfWeek &&
                        slotOverlap &&
                        current.weekMask.intersects(importedMask)
                    ) {
                        conflicts += ImportConflict(
                            courseName = course.name,
                            detail = "${dayOfWeekLabel(session.dayOfWeek)} 第 ${session.startSlot}-${session.endSlot} 节" +
                                " 与「${current.courseName}」（${current.weekMask.describe(60)}）重叠",
                        )
                    }
                }
            }
        }
        return conflicts.distinctBy { it.courseName to it.detail }
    }
}

/** 用户点「确认导入」后的落库动作。 */
class ApplyImportUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {

    suspend operator fun invoke(
        preview: ImportPreview,
        mode: ImportMode,
        sourceName: String,
        ownerKey: String = OwnerKey.SELF,
    ) = run {
        val semester = repository.getActiveSemester()
            ?: error("没有可用的学期")
        repository.importCourses(
            semesterId = semester.id,
            courses = preview.courses,
            mode = mode,
            sourceType = preview.sourceType,
            sourceName = sourceName,
            ownerKey = ownerKey,
        )
    }
}

/** 给界面提示用：把周次集合说成人话。 */
internal fun describeWeeks(weeks: Set<Int>): String =
    com.classflow.timetable.domain.model.WeekMask.fromWeeks(weeks).describe(60)

internal fun slotsLabelOf(start: Int, end: Int): String =
    if (start == end) "第 $start 节" else "第 $start-$end 节"

/** 供 CSV 模板说明页复用：把示例文本集中在一处，避免文案漂移。 */
object ImportTemplates {
    const val CSV_HEADER = "课程名,教师,地点,星期,开始节,结束节,周次,备注"
    const val CSV_SAMPLE_1 = "高等数学,张老师,教三-201,周一,1,2,1-16,带计算器"
    const val CSV_SAMPLE_2 = "大学英语,李老师,外语楼-305,周三,3,4,1-16双,"
    const val CSV_SAMPLE_3 = "体育,王老师,体育馆,周五,7,8,1-8,选修"

    val CSV_TEMPLATE: String = listOf(CSV_HEADER, CSV_SAMPLE_1, CSV_SAMPLE_2, CSV_SAMPLE_3)
        .joinToString("\n")
}
