package com.classflow.timetable.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 自定义背景图的落盘与读取。
 *
 * 为什么不直接记住用户选的 `content://` URI：
 * 相册/文件选择器给的是一次性读权限，进程重启或过一段时间就失效了，
 * 到时候背景会变成空白 —— 用户只会觉得"这 App 坏了"。
 * 所以选中后立刻解码、缩到屏幕够用的尺寸、压成 JPEG 存进应用私有目录。
 *
 * 缩到 [MAX_EDGE] 是因为手机相册的原图动辄 4000×3000，
 * 直接当背景每次启动都要解一张 48MB 的位图，低端机会直接 OOM。
 */
object BackdropStore {

    /** 固定文件名：一个用户只有一张背景，覆盖即可。 */
    const val FILE_NAME = "backdrop.jpg"

    /** 长边上限。1440p 屏 + 放大裁切也够用，再大只是浪费内存。 */
    private const val MAX_EDGE = 1600

    private const val JPEG_QUALITY = 88

    fun dir(context: Context): File = File(context.filesDir, "backdrop")

    fun file(context: Context): File = File(dir(context), FILE_NAME)

    /** 复制用户选的图到私有目录；失败返回 false（调用方据此提示）。 */
    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeScaled(context, uri) ?: return@runCatching false
            dir(context).mkdirs()
            FileOutputStream(file(context)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()
            true
        }.getOrDefault(false)
    }

    suspend fun load(context: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(context)
            if (!target.exists()) return@runCatching null
            BitmapFactory.decodeFile(target.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        runCatching { file(context).delete() }
        Unit
    }

    /**
     * 两段式解码：先只读尺寸算 [BitmapFactory.Options.inSampleSize]，再真解码。
     * 直接 decodeStream 一张 12MP 的图，光预览就吃掉 48MB。
     */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}
