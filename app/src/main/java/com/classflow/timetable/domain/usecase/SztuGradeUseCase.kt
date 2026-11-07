package com.classflow.timetable.domain.usecase

import com.classflow.timetable.data.local.SztuAccountStore
import com.classflow.timetable.data.remote.sztu.SztuAuthClient
import com.classflow.timetable.data.remote.sztu.ZfGradeParser
import javax.inject.Inject

/** 没登录过（本机没有会话）——和"会话过期"分开，提示语不一样。 */
class NotLoggedInException : Exception("还没有登录学校教务系统")

/**
 * 成绩查询用例。
 *
 * 每个学期单独抓一次：`cjcx_list?kksj=xxx`。
 * 每次都 new 一个 [SztuAuthClient] 并把同一份 Cookie 灌进去 —— 这样多个学期的请求
 * 可以并发，而不会踩到共享 CookieJar 的线程安全问题。
 */
class SztuGradeUseCase @Inject constructor(
    private val accountStore: SztuAccountStore,
) {

    suspend fun cookies(): String {
        val saved = accountStore.savedCookies()
        if (saved.isBlank()) throw NotLoggedInException()
        return saved
    }

    /** 抓一个学期；[key] 为 null 表示让教务系统自己决定（通常是默认学期/全部）。 */
    suspend fun load(key: String?, cookies: String): ZfGradeParser.Result {
        val client = SztuAuthClient().apply { importCookies(cookies) }
        val html = client.fetchGradesHtml(key)
        return ZfGradeParser.parse(html)
    }

}
