package com.personal.mise.bowl

import com.personal.mise.data.IngredientKind

/**
 * 调料碗自动整合（本地规则版）。
 *
 * 规则：每一种用料（调料、配料、主料都算）都要进某个碗。
 * 一起下锅、或者短时间内依次下锅的用料合成一个碗：连续几步都用到用料、中间没有别的步骤隔开的，算同一个碗；
 * 没有提到用料的步骤，以及带计时的步骤（腌制、等待），会把前后分开。
 * 炒菜用的油不进碗。同一种调料分几个碗时标「分次加入」；
 * 主料、配料只进最后一个用到它的碗（备料时切好、下锅时才放的那一次）。
 */
object SauceBowlGenerator {

    data class IngredientInput(val name: String, val amountText: String, val kind: String)

    data class GeneratedItem(val ingredientIndex: Int, val amountText: String)

    data class GeneratedBowl(val label: String, val stepIndex: Int, val items: List<GeneratedItem>)

    private val vagueAmounts = setOf("", "适量", "少许", "少量", "一点", "若干", "根据口味")

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
            if (timers.getOrNull(stepIndex) != null) flush()
        }
        flush()

        val membersPerGroup = groups.map { group -> group.flatMap { matchesPerStep[it] }.distinct() }
        val lastGroupOf = IntArray(ingredients.size) { -1 }
        membersPerGroup.forEachIndexed { g, members -> members.forEach { lastGroupOf[it] = g } }

        // 主料、配料只进最后一个碗；调料、爆香料每个出现过的碗都进
        val finalMembers = membersPerGroup.mapIndexed { g, members ->
            members.filter { isSplittable(ingredients[it]) || lastGroupOf[it] == g }
        }
        val appearances = IntArray(ingredients.size)
        finalMembers.forEach { members -> members.forEach { appearances[it]++ } }

        return groups.mapIndexed { g, group ->
            val items = finalMembers[g].map { idx ->
                val amount = ingredients[idx].amountText.trim()
                val text = if (appearances[idx] > 1 && amount !in vagueAmounts) "分次加入，共 $amount" else amount
                GeneratedItem(idx, text)
            }
            GeneratedBowl(label(g), group.first(), items)
        }
    }

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
