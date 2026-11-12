package com.classflow.timetable

import com.classflow.timetable.data.remote.sztu.ZfExamParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 考试查询的解析。
 *
 * 这套用例**不是**用真实考试数据跑出来的 —— 期末安排还没发布，学校那边暂时没有页面可抓。
 * 所以这里锁的是"结构"：不管教务把列名写成什么、顺序怎么排，只要有课程和时间两列就得认出来；
 * 反过来，页面上没有结果表时绝不能当成"零场考试"，不然用户会以为这学期不用考试。
 */
class ZfExamParserTest {

    private fun html(rows: String, headers: String = DEFAULT_HEADERS): String = """
        <html><body>
        <table id="dataList">
        <tr>$headers</tr>
        $rows
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `标准表头能拿到时间地点座位`() {
        val result = ZfExamParser.parse(
            html(
                """<tr><td>1</td><td>2026-2027-1期末考试</td><td>传感器与检测技术</td>
                    <td>2027-01-05 09:00-11:00</td><td>C-5-215</td><td>12</td><td>闭卷</td></tr>""",
            ),
            semesterKey = "2026-2027-1",
        )

        val row = result.rows.single()
        assertEquals("传感器与检测技术", row.course)
        assertEquals(LocalDate.of(2027, 1, 5), row.date)
        assertEquals(LocalTime.of(9, 0), row.start)
        assertEquals(LocalTime.of(11, 0), row.end)
        assertEquals("C-5-215", row.location)
        assertEquals("12", row.seat)
        assertEquals("闭卷", row.form)
    }

    @Test
    fun `列的顺序变了也能认出来`() {
        val headers = "<th>序号</th><th>考试时间</th><th>考试地点</th><th>课程名称</th><th>座位号</th>"
        val result = ZfExamParser.parse(
            html(
                """<tr><td>1</td><td>2027-06-20 14:00-16:00</td><td>D-3-321</td>
                    <td>人工智能算法导论</td><td>8</td></tr>""",
                headers,
            ),
            semesterKey = "2026-2027-2",
        )

        val row = result.rows.single()
        assertEquals("人工智能算法导论", row.course)
        assertEquals(LocalDate.of(2027, 6, 20), row.date)
        assertEquals("D-3-321", row.location)
        assertEquals("8", row.seat)
    }

    @Test
    fun `只写月日时年份按学期推`() {
        // 第 1 学期 9 月开学，1 月的考试已经是次年 —— 这里最容易错一年
        val autumn = ZfExamParser.parse(
            html("""<tr><td>1</td><td>期末</td><td>大学物理</td><td>1月6日 9:00-11:00</td><td>C-5-101</td><td>1</td><td>闭卷</td></tr>"""),
            semesterKey = "2026-2027-1",
        )
        assertEquals(LocalDate.of(2027, 1, 6), autumn.rows.single().date)

        // 第 1 学期 12 月的考试还在当年
        val december = ZfExamParser.parse(
            html("""<tr><td>1</td><td>期末</td><td>大学物理</td><td>12月28日 9:00-11:00</td><td>C-5-101</td><td>1</td><td>闭卷</td></tr>"""),
            semesterKey = "2026-2027-1",
        )
        assertEquals(LocalDate.of(2026, 12, 28), december.rows.single().date)

        val spring = ZfExamParser.parse(
            html("""<tr><td>1</td><td>期末</td><td>大学物理</td><td>6月21日 9:00-11:00</td><td>C-5-101</td><td>1</td><td>闭卷</td></tr>"""),
            semesterKey = "2026-2027-2",
        )
        assertEquals(LocalDate.of(2027, 6, 21), spring.rows.single().date)
    }

    @Test
    fun `只查期末，补考不混进来`() {
        val result = ZfExamParser.parse(
            html(
                """
                <tr><td>1</td><td>2026-2027-1期末考试</td><td>传感器</td><td>2027-01-05 09:00-11:00</td><td>C-5-215</td><td>3</td><td>闭卷</td></tr>
                <tr><td>2</td><td>2026-2027-1补考</td><td>传感器</td><td>2027-02-20 09:00-11:00</td><td>C-5-215</td><td>3</td><td>闭卷</td></tr>
                """.trimIndent(),
            ),
            semesterKey = "2026-2027-1",
            finalOnly = true,
        )

        assertEquals(1, result.rows.size)
        assertTrue(result.rows.single().name.contains("期末"))
    }

    @Test
    fun `教务没标期末时全部列出来并给提示`() {
        val result = ZfExamParser.parse(
            html("""<tr><td>1</td><td></td><td>体育</td><td>2027-01-09 09:00-10:00</td><td>体育馆</td><td></td><td></td></tr>"""),
            semesterKey = "2026-2027-1",
        )

        assertEquals(1, result.rows.size)
        assertNotNull(result.note)
    }

    @Test
    fun `空表就是零场，不报错也不编造`() {
        val result = ZfExamParser.parse(html(""), semesterKey = "2026-2027-1")
        assertTrue(result.rows.isEmpty())
        assertNull(result.note)
    }

    @Test
    fun `时间认不出来时保留原文`() {
        val result = ZfExamParser.parse(
            html("""<tr><td>1</td><td>期末</td><td>形势与政策</td><td>第19周 星期五 上午</td><td>C-5-201</td><td>5</td><td>开卷</td></tr>"""),
            semesterKey = "2026-2027-1",
        )

        val row = result.rows.single()
        assertNull(row.date)
        assertNull(row.start)
        assertEquals("第19周 星期五 上午", row.rawTime)
        assertEquals("C-5-201", row.location)
    }

    @Test
    fun `页面骨架不算结果，只有真数据行才算`() {
        val skeleton = html("")
        assertTrue(ZfExamParser.hasExamTable(skeleton))
        assertFalse(ZfExamParser.hasExamRows(skeleton))
        assertFalse(ZfExamParser.isEmptyResult(skeleton))

        val withData = html(
            """<tr><td>1</td><td>期末考试</td><td>传感器</td><td>2027-01-05 09:00-11:00</td><td>C-5-215</td><td>3</td><td>闭卷</td></tr>""",
        )
        assertTrue(ZfExamParser.hasExamRows(withData))
    }

    @Test
    fun `明确写了无数据时算空结果`() {
        assertTrue(ZfExamParser.isEmptyResult("<html><body>没有查询到相关数据</body></html>"))
    }

    @Test
    fun `表单照抄页面，只改学期和期末筛选`() {
        val page = """
            <html><body>
            <form action="/jsxsd/xsks/xsksap_list" method="post">
              <input type="hidden" name="token" value="abc">
              <select name="xnxqid">
                <option value="2026-2027-2">2026-2027-2</option>
                <option value="2026-2027-1" selected>2026-2027-1</option>
              </select>
              <select name="xqlb">
                <option value="1">全部</option>
                <option value="2">期末</option>
              </select>
              <input type="submit" value="查询">
            </form>
            </body></html>
        """.trimIndent()

        val form = ZfExamParser.parseQueryForm(page, "2026-2027-2")
        assertNotNull(form)
        requireNotNull(form)

        assertEquals("https://jwxt.sztu.edu.cn/jsxsd/xsks/xsksap_list", form.action)
        assertEquals("post", form.method)
        assertEquals("2026-2027-2", form.fields["xnxqid"])
        assertEquals("2", form.fields["xqlb"])
        assertEquals("abc", form.fields["token"])
        assertFalse(form.fields.containsKey("查询"))
        assertTrue(form.filtersFinal)
        assertEquals(2, form.semesters.size)
    }

    @Test
    fun `学期是数字 id 时按显示文字匹配`() {
        val page = """
            <form action="/jsxsd/xsks/xsksap_list" method="post">
              <select name="xnxqid">
                <option value="31">2026-2027-1</option>
                <option value="32">2026-2027-2</option>
              </select>
            </form>
        """.trimIndent()

        val form = ZfExamParser.parseQueryForm(page, "2026-2027-2")
        assertEquals("32", form?.fields?.get("xnxqid"))
        assertEquals("2026-2027-2", form?.semesterLabel)
    }

    @Test
    fun `页面上没有表单时返回 null`() {
        assertNull(ZfExamParser.parseQueryForm("<html><body>系统繁忙</body></html>", "2026-2027-1"))
    }

    private companion object {
        const val DEFAULT_HEADERS =
            "<th>序号</th><th>考试名称</th><th>课程名称</th><th>考试时间</th>" +
                "<th>考场</th><th>座位号</th><th>考试形式</th>"
    }
}
