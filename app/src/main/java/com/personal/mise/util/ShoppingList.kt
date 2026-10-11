package com.personal.mise.util

import com.personal.mise.data.IngredientKind

/**
 * 今日菜单的购物清单：几道菜里同名的用料合成一项，
 * 单位对得上的用量加成总量，再按菜分开列明细（「150克」和「适量」没法相加，照原文列）。
 */
object ShoppingList {

    /** 一道菜里的一样用料，amount 是已经按倍率换算过的原文 */
    data class Source(val recipeTitle: String, val name: String, val amount: String, val kind: String)

    data class Use(val recipeTitle: String, val amount: String)

    data class Item(val key: String, val name: String, val seasoning: Boolean, val uses: List<Use>) {
        /** 几道菜加起来的量，如「共 5个」「共 350克 + 适量」；加不起来（只有一道菜、都是「适量」）时为空 */
        val total: String
            get() = if (uses.size < 2) "" else ShoppingList.total(uses.map { it.amount })

        /** 如「共 5个 · 3个（番茄炒蛋）、2个（蛋花汤）」；只有一道菜用时不写菜名 */
        val detail: String
            get() {
                if (uses.size == 1) return uses[0].amount
                val each = uses.joinToString("、") { if (it.amount.isBlank()) it.recipeTitle else "${it.amount}（${it.recipeTitle}）" }
                return if (total.isEmpty()) each else "$total · $each"
            }
    }

    // 同一类单位换算成同一个：克、毫升为基准
    private val unitAlias = mapOf("公斤" to "千克", "大勺" to "汤匙", "小勺" to "茶匙")
    private val unitBase = mapOf(
        "千克" to ("克" to 1000.0),
        "斤" to ("克" to 500.0),
        "升" to ("毫升" to 1000.0),
    )

    // 只有「数量+单位」的用量才相加，「2个 ≈100克」「1勺，分两次」这种照原文列
    private val bareNumber = Regex("[\\d./½¼¾⅓⅔]+|[半一两二三四五六七八九十]")

    private fun parse(amount: String): Pair<Double, String>? {
        val q = AmountScaler.quantity(amount) ?: return null
        val u = AmountScaler.unit(amount)
        val compact = UnitText.normalize(amount).filterNot { it.isWhitespace() }
        if (!compact.endsWith(u) || !bareNumber.matches(compact.removeSuffix(u))) return null
        return q to (unitAlias[u] ?: u)
    }

    /** 几份用量的总和：同单位相加，同类单位（克/千克/斤、毫升/升）换算后相加；没有两份能加到一起就是空 */
    fun total(amounts: List<String>): String {
        val parsed = amounts.map { it.trim() }.filter { it.isNotEmpty() }.map { it to parse(it) }
        val groups = parsed.mapNotNull { it.second }.groupBy { unitBase[it.second]?.first ?: it.second }
        if (groups.values.none { it.size > 1 }) return ""
        // 单位一样就保留原单位，不一样再换成基准单位
        val sums = LinkedHashMap<String, Double>()
        groups.forEach { (base, group) ->
            if (group.all { it.second == group[0].second }) {
                sums[group[0].second] = group.sumOf { it.first }
            } else {
                sums[base] = group.sumOf { (q, u) -> q * (unitBase[u]?.second ?: 1.0) }
            }
        }
        val others = parsed.filter { it.second == null }.map { it.first }.distinct()
        return "共 " + (sums.map { (u, q) -> AmountScaler.format(q) + u } + others).joinToString(" + ")
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
