package com.classflow.timetable

import com.classflow.timetable.data.remote.sztu.ZfGradeParser
import com.classflow.timetable.domain.model.GradeRecord
import com.classflow.timetable.domain.model.GradeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成绩页解析的回归测试。
 *
 * 主夹具是**真实页面**（jwxt.sztu.edu.cn/jsxsd/kscj/cjcx_list，用真账号抓下来，页面里没有学号/姓名）。
 * 为什么要钉真实页面：成绩解析出错时通常不会崩，只是"某几门课少了字段、总绩点差了 0.1"，
 * 用户很难发现。所以这里直接断言官方给出的数字（平均学分绩点 3.11、专业排名 95/157）
 * 和我们自己算出来的必须对得上。
 *
 * 另外两种形态（等级制 / 二级制）用构造页面覆盖，换学校版本时也不至于全瞎。
 */
class ZfGradeParserTest {

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "找不到夹具 $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    // ---------- 真实页面 ----------

    @Test
    fun `真实成绩页 - 行数与学期`() {
        val result = ZfGradeParser.parse(loadFixture("cjcx_list_all.html"))

        assertEquals(30, result.rows.size)
        assertEquals(listOf("2025-2026-2", "2025-2026-1"), result.semesters.map { it.key })
        assertEquals("2025-2026 学年 第 2 学期", result.semesters.first().title)
    }

    @Test
    fun `真实成绩页 - 成绩 等级 绩点 学分都要对`() {
        val rows = ZfGradeParser.parse(loadFixture("cjcx_list_all.html")).rows
        val byName = rows.associateBy { it.course }

        // 「成绩」是百分制分数，「总评成绩」才是等级，绩点单独一列
        val physics = byName.getValue("大学物理B1")
        assertEquals("78", physics.score)
        assertEquals("B", physics.level)
        assertEquals(3.0, physics.point!!, 0.001)
        assertEquals(3.0, physics.credit!!, 0.001)
        assertEquals("2025-2026-1", physics.semester)
        assertEquals("必修", physics.nature)
        assertEquals("考试", physics.examType)

        // 二级制课程：成绩写着「通过」，没有等级也没有绩点，学分 0
        val safety = byName.getValue("实验室安全教育")
        assertEquals("通过", safety.score)
        assertEquals("", safety.level)
        assertNull(safety.point)
        assertEquals(0.0, safety.credit!!, 0.001)
    }

    @Test
    fun `真实成绩页 - 自己算的绩点要和教务系统一致`() {
        val parsed = ZfGradeParser.parse(loadFixture("cjcx_list_all.html"))
        val summary = GradeStats.of(parsed.rows)

        // 教务系统顶部写的是「平均学分绩点:3.11」
        assertEquals(3.11, parsed.summary!!.gpa!!, 0.001)
        assertEquals(3.11, summary.gpa!!, 0.005)
        assertEquals(30, parsed.summary!!.courseCount)
        assertEquals(64.5, parsed.summary!!.credits, 0.001)
        assertEquals(64.5, parsed.summary!!.earnedCredits, 0.001)

        // 参与绩点计算的学分是 63.5（1 学分的「通过」课不进分母），已获得学分 64.5 要把它算进来
        assertEquals(63.5, summary.credits, 0.001)
        assertEquals(64.5, summary.earnedCredits, 0.001)
    }

    @Test
    fun `真实成绩页 - 排名来自页面顶部那行`() {
        val rank = ZfGradeParser.parse(loadFixture("cjcx_list_all.html")).rank
        assertEquals("专业", rank!!.label)
        assertEquals(95, rank.position)
        assertEquals(157, rank.total)
        assertEquals("95 / 157", rank.display())
    }

    @Test
    fun `空学期页面 - 解析出零行且不报错`() {
        val result = ZfGradeParser.parse(loadFixture("cjcx_list_empty_semester.html"))
        assertTrue(result.rows.isEmpty())
        assertNull(result.rank)
        assertTrue(result.summary == null)
    }

    // ---------- 构造页面：别的学校版本 ----------

    private fun table(headers: List<String>, rows: List<List<String>>): String = buildString {
        append("<table id=\"dataList\"><thead><tr>")
        headers.forEach { append("<th>$it</th>") }
        append("</tr></thead><tbody>")
        rows.forEach { row ->
            append("<tr>")
            row.forEach { append("<td>$it</td>") }
            append("</tr>")
        }
        append("</tbody></table>")
    }

    @Test
    fun `没有绩点列 - 按学校换算表从百分制推绩点`() {
        val headers = listOf(
            "序号", "开课学期", "课程编号", "课程名称", "成绩", "成绩标识",
            "学分", "总学时", "考核方式", "课程性质", "课程属性", "考试性质",
        )
        val html = table(
            headers,
            listOf(
                listOf("1", "2025-2026-2", "C1", "数据结构", "88", "", "3.0", "48", "考试", "必修", "专业", "正常考试"),
                listOf("2", "2025-2026-2", "C2", "体育", "72", "", "1.0", "32", "考查", "必修", "通识", "正常考试"),
                listOf("3", "2025-2026-2", "C3", "物理", "59", "", "2.0", "32", "考试", "必修", "专业", "正常考试"),
                listOf("4", "2025-2026-2", "C4", "金工实习", "91", "重修", "1.0", "16", "考查", "选修", "实践", "正常考试"),
            ),
        )

        val points = ZfGradeParser.parse(html).rows.associate { it.course to it.point }

        // 92-85 → A(4.0) / 74-70 → C+(2.5) / 59-0 → F(0) / 91 → A(4.0)
        assertEquals(4.0, points["数据结构"]!!, 0.001)
        assertEquals(2.5, points["体育"]!!, 0.001)
        assertEquals(0.0, points["物理"]!!, 0.001)
        assertEquals(4.0, points["金工实习"]!!, 0.001)
    }

    @Test
    fun `成绩列直接给等级 - 也能解析并计算绩点`() {
        val headers = listOf(
            "序号", "开课学期", "课程名称", "成绩", "学分", "考核方式", "课程属性",
        )
        val html = table(
            headers,
            listOf(
                listOf("1", "2026-2027-1", "程序设计", "A+", "3.0", "考试", "必修"),
                listOf("2", "2026-2027-1", "军训", "通过", "2.0", "考查", "必修"),
                listOf("3", "2026-2027-1", "英语听力", "A", "1.0", "考查", "必修"),
            ),
        )

        val result = ZfGradeParser.parse(html)
        val byName = result.rows.associateBy { it.course }

        assertEquals("A+", byName.getValue("程序设计").level)
        assertEquals("", byName.getValue("程序设计").score)
        assertEquals(4.5, byName.getValue("程序设计").point!!, 0.001)

        assertNull(byName.getValue("军训").point)
        assertFalse(byName.getValue("军训").countedInGpa)

        val summary = GradeStats.of(result.rows)
        assertEquals((4.5 * 3.0 + 4.0 * 1.0) / 4.0, summary.gpa!!, 0.001)
        assertEquals(4.0, summary.credits, 0.001)
        assertEquals(6.0, summary.earnedCredits, 0.001)
    }

    @Test
    fun `表尾合计行不算课程`() {
        val headers = listOf(
            "序号", "开课学期", "课程名称", "成绩", "总评成绩", "学分", "绩点",
        )
        val html = table(
            headers,
            listOf(
                listOf("1", "2026-2027-1", "线性代数", "95", "A+", "3.0", "4.5"),
                listOf("", "", "合计", "", "", "3.0", ""),
            ),
        )

        val rows = ZfGradeParser.parse(html).rows
        assertEquals(1, rows.size)
        assertEquals("线性代数", rows.first().course)
    }

    @Test
    fun `登录页与成绩页的判定`() {
        assertFalse(ZfGradeParser.hasGradeTable("<html><body>请先登录</body></html>"))
        assertTrue(ZfGradeParser.hasGradeTable("<table id=\"dataList\"></table>"))

        // 会话过期时教务系统返回的是自己的登录页（200），不能当成"这学期没成绩"
        val loginPage = loadFixture("cjcx_login_page.html")
        assertTrue(ZfGradeParser.isLoginPage(loginPage))
        assertFalse(ZfGradeParser.hasGradeTable(loginPage))
    }

    @Test
    fun `加权平均绩点 - 分母只算有绩点的课程学分`() {
        val rows = listOf(
            GradeRecord(course = "A", level = "A+", point = 4.5, credit = 2.0),
            GradeRecord(course = "B", level = "B", point = 3.0, credit = 3.0),
            GradeRecord(course = "C", score = "通过", credit = 1.0, point = null),
        )

        val summary = GradeStats.of(rows)
        assertEquals((4.5 * 2 + 3.0 * 3) / 5.0, summary.gpa!!, 0.001)
        assertEquals(5.0, summary.credits, 0.001)
        assertEquals(6.0, summary.earnedCredits, 0.001)
    }
}
