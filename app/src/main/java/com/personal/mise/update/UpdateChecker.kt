package com.personal.mise.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File

/**
 * 新版本信息：build 号就是 CI 的 run number，同时也是 versionCode。
 * [changes] 是当前版本之后每个 build 的更新内容，新的在前
 */
data class UpdateInfo(val build: Long, val apkUrl: String, val changes: List<String> = emptyList())

/** 从 GitHub Releases 检查并安装新版本（CI 每次推 main 会发一个 build-N 的 Release） */
class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient()

    /** 有更新返回 [UpdateInfo]，已是最新返回 null；网络出错会抛异常 */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        // 拉最近几十个 Release，把比当前版本新的那些的说明都拼起来，跳过好几个版本也不漏
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body.string()
        }
        val current = currentBuild()
        val releases = JSONArray(body).let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            .mapNotNull { r -> r.getString("tag_name").removePrefix("build-").toLongOrNull()?.let { it to r } }
            .filter { (build, _) -> build > current }
            .sortedByDescending { (build, _) -> build }
        val (build, latest) = releases.firstOrNull() ?: return@withContext null
        val apkUrl = latest.getJSONArray("assets").let { assets ->
            (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url")
        } ?: return@withContext null
        val changes = releases.flatMap { (_, r) -> parseChanges(r.optString("body")) }.distinct()
        UpdateInfo(build, apkUrl, changes)
    }

    /** 下载 APK 到缓存目录；先清掉以前下的包，最多只留一个 */
    suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = updatesDir().apply { deleteRecursively(); mkdirs() }
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

    /**
     * 用 PackageInstaller 装新版。Android 12+ 且安装来源是 Mise 自己时直接装好、不弹确认；
     * 否则系统会经 [InstallResultReceiver] 弹一次确认。还没授权“安装未知应用”时先跳到授权页，返回 false
     */
    suspend fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("mise.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                // FLAG_MUTABLE：系统要往里面填安装结果
                val callback = PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                markRelaunchRequested(context)
                session.commit(callback.intentSender)
            }
        }
        return true
    }

    /** 删掉下载过的安装包；启动时调用，装完新版后旧包就没用了 */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        updatesDir().deleteRecursively()
    }

    private fun updatesDir() = File(context.cacheDir, "updates")

    private fun currentBuild(): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    internal companion object {
        const val REPO = "zhaidy/Mise"

        private const val PREFS = "update"
        private const val KEY_RELAUNCH = "relaunch_after_update"

        /** 记一下“是应用里点的更新”，新版装好后 [UpdatedReceiver] 据此自动重新打开 */
        fun markRelaunchRequested(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_RELAUNCH, System.currentTimeMillis()).commit()
        }

        /** 读一次就清掉；超过 30 分钟的标记当作过期（比如那次安装失败或被取消了） */
        fun consumeRelaunchRequest(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val at = prefs.getLong(KEY_RELAUNCH, 0)
            prefs.edit().remove(KEY_RELAUNCH).apply()
            return at > 0 && System.currentTimeMillis() - at < 30 * 60 * 1000
        }

        /** Release 说明是 CI 用提交标题生成的 Markdown 列表，一行一条 */
        fun parseChanges(body: String): List<String> =
            body.lines()
                .map { it.trim().removePrefix("- ").removePrefix("* ").trim() }
                .filter { it.isNotEmpty() }
    }
}
