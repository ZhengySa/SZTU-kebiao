package com.classflow.timetable.data.remote.sztu

import com.classflow.timetable.domain.model.GradeRank
import com.classflow.timetable.domain.model.GradeRecord
import com.classflow.timetable.domain.model.GradeSemester
import com.classflow.timetable.domain.model.gradeSemesterTitle
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 正方新版教务（jsxsd）「学生成绩查询」解析器。
 *
 * 页面地址：`/jsxsd/kscj/cjcx_list`，带 `?kksj=2025-2026-1` 只看一个学期。
 *
 * 真实页面（深技大，已用真账号抓下来核对）的列是：
 * ```
 * 序号 | 开课学期 | 课程编号 | 课程名称 | 成绩 | 总评成绩 | 成绩标识 |
 * 学分 | 总学时 | 绩点 | 补重学期 | 考核方式 | 考试性质 | 课程属性 | 课程性质 | 通选课类别
 * ```
 * 两个坑：
 *  · **「成绩」是百分制分数，「总评成绩」才是等级（A+/A/B…）** —— 等级列不叫「等级」。
 *  · 页面顶部一行文字里有全部官方统计：`所修门数 / 所修总学分 / 平均学分绩点 / 已获得学分 /
 *    专业绩点排名/专业总人数`。排名只能从这行拿 —— 表格里没有名次，也不能自己算。
 *
 * 不按固定列号取值：不同学校版本列数不一样，先按表头文字认列，认不出来才退回经典列位。
 */
object ZfGradeParser {

    /** 「全部」这一档在 UI 里是一个特殊的值，不写进学期键。 */
    const val ALL_SEMESTERS = ""

    data class Result(
        val semesters: List<GradeSemester> = emptyList(),
        val rows: List<GradeRecord> = emptyList(),
        /** 教务系统自己给出的官方统计；拿不到就是 null。 */
        val summary: PageSummary? = null,
        val rank: GradeRank? = null,
    )

    /** 页面顶部那行官方数字。 */
    data class PageSummary(
        val gpa: Double? = null,
        val averageScore: Double? = null,
        val credits: Double = 0.0,
        val earnedCredits: Double = 0.0,
        val courseCount: Int = 0,
    )

    /** 深圳技术大学《学籍管理规定》第三十一条的等级绩点表。 */
    private val LEVEL_POINTS = mapOf(
        "A+" to 4.5, "A" to 4.0, "B+" to 3.5, "B" to 3.0,
        "C+" to 2.5, "C" to 2.0, "D" to 1.0, "F" to 0.0,
    )

    private val SEMESTER_IN_TEXT = Regex("""(\d{4}-\d{4}-[0-9])""")
    private val LETTER = Regex("""^[A-Fa-f][+-]?$""")
    private val NUMERIC = Regex("""^\d{1,3}(\.\d+)?$""")

    private val GPA_TEXT = Regex("""平均学分绩点\s*[:：]\s*(\d+(?:\.\d+)?)""")
    private val AVG_SCORE_TEXT = Regex("""平均学分绩(?!点)\s*[:：]\s*(\d+(?:\.\d+)?)""")
    private val CREDITS_TEXT = Regex("""所修总学分\s*[:：]\s*(\d+(?:\.\d+)?)""")
    private val EARNED_TEXT = Regex("""已获得学分\s*[:：]\s*(\d+(?:\.\d+)?)""")
    private val COUNT_TEXT = Regex("""所修门数\s*[:：]\s*(\d+)""")

    /** 专业绩点排名/专业总人数:95/157 */
    private val RANK_SLASH = Regex("""(专业|班级|年级|学院|行政班)?[^\s]{0,12}?排名[^0-9]{0,24}?(\d+)\s*/\s*(\d+)""")
    private val RANK_PLAIN = Regex("""(专业|班级|年级|学院|行政班)?\s*排名\s*[:：]?\s*第?\s*(\d+)""")
    private val RANK_ORDINAL = Regex("""第\s*(\d+)\s*名""")

    /** 只有认得出这是成绩页，才值得往下解析；否则说明会话被踢回登录页了。 */
    fun hasGradeTable(html: String): Boolean =
        html.contains("id=\"dataList\"") ||
            (html.contains("课程名称") && html.contains("学分") && html.contains("成绩"))

    /** 会话失效时教务系统返回的是它自己的登录页（HTTP 200，不是 302 到统一认证）。 */
    fun isLoginPage(html: String): Boolean =
        html.contains("LoginToXk") ||
            html.contains("id=\"userAccount\"") ||
            html.contains("name=\"userAccount\"") ||
            html.contains("/jsxsd/assets/css/login.css") ||
            html.contains("j_username")

    fun parse(html: String): Result {
        val doc = Jsoup.parse(html)
        val summary = parsePageSummary(doc)
        val rank = parseRank(doc)

        val table = findTable(doc)
            ?: return Result(summary = summary, rank = rank)

        val headers = headersOf(table)
        val colCourse = column(headers, 3, "课程名称", "课程名", exclude = listOf("课程编号", "课程代码"))
        // 「总评成绩」不是分数，所以认「成绩」列时要把含「总评」的表头排除掉
        val colScore = column(headers, 4, "成绩", exclude = listOf("总评", "标识", "绩点", "等级", "性质", "学期"))
        // 等级列在本校叫「总评成绩」，别处可能就叫「等级」
        val colLevel = column(headers, 5, "总评", "等级", minHeaders = 12)
        val colPoint = column(headers, 9, "绩点", minHeaders = 12)
        val colCredit = column(headers, 7, "学分", exclude = listOf("绩点"))
        val colSemester = column(headers, 1, "学期", "学年")
        // 「考试性质」也含「性质」，不排掉就会把"正常考试"当成课程性质
        val colNature = column(headers, 14, "课程属性", "课程性质", "属性", "性质", "类别",
            exclude = listOf("考试"), minHeaders = 12)
        val colExam = column(headers, 11, "考核", "考试方式", minHeaders = 12)
        val colTag = column(headers, 6, "标识", "备注", minHeaders = 12)

        if (colCourse < 0) return Result(summary = summary, rank = rank)

        val rows = bodyRows(table).mapNotNull { tr ->
            parseRow(
                tr = tr,
                colCourse = colCourse,
                colScore = colScore,
                colLevel = colLevel,
                colPoint = colPoint,
                colCredit = colCredit,
                colSemester = colSemester,
                colNature = colNature,
                colExam = colExam,
                colTag = colTag,
            )
        }

        return Result(
            semesters = semestersOf(doc, rows),
            rows = rows,
            summary = summary,
            rank = rank,
        )
    }

    // ---------- 表定位 ----------

    private fun findTable(doc: Document): Element? {
        doc.selectFirst("table#dataList")?.let { return it }
        return doc.select("table").firstOrNull { table ->
            val text = table.text()
            text.contains("课程名称") && text.contains("学分")
        }
    }

    private fun headersOf(table: Element): List<String> {
        table.select("thead tr").lastOrNull()
            ?.let { return it.children().map { clean(it.text()) } }
        table.select("tr").firstOrNull { it.select("th").size >= 3 }
            ?.let { return it.children().map { clean(it.text()) } }
        return emptyList()
    }

    private fun bodyRows(table: Element): List<Element> {
        val rows = table.select("tbody tr").ifEmpty { table.select("tr") }
        return rows.filter { row ->
            val cells = row.children().filter { it.tagName() == "td" }
            cells.size >= 4 && cells.any { it.text().isNotBlank() }
        }
    }

    /**
     * 认列：先按表头文字匹配，匹配不到才退回正方经典列位。
     *
     * [minHeaders] 是"允许回退"的最低列数：等级/绩点/课程性质这些列只在完整的
     * 16 列版本里才有固定位置，列数不够时宁可这一列空着 —— 取错列的后果是
     * 显示一个"看起来很正常"的错值，比空着更糟。
     */
    private fun column(
        headers: List<String>,
        fallback: Int,
        vararg keys: String,
        exclude: List<String> = emptyList(),
        minHeaders: Int = 7,
    ): Int {
        headers.forEachIndexed { index, header ->
            if (keys.any { header.contains(it) } && exclude.none { header.contains(it) }) {
                return index
            }
        }
        if (headers.isEmpty()) return fallback
        if (headers.size >= minHeaders && fallback in headers.indices) return fallback
        return -1
    }

    // ---------- 单个成绩行 ----------

    private fun parseRow(
        tr: Element,
        colCourse: Int,
        colScore: Int,
        colLevel: Int,
        colPoint: Int,
        colCredit: Int,
        colSemester: Int,
        colNature: Int,
        colExam: Int,
        colTag: Int,
    ): GradeRecord? {
        val cells = tr.children().filter { it.tagName() == "td" }
        fun cell(index: Int): String =
            if (index in cells.indices) clean(cells[index].text()) else ""

        val course = cell(colCourse)
        if (course.isBlank()) return null
        if (course.startsWith("合计") || course.startsWith("平均") || course.startsWith("未查询到") ||
            course == "课程名称"
        ) {
            return null
        }

        val rawScore = cell(colScore).removeSuffix("分").trim()
        val rawLevel = cell(colLevel).uppercase()
        val rawPoint = cell(colPoint).toDoubleOrNull()
        val credit = cell(colCredit).toDoubleOrNull()
        val semester = SEMESTER_IN_TEXT.find(cell(colSemester))?.groupValues?.get(1)
            ?: cell(colSemester)

        val numeric = rawScore.takeIf { NUMERIC.matches(it) }?.toDoubleOrNull()
        val level = when {
            rawLevel.isNotBlank() -> rawLevel
            LETTER.matches(rawScore) -> rawScore.uppercase()
            numeric != null -> levelOfScore(numeric)
            else -> ""
        }

        val point = rawPoint ?: LEVEL_POINTS[level] ?: numeric?.let { score ->
            // 有附加题的课程总分能到 100 以上，那个分数没法反推绩点（学校按「综合基本成绩 + 附加题」两维查表），
            // 这种情况宁可不给绩点，也不猜一个错的进去污染平均分。
            if (score <= 100.0) LEVEL_POINTS[levelOfScore(score)] else null
        }

        // 百分制分数留在 score；等级制课程等级留在 level；「通过」这类文字成绩原样留在 score
        val scoreText = when {
            numeric != null -> rawScore
            LETTER.matches(rawScore) -> ""
            else -> rawScore
        }

        return GradeRecord(
            course = course,
            score = scoreText,
            level = level,
            point = point,
            credit = credit,
            semester = semester,
            nature = cell(colNature),
            examType = cell(colExam),
            tag = normalizeTag(cell(colTag)),
        )
    }

    private fun normalizeTag(raw: String): String =
        when {
            raw.isBlank() -> ""
            raw.contains("初修") || raw.contains("正常") -> ""
            raw.contains("重修") || raw.contains("补考") || raw.contains("缓考") ||
                raw.contains("免修") || raw.contains("作弊") -> raw
            else -> ""
        }

    // ---------- 学期 ----------

    /**
     * 学期列表：优先用下拉框（有的学校版本有），没有就从成绩行里归纳。
     * 深技大这版**没有下拉框**，只有顶部一行「查询条件：开课时间【全部学期】」，
     * 所以"有哪些学期"只能从数据本身推出来 —— 只有真出过成绩的学期才值得列出来。
     */
    private fun semestersOf(doc: Document, rows: List<GradeRecord>): List<GradeSemester> {
        val fromSelect = doc.select("select#kksj, select[name=kksj], select#xnxq01id, select[name=xnxq01id]")
            .flatMap { it.select("option") }
            .mapNotNull { option ->
                val key = option.attr("value").trim()
                if (!SEMESTER_IN_TEXT.matches(key)) return@mapNotNull null
                GradeSemester(key, clean(option.text()).ifBlank { gradeSemesterTitle(key) })
            }

        val fromRows = rows.map { it.semester }
            .filter { it.isNotBlank() }
            .distinct()
            .map { GradeSemester(it, gradeSemesterTitle(it)) }

        return (fromSelect + fromRows).distinctBy { it.key }.sortedByDescending { it.key }
    }

    // ---------- 页面上的官方统计与排名 ----------

    private fun parsePageSummary(doc: Document): PageSummary? {
        val text = doc.text()
        val gpa = GPA_TEXT.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val averageScore = AVG_SCORE_TEXT.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val credits = CREDITS_TEXT.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val earned = EARNED_TEXT.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val count = COUNT_TEXT.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        if (gpa == null && credits == 0.0 && count == 0) return null
        return PageSummary(
            gpa = gpa,
            averageScore = averageScore,
            credits = credits,
            earnedCredits = earned,
            courseCount = count,
        )
    }

    private fun parseRank(doc: Document): GradeRank? {
        val text = doc.text()

        RANK_SLASH.find(text)?.let { match ->
            val position = match.groupValues[2].toIntOrNull() ?: return@let
            return GradeRank(
                label = match.groupValues[1],
                position = position,
                total = match.groupValues[3].toIntOrNull() ?: 0,
            )
        }
        RANK_PLAIN.find(text)?.let { match ->
            val position = match.groupValues[2].toIntOrNull() ?: return@let
            return GradeRank(label = match.groupValues[1], position = position)
        }
        RANK_ORDINAL.find(text)?.let { match ->
            val position = match.groupValues[1].toIntOrNull() ?: return@let
            return GradeRank(position = position)
        }
        return null
    }

    // ---------- 小工具 ----------

    /** 100-93 A+ / 92-85 A / 84-80 B+ / 79-75 B / 74-70 C+ / 69-65 C / 64-60 D / 59-0 F */
    internal fun levelOfScore(score: Double): String = when {
        score >= 93 -> "A+"
        score >= 85 -> "A"
        score >= 80 -> "B+"
        score >= 75 -> "B"
        score >= 70 -> "C+"
        score >= 65 -> "C"
        score >= 60 -> "D"
        else -> "F"
    }

    internal fun pointOfLevel(level: String): Double? = LEVEL_POINTS[level.uppercase()]

    private fun clean(raw: String): String =
        raw.replace('\u00A0', ' ').replace(Regex("""\s+"""), " ").trim()
}
