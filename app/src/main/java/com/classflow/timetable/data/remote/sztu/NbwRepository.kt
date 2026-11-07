package com.classflow.timetable.data.remote.sztu

import com.classflow.timetable.domain.model.NbwCategory
import com.classflow.timetable.domain.model.NbwDetail
import com.classflow.timetable.domain.model.NbwPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 公文通的取数层。
 *
 * 三点和教务那边不一样：
 *  1. **不需要登录。** 公文通是公开页面，没有 Cookie 也能读。所以单独用一个干净的
 *     [MiniHttp] 实例，不碰用户登录态的 CookieJar —— 免得某次公文通请求污染教务会话。
 *  2. **只在校内网可达。** `nbw.sztu.edu.cn` 解析到 10.1.12.148，校外直连必然超时。
 *     所以错误必须分成「连不上（可能要连 VPN）」和「结构变了（App 要更新）」两类，
 *     混成一个"加载失败"会让用户白白去折腾网络。
 *  3. **一定要能离线。** 缓存怎么存由上层决定，这里只负责把异常翻译成人话。
 */
@Singleton
class NbwRepository @Inject constructor(
    // 不能写成 `= MiniHttp(CookieJar())` 这种默认参数：
    // Kotlin 会额外生成一个无参构造函数，Dagger 会认成"两个 @Inject 构造函数"直接报错。
    @Named("nbw") private val http: MiniHttp,
) {

    sealed interface Result<out T> {
        data class Ok<T>(val data: T) : Result<T>

        /** 连不上：先查是否在校园网 / 有没有连 VPN。 */
        data class Offline(val message: String) : Result<Nothing>

        /** 连上了但解析不了：站点改版，App 需要更新。 */
        data class Broken(val message: String) : Result<Nothing>
    }

    suspend fun list(category: NbwCategory, page: Int): Result<NbwPage> = io {
        val url = NbwParser.listUrl(category, page)
        val resp = http.get(url, referer = SztuConfig.NBW_LIST)
        if (resp.code !in 200..299) return@io Result.Broken("公文通返回 HTTP ${resp.code}")
        Result.Ok(NbwParser.parseList(resp.body, page))
    }

    suspend fun detail(path: String): Result<NbwDetail> = io {
        val url = NbwParser.detailUrl(path)
        val resp = http.get(url, referer = SztuConfig.NBW_LIST)
        if (resp.code !in 200..299) return@io Result.Broken("公文通返回 HTTP ${resp.code}")
        Result.Ok(NbwParser.parseDetail(resp.body, path, url))
    }

    /**
     * 切到 IO 线程并把网络异常翻译成 [Result]。
     *
     * HttpURLConnection 抛的是 UnknownHostException / ConnectException /
     * SocketTimeoutException，全是 IOException 的子类，统一归到「连不上」。
     * 站点结构问题在 [NbwParser] 里是 `require`（IllegalArgumentException），
     * 属于「连上了但页面不认识」，必须和网络问题分开报。
     */
    private suspend fun <T> io(block: () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            Result.Broken(e.message ?: "公文通页面结构变了")
        } catch (e: java.io.IOException) {
            Result.Offline("连不上公文通，确认是否在校园网 / 已连上 VPN")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Broken("解析公文通失败：${e.message ?: e::class.simpleName}")
        }
    }
}
