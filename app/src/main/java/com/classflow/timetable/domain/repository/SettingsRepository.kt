package com.classflow.timetable.domain.repository

import com.classflow.timetable.domain.model.AppSettings
import com.classflow.timetable.domain.model.BackdropStrength
import com.classflow.timetable.domain.model.NavTab
import com.classflow.timetable.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setReminderMinutes(minutes: Int)
    suspend fun setBackdropFile(name: String?)
    suspend fun setBackdropStrength(strength: BackdropStrength)
    suspend fun setHomeTab(tab: NavTab)

    /** 打开 / 收起某个底部入口。[NavTab.fixed] 的项会被忽略（设置不允许隐藏）。 */
    suspend fun setBottomTabVisible(tab: NavTab, visible: Boolean)
}
