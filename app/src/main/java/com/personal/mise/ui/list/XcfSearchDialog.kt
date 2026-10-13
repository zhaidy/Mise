package com.personal.mise.ui.list

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.mise.importer.RecipeImporter
import com.personal.mise.importer.SearchHit
import com.personal.mise.importer.XiachufangParser
import com.personal.mise.importer.XiachufangSearch
import com.personal.mise.importer.loadImportUrl
import com.personal.mise.importer.setUpForImport
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import com.personal.mise.ui.common.rememberApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜下厨房：输入菜名搜，结果列成表，点一条就用它的链接导入（和粘贴链接导入走同一条路）。
 * 搜索页打不开或解析不出来时，可以「在网页里搜」：直接显示下厨房搜索页，点进哪个菜谱就导入哪个。
 */
@Composable
fun XcfSearchDialog(onDismiss: () -> Unit, onPick: (url: String) -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var keyword by rememberSaveable { mutableStateOf("") }
    // 当前结果对应的关键词；改了输入框但没按搜索时，「加载更多」和「在网页里搜」还用这个
    var searched by rememberSaveable { mutableStateOf("") }
    val hits = remember { mutableStateListOf<SearchHit>() }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var webOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun load(word: String, next: Int) {
        if (word.isBlank() || loading) return
        loading = true
        error = null
        if (next == 1) {
            hits.clear()
            searched = word.trim()
        }
        scope.launch {
            try {
                val found = app.importer.search(word, next)
                val fresh = found.filter { hit -> hits.none { it.recipeId == hit.recipeId } }
                hits += fresh
                page = next
                hasMore = fresh.isNotEmpty()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    // 转屏后重新搜一次
    LaunchedEffect(Unit) {
        if (searched.isNotBlank()) load(searched, 1) else focus.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        placeholder = { Text("在下厨房搜菜名，比如 红烧肉") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); load(keyword, 1) }),
                        trailingIcon = {
                            IconButton(onClick = { keyboard?.hide(); load(keyword, 1) }, enabled = keyword.isNotBlank()) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索")
                            }
                        },
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        shape = FieldShape,
                        colors = miseFieldColors(),
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        hits.isEmpty() && loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        hits.isEmpty() -> Column(
                            Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                when {
                                    error != null -> "搜索失败：$error"
                                    searched.isNotBlank() -> "没有搜到「$searched」"
                                    else -> "搜到后点一个菜谱，就会导入进来，导入后还能再改"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (searched.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { webOpen = true }) { Text("在网页里搜") }
                            }
                        }
                        else -> LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(hits, key = { it.recipeId }) { hit -> HitRow(hit, app.importer) { onPick(hit.url) } }
                            item {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    when {
                                        loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                        error != null -> TextButton(onClick = { load(searched, page + 1) }) { Text("加载失败，重试") }
                                        hasMore -> TextButton(onClick = { load(searched, page + 1) }) { Text("加载更多") }
                                    }
                                    TextButton(onClick = { webOpen = true }) { Text("在网页里搜") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (webOpen) {
        XcfWebSearchDialog(
            url = XiachufangSearch.searchUrl(searched.ifBlank { keyword }),
            onDismiss = { webOpen = false },
            onPick = { webOpen = false; onPick(it) },
        )
    }
}

@Composable
private fun HitRow(hit: SearchHit, importer: RecipeImporter, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(hit.coverUrl, importer, Modifier.width(96.dp).height(72.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            hit.honor?.let {
                Text(
                    it,
                    Modifier
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                )
            }
            hit.ingredients?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            hit.stats?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 搜索结果缩略图只放在内存里，不存到菜谱图片目录 */
private val thumbCache = LruCache<String, Bitmap>(60)

@Composable
private fun Thumb(url: String?, importer: RecipeImporter, modifier: Modifier) {
    val bitmap by produceState(url?.let { thumbCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = importer.fetchImage(url)?.let { bytes ->
            withContext(Dispatchers.Default) { decodeThumb(bytes) }
        }?.also { thumbCache.put(url, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** 缩到宽 300 像素左右再解码，原图很大时省内存 */
private fun decodeThumb(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 300) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * 直接显示下厨房搜索页（WebView 会自己过防爬虫验证），点进菜谱时拦下来，用这个菜谱的链接导入。
 * 手机版页面换菜谱可能不走真正的跳转（前端路由），所以网址变了也看一眼。
 */
@Composable
private fun XcfWebSearchDialog(url: String, onDismiss: () -> Unit, onPick: (url: String) -> Unit) {
    val context = LocalContext.current
    val pick by rememberUpdatedState(onPick)
    val webView = remember {
        WebView(context).apply {
            setUpForImport(RecipeImporter.MOBILE_UA)
            var picked = false
            fun check(target: String?): Boolean {
                val id = target?.let { XiachufangParser.recipeId(it) } ?: return false
                if (!picked) {
                    picked = true
                    pick(XiachufangParser.desktopUrl(id))
                }
                return true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    check(request.url.toString())

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    check(url)
                }
            }
            loadImportUrl(url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "点开想要的菜谱就会导入；有验证就按提示完成",
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
