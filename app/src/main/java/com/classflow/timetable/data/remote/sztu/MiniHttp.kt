package com.classflow.timetable.data.remote.sztu

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

/** 极简 Cookie 容器：只按域名 + 名字存，够用且可序列化到本地。 */
class CookieJar {

    private data class Entry(val domain: String, val name: String, var value: String)

    private val entries = mutableListOf<Entry>()

    fun store(host: String, domain: String?, name: String, value: String) {
        val d = (domain ?: host).lowercase().removePrefix(".")
        entries.removeAll { it.domain == d && it.name == name }
        entries += Entry(d, name, value)
    }

    fun headerFor(host: String): String {
        val h = host.lowercase()
        return entries
            .filter { h == it.domain || h.endsWith("." + it.domain) }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    fun clear() = entries.clear()

    fun dump(): String = entries.joinToString("\n") { "${it.domain}\t${it.name}\t${it.value}" }

    fun restore(raw: String) {
        entries.clear()
        raw.lineSequence().forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 3) entries += Entry(parts[0], parts[1], parts[2])
        }
    }
}

/**
 * 只用 HttpURLConnection，不引第三方网络库。
 * 手动处理 302：因为登录流程靠"跟着跳转把 Cookie 一路带过去"，
 * 交给系统自动跳转反而拿不到中间态的 Set-Cookie。
 */
class MiniHttp(val jar: CookieJar) {

    data class Response(
        val code: Int,
        val finalUrl: String,
        val body: String,
        val bytes: ByteArray,
    )

    fun get(
        url: String,
        referer: String? = null,
        maxRedirects: Int = 8,
    ): Response = execute("GET", url, null, referer, ajax = false, maxRedirects = maxRedirects)

    fun postForm(
        url: String,
        form: Map<String, String>,
        referer: String?,
        ajax: Boolean = false,
        maxRedirects: Int = 8,
    ): Response {
        val body = form.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        return execute("POST", url, body, referer, ajax, maxRedirects)
    }

    private fun execute(
        method: String,
        url: String,
        body: String?,
        referer: String?,
        ajax: Boolean,
        maxRedirects: Int,
    ): Response {
        var current = url
        var currentMethod = method
        var currentBody = body
        var redirects = 0

        while (true) {
            val parsed = URL(current)
            require(SztuConfig.ALLOWED_HOSTS.contains(parsed.host.lowercase())) {
                "拒绝访问非白名单域名：${parsed.host}"
            }

            val conn = (parsed.openConnection() as HttpURLConnection).apply {
                requestMethod = currentMethod
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", SztuConfig.UA)
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                referer?.let { setRequestProperty("Referer", it) }
                if (ajax) setRequestProperty("X-Requested-With", "XMLHttpRequest")
                jar.headerFor(parsed.host).takeIf { it.isNotEmpty() }
                    ?.let { setRequestProperty("Cookie", it) }
                if (currentBody != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
            }

            try {
                if (currentBody != null) {
                    conn.outputStream.use { it.write(currentBody.toByteArray(Charsets.UTF_8)) }
                }

                val code = conn.responseCode
                absorbCookies(parsed.host, conn)

                if (code in 300..399 && redirects < maxRedirects) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) {
                        return Response(code, current, "", ByteArray(0))
                    }
                    current = java.net.URI(current).resolve(location.trim()).toString()
                    // 302 之后浏览器会改用 GET 且丢掉 body
                    currentMethod = "GET"
                    currentBody = null
                    redirects++
                    continue
                }

                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val bytes = stream?.use { readAll(it) } ?: ByteArray(0)
                val contentType = conn.contentType
                val text = decode(bytes, contentType)
                return Response(code, current, text, bytes)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun absorbCookies(host: String, conn: HttpURLConnection) {
        val all = conn.headerFields
            .filterKeys { it != null && it.equals("Set-Cookie", ignoreCase = true) }
            .values
            .flatten()
        all.forEach { raw ->
            val segments = raw.split(';')
            val pair = segments.firstOrNull()?.trim() ?: return@forEach
            val eq = pair.indexOf('=')
            if (eq <= 0) return@forEach
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            val domain = segments.drop(1)
                .map { it.trim() }
                .firstOrNull { it.startsWith("domain=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.trim()
            jar.store(host, domain, name, value)
        }
    }

    private fun readAll(stream: java.io.InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = stream.read(chunk)
            if (n <= 0) break
            buffer.write(chunk, 0, n)
        }
        return buffer.toByteArray()
    }

    /** 正方部分页面是 GBK，统一身份认证是 UTF-8，所以先看响应头，再看实际字节。 */
    private fun decode(bytes: ByteArray, contentType: String?): String {
        val declared = Regex("charset=([\\w\\-]+)", RegexOption.IGNORE_CASE)
            .find(contentType ?: "")
            ?.groupValues?.get(1)
        if (declared != null) {
            runCatching { return String(bytes, Charset.forName(declared)) }
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) String(bytes, Charset.forName("GBK")) else utf8
    }
}
