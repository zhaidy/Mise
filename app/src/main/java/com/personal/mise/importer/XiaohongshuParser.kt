package com.personal.mise.importer

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

/** 小红书笔记：标题、正文和配图；排版随意，用料步骤常常写在图片上，后面交给 AI 整理 */
data class XhsNote(val title: String, val desc: String, val imageUrls: List<String>)

/**
 * 小红书笔记页面解析。所有和小红书页面结构有关的代码都在这个类里。
 *
 * 网页版把笔记数据放在 `window.__INITIAL_STATE__ = {...}` 里（JSON，但值可能是 undefined），
 * 笔记对象里有 title、desc、imageList。具体嵌套路径各版本不一样，所以按字段找笔记对象；
 * 找不到时退回 og 标签和 description。
 */
object XiaohongshuParser {

    private val hostRegex = Regex("^https?://(?:[\\w-]+\\.)*(?:xiaohongshu\\.com|xhslink\\.(?:com|cn))(?:[/:?#]|$)", RegexOption.IGNORE_CASE)

    fun isNoteUrl(url: String): Boolean = hostRegex.containsMatchIn(url)

    fun parse(html: String, pageUrl: String): XhsNote {
        val doc = Jsoup.parse(html, pageUrl)
        val state = doc.select("script").firstNotNullOfOrNull { initialState(it.data()) }
        val note = state?.let { knownNote(it) ?: findNote(it) }

        val title = note?.optString("title")?.cleanNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.let { stripSiteName(it) }.orEmpty()
        val desc = note?.optString("desc")?.cleanNull()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        val images = note?.optJSONArray("imageList")?.let { list ->
            (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let { img -> imageUrl(img) } }
        }.orEmpty().ifEmpty {
            doc.select("meta[property=og:image], meta[name=og:image]").map { it.attr("content").trim() }.filter { it.isNotEmpty() }
        }.map { https(it) }.distinct()

        return XhsNote(title, cleanDesc(desc), images)
    }

    /** 「#家常菜[话题]#」→「#家常菜」，「[赞R]」这类表情代码去掉 */
    fun cleanDesc(desc: String): String = desc
        .replace(Regex("#([^#\\[\\]\\n]+)\\[话题]#"), "#$1 ")
        .replace(Regex("\\[[^\\[\\]\\n]{1,8}R]"), "")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .trim()

    private fun stripSiteName(title: String) = title.replace(Regex("\\s*[-|_]\\s*小红书.*$"), "").trim()

    private val stateRegex = Regex("window\\.__INITIAL_STATE__\\s*=\\s*")

    /** JS 对象里的 undefined 不是合法 JSON，换成 null 再解析 */
    private fun initialState(script: String): JSONObject? {
        val m = stateRegex.find(script) ?: return null
        val json = script.substring(m.range.last + 1).trim().trimEnd(';')
            .replace(Regex("(?<=[:\\[,])\\s*undefined(?=\\s*[,}\\]])"), "null")
        return runCatching { JSONObject(json) }.getOrNull()
    }

    /** 电脑版在 note.noteDetailMap.<笔记 id>.note，手机版在 noteData.data.noteData */
    private fun knownNote(state: JSONObject): JSONObject? {
        val detailMap = state.optJSONObject("note")?.optJSONObject("noteDetailMap")
        val desktop = detailMap?.let { map ->
            map.keys().asSequence().firstNotNullOfOrNull { map.optJSONObject(it)?.optJSONObject("note") }
        }
        val mobile = state.optJSONObject("noteData")?.optJSONObject("data")?.optJSONObject("noteData")
        return listOfNotNull(desktop, mobile).firstOrNull { looksLikeNote(it) }
    }

    private fun looksLikeNote(node: JSONObject): Boolean =
        node.has("desc") && (node.has("imageList") || node.has("noteId")) &&
            (node.optString("desc").cleanNull().isNotBlank() || (node.optJSONArray("imageList")?.length() ?: 0) > 0)

    /** 递归找第一个像笔记的对象：有 desc，并且有 imageList 或 noteId（用户资料里也有 desc，要排除） */
    private fun findNote(node: Any?): JSONObject? = when (node) {
        is JSONObject ->
            if (looksLikeNote(node)) node else node.keys().asSequence().toList().firstNotNullOfOrNull { findNote(node.opt(it)) }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findNote(node.opt(it)) }
        else -> null
    }

    private fun imageUrl(img: JSONObject): String? {
        listOf("urlDefault", "url", "urlPre").forEach { key ->
            img.optString(key).cleanNull().takeIf { it.isNotBlank() }?.let { return it }
        }
        val infos = img.optJSONArray("infoList") ?: return null
        val urls = (0 until infos.length()).mapNotNull { i ->
            infos.optJSONObject(i)?.let { it.optString("imageScene") to it.optString("url").cleanNull() }?.takeIf { it.second.isNotBlank() }
        }
        return (urls.firstOrNull { it.first.contains("DFT") } ?: urls.lastOrNull())?.second
    }

    private fun https(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https://" + url.removePrefix("http://")
        else -> url
    }

    /** org.json 把 JSON null 读成字符串 "null" */
    private fun String.cleanNull() = if (this == "null") "" else this
}
