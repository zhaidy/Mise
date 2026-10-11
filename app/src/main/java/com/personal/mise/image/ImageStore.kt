package com.personal.mise.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    /** 同步下载了新图片时加一，界面读它，图片到了就重新显示 */
    val version = mutableIntStateOf(0)

    /** 上传用的字节：太大时缩小重压，保证放得进一个 Firestore 文档 */
    suspend fun bytesForSync(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = file(relativePath).takeIf { it.isFile }?.readBytes() ?: return@runCatching null
            if (bytes.size <= SYNC_MAX_BYTES) return@runCatching bytes
            val bitmap = decodeBytes(bytes)?.let { scaleDown(it, SYNC_SIDE) } ?: return@runCatching null
            var quality = 85
            var out: ByteArray
            do {
                out = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                quality -= 10
            } while (out.size > SYNC_MAX_BYTES && quality >= 35)
            bitmap.recycle()
            out.takeIf { it.size <= SYNC_MAX_BYTES }
        }.getOrNull()
    }

    /** 保存从别的手机同步来的图片；路径不合规时拒绝 */
    suspend fun saveSynced(relativePath: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!isSyncPath(relativePath)) return@withContext false
        val ok = runCatching {
            val target = File(dir, relativePath.removePrefix("$DIR/"))
            val part = File(target.path + ".part")
            part.writeBytes(bytes)
            part.renameTo(target)
        }.getOrDefault(false)
        if (ok) version.intValue++
        ok
    }

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

    private fun scaleDown(bitmap: Bitmap, maxSide: Int = MAX_SIDE): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longSide
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

        /** Firestore 一个文档最多 1 MiB，留点余量 */
        const val SYNC_MAX_BYTES = 900_000
        const val SYNC_SIDE = 1200
        private val NAME = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*")

        /** 只认 images/ 下一层、名字干净的文件，防止同步来的路径写到别处 */
        fun isSyncPath(path: String): Boolean {
            val prefix = "$DIR/"
            return path.startsWith(prefix) && NAME.matches(path.removePrefix(prefix)) && ".." !in path
        }
    }
}
