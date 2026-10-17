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

    /**
     * [memberKeys]：AI 把几样合成一项时，原来那几样的名字（ShoppingList.keyOf）；
     * 家里有的记录按原来的名字也认，免得合并后之前勾过的又要重新勾。
     */
    data class Item(
        val key: String,
        val name: String,
        val seasoning: Boolean,
        val uses: List<Use>,
        val memberKeys: Set<String> = setOf(key),
    ) {
        /** 家里有：这一项勾过，或者合进来的每一样都勾过 */
        fun ownedIn(have: Set<String>): Boolean = key in have || memberKeys.all { it in have }

        /**
         * 勾或取消时记的名字：合成后的名字加原来那几样的名字。只记合成后的名字的话，
         * 共享厨房里没整理过的手机、或者重开 app 没了 AI 分组，都认不出来。
         */
        val keys: Set<String> get() = memberKeys + key

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

    /** 发给别人或贴到备忘录的文字；按区域分好时带上区域名。[extras] 是自己加的，放在最后 */
    fun shareText(items: List<Item>, sections: List<Group>? = null, extras: List<String> = emptyList()): String {
        fun line(item: Item) = "□ ${item.name}" + item.detail.let { if (it.isBlank()) "" else " $it" }
        val extraLines = extras.map { "□ $it" }
        if (sections == null) return (listOf("购物清单") + items.map { line(it) } + extraLines).joinToString("\n")
        val keys = items.map { it.key }.toSet()
        val groups = sections.mapNotNull { group ->
            group.items.filter { it.key in keys }.takeIf { it.isNotEmpty() }?.let { "【${group.section}】\n" + it.joinToString("\n") { item -> line(item) } }
        } + listOfNotNull(extraLines.takeIf { it.isNotEmpty() }?.let { "【另外要买】\n" + it.joinToString("\n") })
        return "购物清单\n" + groups.joinToString("\n")
    }

    // AI 整理

    /** 超市里的区域，AI 分组和显示都按这个顺序 */
    val SECTIONS = listOf("蔬菜水果", "肉禽蛋", "水产", "豆制品", "主食干货", "乳品烘焙", "调料", "其他")

    /** AI 回的一组：叫什么、在哪个区、包括清单里第几项（从 0 开始） */
    data class AiGroup(val name: String?, val section: String, val items: List<Int>)

    /** 一个区域里要买的东西 */
    data class Group(val section: String, val items: List<Item>)

    /**
     * 按 AI 的分组把几项合成一项、放进区域。序号不对、重复的跳过；AI 漏掉的照原样放进
     * 「调料」或「其他」。合并后的用量照原来的规则加总，明细一样不少。
     */
    fun regroup(items: List<Item>, groups: List<AiGroup>): List<Group> {
        val used = mutableSetOf<Int>()
        val merged = mutableListOf<Pair<String, Item>>()
        groups.forEach { g ->
            val members = g.items.filter { it in items.indices && used.add(it) }.map { items[it] }
            if (members.isEmpty()) return@forEach
            val name = g.name ?: members.first().name
            val section = g.section.takeIf { it in SECTIONS } ?: defaultSection(members)
            merged += section to if (members.size == 1 && g.name == null) members.single() else Item(
                key = keyOf(name),
                name = name,
                seasoning = members.all { it.seasoning },
                uses = members.flatMap { it.uses },
                memberKeys = members.flatMap { it.memberKeys }.toSet(),
            )
        }
        items.indices.filter { it !in used }.forEach { merged += defaultSection(listOf(items[it])) to items[it] }
        // 两组起了同一个名字时合在一起，免得同一个 key 出现两次
        val byKey = LinkedHashMap<String, Pair<String, Item>>()
        merged.forEach { (section, item) ->
            val old = byKey[item.key]
            byKey[item.key] = if (old == null) section to item else old.first to old.second.copy(
                uses = old.second.uses + item.uses,
                memberKeys = old.second.memberKeys + item.memberKeys,
                seasoning = old.second.seasoning && item.seasoning,
            )
        }
        return byKey.values.groupBy({ it.first }, { it.second })
            .map { (section, list) -> Group(section, list) }
            .sortedBy { SECTIONS.indexOf(it.section) }
    }

    private fun defaultSection(members: List<Item>): String = if (members.all { it.seasoning }) "调料" else "其他"
}
