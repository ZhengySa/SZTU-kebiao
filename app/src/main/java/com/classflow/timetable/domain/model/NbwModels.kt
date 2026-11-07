package com.classflow.timetable.domain.model

import kotlinx.serialization.Serializable

/**
 * 公文通的一条公告（列表页里的一行）。
 *
 * 只留展示需要的字段：分类、部门、标题、日期、有没有附件。
 * 正文不进列表 —— 15481 条公告的正文全抓一遍既慢又没意义，
 * 点进详情页时再单独取。
 */
@Serializable
data class NbwNotice(
    /** 详情页路径，形如 `info/1019/52710.htm`。列表刷新后用它判断是不是同一条。 */
    val path: String,
    val title: String,
    val category: String,
    val department: String,
    /** 原样保留 `2026-11-05`，不转 LocalDate —— 页面偶尔出现非标准格式，转不了就整条丢了。 */
    val date: String,
    val hasAttachment: Boolean,
) {
    val id: String get() = path
}

/**
 * 公文通的栏目。
 *
 * 对应站点导航上的五个频道（教务/科研/行政/学工/校园）。
 * `wbtreeid` 是站点自带的栏目号，写死而不是从页面解析：
 * 导航栏在窄屏下会被折叠，解析不到就等于分类功能整体失灵。
 */
enum class NbwCategory(val treeId: String, val label: String) {
    ALL("1029", "全部"),
    JIAOWU("1018", "教务"),
    KEYAN("1019", "科研"),
    XINGZHENG("1020", "行政"),
    XUEGONG("1021", "学工"),
    XIAOYUAN("1022", "校园"),
}

/** 列表页 + 分页信息。 */
data class NbwPage(
    val notices: List<NbwNotice> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalCount: Int = 0,
)

/** 详情页正文。 */
data class NbwDetail(
    val path: String,
    val title: String,
    val author: String,
    /** `2026年11月05日 14:58`，原样保留。 */
    val publishedAt: String,
    /** 正文的纯文本段落，一段一条。保留段落是为了阅读节奏，不是给编译器看的。 */
    val paragraphs: List<String>,
    val attachments: List<NbwAttachment> = emptyList(),
    val prev: NbwLink? = null,
    val next: NbwLink? = null,
)

/** 附件。`url` 是站点拼好的绝对地址（download.jsp 的查询串里带 wbfileid）。 */
data class NbwAttachment(val name: String, val url: String)

/** 详情页底部「上一篇 / 下一篇」。 */
data class NbwLink(val title: String, val path: String)
