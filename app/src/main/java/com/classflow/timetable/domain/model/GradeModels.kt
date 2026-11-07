package com.classflow.timetable.domain.model

import kotlinx.serialization.Serializable

/**
 * 一门课的成绩。
 *
 * 只留用户真正会看的东西：课程名、成绩、等级、绩点、学分。
 * 「成绩」和「等级」并存不是冗余 —— 学校有的课记百分制（92），有的课直接记等级（A+），
 * 两种都可能在同一个人的成绩单里出现，所以两个字段各自可为空。
 */
@Serializable
data class GradeRecord(
    val course: String,
    /** 百分制原文，如 "92"；等级制课程这里是空串。 */
    val score: String = "",
    /** 等级，如 "A+"。百分制课程也能从分数推出来。 */
    val level: String = "",
    /** 绩点。二级制（P/NP）课程没有绩点，就是 null。 */
    val point: Double? = null,
    /** 学分。 */
    val credit: Double? = null,
    /** 开课学期，统一成 "2025-2026-1" 这种键；拿不到就是空串。 */
    val semester: String = "",
    /** 课程性质：必修 / 选修…… */
    val nature: String = "",
    /** 考核方式：考试 / 考查。 */
    val examType: String = "",
    /** 成绩标识：重修 / 补考 / 缓考。正常成绩为空。 */
    val tag: String = "",
) {
    val numericScore: Double? get() = score.trim().toDoubleOrNull()

    val semesterTitle: String get() = gradeSemesterTitle(semester)

    /** 是否计入平均学分绩点：二级制和不合格的不参与加权（不合格绩点是 0，仍占学分，见 [GradeSummary]）。 */
    val countedInGpa: Boolean get() = point != null && (credit ?: 0.0) > 0.0
}

/** 一个学期：教务系统下拉里的 `value` + 显示文字。 */
@Serializable
data class GradeSemester(
    val key: String,
    val title: String = gradeSemesterTitle(key),
)

/** 排名。学校只给「第几名」时 [total] 是 0。 */
@Serializable
data class GradeRank(
    /** 「专业」「班级」这类口径；学校没写就是空串。 */
    val label: String = "",
    val position: Int = 0,
    val total: Int = 0,
) {
    val hasValue: Boolean get() = position > 0

    fun display(): String =
        if (total > 0) "$position / $total" else "第 $position 名"
}

/**
 * 一个学期（或全部学期）的统计。
 *
 * 平均学分绩点按学校《学籍管理规定》第三十二条：
 * GPA = Σ(课程绩点 × 课程学分) / Σ 课程学分。
 * 只统计有绩点的课（二级制课程不计入），学分也只用参与计算的课，
 * 否则分母会被 P/NP 课程撑大，绩点被稀释。
 */
@Serializable
data class GradeSummary(
    val gpa: Double? = null,
    val credits: Double = 0.0,
    val earnedCredits: Double = 0.0,
    val courseCount: Int = 0,
    val failedCount: Int = 0,
) {
    val gpaText: String get() = gpa?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "—"
}

object GradeStats {

    fun of(rows: List<GradeRecord>): GradeSummary {
        if (rows.isEmpty()) return GradeSummary()

        var weighted = 0.0
        var gpaCredits = 0.0
        var earned = 0.0
        var failed = 0

        rows.forEach { row ->
            val credit = row.credit ?: 0.0
            val point = row.point
            if (row.countedInGpa && point != null) {
                weighted += point * credit
                gpaCredits += credit
                if (point > 0.0) earned += credit
                if (point <= 0.0) failed++
            } else if (credit > 0 && point == null && isPassing(row)) {
                // 二级制（P/NP）、「通过」这类没有绩点的课：不算绩点，但拿到学分要算进总学分
                earned += credit
            }
        }

        return GradeSummary(
            gpa = if (gpaCredits > 0) weighted / gpaCredits else null,
            credits = gpaCredits,
            earnedCredits = earned,
            courseCount = rows.size,
            failedCount = failed,
        )
    }

    private fun isPassing(row: GradeRecord): Boolean {
        val numeric = row.numericScore
        if (numeric != null) return numeric >= 60
        val text = row.level.ifBlank { row.score }.uppercase()
        return !(text == "F" || text.contains("不通过") || text.contains("不合格") || text.contains("不及格"))
    }
}

/** `2025-2026-1` → `2025-2026 学年 第 1 学期`；认不出来就原样返回。 */
fun gradeSemesterTitle(key: String): String {
    val parts = key.trim().split('-')
    if (parts.size < 3) return key
    return "${parts[0]}-${parts[1]} 学年 第 ${parts[2]} 学期"
}
