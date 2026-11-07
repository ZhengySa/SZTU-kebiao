package com.classflow.timetable

import com.classflow.timetable.data.remote.sztu.ZfCalendarParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 教学周历解析：真正的开学日期只能从这里拿。
 *
 * 页面第一列是周次，第二列是**星期日**（不是星期一），
 * 所以"第 1 周的星期一"要拿周日日期 +1 天，并且跨月时要进位。
 * 这两点如果用错，整学期所有周次都会偏一周。
 */
class ZfCalendarParserTest {

    private fun table(rows: String): String = """
        <html><body>
        <table border="1">
          <tr><td>周次</td><td>星期日</td><td>星期一</td><td>星期二</td>
              <td>星期三</td><td>星期四</td><td>星期五</td><td>星期六</td><td>备注</td></tr>
          $rows
        </table>
        </body></html>
    """.trimIndent()

    private fun row(week: Int, sunday: String, monday: String, saturday: String) =
        "<tr><td>$week</td><td>$sunday</td><td>$monday</td><td>&nbsp;</td>" +
            "<td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>$saturday</td><td>&nbsp;</td></tr>"

    @Test
    fun `秋季学期 - 第1周星期日不跨月时星期一取同月`() {
        val html = table(
            row(1, "08月30日", "31", "09月05日") +
                row(2, "09月06日", "07", "09月12日"),
        )
        val c = ZfCalendarParser.parse(html, ZfCalendarParser.academicYearOf("2026-2027-1"))
        assertEquals(LocalDate.of(2026, 8, 31), c.startDate)
        assertEquals(2, c.totalWeeks)
    }

    @Test
    fun `秋季学期 - 星期日跨月时星期一要进位到下个月`() {
        // 周日 08月31日 → 周一 01（9 月 1 日）
        val html = table(row(1, "08月31日", "01", "09月06日"))
        val c = ZfCalendarParser.parse(html, ZfCalendarParser.academicYearOf("2026-2027-1"))
        assertEquals(LocalDate.of(2026, 9, 1), c.startDate)
    }

    @Test
    fun `春季学期 - 开学日期落在次年且不会被再加一年`() {
        // 2027-2028 学年第二学期：2028 年 2 月 20 日（周日）第 1 周
        val html = table(row(1, "02月20日", "21", "02月26日"))
        val c = ZfCalendarParser.parse(html, ZfCalendarParser.academicYearOf("2027-2028-2"))
        assertEquals(LocalDate.of(2028, 2, 21), c.startDate)
    }

    @Test
    fun `学期代号 - 第一学期用起始年 第二学期用结束年`() {
        assertEquals(2026, ZfCalendarParser.academicYearOf("2026-2027-1"))
        assertEquals(2028, ZfCalendarParser.academicYearOf("2027-2028-2"))
    }

    @Test
    fun `页面里没有表或没有日期时返回空值而不是崩溃`() {
        val empty = ZfCalendarParser.parse("<html><body>没有任何表格</body></html>", 2026)
        assertNull(empty.startDate)
        assertEquals(0, empty.totalWeeks)
    }
}
