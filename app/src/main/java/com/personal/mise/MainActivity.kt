package com.personal.mise

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.ui.MiseRoot
import com.personal.mise.ui.NavRequest
import com.personal.mise.ui.theme.MiseTheme
import com.personal.mise.update.UpdateNotifications
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val navRequests = MutableStateFlow<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 更新后已经回到应用了，“已更新，点这里打开”的通知就没用了
        UpdateNotifications.cancelUpdated(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MiseTheme {
                MiseRoot(navRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    sharedImages(intent).takeIf { it.isNotEmpty() }?.let { navRequests.value = NavRequest.ImportImages(it) }
                    return
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                navRequests.value = NavRequest.Import(text, aiNote = !UrlExtractor.isLinkShare(text))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                sharedImages(intent).takeIf { it.isNotEmpty() }?.let { navRequests.value = NavRequest.ImportImages(it) }
            }
            ACTION_OPEN_COOKING -> {
                val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
                navRequests.value = NavRequest.Cooking(recipeId)
            }
        }
    }

    /** 分享来的图片：系统一般会放进 ClipData（读权限也跟着它授予），没有时再看 EXTRA_STREAM */
    private fun sharedImages(intent: Intent): List<Uri> {
        intent.clipData?.let { clip ->
            val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            if (uris.isNotEmpty()) return uris
        }
        return if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
    }

    companion object {
        const val ACTION_OPEN_COOKING = "com.personal.mise.OPEN_COOKING"
        const val EXTRA_RECIPE_ID = "recipe_id"

        /** 从备份恢复后数据库已关闭，整个进程重来一遍 */
        fun restartApp(context: Context) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }
}
