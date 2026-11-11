package com.classflow.timetable.ui.icon

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import com.classflow.timetable.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把用户选的图片变成桌面图标。
 *
 * 这里必须先说清楚一件事：**App 没法在运行时把自己的应用图标改成任意图片。**
 * manifest 的 icon 是编译期资源，[LauncherIcon] 那套 alias 也只能用打包好的图。
 * 所以"自定义图标"的落点是**桌面快捷方式** —— 系统允许快捷方式用任意 bitmap 当图标，
 * 效果是桌面上多一个你选的图，点开还是这个 App。
 *
 * 不糊弄成"替换了应用图标"：设置页的说明文字写的就是快捷方式，
 * 因为用户迟早会发现自己从抽屉里打开时还是原图标。
 */
object CustomIconPin {

    private const val SHORTCUT_ID = "classflow-custom-icon"

    /** 系统级尺寸：厂商 launcher 最大的快捷方式图标一般是 192dp，按 3x 留余量。 */
    private const val ICON_EDGE = 576

    fun isSupported(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    /**
     * 从相册 URI 生成图标并请求"添加到桌面"。
     *
     * 返回 false 的情况有两种，都不该静默：图片解码失败、或者系统/launcher 不支持固定快捷方式
     * （国产 ROM 里 MIUI、ColorOS 都支持，但不保证每家都在）。
     */
    suspend fun pin(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = decodeSquare(uri, context) ?: return@runCatching false
            val icon = circleCrop(source)
            if (source !== icon) source.recycle()

            val manager = context.getSystemService(ShortcutManager::class.java) ?: return@runCatching false
            if (!manager.isRequestPinShortcutSupported) return@runCatching false

            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel(context.applicationInfo.loadLabel(context.packageManager))
                .setLongLabel("ClassFlow")
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(icon))
                .setIntent(intent)
                .build()

            // 已存在的同名 shortcut 先更新，否则第二次点"自定义"没有任何反应
            runCatching { manager.updateShortcuts(listOf(shortcut)) }
            manager.requestPinShortcut(shortcut, null)
        }.getOrDefault(false)
    }

    /** 两张图取中心的正方形：`Crop` 会对不齐中心，图标会偏。 */
    private fun decodeSquare(uri: Uri, context: Context): Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val shortEdge = minOf(bounds.outWidth, bounds.outHeight)
        while (shortEdge / (sample * 2) >= ICON_EDGE) sample *= 2

        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val edge = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - edge) / 2
        val top = (bitmap.height - edge) / 2
        val cropped = Bitmap.createBitmap(bitmap, left, top, edge, edge)
        return if (cropped === bitmap) bitmap else {
            bitmap.recycle()
            cropped
        }
    }

    /**
     * 裁成圆形。
     *
     * 不裁成圆角方形：各家 launcher 对快捷方式图标的遮罩规则不一致，
     * 圆角方形的四个角有时会被二次裁切，露出白边。圆形在任何形状的遮罩下都不会出问题。
     */
    private fun circleCrop(source: Bitmap): Bitmap {
        val edge = source.width
        val radius = edge / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val target = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        Canvas(target).drawCircle(radius, radius, radius, paint)
        // 顺带缩到系统建议的上限，太大只是白白占内存
        return if (edge > ICON_EDGE) {
            Bitmap.createScaledBitmap(target, ICON_EDGE, ICON_EDGE, true).also {
                if (it !== target) target.recycle()
            }
        } else {
            target
        }
    }

    @Suppress("unused")
    private val requiresAtLeastO: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}
