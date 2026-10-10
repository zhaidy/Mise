package com.personal.mise.importer

object UrlExtractor {
    private val url = Regex("https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'*+,;=%]+")

    /** 分享文字里通常是「标题 + 链接」，取第一个 URL */
    fun firstUrl(text: String): String? =
        url.find(text)?.value?.trimEnd('.', ',', ')', ']', '\'', '!', '?')
}
