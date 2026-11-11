package com.classflow.timetable.domain.model

import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
)

/** 学期设置的编辑草稿（保存前允许非法值，由 UI 校验）。 */
data class SemesterDraft(
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val weekOverride: Int?,
)
