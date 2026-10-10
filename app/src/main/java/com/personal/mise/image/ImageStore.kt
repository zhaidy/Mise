package com.personal.mise.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 图片存在 App 私有目录 files/images/，数据库里只存相对路径（如 images/xxx.jpg）。
 * 保存时长边压缩到 1600 像素，存成 JPEG。
 */
class ImageStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(relativePath: String): File = File(context.filesDir, relativePath)

    /** 从相册选的图片 */
    suspend fun importUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val size = info.size
                    val scale = MAX_SIDE.toFloat() / max(size.width, size.height)
                    if (scale < 1f) {
                        decoder.setTargetSize(
                            (size.width * scale).roundToInt().coerceAtLeast(1),
                            (size.height * scale).roundToInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@runCatching null
                decodeBytes(bytes)
            }
            bitmap?.let { save(it) }
        }.getOrNull()
    }

    /** 网上下载的图片字节 */
    suspend fun saveBytes(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching { decodeBytes(bytes)?.let { save(it) } }.getOrNull()
    }

    fun delete(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        runCatching { file(relativePath).delete() }
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return scaleDown(decoded)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= MAX_SIDE) return bitmap
        val scale = MAX_SIDE.toFloat() / longSide
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun save(bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(dir, name)
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        return "$DIR/$name"
    }

    companion object {
        const val DIR = "images"
        const val MAX_SIDE = 1600
    }
}
