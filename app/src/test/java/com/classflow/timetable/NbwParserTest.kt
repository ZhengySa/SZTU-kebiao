package com.classflow.timetable

import com.classflow.timetable.data.remote.sztu.NbwParser
import com.classflow.timetable.domain.model.NbwCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公文通解析的回归测试。
 *
 * 两个夹具都是**真实页面**（2026-11-05 从 nbw.sztu.edu.cn 抓下来，含当天 20 条公告）。
 * 为什么必须钉真实页面：这个站是博达站群，列表 `<li>` 里的字段只靠 class 名
 * （width01~width06）来认，改一次版就可能整体错位。错位的表现是
 * 「部门名显示在标题的位置」这种"看起来很正常"的错，不测就发现不了。
 */
class NbwParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "找不到夹具 $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    // ---------- 列表页 ----------

    @Test
    fun `列表页 - 一页 20 条，分页信息正确`() {
        val page = NbwParser.parseList(fixture("nbw_list.html"))

        assertEquals(20, page.notices.size)
        assertEquals(1, page.page)
        assertEquals(775, page.totalPages)
        assertEquals(15481, page.totalCount)
    }

    @Test
    fun `列表页 - 首条字段逐个核对`() {
        val first = NbwParser.parseList(fixture("nbw_list.html")).notices.first()

        assertEquals("info/1018/52712.htm", first.path)
        assertEquals("2026下半年交通运输专业硕士研究生学位论文答辩公告", first.title)
        assertEquals("教务", first.category)
        assertEquals("城市交通与物流学院", first.department)
        assertEquals("2026-11-05", first.date)
        assertFalse(first.hasAttachment)
    }

    @Test
    fun `列表页 - 附件标记能认出来`() {
        val notices = NbwParser.parseList(fixture("nbw_list.html")).notices
        val withFile = notices.first { it.path == "info/1019/52710.htm" }
        assertTrue(withFile.hasAttachment)
        assertTrue(withFile.title.startsWith("关于新材料与新能源学院"))
    }

    @Test
    fun `列表页 - 分类要覆盖五个频道`() {
        val categories = NbwParser.parseList(fixture("nbw_list.html"))
            .notices.map { it.category }.toSet()
        assertTrue(categories.containsAll(setOf("教务", "科研", "学工", "校园")))
    }

    @Test
    fun `列表页 - 标题取 title 属性而不是被截断的 text`() {
        // 站点对超长标题用 CSS 截断，text 是全的；但 title 属性才是站点认定的全文。
        // 这条断言防的是有人把取值改回 text() 之后，超长标题悄悄变短。
        val notices = NbwParser.parseList(fixture("nbw_list.html")).notices
        assertTrue(notices.all { it.title.isNotBlank() })
        assertTrue(notices.none { it.title.startsWith(".") })
    }

    // ---------- 详情页 ----------

    @Test
    fun `详情页 - 标题 作者 时间`() {
        val d = NbwParser.parseDetail(
            fixture("nbw_detail.html"),
            "info/1019/52710.htm",
            "https://nbw.sztu.edu.cn/info/1019/52710.htm",
        )

        assertEquals("info/1019/52710.htm", d.path)
        assertTrue(d.title.contains("准分子激光器"))
        assertEquals("科研与校企合作部", d.author)
        assertTrue(d.publishedAt.startsWith("2026年11月05日"))
    }

    @Test
    fun `详情页 - 正文段落完整且不含空段`() {
        val d = NbwParser.parseDetail(
            fixture("nbw_detail.html"),
            "info/1019/52710.htm",
            "https://nbw.sztu.edu.cn/info/1019/52710.htm",
        )

        assertTrue("正文至少要有 10 段", d.paragraphs.size >= 10)
        assertTrue(d.paragraphs.none { it.isBlank() })
        assertTrue(d.paragraphs.none { it == "\u00A0" })
        assertTrue("正文里应该出现单位抬头", d.paragraphs.first().contains("各单位"))
    }

    @Test
    fun `详情页 - 附件名字与下载地址`() {
        val d = NbwParser.parseDetail(
            fixture("nbw_detail.html"),
            "info/1019/52710.htm",
            "https://nbw.sztu.edu.cn/info/1019/52710.htm",
        )

        assertEquals(1, d.attachments.size)
        val a = d.attachments.first()
        assertEquals("附件：测试化验加工单位法人证书.pdf", a.name)
        assertTrue(a.url.startsWith("https://nbw.sztu.edu.cn/system/_content/download.jsp"))
        assertTrue(a.url.contains("wbfileid=6593326"))
    }

    @Test
    fun `详情页 - 上一篇为空 下一篇有标题`() {
        val d = NbwParser.parseDetail(
            fixture("nbw_detail.html"),
            "info/1019/52710.htm",
            "https://nbw.sztu.edu.cn/info/1019/52710.htm",
        )

        // 这条是当天最新的一条，所以「上一篇：没有了！」
        assertEquals(null, d.prev)
        assertEquals("52707.htm", d.next?.path)
        assertTrue(d.next!!.title.contains("董丽"))
    }

    @Test
    fun `详情页 - 相对路径补成 info 路径`() {
        // 页面上写的是 52707.htm，直接拿去请求会把 /info/1019/ 丢掉，404
        assertEquals(
            "info/1019/52707.htm",
            NbwParser.resolveSibling("info/1019/52710.htm", "52707.htm"),
        )
        // 已经是完整路径的原样返回
        assertEquals(
            "info/1019/52707.htm",
            NbwParser.resolveSibling("info/1019/52710.htm", "info/1019/52707.htm"),
        )
    }

    // ---------- URL ----------

    @Test
    fun `URL - 首页不带 PAGENUM 其余页带`() {
        assertFalse(NbwParser.listUrl(NbwCategory.ALL, 1).contains("PAGENUM"))
        assertTrue(NbwParser.listUrl(NbwCategory.ALL, 2).contains("PAGENUM=2"))
        assertTrue(NbwParser.listUrl(NbwCategory.JIAOWU, 1).contains("wbtreeid=1018"))
        assertTrue(NbwParser.listUrl(NbwCategory.XIAOYUAN, 3).contains("wbtreeid=1022"))
        assertEquals("https://nbw.sztu.edu.cn/info/1019/52710.htm", NbwParser.detailUrl("info/1019/52710.htm"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `列表页 - 结构变了要报错而不是返回空列表`() {
        // 返回空列表会被上层当成"今天没有通知"，比报错更糟
        NbwParser.parseList("<html><body><p>站点维护中</p></body></html>")
    }
}
