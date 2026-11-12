package com.classflow.timetable.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.classflow.timetable.domain.model.AppSettings
import com.classflow.timetable.domain.model.BackdropStrength
import com.classflow.timetable.domain.model.NavTab
import com.classflow.timetable.domain.model.ThemeMode
import com.classflow.timetable.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "classflow_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderMinutes = intPreferencesKey("reminder_minutes")
        val backdropFile = stringPreferencesKey("backdrop_file")
        val backdropStrength = stringPreferencesKey("backdrop_strength")
        val homeTab = stringPreferencesKey("home_tab")
        val bottomTabs = stringPreferencesKey("bottom_tabs")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                themeMode = prefs[Keys.themeMode]
                    ?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } }
                    ?: ThemeMode.SYSTEM,
                reminderEnabled = prefs[Keys.reminderEnabled] ?: true,
                reminderMinutes = prefs[Keys.reminderMinutes] ?: 15,
                backdropFile = prefs[Keys.backdropFile]?.takeIf { it.isNotBlank() },
                backdropStrength = BackdropStrength.safeOf(prefs[Keys.backdropStrength]),
                homeTab = NavTab.safeOf(prefs[Keys.homeTab]) ?: NavTab.TODAY,
                bottomTabs = NavTab.decode(prefs[Keys.bottomTabs]),
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    override suspend fun setReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.reminderEnabled] = enabled }
    }

    override suspend fun setReminderMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.reminderMinutes] = minutes.coerceIn(0, 120) }
    }

    override suspend fun setBackdropFile(name: String?) {
        context.dataStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(Keys.backdropFile) else prefs[Keys.backdropFile] = name
        }
    }

    override suspend fun setBackdropStrength(strength: BackdropStrength) {
        context.dataStore.edit { it[Keys.backdropStrength] = strength.name }
    }

    override suspend fun setHomeTab(tab: NavTab) {
        context.dataStore.edit { it[Keys.homeTab] = tab.name }
    }

    override suspend fun setBottomTabVisible(tab: NavTab, visible: Boolean) {
        if (tab.fixed) return
        context.dataStore.edit { prefs ->
            val current = NavTab.decode(prefs[Keys.bottomTabs]).toMutableSet()
            if (visible) current.add(tab) else current.remove(tab)
            // 收起最后一个也不拦：固定项永远在，底栏不会空掉
            prefs[Keys.bottomTabs] = NavTab.encode(current)
        }
    }
}
