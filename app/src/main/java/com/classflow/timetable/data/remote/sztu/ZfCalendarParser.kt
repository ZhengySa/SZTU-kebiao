package com.classflow.timetable.data.remote.sztu

import org.jsoup.Jsoup
import java.time.LocalDate

/**
 * 从"教学周历"里读出开学的那个星期一。
 *
 * 页面是一张 21 行的表，每行第 1 格是周次、第 2~8 格是周日到周六的日期，
 * 只有首尾两格带月份（`08月30日` / `09月05日`），中间几天只写日号。
 * 所以只要把第 1 周的星期一拼出来，就是"开学日期"，比让用户自己填可靠得多。
 */
object ZfCalendarParser {

    data class ZfCalendar(
        val startDate: LocalDate?,
        val totalWeeks: Int,
    )

    /**
     * @param academicYear 该学期的起始年份，例如 `2026-2027-1` → 2026，`2026-2027-2` → 2027
     */
    fun parse(html: String, academicYear: Int): ZfCalendar {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table") ?: return ZfCalendar(null, 0)

        val weekRows = table.select("tr").mapNotNull { row ->
            val cells = row.children()
            if (cells.size < 3) return@mapNotNull null
            val week = cells[0].text().trim().toIntOrNull() ?: return@mapNotNull null
            Triple(week, cells[1].text(), cells[2].text())
        }
        if (weekRows.isEmpty()) return ZfCalendar(null, 0)

        val firstWeek = weekRows.firstOrNull { it.first == 1 } ?: weekRows.first()
        val sunday = parseMonthDay(firstWeek.second, academicYear)
            ?: return ZfCalendar(null, weekRows.size)
        val mondayDay = firstWeek.third.replace('\u00A0', ' ').trim().toIntOrNull()
            ?: return ZfCalendar(null, weekRows.size)

        var month = sunday.monthValue
        var year = sunday.year
        // 周一那格的日号比周日小 ⇒ 跨月了（如 8/30 周日 → 8/31 周一不跨月；8/31 → 9/1 跨月）
        if (mondayDay < sunday.dayOfMonth) {
            month += 1
            if (month > 12) {
                month = 1
                year += 1
            }
        }

        val monday = runCatching { LocalDate.of(year, month, mondayDay) }.getOrNull()
        return ZfCalendar(monday, weekRows.size)
    }

    /**
     * @param year 第 1 周所在的自然年。注意这里**不能**再按"月份小于 6 就 +1 年"去猜：
     *   秋季学期第 1 周在 8/9 月（属于起始年），春季学期第 1 周在 2/3 月（属于次年），
     *   而 [academicYearOf] 已经把"该学期落在哪个自然年"算好了，再 +1 会把春季整整错一年。
     */
    private fun parseMonthDay(raw: String, year: Int): LocalDate? {
        val text = raw.replace('\u00A0', ' ').trim()
        val m = Regex("""(\d{1,2})月(\d{1,2})日""").find(text) ?: return null
        val month = m.groupValues[1].toIntOrNull() ?: return null
        val day = m.groupValues[2].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** 从 `2026-2027-1` 这类学期代号推出该学期所在的起始年份。 */
    fun academicYearOf(semesterKey: String?): Int {
        val fallback = LocalDate.now().year
        if (semesterKey.isNullOrBlank()) return fallback
        val parts = semesterKey.split('-')
        if (parts.size < 3) return fallback
        val first = parts[0].toIntOrNull() ?: return fallback
        val second = parts[1].toIntOrNull() ?: return fallback
        val term = parts[2].toIntOrNull() ?: 1
        return if (term == 1) first else second
    }
}
