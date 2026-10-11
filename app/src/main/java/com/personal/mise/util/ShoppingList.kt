package com.personal.mise.util

import com.personal.mise.data.IngredientKind

/**
 * 今日菜单的购物清单：几道菜里同名的用料合成一项，
 * 用量按菜分开列（「150克」和「适量」没法相加）。
 */
object ShoppingList {

    /** 一道菜里的一样用料，amount 是已经按倍率换算过的原文 */
    data class Source(val recipeTitle: String, val name: String, val amount: String, val kind: String)

    data class Use(val recipeTitle: String, val amount: String)

    data class Item(val key: String, val name: String, val seasoning: Boolean, val uses: List<Use>) {
        /** 如「150克（番茄炒蛋）、2个（蛋花汤）」；只有一道菜用时不写菜名 */
        val detail: String
            get() = if (uses.size == 1) uses[0].amount
            else uses.joinToString("、") { if (it.amount.isBlank()) it.recipeTitle else "${it.amount}（${it.recipeTitle}）" }
    }

    /** 合并时认作同一样东西：忽略空格和大小写 */
    fun keyOf(name: String): String = name.filterNot { it.isWhitespace() }.lowercase()

    /** 主料在前、调料在后，各自按第一次出现的顺序 */
    fun build(sources: List<Source>): List<Item> =
        sources.filter { it.name.isNotBlank() }
            .groupBy { keyOf(it.name) }
            .map { (key, group) ->
                Item(
                    key = key,
                    name = group.first().name.trim(),
                    seasoning = group.all { it.kind == IngredientKind.SEASONING },
                    uses = group.map { Use(it.recipeTitle, it.amount.trim()) },
                )
            }
            .sortedBy { it.seasoning }

    /** 发给别人或贴到备忘录的文字 */
    fun shareText(items: List<Item>): String =
        "购物清单\n" + items.joinToString("\n") { item ->
            "□ ${item.name}" + item.detail.let { if (it.isBlank()) "" else " $it" }
        }
}
