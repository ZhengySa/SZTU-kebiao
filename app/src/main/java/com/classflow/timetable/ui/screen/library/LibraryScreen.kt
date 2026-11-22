package com.classflow.timetable.ui.screen.library

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette

/**
 * 图书馆的检索地址。
 *
 * `schoolId` 是学校在这套 OPAC 里的编号，改学校就得换 —— 所以单独放一处，
 * 别散在 UI 里。
 */
object LibraryTarget {
    private const val BASE =
        "https://agentdockingopac.featurelib.libsou.com/html/v2.0/opac/Ilib2021_interface/index.html"

    const val HOME: String = "$BASE?schoolId=32550"
}

/**
 * 百宝箱 → 图书馆藏查询。
 *
 * 为什么套 WebView 而不是原生列表：
 *  · 馆藏检索是学校买的服务，检索条件、分页、馆藏状态都在对方服务器上，
 *    原生页只能靠抓接口，人家一改版就废；
 *  · 这个站点本来就是移动端适配过的（自带 viewport），套进来跟原生小程序差不多。
 * 所以这一页只做四件事：装下网页、给进度、处理返回、出错了能给条活路
 * （重试 / 甩给浏览器）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LibraryScreen(onBack: () -> Unit) {
    val palette = LocalGlassPalette.current
    val context = LocalContext.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    // 旋转屏幕或进程被回收后按上次停留的位置回来，不用从头搜一遍
    var currentUrl by rememberSaveable { mutableStateOf(LibraryTarget.HOME) }

    fun openInBrowser() {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
        }
    }

    // 网页里往回翻一层，翻到底了才退出这一页 —— 否则从检索结果按返回会直接跳回设置
    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            // WebView 自己不会跟着页面一起回收，不 destroy 会漏一整个渲染进程
            webView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "图书馆藏查询",
            subtitle = "查书名 · 作者 · 索书号 · 在馆状态",
            onBack = onBack,
            actions = {
                if (failed) {
                    HeaderButton(palette.textSecondary, "重试") {
                        failed = false
                        webView?.reload()
                    }
                } else {
                    HeaderButton(palette.textSecondary, "刷新") { webView?.reload() }
                }
                HeaderButton(palette.textSecondary, "在浏览器打开", onClick = ::openInBrowser)
            },
        )

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        // 网页是白底，先铺一层白，免得加载瞬间闪出一块 App 的玻璃背景
                        setBackgroundColor(0xFFFFFFFF.toInt())

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            // 纯 https 站点，不许降级混载；file/content 一律关掉，
                            // 这个页面不需要读本地文件，多开着只是给网页多一条路。
                            // （setAllowFileAccessFromFileURLs 在 API 30 起已恒为 false，不再显式调用）
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            allowFileAccess = false
                            allowContentAccess = false
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let { currentUrl = it }
                                failed = false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let { currentUrl = it }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url ?: return false
                                return when (target.scheme) {
                                    "http", "https" -> false // 站内跳转，留在 WebView 里
                                    else -> {
                                        // tel: / mailto: / 微信 之类交给系统，否则点了没反应
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                                        true
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                // 只认主文档的失败。图片、统计脚本挂掉不该让整页变错误页
                                if (request?.isForMainFrame == true) failed = true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }

                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                update = { view ->
                    if (webView == null) webView = view
                    // 加载失败时 WebView 会自己画一页 "Webpage not available"，
                    // 那页英文报错跟下面的中文卡片叠在一起很丑，直接让它隐身。
                    view.visibility = if (failed) android.view.View.GONE else android.view.View.VISIBLE
                },
            )

            if (progress in 1..99) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(palette.textPrimary.copy(alpha = 0.06f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress / 100f)
                            .height(2.dp)
                            .background(palette.accent),
                    )
                }
            }

            if (failed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.textPrimary.copy(alpha = 0.03f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "没能打开图书馆",
                        color = palette.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "看看网络，或者换浏览器试试",
                        color = palette.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Column(
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palette.accent)
                            .clickable {
                                failed = false
                                webView?.reload()
                            }
                            .padding(horizontal = 26.dp, vertical = 11.dp),
                    ) {
                        Text("重新加载", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(
                        text = "用浏览器打开",
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .clickable(onClick = ::openInBrowser)
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

/** header 右侧的小圆按钮。文字用无障碍描述，图标自己表意。 */
@Composable
private fun HeaderButton(tint: Color, label: String, onClick: () -> Unit) {
    val palette = LocalGlassPalette.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(palette.glassFillTop)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (label == "刷新" || label == "重试") Icons.Filled.Refresh else Icons.Filled.OpenInBrowser,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
