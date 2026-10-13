package com.personal.mise.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder

/** 搜索结果里的一条菜谱；coverUrl 是带缩略图参数的小图，stats 是「综合评分 8.0（1234 做过）」这类原文 */
data class SearchHit(
    val recipeId: String,
    val title: String,
    val coverUrl: String?,
    val ingredients: String?,
    val stats: String?,
) {
    val url: String get() = XiachufangParser.desktopUrl(recipeId)
}

/**
 * 下厨房搜索页解析。电脑版搜索结果的结构：
 * `div.normal-recipe-list li`，里面 `div.cover img`（懒加载，图在 data-src）、`p.name a[href=/recipe/ID/]`、
 * `p.ing` 用料、`p.stats` 评分和做过的人数。
 *
 * 下厨房常改版，手机版页面结构也不一样，所以不死认这些 class：
 * 页面里每个指向 /recipe/数字/ 的链接都算一条结果，往上找到包着它的卡片（li 或 div.recipe），
 * 再在卡片里找标题、图片、用料；认得的 class 只是用来挑更准的那一项。
 */
object XiachufangSearch {

    private val recipeHref = Regex("/recipe/(\\d+)")
    private val spaces = Regex("\\s+")

    fun searchUrl(keyword: String, page: Int = 1): String {
        val q = URLEncoder.encode(keyword.trim(), "UTF-8")
        return "https://www.xiachufang.com/search/?keyword=$q&cat=1001" + if (page > 1) "&page=$page" else ""
    }

    fun parse(html: String, pageUrl: String): List<SearchHit> {
        val doc = Jsoup.parse(html, pageUrl)
        // 有结果列表就只看列表，免得把侧栏的「热门菜谱」也算进来
        val root = doc.selectFirst("div.normal-recipe-list, div.search-result-list") ?: doc.body() ?: return emptyList()
        val hits = LinkedHashMap<String, SearchHit>()
        for (link in root.select("a[href]")) {
            val id = recipeHref.find(link.attr("href"))?.groupValues?.get(1) ?: continue
            val card = card(link)
            val hit = hit(id, link, card) ?: continue
            // 同一张卡片里封面和菜名是两个链接，留信息多的那条
            val old = hits[id]
            if (old == null || hit.score > old.score) hits[id] = hit
        }
        return hits.values.toList()
    }

    /** 搜索页正常打开了，只是没搜到（页面上有搜索框，还写着没找到） */
    fun isEmptyResultPage(html: String): Boolean {
        val doc = Jsoup.parse(html)
        return doc.selectFirst("input[name=keyword]") != null && doc.body()?.text().orEmpty().let { "没有找到" in it || "没有搜到" in it }
    }

    /** 往上找包着这条菜谱的卡片；卡片里不能有别的菜谱的链接，否则就是整个列表了 */
    private fun card(link: Element): Element {
        var card = link
        var parent = link.parent()
        val id = recipeHref.find(link.attr("href"))?.groupValues?.get(1)
        while (parent != null && parent.tagName() != "body") {
            val others = parent.select("a[href]").any { a ->
                recipeHref.find(a.attr("href"))?.groupValues?.get(1).let { it != null && it != id }
            }
            if (others) break
            card = parent
            if (parent.tagName() == "li" || parent.hasClass("recipe")) break
            parent = parent.parent()
        }
        return card
    }

    private fun hit(id: String, link: Element, card: Element): SearchHit? {
        val img = card.selectFirst("div.cover img, img")
        val title = listOf(
            card.selectFirst("p.name, .name, .recipe-name, h3, h4")?.text(),
            link.text(),
            img?.attr("alt"),
        ).map { it?.replace(spaces, " ")?.trim().orEmpty() }.firstOrNull { it.isNotEmpty() } ?: return null
        val ingredients = card.selectFirst("p.ing, .ing, .ings")?.text()?.replace(spaces, " ")?.trim()?.takeIf { it.isNotEmpty() }
        val stats = card.selectFirst("p.stats, .stats")?.text()?.replace(spaces, " ")?.trim()?.takeIf { it.isNotEmpty() }
        return SearchHit(id, title, img?.let { XiachufangParser.imageUrl(it, original = false) }, ingredients, stats)
    }

    private val SearchHit.score: Int
        get() = title.length.coerceAtMost(1) + (if (coverUrl != null) 1 else 0) +
            (if (ingredients != null) 1 else 0) + (if (stats != null) 1 else 0)
}
