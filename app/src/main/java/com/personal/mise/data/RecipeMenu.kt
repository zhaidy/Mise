package com.personal.mise.data

import com.personal.mise.util.DayText
import java.time.LocalDate

/** 菜单上「上次做的时间」的颜色档：一周内、一般、一个多月没做、没做过 */
enum class Freshness {
    FRESH, NORMAL, STALE, NEVER;

    companion object {
        fun of(date: String?, today: LocalDate = LocalDate.now()): Freshness {
            val d = date?.let { DayText.parse(it) } ?: return if (date == null) NEVER else NORMAL
            val days = today.toEpochDay() - d.toEpochDay()
            return when {
                days <= 7 -> FRESH
                days > 30 -> STALE
                else -> NORMAL
            }
        }
    }
}

/** 菜单上的一道菜；date 是最后一次做的日子，没做过为 null */
data class MenuItem(val recipe: Recipe, val date: String?, val freshness: Freshness)

/** 大类下的一组；sub 为 null 是没有小类的那些 */
data class MenuGroup(val sub: String?, val items: List<MenuItem>)

data class MenuSection(val name: String, val groups: List<MenuGroup>) {
    val count: Int get() = groups.sumOf { it.items.size }
}

/** 把菜谱按分类排成菜单：大类、小类都按菜多少排，没分类的放最后叫「其他」 */
object RecipeMenu {
    const val OTHER = "其他"

    fun sections(
        recipes: List<Recipe>,
        lastCooked: Map<String, String>,
        today: LocalDate = LocalDate.now(),
    ): List<MenuSection> {
        fun item(r: Recipe) = lastCooked[r.uid].let { MenuItem(r, it, Freshness.of(it, today)) }
        val (categorized, other) = recipes.partition { !it.category.isNullOrBlank() }
        val sections = categorized.groupBy { it.category!! }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Recipe>>> { it.value.size }.thenBy { it.key })
            .map { (name, rs) ->
                val (withSub, noSub) = rs.partition { !it.subcategory.isNullOrBlank() }
                val subs = withSub.groupBy { it.subcategory!! }.entries
                    .sortedWith(compareByDescending<Map.Entry<String, List<Recipe>>> { it.value.size }.thenBy { it.key })
                    .map { (sub, items) -> MenuGroup(sub, items.map(::item)) }
                MenuSection(name, listOfNotNull(noSub.takeIf { it.isNotEmpty() }?.let { MenuGroup(null, it.map(::item)) }) + subs)
            }
        return if (other.isEmpty()) sections else sections + MenuSection(OTHER, listOf(MenuGroup(null, other.map(::item))))
    }

    /** 价格栏的文字：「今天」「3 天前」「8月20日」，没做过是「还没做过」 */
    fun whenText(item: MenuItem, today: LocalDate = LocalDate.now()): String =
        item.date?.let { DayText.ago(it, today) } ?: "还没做过"
}
