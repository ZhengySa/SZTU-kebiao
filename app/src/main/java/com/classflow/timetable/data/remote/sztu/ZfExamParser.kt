package com.classflow.timetable.data.remote.sztu

import com.classflow.timetable.domain.model.ExamEntry
import com.classflow.timetable.domain.model.ExamSemester
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.LocalTime

/**
 * 正方教务（jsxsd）「学生考试查询」解析器。
 *
 * 链路（从学校的接口结构反推，尚未用真实考试数据跑过，所以每一步都留了退路）：
 *  1. GET  `/jsxsd/xsks/xsksap_query`  —— 查询页，里面有学期下拉框和查询表单
 *  2. POST `/jsxsd/xsks/xsksap_list`   —— 表单提交，返回结果表
 *
 * 为什么不在代码里写死 `xnxqid=2026-2027-1&xqlb=2` 这种参数：
 * 正方的每个部署字段名都不一样，有的叫 xqlb，有的叫 xqlbmc，还有的直接把学期做成数字 id。
 * 所以这里**照抄页面上的表单**：把 query 页里所有 input 的默认值、下拉框当前选中的值原样带上，
 * 只把「学期」那一项改成我们要查的学期。这样即使字段名变了，也还是能查。
 *
 * 解析同样不按固定列号：先认表头文字（课程 / 时间 / 地点 / 座位…），认不出来的列就当没有。
 */
object ZfExamParser {

    /** 页面上的查询表单。 */
    data class Form(
        /** 绝对地址，已经用页面 URL 解析过相对路径。 */
        val action: String,
        val method: String,
        val fields: Map<String, String>,
        val semesters: List<ExamSemester>,
        /** 表单里当前选中的学期（给界面显示"查的是哪一学期"）。 */
        val semesterLabel: String,
        /** 表单里有「期末」这种筛选项，并且我们已经选中了它。 */
        val filtersFinal: Boolean,
    )

    data class Result(
        val rows: List<ExamEntry> = emptyList(),
        /** 给界面的一句额外提示，例如"教务没标注期末，先全部列出"。 */
        val note: String? = null,
    )

    private val SEMESTER_KEY = Regex("""\d{4}-\d{4}-[0-9]""")
    private val DATE_FULL = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})""")
    private val DATE_MONTH_DAY = Regex("""(?<!\d)(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
    private val TIME_RANGE =
        Regex("""(\d{1,2})\s*[:：]\s*(\d{2})\s*[-~～—－至到]+\s*(\d{1,2})\s*[:：]\s*(\d{2})""")
    private val TIME_ONE = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")

    private val EMPTY_MARKERS = listOf(
        "无数据", "没有数据", "暂无数据", "暂无", "未查询到", "没有查询到", "没有相关",
    )

    // ---------- 页面判定 ----------

    /** 页面上有没有"考试结果表"的骨架（哪怕一行数据都没有）。 */
    fun hasExamTable(html: String): Boolean = findResultTable(Jsoup.parse(html)) != null

    /** 结果表里至少有一行数据。GET 回来的页面只有表骨架，不能当成"查到结果了"。 */
    fun hasExamRows(html: String): Boolean {
        val doc = Jsoup.parse(html)
        val table = findResultTable(doc) ?: return false
        return bodyRows(table).any { row -> row.select("td").size >= 2 }
    }

    /** 教务明确说了"没有数据"。注意：**表缺失 ≠ 零条考试**，两者要分开处理。 */
    fun isEmptyResult(html: String): Boolean {
        if (hasExamRows(html)) return false
        return EMPTY_MARKERS.any { html.contains(it) }
    }

    // ---------- 查询表单 ----------

    /**
     * 从查询页里把表单扒出来。
     *
     * [preferredSemester] 例如 `2026-2027-1`；匹配不到就沿用页面默认值（一般是当前学期）。
     */
    fun parseQueryForm(html: String, preferredSemester: String?): Form? {
        val doc = Jsoup.parse(html, SztuConfig.EXAM_QUERY)
        val forms = doc.select("form")
        val form = forms.firstOrNull { it.select("select").any { s -> isSemesterSelect(s) } }
            ?: forms.firstOrNull { it.select("input[name=xnxqid], input[name=xnxq01id]").isNotEmpty() }
            ?: forms.firstOrNull { it.select("select").isNotEmpty() }
            ?: return null

        val fields = linkedMapOf<String, String>()
        val semesters = mutableListOf<ExamSemester>()
        var semesterLabel = ""
        var filtersFinal = false

        form.select("input[name]").forEach { input ->
            val type = input.attr("type").lowercase()
            if (type in setOf("button", "submit", "reset", "image", "file")) return@forEach
            // 没勾上的 checkbox/radio 浏览器不会提交，这里保持一致
            if ((type == "checkbox" || type == "radio") && !input.hasAttr("checked")) return@forEach
            fields[input.attr("name")] = input.attr("value")
        }

        form.select("select[name]").forEach { select ->
            val options = select.select("option").map { option ->
                ExamSemester(
                    key = option.attr("value").ifBlank { clean(option.text()) },
                    label = clean(option.text()).ifBlank { option.attr("value") },
                )
            }
            if (options.isEmpty()) return@forEach

            val selectedLabel = select.select("option[selected]").firstOrNull()
                ?.let { clean(it.text()).ifBlank { it.attr("value") } }
            val defaultOption = options.firstOrNull {
                it.label == selectedLabel || it.key == selectedLabel
            }

            val chosen = when {
                isSemesterSelect(select) -> {
                    semesters += options
                    pickSemester(options, preferredSemester, defaultOption)
                }
                // 不是学期框，但里面有「期末」这一档：说明这就是考试类型筛选，直接选它
                options.any { it.label.contains("期末") } -> {
                    filtersFinal = true
                    options.first { it.label.contains("期末") }
                }
                else -> defaultOption
                    ?: options.firstOrNull { it.key.isNotBlank() }
                    ?: options.first()
            }
            if (isSemesterSelect(select)) semesterLabel = chosen.label
            fields[select.attr("name")] = chosen.key
        }

        val action = form.absUrl("action").ifBlank { SztuConfig.EXAM_LIST }
        val method = form.attr("method").ifBlank { "post" }.lowercase()

        return Form(
            action = action,
            method = method,
            fields = fields,
            semesters = semesters.distinctBy { it.key },
            semesterLabel = semesterLabel,
            filtersFinal = filtersFinal,
        )
    }

    /** 学期下拉：选项值或显示文字里带 `2026-2027-1`，就当它是学期框。 */
    private fun isSemesterSelect(select: Element): Boolean {
        val name = select.attr("name").lowercase()
        if (name.contains("xnxq") || name.contains("xqid")) return true
        return select.select("option").any { option ->
            SEMESTER_KEY.containsMatchIn(option.attr("value")) || SEMESTER_KEY.containsMatchIn(option.text())
        }
    }

    private fun pickSemester(
        options: List<ExamSemester>,
        preferred: String?,
        fallback: ExamSemester?,
    ): ExamSemester {
        if (!preferred.isNullOrBlank()) {
            options.firstOrNull { it.key == preferred || it.label == preferred }?.let { return it }
            options.firstOrNull { it.key.contains(preferred) || it.label.contains(preferred) }
                ?.let { return it }
            val parts = preferred.split('-')
            val year = parts.take(2).joinToString("-")
            val term = parts.getOrNull(2)
            if (year.isNotBlank() && term != null) {
                options.firstOrNull {
                    (it.key.contains(year) || it.label.contains(year)) && it.key.endsWith("-$term")
                }?.let { return it }
            }
        }
        return fallback
            ?: options.firstOrNull { SEMESTER_KEY.containsMatchIn(it.key) }
            ?: options.firstOrNull { SEMESTER_KEY.containsMatchIn(it.label) }
            ?: options.first()
    }

    // ---------- 结果解析 ----------

    fun parse(html: String, semesterKey: String? = null, finalOnly: Boolean = true): Result {
        val doc = Jsoup.parse(html)
        val table = findResultTable(doc) ?: return Result()
        val headers = headerTexts(table)
        if (headers.isEmpty()) return Result()

        val cols = columnsOf(headers)
        if (cols.course < 0) return Result()

        val rows = bodyRows(table)
            .mapNotNull { row -> toEntry(row, cols, semesterKey) }
            .distinctBy { it.identity + "|" + it.name }
            .sortedWith(
                compareBy<ExamEntry> { it.date ?: LocalDate.MAX }
                    .thenBy { it.start ?: LocalTime.MAX }
                    .thenBy { it.course },
            )

        if (!finalOnly) return Result(rows)

        val finals = rows.filter { it.isFinal }
        return when {
            rows.isEmpty() -> Result(rows)
            finals.isEmpty() -> Result(
                rows,
                "教务这次返回的考试没标注「期末」，先把查到的 ${rows.size} 场都列出来了。",
            )
            else -> Result(finals)
        }
    }

    private val ExamEntry.isFinal: Boolean
        get() = name.contains("期末") || name.contains("期终") || note.contains("期末")

    private data class Cols(
        val course: Int,
        val name: Int,
        val time: Int,
        val location: Int,
        val seat: Int,
        val form: Int,
        val note: Int,
    )

    private fun columnsOf(headers: List<String>): Cols = Cols(
        course = pick(headers, "考试科目", "课程名称", "考试课程", "科目", "课程",
            exclude = listOf("编号", "代码", "课号", "学期")),
        name = pick(headers, "考试名称", "考试批次", "考试场次", "场次", "批次"),
        time = pick(headers, "考试时间", "时间", "日期"),
        location = pick(headers, "考试地点", "考场", "地点", "教室", "教学楼"),
        seat = pick(headers, "座位", "座号"),
        form = pick(headers, "考试形式", "考核方式", "考试方式", "形式"),
        note = pick(headers, "备注", "说明"),
    )

    private fun pick(headers: List<String>, vararg keys: String, exclude: List<String> = emptyList()): Int {
        keys.forEach { key ->
            val index = headers.indexOfFirst { header ->
                header.contains(key) && exclude.none { header.contains(it) }
            }
            if (index >= 0) return index
        }
        return -1
    }

    private fun toEntry(row: Element, cols: Cols, semesterKey: String?): ExamEntry? {
        val cells = row.select("td").map { clean(it.text()) }
        fun cell(index: Int): String = if (index in cells.indices) cells[index] else ""

        val course = cell(cols.course)
        if (course.isBlank()) return null
        // 表格尾部的合计行 / 提示行
        if (course.startsWith("合计") || course.startsWith("平均") || course.startsWith("共")) return null

        val rawTime = cell(cols.time)
        val (date, start, end) = parseWhen(rawTime, semesterKey)

        return ExamEntry(
            course = course,
            name = cell(cols.name),
            date = date,
            start = start,
            end = end,
            rawTime = rawTime,
            location = cell(cols.location),
            seat = cell(cols.seat),
            form = cell(cols.form),
            note = cell(cols.note),
        )
    }

    // ---------- 时间 ----------

    private fun parseWhen(raw: String, semesterKey: String?): Triple<LocalDate?, LocalTime?, LocalTime?> {
        if (raw.isBlank()) return Triple(null, null, null)

        val date = parseDate(raw, semesterKey)
        val range = TIME_RANGE.find(raw)
        val start: LocalTime?
        val end: LocalTime?
        if (range != null) {
            start = timeOf(range.groupValues[1], range.groupValues[2])
            end = timeOf(range.groupValues[3], range.groupValues[4])
        } else {
            val one = TIME_ONE.find(raw)
            start = one?.let { timeOf(it.groupValues[1], it.groupValues[2]) }
            end = null
        }
        return Triple(date, start, end)
    }

    private fun parseDate(raw: String, semesterKey: String?): LocalDate? {
        DATE_FULL.find(raw)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            return safeDate(year, month, day)
        }
        DATE_MONTH_DAY.find(raw)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val year = academicYearOf(semesterKey, month) ?: return null
            return safeDate(year, month, day)
        }
        return null
    }

    /**
     * 只写了「1月5日」时，年份从学期推。
     *
     * `2026-2027-1` 是 2026 年 9 月开学，期末考试落在 2027 年 1 月 —— 跨年了；
     * 所以不能简单取学年的第一年：第 1 学期里 9 月以后算当年，1-8 月算次年。
     * 第 2 学期（春季）整段都在次年。
     */
    private fun academicYearOf(semesterKey: String?, month: Int): Int? {
        val parts = semesterKey?.split('-') ?: return null
        if (parts.size < 3) return null
        val first = parts[0].toIntOrNull() ?: return null
        val second = parts[1].toIntOrNull() ?: return null
        return if (parts[2] == "1") {
            if (month >= 8) first else second
        } else {
            second
        }
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun timeOf(hour: String, minute: String): LocalTime? =
        runCatching { LocalTime.of(hour.toInt(), minute.toInt()) }.getOrNull()

    // ---------- 表格结构 ----------

    /**
     * 找结果表：第一行的单元格里同时出现「课程/科目」和「时间」才算。
     * 只认列数 ≥3 的表，避免把查询页上那种两列布局表当成结果表。
     */
    private fun findResultTable(doc: Document): Element? {
        return doc.select("table").firstOrNull { table ->
            val first = table.select("tr").firstOrNull() ?: return@firstOrNull false
            val texts = first.select("th,td").map { clean(it.text()) }
            texts.size >= 3 &&
                texts.any { it.contains("课程") || it.contains("科目") || it.contains("考试名称") } &&
                texts.any { it.contains("时间") || it.contains("日期") }
        }
    }

    /** 表头：从顶部开始的连续全 `th` 行，按 colspan 展开，逐列拼起来。 */
    private fun headerTexts(table: Element): List<String> {
        val rows = mutableListOf<List<String>>()
        for (tr in table.select("tr")) {
            val cells = tr.children()
            if (cells.isEmpty()) continue
            if (cells.any { it.tagName() != "th" }) {
                if (rows.isEmpty() && tr.select("td").size >= 3) {
                    // 有的版本表头用 td 写
                    rows += expand(tr.select("td"))
                }
                break
            }
            rows += expand(cells)
            if (rows.size >= 2) break
        }
        if (rows.isEmpty()) return emptyList()
        val width = rows.maxOf { it.size }
        return (0 until width).map { index ->
            rows.mapNotNull { row -> row.getOrNull(index)?.takeIf { it.isNotBlank() } }
                .joinToString(" ")
        }
    }

    private fun expand(cells: List<Element>): List<String> {
        val out = mutableListOf<String>()
        cells.forEach { cell ->
            val span = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 12) ?: 1
            val text = clean(cell.text())
            repeat(span) { out += text }
        }
        return out
    }

    private fun bodyRows(table: Element): List<Element> {
        val all = table.select("tr")
        val skip = all.count { tr ->
            tr.children().isNotEmpty() && tr.children().all { it.tagName() == "th" }
        }.coerceAtLeast(1)
        return all.drop(skip).filter { it.select("td").isNotEmpty() }
    }

    private fun clean(text: String): String =
        text.replace('\u00A0', ' ').replace(Regex("""\s+"""), " ").trim()
}
