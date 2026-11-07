package com.classflow.timetable.data.remote.sztu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 登录页准备好之后拿到的两样东西：会话令牌 + 验证码图片。 */
data class LoginChallenge(
    val token: String,
    val captchaJpeg: ByteArray,
    val alreadyLoggedIn: Boolean = false,
)

sealed interface LoginOutcome {
    data object Success : LoginOutcome

    /** 账号密码对了但验证码错/过期，需要重新取一张验证码。 */
    data class CaptchaRejected(val message: String) : LoginOutcome

    data class Failed(val message: String) : LoginOutcome
}

class SessionExpiredException : Exception("登录状态已失效，请重新登录")

/**
 * 学校统一身份认证（竹云 UIS）+ 正方教务系统的会话客户端。
 *
 * 完整流程（每一步都在真机上验证过）：
 *  1. GET 教务系统首页 → 被 302 到认证页，页面里用 JS 注入一个会话令牌 spAuthChainCode
 *  2. GET 验证码图片（和会话绑定）
 *  3. POST ActionAuthChain，密码先用 DES 加密，带验证码 → 返回 JSON 表明密码对不对
 *  4. POST AuthnEngine（就是浏览器"提交表单"那一步）→ 302 一路跳回教务系统，拿到 jsxsd 会话
 *  5. 带着会话 GET 学期理论课表
 *
 * 关于验证码：**没有做任何识别或绕过**。图片原样交给用户，用户自己输入。
 */
class SztuAuthClient(
    val jar: CookieJar = CookieJar(),
) {

    private val http = MiniHttp(jar)
    private var token: String = ""

    suspend fun prepare(): LoginChallenge = withContext(Dispatchers.IO) {
        val landing = http.get(SztuConfig.JWXT_ENTRY)
        if (isJwxtHome(landing.finalUrl, landing.body) || hasTimetable(landing.body)) {
            return@withContext LoginChallenge("", ByteArray(0), alreadyLoggedIn = true)
        }

        token = Regex("""\$\(spCode\)\.val\("([^"]+)"\)""")
            .find(landing.body)?.groupValues?.get(1)
            ?: Regex("""id="spAuthChainCode1"[^>]*value="([^"]*)"""")
                .find(landing.body)?.groupValues?.get(1)
            ?: ""

        val captcha = http.get("${SztuConfig.CAPTCHA}?dt=${System.currentTimeMillis()}")
        LoginChallenge(token = token, captchaJpeg = captcha.bytes)
    }

    /**
     * @param captcha 用户从图片上读到的 4 位字符
     */
    suspend fun submit(
        username: String,
        password: String,
        captcha: String,
    ): LoginOutcome = withContext(Dispatchers.IO) {
        val form = mapOf(
            "op" to "login",
            "spAuthChainCode" to token,
            "j_username" to username.trim(),
            "j_password" to DesEcb.encryptPassword(password),
            "j_checkcode" to captcha.trim(),
        )

        val step1 = http.postForm(
            url = SztuConfig.AUTH_CHAIN_POST,
            form = form,
            referer = SztuConfig.AUTH_CHAIN_ENTRY,
            ajax = true,
        )

        val json = runCatching { JSONObject(step1.body) }.getOrNull()
            ?: return@withContext LoginOutcome.Failed(
                "认证服务返回了非预期的内容，可能是校园网/服务临时异常，稍后再试。"
            )

        if (json.optString("loginFailed") != "false") {
            val tip = json.optString("authnErrorTip").takeIf { it.isNotBlank() }
                ?: "登录失败，请检查账号密码。"
            val isCaptcha = tip.contains("验证码")
            return@withContext if (isCaptcha) {
                LoginOutcome.CaptchaRejected(tip)
            } else {
                LoginOutcome.Failed(tip)
            }
        }

        // 账号密码 + 验证码已通过，交给认证引擎把票据发回教务系统
        val step2 = http.postForm(
            url = SztuConfig.AUTH_ENGINE,
            form = form,
            referer = SztuConfig.AUTH_CHAIN_ENTRY,
            ajax = false,
        )

        if (isJwxtHome(step2.finalUrl, step2.body) || hasTimetable(step2.body)) {
            LoginOutcome.Success
        } else {
            LoginOutcome.Failed("账号校验通过了，但没进到教务系统，请重试一次。")
        }
    }

    /**
     * 用现有会话直接抓课表。会话过期时抛 [SessionExpiredException]，由上层引导重新登录。
     *
     * 判定标准只有一个：**响应里有没有 `id="timetable"` 这张表**。
     * 之前用「URL 里含 /jsxsd/」来判断，结果未登录时教务系统也会在 /jsxsd/ 下返回一个错误页，
     * 于是被误判成"已登录"，最后走到解析器才报"没找到课表表格"——错误信息指向了错误的原因。
     */
    suspend fun fetchTimetableHtml(semesterKey: String? = null): String =
        withContext(Dispatchers.IO) {
            val url = if (semesterKey.isNullOrBlank()) {
                SztuConfig.TIMETABLE
            } else {
                "${SztuConfig.TIMETABLE}?xnxq01id=$semesterKey"
            }
            val resp = http.get(url)
            if (hasTimetable(resp.body)) return@withContext resp.body

            if (isAuthPage(resp.finalUrl, resp.body)) throw SessionExpiredException()
            throw IllegalStateException(
                "教务系统没返回课表页（可能是这个学期还没排课，或服务临时异常）。",
            )
        }

    /**
     * 抓某个学期的成绩页。`semesterKey` 为空表示不指定学期（教务系统给什么就用什么）。
     *
     * 正方有两种实现：GET 带参数就能查，或者必须先 POST 表单。先 GET，
     * 拿回来的不是成绩页再补一次 POST —— 两种情况都见过，不能只赌一种。
     */
    suspend fun fetchGradesHtml(semesterKey: String? = null): String = withContext(Dispatchers.IO) {
        val url = if (semesterKey.isNullOrBlank()) {
            SztuConfig.GRADES
        } else {
            "${SztuConfig.GRADES}?kksj=$semesterKey"
        }

        val resp = http.get(url)
        if (ZfGradeParser.hasGradeTable(resp.body)) return@withContext resp.body
        if (isAuthPage(resp.finalUrl, resp.body) || ZfGradeParser.isLoginPage(resp.body)) {
            throw SessionExpiredException()
        }

        // GET 不成，试一次 POST（有的部署把查询绑在表单提交上）
        val posted = http.postForm(
            url = SztuConfig.GRADES,
            form = buildMap {
                put("kksj", semesterKey.orEmpty())
                put("kcxz", "")
                put("kcmc", "")
                put("xsfs", "all")
            },
            referer = SztuConfig.GRADES,
        )
        if (ZfGradeParser.hasGradeTable(posted.body)) return@withContext posted.body
        if (isAuthPage(posted.finalUrl, posted.body) || ZfGradeParser.isLoginPage(posted.body)) {
            throw SessionExpiredException()
        }

        throw IllegalStateException("教务系统没返回成绩页（可能是这学期还没出成绩，或服务临时异常）。")
    }

    /** 会话有效性探测：能拿到带课表的页面才算有效。 */
    suspend fun hasValidSession(): Boolean = withContext(Dispatchers.IO) {
        runCatching { hasTimetable(http.get(SztuConfig.TIMETABLE).body) }.getOrDefault(false)
    }

    /** 教学周历，用来算开学日期。拿不到不算失败，只是开学日期回退到用户原来的设置。 */
    suspend fun fetchCalendarHtml(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = http.get(SztuConfig.CALENDAR)
            if (isAuthPage(resp.finalUrl, resp.body)) null else resp.body
        }.getOrNull()
    }

    private fun hasTimetable(body: String): Boolean = body.contains("id=\"timetable\"")

    /** 是不是被踢回统一身份认证了（= 会话失效）。 */
    private fun isAuthPage(finalUrl: String, body: String): Boolean =
        finalUrl.contains(SztuConfig.AUTH_HOST) || body.contains("spAuthChainCode")

    /** 教务系统首页（登录成功后落到的那页），用来判断"其实已经登录过了"。 */
    private fun isJwxtHome(finalUrl: String, body: String): Boolean =
        finalUrl.contains("/jsxsd/framework/xsMain") || body.contains("xsMain.htmlx")

    /** 换一张验证码（同会话内重取，不影响已填的账号密码）。 */
    suspend fun refreshCaptcha(): ByteArray = withContext(Dispatchers.IO) {
        http.get("${SztuConfig.CAPTCHA}?dt=${System.currentTimeMillis()}").bytes
    }

    fun exportCookies(): String = jar.dump()

    fun importCookies(raw: String) {
        if (raw.isNotBlank()) jar.restore(raw)
    }

    /**
     * 从 WebView 的 CookieManager 导入会话。
     *
     * App 现在不再自己实现"输账号密码+验证码"的表单流程 —— 登录交给学校官方页面
     * （内嵌 WebView），验证码是学校自己的验证码，密码不经过 App 的任何输入框。
     * 登录成功后把 WebView 拿到的 Cookie 塞进 [CookieJar]，后续抓课表走的还是
     * 原来的 HttpURLConnection + 白名单通道。
     */
    fun importCookieHeader(host: String, cookieHeader: String) {
        cookieHeader.split(';').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val name = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (name.isNotEmpty()) jar.store(host, host, name, value)
            }
        }
    }
}
