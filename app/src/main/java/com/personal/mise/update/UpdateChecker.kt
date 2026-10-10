package com.personal.mise.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** 新版本信息：build 号就是 CI 的 run number，同时也是 versionCode */
data class UpdateInfo(val build: Long, val apkUrl: String)

/** 从 GitHub Releases 检查并安装新版本（CI 每次推 main 会发一个 build-N 的 Release） */
class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient()

    /** 有更新返回 [UpdateInfo]，已是最新返回 null；网络出错会抛异常 */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body.string()
        }
        val json = JSONObject(body)
        val build = json.getString("tag_name").removePrefix("build-").toLongOrNull()
            ?: return@withContext null
        if (build <= currentBuild()) return@withContext null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                return@withContext UpdateInfo(build, asset.getString("browser_download_url"))
            }
        }
        null
    }

    /** 下载 APK 到缓存目录 */
    suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "mise-${info.build}.apk")
        val request = Request.Builder().url(info.apkUrl).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body.byteStream().use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        file
    }

    /** 调起系统安装器；还没授权“安装未知应用”时先跳到授权页，返回 false */
    fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    private fun currentBuild(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    private companion object {
        const val REPO = "zhaidy/Mise"
    }
}
