package com.classflow.timetable.domain.imports

import com.classflow.timetable.domain.model.TimeSlot
import java.time.Duration
import java.time.LocalTime

/**
 * 解析器共用的工具：时间 → 节次、周次区间 → 周号集合。
 *
 * 单独抽出来是因为 ICS 和 CSV 两条链路都要用，
 * 而且这两段逻辑最容易出错（尤其跨午休的长课），放一起好测。
 */
internal object ImportSupport {

    /**
     * 把「08:10-09:50」这类真实时间映射成节次区间。
     *
     * 策略：起始节取开始时间最接近的节次，结束节取结束时间最接近的节次；
     * 这样即使学校的节次表和我们默认的 12 节不完全一致，也能落在合理位置，
     * 不会出现「导入后一门课占了 8 节」的灾难。
     */
    fun mapToSlots(
        start: LocalTime,
        end: LocalTime,
        slots: List<TimeSlot>,
    ): Pair<Int, Int>? {
        if (slots.isEmpty()) return null

        val startSlot = slots.minByOrNull { diffMinutes(it.start, start) } ?: return null
        val endSlot = slots
            .filter { it.index >= startSlot.index }
            .minByOrNull { diffMinutes(it.end, end) } ?: startSlot

        return startSlot.index to maxOf(endSlot.index, startSlot.index)
    }

    private fun diffMinutes(a: LocalTime, b: LocalTime): Long =
        kotlin.math.abs(Duration.between(a, b).toMinutes())

    /** 把 1..totalWeeks 中落在 [from, to] 且满足步长的周号收集出来。 */
    fun weeksInRange(from: Int, to: Int, totalWeeks: Int, step: Int = 1): Set<Int> {
        if (to < from) return emptySet()
        val safeStep = if (step <= 0) 1 else step
        val result = sortedSetOf<Int>()
        var week = from
        while (week <= to) {
            if (week in 1..totalWeeks) result += week
            week += safeStep
        }
        return result
    }

    /** 单双周与逗号/区间混写的周次表达式，例如 `1-16`、`1-8,10-16`、`1-16单`、`单周`。 */
    fun parseWeekExpression(raw: String, totalWeeks: Int): Set<Int> {
        val text = raw.trim()
        if (text.isEmpty()) return (1..totalWeeks).toSet()

        // 先处理整体单/双周
        if (text.contains("单") || text.equals("odd", ignoreCase = true)) {
            val range = extractRange(text, totalWeeks)
            return (range).filter { it % 2 == 1 }.toSet()
        }
        if (text.contains("双") || text.equals("even", ignoreCase = true)) {
            val range = extractRange(text, totalWeeks)
            return (range).filter { it % 2 == 0 }.toSet()
        }

        val weeks = sortedSetOf<Int>()
        text.replace("，", ",")
            .replace("周", "")
            .split(',', '、', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val rangeMatch = Regex("^(\\d+)\\s*[-~到至]\\s*(\\d+)$").find(token)
                if (rangeMatch != null) {
                    val from = rangeMatch.groupValues[1].toIntOrNull() ?: return@forEach
                    val to = rangeMatch.groupValues[2].toIntOrNull() ?: return@forEach
                    weeks += weeksInRange(from, to, totalWeeks)
                } else {
                    token.toIntOrNull()?.let { if (it in 1..totalWeeks) weeks += it }
                }
            }

        return if (weeks.isEmpty()) (1..totalWeeks).toSet() else weeks
    }

    private fun extractRange(text: String, totalWeeks: Int): Set<Int> {
        val match = Regex("(\\d+)\\s*[-~到至]\\s*(\\d+)").find(text)
        return if (match != null) {
            val from = match.groupValues[1].toIntOrNull() ?: 1
            val to = match.groupValues[2].toIntOrNull() ?: totalWeeks
            weeksInRange(from, to, totalWeeks)
        } else {
            (1..totalWeeks).toSet()
        }
    }

    /** 「周一 / 星期一 / 礼拜一 / 1 /一」都认。 */
    fun parseDayOfWeek(raw: String): Int? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        text.toIntOrNull()?.let { return if (it in 1..7) it else null }

        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        val aliases = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6,
            "日" to 7, "天" to 7, "七" to 7,
        )
        names.forEach { ch ->
            if (text.contains(ch)) return aliases[ch]
        }
        // 英文缩写（ICS 里常见）
        return when (text.uppercase()) {
            "MO", "MON", "MONDAY" -> 1
            "TU", "TUE", "TUESDAY" -> 2
            "WE", "WED", "WEDNESDAY" -> 3
            "TH", "THU", "THURSDAY" -> 4
            "FR", "FRI", "FRIDAY" -> 5
            "SA", "SAT", "SATURDAY" -> 6
            "SU", "SUN", "SUNDAY" -> 7
            else -> null
        }
    }
}
