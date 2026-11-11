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
)

/** 学期设置的编辑草稿（保存前允许非法值，由 UI 校验）。 */
data class SemesterDraft(
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val weekOverride: Int?,
)
