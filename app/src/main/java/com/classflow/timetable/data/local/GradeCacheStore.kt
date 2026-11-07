package com.classflow.timetable.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.classflow.timetable.domain.model.GradeRank
import com.classflow.timetable.domain.model.GradeRecord
import com.classflow.timetable.domain.model.GradeSemester
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 上一次成功抓到的成绩，用来在网络不通/会话过期时还能看。 */
@Serializable
data class GradeCache(
    val savedAt: Long = 0L,
    val semesters: List<GradeSemester> = emptyList(),
    val rows: List<GradeRecord> = emptyList(),
    val rank: GradeRank? = null,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * 成绩缓存。
 *
 * 为什么不直接每次联网取：成绩是低频数据（一学期变几次），
 * 但会话是会过期的；过期之后如果连"上次看到的成绩"都看不到，
 * 这个功能就等于在没网/没登录时完全不存在。
 */
@Singleton
class GradeCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val key = stringPreferencesKey("grade_cache_json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("grade_cache") },
        )
    }

    val cache: Flow<GradeCache> = dataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString<GradeCache>(raw) }.getOrNull()
        } ?: GradeCache()
    }

    suspend fun save(value: GradeCache) {
        dataStore.edit { prefs -> prefs[key] = json.encodeToString(value) }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(key) }
    }
}
