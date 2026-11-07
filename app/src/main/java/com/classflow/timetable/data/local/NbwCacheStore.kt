package com.classflow.timetable.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.classflow.timetable.domain.model.NbwDetail
import com.classflow.timetable.domain.model.NbwNotice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 已读过的公告正文。不进 UI 状态，纯离线兜底用。 */
@Serializable
data class NbwDetailCache(
    val title: String = "",
    val author: String = "",
    val publishedAt: String = "",
    val paragraphs: List<String> = emptyList(),
    val attachmentNames: List<String> = emptyList(),
    val attachmentUrls: List<String> = emptyList(),
    val savedAt: Long = 0L,
)

@Serializable
data class NbwCache(
    /** 「最新一页 + 全部栏目」的副本，专门给离线时当首页用。 */
    val notices: List<NbwNotice> = emptyList(),
    val savedAt: Long = 0L,
    val details: Map<String, NbwDetailCache> = emptyMap(),
) {
    val isEmpty: Boolean get() = notices.isEmpty()

    fun detailOf(path: String): NbwDetail? {
        val d = details[path] ?: return null
        return NbwDetail(
            path = path,
            title = d.title,
            author = d.author,
            publishedAt = d.publishedAt,
            paragraphs = d.paragraphs,
            attachments = d.attachmentNames.zip(d.attachmentUrls)
                .map { (n, u) -> com.classflow.timetable.domain.model.NbwAttachment(n, u) },
        )
    }
}

/**
 * 公文通离线缓存。
 *
 * 这个缓存不是"锦上添花"，是功能能不能用的前提：
 * `nbw.sztu.edu.cn` 只在校内网可达，用户出了校门（通勤、回家）就打不开。
 * 没有缓存的话，这个 Tab 在校外就是一句"连不上"，等于不存在。
 *
 * 只缓存首页（最新 20 条）+ 用户点开过的正文，不缓存历史页：
 * 15481 条全存进 DataStore 会把首启拖慢，而历史公告的时效性本来就不强。
 */
@Singleton
class NbwCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val key = stringPreferencesKey("nbw_cache_json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("nbw_cache") },
        )
    }

    val cache: Flow<NbwCache> = dataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString<NbwCache>(raw) }.getOrNull()
        } ?: NbwCache()
    }

    suspend fun saveHome(notices: List<NbwNotice>) {
        dataStore.edit { prefs ->
            val old = prefs[key]?.let { runCatching { json.decodeFromString<NbwCache>(it) }.getOrNull() }
                ?: NbwCache()
            prefs[key] = json.encodeToString(
                old.copy(notices = notices, savedAt = System.currentTimeMillis()),
            )
        }
    }

    suspend fun saveDetail(detail: NbwDetail) {
        dataStore.edit { prefs ->
            val old = prefs[key]?.let { runCatching { json.decodeFromString<NbwCache>(it) }.getOrNull() }
                ?: NbwCache()
            // 正文上限 80 篇：够翻一阵子，又不至于把 DataStore 撑到几 MB
            val trimmed = if (old.details.size >= MAX_DETAILS) {
                old.details.entries
                    .sortedByDescending { it.value.savedAt }
                    .take(MAX_DETAILS - 1)
                    .associate { it.key to it.value }
            } else {
                old.details
            }
            val entry = NbwDetailCache(
                title = detail.title,
                author = detail.author,
                publishedAt = detail.publishedAt,
                paragraphs = detail.paragraphs,
                attachmentNames = detail.attachments.map { it.name },
                attachmentUrls = detail.attachments.map { it.url },
                savedAt = System.currentTimeMillis(),
            )
            prefs[key] = json.encodeToString(
                old.copy(details = trimmed + (detail.path to entry)),
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(key) }
    }

    private companion object {
        const val MAX_DETAILS = 80
    }
}
