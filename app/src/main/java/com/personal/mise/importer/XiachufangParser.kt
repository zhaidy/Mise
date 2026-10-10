package com.personal.mise.importer

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ParsedStep(val text: String, val imageUrl: String?)

data class ParsedRecipe(
    val title: String?,
    val coverUrl: String?,
    val servings: String?,
    /** (名称, 用量原文) */
    val ingredients: List<Pair<String, String>>,
    val steps: List<ParsedStep>,
) {
    val isComplete: Boolean get() = !title.isNullOrBlank() && ingredients.isNotEmpty() && steps.isNotEmpty()
    val score: Int get() = (if (title.isNullOrBlank()) 0 else 1) + ingredients.size + steps.size * 2 +
        steps.count { it.imageUrl != null } + (if (coverUrl != null) 1 else 0)
}

/**
 * 下厨房菜谱页面解析。所有和下厨房页面结构有关的代码都在这个类里，下厨房改版时只改这里。
 *
 * 选择器按真实页面验证过（见 src/test/resources/xiachufang/ 下的页面快照）：
 * - 标题 `h1.page-title`
 * - 封面 `div.cover img`
 * - 用料 `div.ings tr`，`td.name` + `td.unit`，`tr.delimiter` 是分组标题
 * - 步骤 `div.steps ol li`，`p.text` + `img`
 *
 * 页面里的 JSON-LD 只有短标题、裁切过的小封面、拼在一起的用料和步骤文字（没有步骤图），
 * 所以优先用 HTML，每一项解析不到时再退回 JSON-LD 和 og 标签。
 */
object XiachufangParser {

    private val recipeIdRegex = Regex("xiachufang\\.com/recipe/(\\d+)")

    fun recipeId(url: String): String? = recipeIdRegex.find(url)?.groupValues?.get(1)

    fun desktopUrl(recipeId: String) = "https://www.xiachufang.com/recipe/$recipeId/"

    fun parse(html: String, pageUrl: String): ParsedRecipe {
        val doc = Jsoup.parse(html, pageUrl)
        val ld = jsonLdRecipe(doc)

        val title = doc.selectFirst("h1.page-title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: ld?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

        val cover = doc.selectFirst("div.cover img")?.let { imageUrl(it) }
            ?: ld?.let { jsonImage(it) }?.let { cleanImageUrl(it, doc.location()) }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
                ?.let { cleanImageUrl(it, doc.location()) }

        val htmlIngredients = doc.select("div.ings tr").filterNot { it.hasClass("delimiter") }.mapNotNull { row ->
            val name = row.selectFirst("td.name")?.text()?.trim().orEmpty()
            val unit = row.selectFirst("td.unit")?.text()?.trim().orEmpty()
            if (name.isEmpty()) null else name to unit
        }
        val ingredients = htmlIngredients.ifEmpty {
            ld?.optJSONArray("recipeIngredient")?.strings()?.map { splitIngredient(it) }.orEmpty()
        }

        val htmlSteps = doc.select("div.steps ol li").mapNotNull { li ->
            val text = li.selectFirst("p.text")?.text()?.trim().orEmpty()
            val img = li.selectFirst("img")?.let { imageUrl(it) }
            if (text.isEmpty() && img == null) null else ParsedStep(text, img)
        }
        val steps = htmlSteps.ifEmpty { ld?.let { jsonSteps(it) }.orEmpty() }

        val servings = ld?.opt("recipeYield")?.let { yield ->
            when (yield) {
                is JSONArray -> yield.strings().firstOrNull()
                else -> yield.toString()
            }
        }?.trim()?.takeIf { it.isNotEmpty() }

        return ParsedRecipe(title, cover, servings, ingredients, steps)
    }

    private fun imageUrl(img: Element): String? {
        val raw = listOf("data-src", "data-original", "src")
            .map { img.absUrl(it).ifEmpty { img.attr(it) } }
            .firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
            ?: return null
        return cleanImageUrl(raw, img.baseUri())
    }

    /** 下厨房图片地址后面的 ?imageView2/... 是缩略图参数，去掉后是原图 */
    fun cleanImageUrl(url: String, base: String): String {
        var u = url.trim()
        if (u.startsWith("//")) u = "https:$u"
        if (!u.startsWith("http")) {
            u = runCatching { java.net.URI(base).resolve(u).toString() }.getOrDefault(u)
        }
        if (u.contains("chuimg.com")) u = u.substringBefore('?')
        return u
    }

    private fun jsonLdRecipe(doc: Document): JSONObject? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val text = script.data().trim()
            val parsed = runCatching {
                if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
            }.getOrNull() ?: continue
            findRecipe(parsed)?.let { return it }
        }
        return null
    }

    private fun findRecipe(node: Any?): JSONObject? = when (node) {
        is JSONObject -> {
            val type = node.opt("@type")
            val isRecipe = type == "Recipe" || (type is JSONArray && type.strings().contains("Recipe"))
            if (isRecipe) node else findRecipe(node.optJSONArray("@graph"))
        }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findRecipe(node.opt(it)) }
        else -> null
    }

    private fun jsonImage(ld: JSONObject): String? = when (val image = ld.opt("image")) {
        is String -> image
        is JSONArray -> image.opt(0)?.let { if (it is JSONObject) it.optString("url") else it.toString() }
        is JSONObject -> image.optString("url")
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun jsonSteps(ld: JSONObject): List<ParsedStep> = when (val instructions = ld.opt("recipeInstructions")) {
        is String -> splitNumberedSteps(instructions).map { ParsedStep(it, null) }
        is JSONArray -> (0 until instructions.length()).mapNotNull { i ->
            when (val item = instructions.opt(i)) {
                is JSONObject -> item.optString("text").trim().takeIf { it.isNotEmpty() }?.let { text ->
                    val image = item.opt("image")?.let { if (it is JSONObject) it.optString("url") else it.toString() }
                    ParsedStep(text, image?.takeIf { it.startsWith("http") })
                }
                is String -> item.trim().takeIf { it.isNotEmpty() }?.let { ParsedStep(it, null) }
                else -> null
            }
        }
        else -> emptyList()
    }

    /** 「1.番茄切丁 2.土豆切丁」→ 每步一条 */
    fun splitNumberedSteps(text: String): List<String> {
        val marker = Regex("(?:^|\\s)(\\d{1,2})[.、．]")
        val matches = marker.findAll(text).toList()
        if (matches.isEmpty()) return listOf(text.trim()).filter { it.isNotEmpty() }
        return matches.mapIndexed { i, m ->
            val start = m.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            text.substring(start, end).trim()
        }.filter { it.isNotEmpty() }
    }

    private const val UNITS = "(?:千克|公斤|毫升|大勺|小勺|汤匙|茶匙|克|斤|两|升|kg|g|ml|个|根|勺|片|瓣|颗|只|块|把|碗|杯|袋|包|条|棵|朵|张|段)"

    // 阿拉伯数字后面的单位可省略；中文数字必须带单位，避免把「十三香」拆开
    private val amountPrefix = Regex(
        "^(适量|少许|少量|若干|" +
            "\\d+(?:[./]\\d+)?\\s*${UNITS}?半?|" +
            "[一二两三四五六七八九十半几]+\\s*${UNITS}半?)\\s*(.+)$",
        RegexOption.IGNORE_CASE,
    )

    /** JSON-LD 里的用料是「150克鸡胸肉」这种拼在一起的写法，拆成名称和用量 */
    fun splitIngredient(text: String): Pair<String, String> {
        val t = text.trim()
        val m = amountPrefix.find(t) ?: return t to ""
        return m.groupValues[2].trim() to m.groupValues[1].trim()
    }

    private fun JSONArray.strings(): List<String> =
        (0 until length()).mapNotNull { opt(it)?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
}
