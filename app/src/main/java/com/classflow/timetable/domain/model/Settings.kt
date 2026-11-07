package com.classflow.timetable.domain.model

import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 全部来自 DataStore 的偏好设置。 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = true,
    val reminderMinutes: Int = 15,
)

/** 学期设置的编辑草稿（保存前允许非法值，由 UI 校验）。 */
data class SemesterDraft(
    val name: String,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val weekOverride: Int?,
)
