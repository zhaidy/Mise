package com.personal.mise.util

/**
 * 切配汇总：从步骤里认出每样用料怎么切（「葱切段」「土豆去皮，切成细丝」「撒上葱花」），
 * 再把几道菜的同一样用料合在一起，如「葱：切段 2根、切末 1根」，一次备完。
 */
object CutPlan {

    data class Ingredient(val id: Long, val name: String, val amount: String)

    /** 一道菜；key 区分清单里的各道菜，amount 已经按倍率换算过 */
    data class Recipe(val key: String, val title: String, val ingredients: List<Ingredient>, val steps: List<String>)

    /** 一道菜里一样用料的切法，一样用料可能有几种切法（葱白切段、葱叶切末） */
    data class Cut(val recipeKey: String, val recipeTitle: String, val ingredientId: Long, val methods: List<String>, val amount: String) {
        val method: String get() = methods.joinToString("/")
    }

    data class Item(val key: String, val name: String, val cuts: List<Cut>) {
        /** 同一种切法合在一起，如「切段 2根、切末 1根」；用量加不起来时照原文列 */
        val summary: String
            get() = cuts.groupBy { it.method }.entries.joinToString("、") { (method, group) ->
                val amounts = group.map { it.amount.trim() }.filter { it.isNotEmpty() }
                val total = ShoppingList.total(amounts).removePrefix("共 ")
                val amount = when {
                    amounts.isEmpty() -> ""
                    amounts.size == 1 -> amounts[0]
                    total.isNotEmpty() -> total
                    else -> amounts.joinToString(" + ")
                }
                "$method $amount".trim()
            }

        /** 每道菜各自怎么切，如「切段 2根（红烧肉）」 */
        val detail: List<String>
            get() = cuts.map { "${it.method} ${it.amount}".trim() + "（${it.recipeTitle}）" }
    }

    // 切成什么样
    private const val SHAPES = "滚刀块|段|末|丝|片|丁|块|条|碎|粒|花|蓉"

    // 「切成 2 厘米的段」「切成细丝」：切和形状之间只许出现尺寸、粗细这类字，免得「切好后放入锅中翻炒片刻」被当成切片
    private const val SIZE = "[\\d.~\\-约左右厘米毫米公分cm小大薄厚细粗长短均匀的等宽\\s]"
    private val modifiers = setOf('小', '大', '薄', '厚', '细', '粗')

    private val cutPatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("(?:切|改刀)(?:成|为)?($SIZE{0,10}?)($SHAPES)") to { m -> "切" + modifier(m.groupValues[1]) + m.groupValues[2] },
        Regex("剁(?:成)?($SIZE{0,6}?)(末|碎|蓉|泥|块)") to { m -> "剁" + m.groupValues[2] },
        Regex("拍(碎|扁|松|散)") to { m -> "拍" + m.groupValues[1] },
        Regex("撕成?[小大]?(块|条|片|丝)") to { m -> "撕" + m.groupValues[1] },
        Regex("(?:擦|刨)成?丝") to { _ -> "擦丝" },
    )

    private fun modifier(between: String): String = between.lastOrNull()?.takeIf { it in modifiers }?.toString().orEmpty()

    private val leadingCut = Regex("^\\s*(?:再|然后|并|再次)?(?:切|改刀|剁|拍|撕|擦|刨)")

    private val clauseBreak = Regex("[，。；！？,;!?\\n]")

    // 用料名前面这些字去掉也认：「大葱」也认「葱」，「老姜」也认「姜」
    private const val PREFIXES = "小大青红绿黄白紫鲜老嫩干生新"
    private val singleAromatics = setOf("葱", "姜", "蒜")

    /** 「葱花」「蒜末」这种名字本身就带着切法 */
    private val aromatics = listOf("葱", "姜", "蒜", "辣椒", "小米辣", "香菜", "洋葱")
    private val impliedShapes = mapOf('段' to "切段", '末' to "切末", '丝' to "切丝", '片' to "切片", '丁' to "切丁", '花' to "切葱花", '蓉' to "剁蓉", '碎' to "切碎")

    /** 步骤里提到这样用料时用的名字，长的在前 */
    internal fun aliases(name: String): List<String> {
        val clean = name.replace(Regex("[（(].*?[)）]"), "").trim()
        if (clean.isEmpty()) return emptyList()
        val list = mutableListOf(clean)
        if (clean.length > 1 && clean[0] in PREFIXES) {
            val rest = clean.substring(1)
            if (rest.length >= 2 || rest in singleAromatics) list += rest
        }
        return list
    }

    /** 名字本身带切法的：「葱花」→ 切葱花、「蒜末」→ 切末 */
    private fun impliedByName(name: String): String? {
        val shape = name.lastOrNull() ?: return null
        val base = name.dropLast(1)
        if (base.isEmpty() || aromatics.none { base.endsWith(it) }) return null
        return impliedShapes[shape]
    }

    /** 一道菜里每样用料的切法（按用料下标），没认出来的不在里面 */
    internal fun methods(ingredients: List<Ingredient>, steps: List<String>): Map<Int, List<String>> {
        val found = LinkedHashMap<Int, LinkedHashSet<String>>()
        fun add(index: Int, method: String) = found.getOrPut(index) { LinkedHashSet() }.add(method)

        // 长名字先认，认过的位置不再给短名字：「花椒」不会被「椒」抢走
        val candidates = ingredients.withIndex()
            .flatMap { (i, g) -> aliases(g.name).map { i to it } }
            .sortedByDescending { it.second.length }

        steps.forEach { step ->
            // 「土豆去皮，切成细丝」：切法没跟着用料名时，算前一句提到的用料
            var previous: List<Int> = emptyList()
            clauseBreak.split(step).forEach { clause ->
                val claimed = BooleanArray(clause.length)
                val mentions = mutableListOf<Pair<Int, IntRange>>() // 用料下标 to 位置
                candidates.forEach { (index, alias) ->
                    var from = clause.indexOf(alias)
                    while (from >= 0) {
                        val range = from until from + alias.length
                        if (range.none { claimed[it] }) {
                            range.forEach { claimed[it] = true }
                            mentions += index to range
                        }
                        from = clause.indexOf(alias, from + 1)
                    }
                }
                mentions.sortBy { it.second.first }
                val cuts = cutPatterns.flatMap { (regex, label) -> regex.findAll(clause).map { it.range.first to label(it) }.toList() }
                    .sortedBy { it.first }

                mentions.forEach { (index, range) ->
                    // 名字后面紧跟着形状：「撒上葱花」「姜丝」
                    val next = clause.getOrNull(range.last + 1)
                    val implied = next?.let { impliedShapes[it] }?.takeIf { aromatics.any { a -> ingredients[index].name.contains(a) } }
                    // 后面最近的切法，「葱、姜切末」两样都算切末
                    val cut = cuts.firstOrNull { it.first > range.last }?.second
                    when {
                        cut != null -> add(index, cut)
                        implied != null -> add(index, implied)
                    }
                }
                val mentioned = mentions.map { it.first }.distinct()
                // 只认紧挨着的一句、而且这句一开头就是切：「加葱姜，煮好后切块」切的是肉不是葱姜
                if (mentioned.isEmpty() && cuts.isNotEmpty() && leadingCut.containsMatchIn(clause)) {
                    previous.forEach { index -> cuts.forEach { add(index, it.second) } }
                }
                previous = mentioned
            }
        }
        ingredients.forEachIndexed { index, g ->
            if (index !in found) impliedByName(g.name.trim())?.let { add(index, it) }
        }
        return found.mapValues { it.value.toList() }
    }

    /** 每道菜的切配合在一起；同名用料合成一项，按第一次出现的顺序 */
    fun build(recipes: List<Recipe>): List<Item> {
        val cuts = recipes.flatMap { recipe ->
            methods(recipe.ingredients, recipe.steps).map { (index, methods) ->
                val g = recipe.ingredients[index]
                Cut(recipe.key, recipe.title, g.id, methods, g.amount) to g.name.trim()
            }.sortedBy { (cut, _) -> recipe.ingredients.indexOfFirst { it.id == cut.ingredientId } }
        }
        return cuts.groupBy { ShoppingList.keyOf(it.second) }.map { (key, group) ->
            Item(key, group.first().second, group.map { it.first })
        }
    }

    /** 一道菜里一样用料的切法，备料清单按菜看时写在用量后面 */
    fun methodsById(recipe: Recipe): Map<Long, String> =
        methods(recipe.ingredients, recipe.steps).entries.associate { (index, methods) ->
            recipe.ingredients[index].id to methods.joinToString("/")
        }
}
