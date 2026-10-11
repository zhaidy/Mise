package com.personal.mise.importer

object UrlExtractor {
    private val url = Regex("https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'*+,;=%]+")

    /** 分享文字里通常是「标题 + 链接」，取第一个 URL */
    fun firstUrl(text: String): String? =
        url.find(text)?.value?.trimEnd('.', ',', ')', ']', '\'', '!', '?')

    /**
     * 分享过来的是「一句标题 + 链接」（按链接导入），还是一大段笔记（交给 AI 整理）。
     * 去掉链接后只剩一句话以内的文字就当作链接；小红书的链接总是按链接导入（抓到的正文比分享文字全）。
     */
    fun isLinkShare(text: String): Boolean {
        val link = firstUrl(text) ?: return false
        if (XiaohongshuParser.isNoteUrl(link)) return true
        return text.replace(link, "").trim().length <= MAX_LINK_CAPTION
    }

    private const val MAX_LINK_CAPTION = 60
}
