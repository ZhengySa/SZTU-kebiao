package com.classflow.timetable.domain.repository

import com.classflow.timetable.domain.model.AppSettings
import com.classflow.timetable.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setReminderEnabled(enabled: Boolean)
    suspend fun setReminderMinutes(minutes: Int)
}
