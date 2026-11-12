package com.classflow.timetable.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.classflow.timetable.core.security.SecureBox
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.preferences.core.PreferenceDataStoreFactory

/**
 * 教务系统账号的本地存储。
 *
 * 三件事分开放：
 * · 学号          明文（本身不是秘密，界面上也要显示）
 * · 密码          Android Keystore 加密后存，只是为了让「刷新课表」不必每次手打
 * · 会话 Cookie  直接存，会话过期就当没有
 *
 * 用户不勾「记住密码」时，密码字段永远是空的，本机不留任何可解密的东西。
 *
 * 「情侣课表」（另一个人的课表）单独一套键：
 *  - 只存会话和学号，**不存密码** —— 别人的密码没有理由留在本机
 *  - 会话分开存，否则给 TA 登录一次就会把自己的登录状态顶掉
 */
@Singleton
class SztuAccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val USERNAME = stringPreferencesKey("sztu_username")
        val PASSWORD = stringPreferencesKey("sztu_password_enc")
        val COOKIES = stringPreferencesKey("sztu_cookies")
        val SEMESTER = stringPreferencesKey("sztu_semester")
        val SEMESTER_NAME = stringPreferencesKey("sztu_semester_name")
        val LAST_SYNC = longPreferencesKey("sztu_last_sync")
        // 上次尝试「自动补教室」的时间。补不上（比如教务自己没写教室）时别每次启动都去敲一遍
        val ROOM_REPAIR_AT = longPreferencesKey("sztu_room_repair_at")
        // 情侣课表：只记「谁的课表 + 会话」，不记密码
        val PARTNER_USERNAME = stringPreferencesKey("sztu_partner_username")
        val PARTNER_COOKIES = stringPreferencesKey("sztu_partner_cookies")
        val PARTNER_SEMESTER = stringPreferencesKey("sztu_partner_semester")
        val PARTNER_SEMESTER_NAME = stringPreferencesKey("sztu_partner_semester_name")
        val PARTNER_LAST_SYNC = longPreferencesKey("sztu_partner_last_sync")
    }

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("sztu_account") },
        )
    }

    val username: Flow<String> = dataStore.data.map { it[Keys.USERNAME].orEmpty() }

    val semesterName: Flow<String> = dataStore.data.map { it[Keys.SEMESTER_NAME].orEmpty() }

    val lastSyncAt: Flow<Long> = dataStore.data.map { it[Keys.LAST_SYNC] ?: 0L }

    suspend fun currentUsername(): String = username.first()

    suspend fun savedPassword(): String? = SecureBox.decrypt(
        dataStore.data.first()[Keys.PASSWORD],
    )

    suspend fun savedCookies(): String = dataStore.data.first()[Keys.COOKIES].orEmpty()

    suspend fun savedSemester(): String = dataStore.data.first()[Keys.SEMESTER].orEmpty()

    /** 上次尝试自动补教室的时间戳（0 表示从没试过）。 */
    suspend fun lastRoomRepairAt(): Long = dataStore.data.first()[Keys.ROOM_REPAIR_AT] ?: 0L

    suspend fun markRoomRepairAttempt(at: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs -> prefs[Keys.ROOM_REPAIR_AT] = at }
    }

    suspend fun saveLogin(
        username: String,
        password: String?,
        rememberPassword: Boolean,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username.trim()
            if (rememberPassword && !password.isNullOrEmpty()) {
                SecureBox.encrypt(password)?.let { prefs[Keys.PASSWORD] = it }
            } else {
                prefs.remove(Keys.PASSWORD)
            }
        }
    }

    suspend fun saveSession(cookies: String, semesterKey: String, semesterName: String) {
        dataStore.edit { prefs ->
            prefs[Keys.COOKIES] = cookies
            prefs[Keys.SEMESTER] = semesterKey
            prefs[Keys.SEMESTER_NAME] = semesterName
            prefs[Keys.LAST_SYNC] = System.currentTimeMillis()
        }
    }

    suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.COOKIES)
            prefs.remove(Keys.LAST_SYNC)
        }
    }

    suspend fun forgetEverything() {
        dataStore.edit { it.clear() }
    }

    // ---------- 情侣课表（另一个人的会话） ----------

    /** TA 的学号，用来显示「已导入 202500000000 的课表」；没导过就是空串。 */
    val partnerUsername: Flow<String> = dataStore.data.map { it[Keys.PARTNER_USERNAME].orEmpty() }

    val partnerSemesterName: Flow<String> = dataStore.data.map { it[Keys.PARTNER_SEMESTER_NAME].orEmpty() }

    val partnerLastSyncAt: Flow<Long> = dataStore.data.map { it[Keys.PARTNER_LAST_SYNC] ?: 0L }

    suspend fun currentPartnerUsername(): String = partnerUsername.first()

    suspend fun savedPartnerCookies(): String = dataStore.data.first()[Keys.PARTNER_COOKIES].orEmpty()

    suspend fun savedPartnerSemester(): String = dataStore.data.first()[Keys.PARTNER_SEMESTER].orEmpty()

    suspend fun savePartnerLogin(username: String) {
        dataStore.edit { prefs -> prefs[Keys.PARTNER_USERNAME] = username.trim() }
    }

    suspend fun savePartnerSession(
        cookies: String,
        semesterKey: String,
        semesterName: String,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.PARTNER_COOKIES] = cookies
            prefs[Keys.PARTNER_SEMESTER] = semesterKey
            prefs[Keys.PARTNER_SEMESTER_NAME] = semesterName
            prefs[Keys.PARTNER_LAST_SYNC] = System.currentTimeMillis()
        }
    }

    /** 清除 TA 的会话（不动自己的登录状态）。 */
    suspend fun forgetPartnerSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PARTNER_COOKIES)
            prefs.remove(Keys.PARTNER_LAST_SYNC)
        }
    }

    /** 卸载 TA 的整份资料：学号 + 会话都清掉。 */
    suspend fun forgetPartner() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.PARTNER_USERNAME)
            prefs.remove(Keys.PARTNER_COOKIES)
            prefs.remove(Keys.PARTNER_SEMESTER)
            prefs.remove(Keys.PARTNER_SEMESTER_NAME)
            prefs.remove(Keys.PARTNER_LAST_SYNC)
        }
    }
}
