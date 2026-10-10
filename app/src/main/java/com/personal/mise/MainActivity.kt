package com.personal.mise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.ui.MiseRoot
import com.personal.mise.ui.NavRequest
import com.personal.mise.ui.theme.MiseTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val navRequests = MutableStateFlow<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                navRequests.value = NavRequest.Import(text, aiNote = !UrlExtractor.isLinkShare(text))
            }
            ACTION_OPEN_COOKING -> {
                val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
                navRequests.value = NavRequest.Cooking(recipeId)
            }
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
