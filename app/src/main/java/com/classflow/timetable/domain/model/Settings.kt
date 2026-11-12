package com.classflow.timetable.domain.model

import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 自定义背景的「存在感」。
 *
 * 名字说的是背景本身有多浓，不是压制有多强 —— 用户选「浓」就是希望照片看得更清楚，
 * 那界面文字就得更费劲，这本来就是一对矛盾，把选择权交给用户比自己拍板更靠谱。
 *
 * 默认给 [MEDIUM]：实测 LIGHT 在深色主题下几乎看不出是张照片（等于白设），
 * STRONG 又会让「今日无课」这种压在照片上的文字开始吃力。
 * 中间这档是「能看出照片、字也读得清」的平衡点，两个方向都留了余地。
 */
enum class BackdropStrength {
    /** 背景只留一点色彩倾向，文字最清晰。 */
    LIGHT,

    /** 背景能看出是什么图，界面依然干净。 */
    MEDIUM,

    /** 尽量保留照片原貌，界面会更花，读起来也更累。 */
    STRONG,
    ;

    val label: String
        get() = when (this) {
            LIGHT -> "淡"
            MEDIUM -> "中"
            STRONG -> "浓"
        }

    companion object {
        fun safeOf(raw: String?): BackdropStrength =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

/**
 * 可以放在底部栏、也可以当首页的入口。
 *
 * 这里只有「名字」，不带路由和图标——[com.classflow.timetable.domain.model] 这层不该知道 Compose。
 * 路由和图标在 `ui/navigation/TabSpecs.kt` 里映射。
 *
 * [SETTINGS] 是固定的：它是唯一能把收起来的入口重新打开的地方，
 * 允许隐藏就等于给用户一个「再也进不去设置」的开关，不划算。
 */
enum class NavTab(val label: String, val fixed: Boolean = false) {
    TODAY("今日"),
    WEEK("课表"),
    POMODORO("番茄钟"),
    BUS("巴士"),
    MAP("地图"),
    SETTINGS("设置", fixed = true),
    ;

    companion object {
        /**
         * 默认显示在底部栏的入口。
         *
         * 不是 `entries.toList()`：番茄钟是新加的，默认塞进去会让老用户升级完突然多一个按钮，
         * 五个挤在一排也快到极限了。它可以随时在设置里打开，需要的人自己放上来。
         */
        val default: List<NavTab> get() = listOf(TODAY, WEEK, BUS, MAP, SETTINGS)

        fun safeOf(raw: String?): NavTab? = entries.firstOrNull { it.name == raw }

        /**
         * 把 DataStore 里那串逗号分隔的名字读回集合。
         *
         * 三条规矩，都是为了「坏数据不能让底部栏消失」：
         * 认不出的名字直接丢（版本回滚时可能留下新名字）、固定项永远补回来
         * （它是唯一能把收起来的入口重新打开的地方）、整串是空就退回默认。
         */
        fun decode(raw: String?): Set<NavTab> {
            if (raw == null) return default.toSet()
            val parsed = raw.split(',').mapNotNull { safeOf(it.trim()) }.toSet()
            return parsed + entries.filter { it.fixed }
        }

        fun encode(tabs: Set<NavTab>): String =
            tabs.sortedBy { it.ordinal }.joinToString(",") { it.name }
    }
}

/** 全部来自 DataStore 的偏好设置。 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = true,
    val reminderMinutes: Int = 15,
    /**
     * 自定义背景图的文件名（放在 filesDir/backdrop/ 下）。
     * null 表示用默认的渐变 + 光斑背景。
     *
     * 存文件名而不是 content:// URI：相册给的临时授权随时会失效，
     * 到时候背景会变成一张灰图，用户根本不知道发生了什么。
     */
    val backdropFile: String? = null,
    /** 背景图的存在感，只在 [backdropFile] 不为空时有意义。 */
    val backdropStrength: BackdropStrength = BackdropStrength.MEDIUM,
    /** 冷启动落在哪一页。运行期间改它不会把用户从当前页面拽走，下次打开才生效。 */
    val homeTab: NavTab = NavTab.TODAY,
    /**
     * 底部栏显示哪些入口，至少含 [NavTab.SETTINGS]。
     *
     * 用 Set 而不是 List：这里的「有没有」才是语义，顺序在渲染时按枚举顺序排，
     * 不跟着用户点击开关的先后乱跳。
     */
    val bottomTabs: Set<NavTab> = NavTab.default.toSet(),
)

/** 学期设置的编辑草稿（保存前允许非法值，由 UI 校验）。 */
data class SemesterDraft(
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val weekOverride: Int?,
)
