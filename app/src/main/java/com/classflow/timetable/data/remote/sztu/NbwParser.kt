package com.classflow.timetable.data.remote.sztu

import com.classflow.timetable.domain.model.NbwAttachment
import com.classflow.timetable.domain.model.NbwCategory
import com.classflow.timetable.domain.model.NbwDetail
import com.classflow.timetable.domain.model.NbwLink
import com.classflow.timetable.domain.model.NbwNotice
import com.classflow.timetable.domain.model.NbwPage
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 公文通（学校通知公告平台，VSB / 博达站群系统）解析器。
 *
 * 站点结构（2026-11 实地抓取核对）：
 * ```
 * 列表 list.jsp?urltype=tree.TreeTempUrl&wbtreeid=<栏目号>[&PAGENUM=n]
 *   ul → li#line_u6_N
 *        div.width01  序号
 *        div.width02  分类（教务/科研/…）→ a[href*=wbtreeid=1018]
 *        div.width03  发文部门             → a[href*=scourname=…]
 *        div.width04  标题                 → a[href=info/1019/52710.htm][title=全文]
 *        div.width05  附件标记（有则 img[src*=fujian.png]）
 *        div.width06  日期 yyyy-MM-dd
 *   分页 div.pb_sys_common：共 15481 条 / 775 页，链接形如 `?totalpage=775&PAGENUM=2&…`
 *
 * 详情 info/1019/52710.htm
 *   h1.article-title                        标题
 *   div.article-sm                          作者 / 发布时间 / 点击数
 *   #vsb_content > div.v_news_content       正文（一堆带内联样式的 <p>）
 *   ul.fujian li a[href*=download.jsp]      附件，文字形如“附件【xxx.pdf】
 *   div.article-link                        上一篇 / 下一篇
 * ```
 *
 * 解析时不按 DOM 顺序数 div：站点改版一次顺序就全错，而且错得很安静
 * （部门显示到标题栏也能"看起来正常"）。这里一律按 class 定位，取不到就留空。
 */
object NbwParser {

    /** 列表页每日最多 20 条。 */
    const val PAGE_SIZE_HINT = 20

    private val NOTICE_PATH = Regex("""^info/(\d+)/(\d+)\.htm$""")

    fun isLoginPage(html: String): Boolean =
        html.contains("id=\"username\"") || html.contains("统一身份认证")

    /** 解析列表页。认不出列表结构时抛出 —— 宁可报错，也不要给用户一个"空公文通"。 */
    fun parseList(html: String, page: Int = 1): NbwPage {
        val doc = Jsoup.parse(html)
        val items = doc.select("li[id^=line_u6_]")
        require(items.isNotEmpty()) { "公文通列表结构变了：没有找到 li#line_u6_*" }

        val notices = items.mapNotNull { li -> parseItem(li) }

        val counter = doc.selectFirst("span.p_t")?.text().orEmpty()
        val totalCount = Regex("""共\s*(\d+)\s*条""").find(counter)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        // “/775页” 是最后一页；站点同时把它写在 totalpage= 参数里，两个都认一下
        val totalPages = Regex("""/(\d+)\s*页""").find(counter)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""totalpage=(\d+)""").find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: 1

        return NbwPage(
            notices = notices,
            page = page,
            totalPages = totalPages,
            totalCount = totalCount,
        )
    }

    private fun parseItem(li: Element): NbwNotice? {
        val titleLink = li.selectFirst("div.width04 a[href]") ?: return null
        val path = titleLink.attr("href").trim()
        if (!NOTICE_PATH.matches(path)) return null

        // title 属性是全文（列表里超长会被 CSS 截断），比 text() 可靠
        val title = titleLink.attr("title").ifBlank { titleLink.text() }.trim()
        if (title.isEmpty()) return null

        val category = li.selectFirst("div.width02 a")?.text()?.trim().orEmpty()
        val department = li.selectFirst("div.width03 a")?.text()?.trim().orEmpty()
        val date = li.selectFirst("div.width06")?.text()?.trim().orEmpty()
        val hasAttachment = li.selectFirst("div.width05 img") != null

        return NbwNotice(
            path = path,
            title = title,
            category = category,
            department = department,
            date = date,
            hasAttachment = hasAttachment,
        )
    }

    /** 解析详情页。 */
    fun parseDetail(html: String, path: String, baseUrl: String): NbwDetail {
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.article-title")?.text()?.trim()
            ?: doc.selectFirst("div.neirong h1")?.text()?.trim()
            ?: ""

        val meta = doc.selectFirst("div.article-sm")?.text().orEmpty()
        val author = Regex("""作者[：:]\s*(\S+)""").find(meta)?.groupValues?.get(1).orEmpty()
        val publishedAt = Regex("""发布时间[：:]\s*([0-9年月日\s:]+)""")
            .find(meta)?.groupValues?.get(1)?.trim().orEmpty()

        val content = doc.selectFirst("div.v_news_content")
            ?: doc.selectFirst("#vsb_content")

        // 一段一块。图片（<img>）在纯文本里没法保留位置，跳过；
        // 公告正文几乎都是文字，个别通知用图片发全文的情况另说了。
        val paragraphs = content?.select("p")
            ?.map { it.text().trim() }
            ?.filter { it.isNotEmpty() }
            ?.filterNot { it == "\u00A0" }
            ?.map { it.replace('\u00A0', ' ') }
            .orEmpty()
            // 有些页面整篇正文塞在一个 <div> 里没有 <p>，兜底按 br 切
            .ifEmpty {
                content?.html()
                    ?.replace(Regex("""(?i)<br\s*/?>"""), "\n")
                    ?.let { Jsoup.parse(it).text() }
                    ?.split('\n')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
            }

        val attachments = doc.select("ul.fujian li a[href]").mapNotNull { a ->
            val href = a.attr("href").trim()
            val name = a.text().trim()
                .removePrefix("附件【")
                .removeSuffix("】")
                .ifBlank { "附件" }
            if (href.isEmpty()) null else NbwAttachment(name = name, url = absolute(baseUrl, href))
        }

        val prev = linkOf(doc, "上一篇")
        val next = linkOf(doc, "下一篇")

        return NbwDetail(
            path = path,
            title = title,
            author = author,
            publishedAt = publishedAt,
            paragraphs = paragraphs,
            attachments = attachments,
            prev = prev,
            next = next,
        )
    }

    /** `<p class="txt-elise"><span>上一篇：</span><a href="52707.htm">标题</a></p>`，没有链接就是「没有了」。 */
    private fun linkOf(doc: Document, label: String): NbwLink? {
        val p = doc.select("div.article-link p").firstOrNull { it.text().startsWith(label) } ?: return null
        val a = p.selectFirst("a[href]") ?: return null
        val title = a.text().trim()
        val href = a.attr("href").trim()
        if (title.isEmpty() || href.isEmpty()) return null
        return NbwLink(title = title, path = href)
    }

    private fun absolute(baseUrl: String, href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> "https://${SztuConfig.NBW_HOST}$href"
        else -> baseUrl.trimEnd('/') + "/" + href
    }

    /** 「全部」不带栏目过滤，其余要在 URL 上拼 wbtreeid。 */
    fun listUrl(category: NbwCategory, page: Int): String {
        val base = SztuConfig.NBW_LIST + "?urltype=tree.TreeTempUrl&wbtreeid=${category.treeId}"
        return if (page <= 1) base else "$base&PAGENUM=$page"
    }

    fun detailUrl(path: String): String = "https://${SztuConfig.NBW_HOST}/$path"

    /** 详情页里的「上一篇/下一篇」是相对路径（`52707.htm`），要补成站点内的 info 路径。 */
    fun resolveSibling(pagePath: String, sibling: String): String = when {
        NOTICE_PATH.matches(sibling) -> sibling
        sibling.matches(Regex("""^\d+\.htm$""")) -> pagePath.substringBeforeLast('/') + "/" + sibling
        else -> sibling
    }
}
