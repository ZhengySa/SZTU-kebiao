package com.classflow.timetable.data.remote.sztu

import com.classflow.timetable.domain.model.SlotSection
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * 正方新版教务（jsxsd）「学期理论课表」解析器。
 *
 * 页面结构（已按真实页面核对）：
 * ```
 * <table id="timetable">
 *   <tr><th>&nbsp;</th><th>星期一</th>…<th>星期日</th></tr>
 *   <tr>
 *     <th>第1 2节<br>(01,02小节)<br>08:30-09:55</th>
 *     <td><div class="kbcontent1">…</div><div class="kbcontent">…</div></td>  × 7
 *   </tr>
 * </table>
 * ```
 * 一格里可能塞了好几门课，用 `----------------------` 分隔；
 * 而且一门跨 3 节的课会在多行里重复出现（第3-4节一行、第5节一行），
 * 所以**真实的节次区间以课程自己写的 [03-04-05节] 为准**，并据此去重。
 */
object ZfKbParser {

    data class ZfSlot(
        val index: Int,
        val startMinute: Int,
        val endMinute: Int,
        val section: SlotSection,
    )

    data class ZfEntry(
        val name: String,
        val className: String?,
        val teacher: String?,
        val weeks: Set<Int>,
        val dayOfWeek: Int,
        val startSlot: Int,
        val endSlot: Int,
        val room: String?,
        val building: String?,
    ) {
        val dedupeKey: String =
            "$name|$teacher|${weeks.sorted()}|$dayOfWeek|$startSlot-$endSlot|$room"
    }

    data class ZfTimetable(
        val semesterKeys: List<String>,
        val currentSemester: String?,
        val slots: List<ZfSlot>,
        val entries: List<ZfEntry>,
    ) {
        val maxWeek: Int get() = entries.maxOfOrNull { it.weeks.maxOrNull() ?: 0 } ?: 0
    }

    /** 分行占位符：用不可见控制字符，正文里不可能出现。 */
    private const val SEP = "\u0001"

    private val SLOT_RANGE = Regex("""\((\d{2})(?:,(\d{2}))?小节\)""")
    private val TIME_RANGE = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""")
    private val WEEK_LINE = Regex("""^([\d,\-]+)\(周\)(?:\[([\d\-]+)节\])?$""")
    private val SLOT_LIST = Regex("""^\[?([\d\-]+)节\]?$""")
    private val DASH_LINE = Regex("""^-{4,}$""")

    fun parse(html: String): ZfTimetable {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table#timetable")
            ?: error("没找到课表表格，可能登录状态已失效")

        val slots = sortedMapOf<Int, ZfSlot>()
        val collected = mutableMapOf<String, ZfEntry>()

        for (row in table.select("tr")) {
            val cells = row.children()
            if (cells.isEmpty()) continue
            val headText = cells[0].text()

            // 只有"第X节 (NN小节) HH:mm-HH:mm"这种行才是节次行，其它（表头、备注）跳过
            val slotMatch = SLOT_RANGE.find(headText) ?: continue
            val first = slotMatch.groupValues[1].toIntOrNull() ?: continue
            val second = slotMatch.groupValues[2].toIntOrNull()
            val rowStart = first
            val rowEnd = second ?: first

            val timeMatch = TIME_RANGE.find(headText)
            val startMin = timeMatch?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
            val endMin = timeMatch?.let { it.groupValues[3].toInt() * 60 + it.groupValues[4].toInt() }

            if (startMin != null && endMin != null) {
                for (i in rowStart..rowEnd) {
                    slots[i] = ZfSlot(i, startMin, endMin, sectionOf(startMin))
                }
            }

            cells.drop(1).forEachIndexed { index, cell ->
                val dayOfWeek = index + 1
                if (dayOfWeek > 7) return@forEachIndexed
                cell.select("div.kbcontent").forEach { div ->
                    parseBlocks(div.html(), dayOfWeek, rowStart, rowEnd).forEach { entry ->
                        collected.putIfAbsent(entry.dedupeKey, entry)
                    }
                }
            }
        }

        val semesterKeys = doc.select("select#xnxq01id option")
            .mapNotNull { it.attr("value").takeIf { v -> v.isNotBlank() } }
        val currentSemester = doc.selectFirst("select#xnxq01id option[selected]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }

        return ZfTimetable(
            semesterKeys = semesterKeys,
            currentSemester = currentSemester,
            slots = slots.values.toList(),
            entries = collected.values.sortedWith(compareBy({ it.startSlot }, { it.name })),
        )
    }

    /**
     * 把一个格子里的多个课程块拆开并解析。
     * 这里不用 jsoup 二次 parse（一页有近 200 个格子，会明显拖慢），
     * 直接把 HTML 里的 `<br>` 当换行、把标签剥掉。
     *
     * 一个坑：教学楼（`font[name=jxlmc]`）和教室（`font[title=教室]`）之间**没有 `<br>`**，
     * 如果只按 `<br>` 分行，两者会被粘成一行「【C-5公共教学楼】C-5-225」，教室就丢了。
     * 所以每个 `<font>` 前后也补一个换行符——一个 font 就是一个字段，本来就是这个页面的约定。
     */
    private fun parseBlocks(
        rawHtml: String,
        dayOfWeek: Int,
        rowStart: Int,
        rowEnd: Int,
    ): List<ZfEntry> {
        val cleaned = rawHtml
            .replace(Regex("""(?is)<div[^>]*class=['"]item-box['"][^>]*>.*?</div>"""), "")
        val lines = cleaned
            .replace(Regex("""(?is)<br\s*/?>"""), SEP)
            .replace(Regex("""(?is)<font\b"""), "$SEP<font")
            .replace(Regex("""(?is)</font>"""), SEP)
            .replace(Regex("""<[^>]+>"""), "")
            .let { Parser.unescapeEntities(it, false) }
            .split(SEP[0])
            .map { it.replace('\u00A0', ' ').trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return emptyList()

        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        lines.forEach { line ->
            if (DASH_LINE.matches(line)) {
                if (current.isNotEmpty()) blocks += current
                current = mutableListOf()
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) blocks += current

        return blocks.mapNotNull { parseOneBlock(it, dayOfWeek, rowStart, rowEnd) }
    }

    private fun parseOneBlock(
        lines: List<String>,
        dayOfWeek: Int,
        rowStart: Int,
        rowEnd: Int,
    ): ZfEntry? {
        val weekIndex = lines.indexOfFirst { it.contains("(周)") }
        if (weekIndex < 1) return null

        val weekLine = lines[weekIndex]
        val weekMatch = WEEK_LINE.find(weekLine.replace(" ", ""))
            ?: Regex("""([\d,\-]+)\(周\)""").find(weekLine.replace(" ", ""))
            ?: return null
        val weeks = parseWeeks(weekMatch.groupValues[1])
        if (weeks.isEmpty()) return null

        // 节次：优先用课程自己声明的 [03-04-05节]
        val declaredSlots = SLOT_LIST.find(weekLine.replace(" ", "").substringAfter(")"))
            ?.groupValues?.get(1)
            ?.split('-')
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it > 0 }
            .orEmpty()
        val startSlot = declaredSlots.minOrNull() ?: rowStart
        val endSlot = declaredSlots.maxOrNull() ?: rowEnd

        // 周次行之前的字段顺序是：课程名 → (教学班) → 教师。
        // 但"教学班"和"教师"都可能缺，所以不能死按位置取，按形态判断更稳。
        val name = lines.first().trim().takeIf { it.isNotEmpty() } ?: return null
        if (name.isBlank() || name == "\u00A0") return null

        val headLines = lines.subList(1, weekIndex)
        val classLine = headLines.firstOrNull { it.startsWith("(") && it.endsWith(")") }
        val className = classLine?.removeSurrounding("(", ")")?.takeIf { it.isNotBlank() }
        val teacher = headLines
            .firstOrNull { it != classLine && !it.startsWith("(") && it.isNotBlank() }

        val tailLines = lines.drop(weekIndex + 1)
        val building = tailLines.firstOrNull { it.startsWith("【") }
            ?.removeSurrounding("【", "】")
        val room = tailLines
            .filterNot { it.startsWith("【") }
            .firstOrNull {
                it.isNotBlank() &&
                    !it.startsWith("通知单") &&
                    !it.startsWith("班级") &&
                    !it.startsWith("备注") &&
                    !it.startsWith("(")
            }

        return ZfEntry(
            name = name,
            className = className,
            teacher = teacher,
            weeks = weeks,
            dayOfWeek = dayOfWeek,
            startSlot = startSlot,
            endSlot = endSlot,
            room = room,
            building = building,
        )
    }

    internal fun parseWeeks(raw: String): Set<Int> {
        val result = mutableSetOf<Int>()
        raw.split(',').forEach { part ->
            val p = part.trim()
            if (p.isEmpty()) return@forEach
            val range = Regex("""^(\d+)\s*-\s*(\d+)$""").find(p)
            if (range != null) {
                val a = range.groupValues[1].toInt()
                val b = range.groupValues[2].toInt()
                if (a in 1..60 && b in 1..60) {
                    if (a <= b) (a..b).forEach { result += it } else (b..a).forEach { result += it }
                }
            } else {
                p.toIntOrNull()?.takeIf { it in 1..60 }?.let { result += it }
            }
        }
        return result
    }

    private fun sectionOf(startMinute: Int): SlotSection = when {
        startMinute < 12 * 60 + 30 -> SlotSection.MORNING
        startMinute < 18 * 60 -> SlotSection.AFTERNOON
        else -> SlotSection.EVENING
    }
}
