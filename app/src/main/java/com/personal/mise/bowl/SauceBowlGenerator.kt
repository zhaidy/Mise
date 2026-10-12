package com.personal.mise.bowl

import com.personal.mise.data.IngredientKind

/**
 * 调料碗自动整合（本地规则版）。
 *
 * 规则：每一种用料（调料、配料、主料都算）都要进某个碗。
 * 一起下锅、或者短时间内依次下锅的用料合成一个碗：连续几步都用到用料、中间没有别的步骤隔开的，算同一个碗；
 * 没有提到用料的步骤，带计时的步骤（腌制、等待），以及要在锅里炒一会儿的步骤（煸炒出油、炒至变色、爆香），
 * 会把前后分开。
 * 只是切配的步骤不算下锅：碗的下锅步骤取组里第一个真正下锅的步骤；葱姜蒜在切配步骤里出现、后面还会再用到的，
 * 不进切配所在的碗。
 * 炒菜用的油不进碗。同一种调料分几个碗时标「分次加入」；
 * 主料、配料只进最后一个用到它的碗（备料时切好、下锅时才放的那一次）。
 */
object SauceBowlGenerator {

    data class IngredientInput(val name: String, val amountText: String, val kind: String)

    data class GeneratedItem(val ingredientIndex: Int, val amountText: String)

    data class GeneratedBowl(val label: String, val stepIndex: Int, val items: List<GeneratedItem>)

    private val vagueAmounts = setOf("", "适量", "少许", "少量", "一点", "若干", "根据口味")

    /** 同一样用料分几个碗时每个碗都写这个；用量是「适量」这类说不出总数的就照抄 */
    fun splitAmountText(amount: String): String =
        if (amount.trim() in vagueAmounts) amount.trim() else "分次加入，共 ${amount.trim()}"

    fun label(index: Int): String =
        if (index < 26) "调料碗 ${'A' + index}" else "调料碗 ${index + 1}"

    /** [timers] 与 [steps] 一一对应，带计时的步骤会结束当前这一碗 */
    fun generate(
        ingredients: List<IngredientInput>,
        steps: List<String>,
        timers: List<Int?> = emptyList(),
    ): List<GeneratedBowl> {
        val matchesPerStep = steps.map { text ->
            findIngredients(text, ingredients).filterNot { SeasoningDictionary.isCookingOil(ingredients[it].name) }
        }

        // 连续有用料的步骤归为一组，没有用料的步骤隔断，带计时的步骤收尾
        val groups = mutableListOf<List<Int>>()
        var run = mutableListOf<Int>()
        fun flush() {
            if (run.isNotEmpty()) groups += run.toList()
            run = mutableListOf()
        }
        matchesPerStep.forEachIndexed { stepIndex, found ->
            if (found.isEmpty()) {
                flush()
                return@forEachIndexed
            }
            run += stepIndex
            if (timers.getOrNull(stepIndex) != null || needsCookingTime(steps[stepIndex])) flush()
        }
        flush()

        val prepSteps = steps.map { isPrepOnly(it) }
        val membersPerGroup = groups.map { group -> group.flatMap { matchesPerStep[it] }.distinct() }
        val lastGroupOf = IntArray(ingredients.size) { -1 }
        membersPerGroup.forEachIndexed { g, members -> members.forEach { lastGroupOf[it] = g } }

        // 主料、配料只进最后一个碗；调料、爆香料每个下锅用到的碗都进，只在切配步骤里出现的那次不算
        val finalMembers = membersPerGroup.mapIndexed { g, members ->
            members.filter { idx ->
                lastGroupOf[idx] == g || (
                    isSplittable(ingredients[idx]) &&
                        groups[g].any { step -> !prepSteps[step] && idx in matchesPerStep[step] }
                    )
            }
        }
        val appearances = IntArray(ingredients.size)
        finalMembers.forEach { members -> members.forEach { appearances[it]++ } }

        // 主料挪到后面的碗之后，前面的碗可能空了，空碗不留
        return groups.indices
            .filter { finalMembers[it].isNotEmpty() }
            .mapIndexed { bowlIndex, g ->
                val items = finalMembers[g].map { idx ->
                    val amount = ingredients[idx].amountText.trim()
                    val text = if (appearances[idx] > 1) splitAmountText(amount) else amount
                    GeneratedItem(idx, text)
                }
                val potStep = groups[g].firstOrNull { !prepSteps[it] } ?: groups[g].first()
                GeneratedBowl(label(bowlIndex), potStep, items)
            }
    }

    /** 下锅后要炒、煎、炖一阵子才能放下一样的说法 */
    private val cookingTimeWords = listOf(
        "出油", "煸", "煎", "炸", "炖", "焖", "煮", "爆香", "炒香", "出香", "出味", "炒软", "炒熟", "断生",
        "变色", "变软", "金黄", "焦黄", "收汁", "至",
    )

    /** 切配用的说法；带了这些又没提到开火、下锅的步骤只是备料 */
    private val prepWords = listOf("切", "剁", "剪", "拍", "削", "掰", "撕", "去皮", "去籽", "洗净")
    private val heatWords = listOf("锅", "炒", "煸", "煎", "炸", "炖", "焖", "煮", "蒸", "烧", "爆", "焯", "烤", "火")

    fun needsCookingTime(stepText: String): Boolean =
        !isPrepOnly(stepText) && cookingTimeWords.any { it in stepText }

    fun isPrepOnly(stepText: String): Boolean =
        prepWords.any { it in stepText } && heatWords.none { it in stepText }

    private fun isSplittable(ingredient: IngredientInput): Boolean =
        ingredient.kind == IngredientKind.SEASONING || SeasoningDictionary.isAromatic(ingredient.name)

    /**
     * 找出步骤文字里提到的用料，按第一次出现的位置排序。
     * 较长的词优先占位，避免「冰糖」同时命中「糖」。
     */
    fun findIngredients(stepText: String, ingredients: List<IngredientInput>): List<Int> {
        data class Hit(val ingredient: Int, val start: Int, val end: Int)

        val candidates = mutableListOf<Hit>()
        ingredients.forEachIndexed { idx, ing ->
            for (term in SeasoningDictionary.matchTerms(ing.name)) {
                var from = 0
                while (true) {
                    val at = stepText.indexOf(term, from)
                    if (at < 0) break
                    candidates += Hit(idx, at, at + term.length)
                    from = at + 1
                }
            }
        }

        val accepted = mutableListOf<Hit>()
        candidates
            .sortedWith(compareByDescending<Hit> { it.end - it.start }.thenBy { it.start })
            .forEach { hit ->
                val overlaps = accepted.any { hit.start < it.end && it.start < hit.end }
                if (!overlaps) accepted += hit
            }

        return accepted
            .groupBy { it.ingredient }
            .mapValues { (_, hits) -> hits.minOf { it.start } }
            .entries
            .sortedBy { it.value }
            .map { it.key }
    }
}
