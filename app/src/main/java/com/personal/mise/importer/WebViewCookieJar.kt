package com.personal.mise.importer

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 和 WebView 共用的 cookie：WebView 里跑完下厨房的防爬虫验证（自动的或者手动滑的）拿到的 cookie，
 * 之后直接请求也会带上，不会每次都从「第一次访问」开始、一重试就被限流；cookie 还会存到磁盘，下次打开 App 也在。
 * 拿不到 WebView（系统 WebView 坏了、在更新）时退回只在内存里存。
 */
class WebViewCookieJar : CookieJar {
    private val fallback = MemoryCookieJar()

    private val manager: CookieManager? by lazy {
        runCatching { CookieManager.getInstance().apply { setAcceptCookie(true) } }.getOrNull()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val m = manager
        if (m == null || runCatching { cookies.forEach { m.setCookie(url.toString(), it.toString()) } }.isFailure) {
            fallback.saveFromResponse(url, cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = manager?.let { m -> runCatching { m.getCookie(url.toString()) }.getOrNull() }
            ?: return fallback.loadForRequest(url)
        return header.split(';').mapNotNull { part ->
            val name = part.substringBefore('=').trim()
            if (name.isEmpty() || '=' !in part) return@mapNotNull null
            runCatching {
                Cookie.Builder().name(name).value(part.substringAfter('=').trim()).hostOnlyDomain(url.host).build()
            }.getOrNull()
        }
    }

    /** 把 cookie 写到磁盘 */
    fun flush() {
        manager?.let { runCatching { it.flush() } }
    }
}

/** 只存在内存里的 cookie */
class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store["${it.domain}|${it.path}|${it.name}"] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.values.removeAll { it.expiresAt < now }
        return store.values.filter { it.matches(url) }
    }
}
