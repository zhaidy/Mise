package com.personal.mise.ui.edit

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.mise.importer.RecipeImporter
import com.personal.mise.importer.XiachufangParser
import com.personal.mise.importer.loadImportUrl
import com.personal.mise.importer.outerHtml
import com.personal.mise.importer.setUpForImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 下厨房把自动打开拦住时，把网页直接显示出来，让用户自己过验证（滑块之类）。
 * 每秒看一下页面，解析出菜谱就把网页内容交给 onLoaded；验证拿到的 cookie 留在 WebView 里，之后直接请求也能用。
 */
@Composable
fun VerifyPageDialog(url: String, onDismiss: () -> Unit, onLoaded: (pageUrl: String, html: String) -> Unit) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            setUpForImport(RecipeImporter.MOBILE_UA)
            loadImportUrl(url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    val loaded by rememberUpdatedState(onLoaded)
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(webView) {
        while (!done) {
            delay(1_000)
            val html = webView.outerHtml() ?: continue
            val pageUrl = webView.url ?: url
            val found = withContext(Dispatchers.Default) { !XiachufangParser.parse(html, pageUrl).isEmpty }
            if (found) {
                done = true
                loaded(pageUrl, html)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "有验证就按提示完成，看到菜谱后会自动导入",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "关闭") }
                }
                AndroidView(factory = { webView }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
