package com.personal.mise.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.personal.mise.data.MiseDatabase
import com.personal.mise.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 手动备份：把数据库和 files/images/ 打包成一个 zip，存到用户选的位置。
 * Gemini Key 不放进备份文件。恢复后数据库实例已关闭，调用方需要重启 App。
 */
class BackupManager(private val context: Context, private val db: MiseDatabase) {

    private val dbFile: File get() = context.getDatabasePath(MiseDatabase.NAME)
    private val imagesDir: File get() = File(context.filesDir, ImageStore.DIR)

    /** 导出到 uri，返回菜谱数量 */
    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val sqlite = db.openHelper.writableDatabase
        // 把 WAL 里的改动写回主文件，拷贝 mise.db 一个文件就够了
        sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val count = sqlite.query("SELECT COUNT(*) FROM recipe WHERE deleted = 0").use { if (it.moveToFirst()) it.getInt(0) else 0 }

        val out = context.contentResolver.openOutputStream(uri) ?: error("无法写入所选文件")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            imagesDir.listFiles()?.filter { it.isFile }?.forEach { image ->
                zip.putNextEntry(ZipEntry("${ImageStore.DIR}/${image.name}"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        count
    }

    /** 用备份替换现有的全部数据，返回菜谱数量。之后数据库已关闭（失败时也可能已关闭），必须重启 App */
    suspend fun restore(uri: Uri): Int = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val newDb = File(dbFile.path + ".restore")
        val newImages = File(context.filesDir, "${ImageStore.DIR}.restore")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = if (entry.isDirectory) null else entryTarget(entry.name)
                    if (target != null) {
                        val file = File(staging, target)
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                }
            }

            val stagedDb = File(staging, DB_ENTRY)
            require(stagedDb.isFile) { "不是 Mise 的备份文件" }
            val header = stagedDb.inputStream().use { it.readNBytesCompat(SQLITE_HEADER.size) }
            require(header.contentEquals(SQLITE_HEADER)) { "备份里的数据库已损坏" }
            val (version, count) = SQLiteDatabase.openDatabase(stagedDb.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                // v1 的备份还没有删除标记，恢复后打开时会自动迁移
                val where = if (sqlite.version >= 2) " WHERE deleted = 0" else ""
                sqlite.version to sqlite.rawQuery("SELECT COUNT(*) FROM recipe$where", null)
                    .use { if (it.moveToFirst()) it.getInt(0) else 0 }
            }
            require(version >= 1) { "不是 Mise 的备份文件" }
            require(version <= MiseDatabase.VERSION) { "备份来自更新的版本，请先更新 App" }

            // 先把新文件放到目标旁边，关库后只剩改名，中途出错也不会只换掉一半
            stagedDb.copyTo(newDb, overwrite = true)
            newImages.deleteRecursively()
            newImages.mkdirs()
            File(staging, ImageStore.DIR).listFiles()?.forEach { it.copyTo(File(newImages, it.name), overwrite = true) }

            db.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            check(newDb.renameTo(dbFile)) { "替换数据库失败" }
            imagesDir.deleteRecursively()
            check(newImages.renameTo(imagesDir)) { "替换图片失败" }
            count
        } finally {
            staging.deleteRecursively()
            newDb.delete()
            newImages.deleteRecursively()
        }
    }

    companion object {
        const val DB_ENTRY = "mise.db"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val IMAGE_NAME = Regex("[A-Za-z0-9._-]+")

        /** zip 里允许的条目：mise.db 和 images/ 下一层的图片；其余（含 .. 这类路径）一律丢弃 */
        fun entryTarget(name: String): String? {
            if (name == DB_ENTRY) return DB_ENTRY
            val prefix = "${ImageStore.DIR}/"
            if (!name.startsWith(prefix)) return null
            val file = name.removePrefix(prefix)
            if (!IMAGE_NAME.matches(file) || file == "." || file == "..") return null
            return prefix + file
        }

        private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
            val buf = ByteArray(n)
            var read = 0
            while (read < n) {
                val r = read(buf, read, n - read)
                if (r < 0) break
                read += r
            }
            return if (read == n) buf else buf.copyOf(read)
        }
    }
}
