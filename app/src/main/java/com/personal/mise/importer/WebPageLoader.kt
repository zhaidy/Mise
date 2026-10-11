package com.personal.mise.importer

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * 用一个不显示的 WebView 打开网页，拿渲染后的 HTML。
 *
 * 下厨房的防爬虫会对直接请求返回 429 或验证页，要跑一段 JS 拿到 cookie 才放行；
 * OkHttp 不会跑 JS，WebView 是真浏览器，验证页会自己跳回正常页面。
 * 每秒取一次页面内容，accept 认可（比如解析出了菜谱）就返回。
 */
class WebPageLoader(private val context: Context) {

    /** 返回 (最终网址, HTML)；超时还没拿到认可的页面时返回 null */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun load(url: String, timeoutMs: Long = TIMEOUT_MS, accept: suspend (url: String, html: String) -> Boolean): Pair<String, String>? =
        withContext(Dispatchers.Main) {
            val webView = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                webViewClient = WebViewClient()
            }
            try {
                webView.loadUrl(url, mapOf("Accept-Language" to "zh-CN,zh;q=0.9"))
                withTimeoutOrNull(timeoutMs) {
                    var page: Pair<String, String>? = null
                    while (page == null) {
                        delay(POLL_MS)
                        val html = webView.html() ?: continue
                        val pageUrl = webView.url ?: url
                        if (accept(pageUrl, html)) page = pageUrl to html
                    }
                    page
                }
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }

    private suspend fun WebView.html(): String? = suspendCancellableCoroutine { cont ->
        evaluateJavascript("document.documentElement ? document.documentElement.outerHTML : ''") { result ->
            // 结果是 JSON 编码过的字符串
            val html = runCatching { JSONArray("[$result]").getString(0) }.getOrNull()
            if (cont.isActive) cont.resume(html?.takeIf { it.isNotBlank() })
        }
    }

    companion object {
        private const val TIMEOUT_MS = 25_000L
        private const val POLL_MS = 1_000L
    }
}
