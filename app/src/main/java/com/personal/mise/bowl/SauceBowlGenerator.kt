package com.personal.mise.bowl

import com.personal.mise.data.IngredientKind

/**
 * 调料碗自动整合（本地规则版）。
 *
 * 规则：同一步出现两种或以上调料就合成一个调料碗；只有一种的不配碗。
 * 炒菜用的油不进碗；葱姜蒜等爆香料不单独配碗，但和调料同一步下锅时一起放进这一步的碗。
 */
object SauceBowlGenerator {

    data class IngredientInput(val name: String, val amountText: String, val kind: String)

    data class GeneratedItem(val ingredientIndex: Int, val amountText: String)

    data class GeneratedBowl(val label: String, val stepIndex: Int, val items: List<GeneratedItem>)

    private val vagueAmounts = setOf("", "适量", "少许", "少量", "一点", "若干", "根据口味")

    fun label(index: Int): String =
        if (index < 26) "调料碗 ${'A' + index}" else "调料碗 ${index + 1}"

    fun generate(ingredients: List<IngredientInput>, steps: List<String>): List<GeneratedBowl> {
        val matchesPerStep = steps.map { findIngredients(it, ingredients) }

        // 每个用料出现在几个步骤里
        val appearances = IntArray(ingredients.size)
        matchesPerStep.forEach { found -> found.forEach { appearances[it]++ } }

        val bowls = mutableListOf<GeneratedBowl>()
        matchesPerStep.forEachIndexed { stepIndex, found ->
            val seasonings = found.filter { isBowlSeasoning(ingredients[it]) }
            if (seasonings.size < 2) return@forEachIndexed
            val members = found.filter { idx ->
                isBowlSeasoning(ingredients[idx]) || SeasoningDictionary.isAromatic(ingredients[idx].name)
            }
            val items = members.map { idx ->
                val amount = ingredients[idx].amountText.trim()
                val text = if (appearances[idx] > 1 && amount !in vagueAmounts) "分次加入，共 $amount" else amount
                GeneratedItem(idx, text)
            }
            bowls += GeneratedBowl(label(bowls.size), stepIndex, items)
        }
        return bowls
    }

    private fun isBowlSeasoning(ingredient: IngredientInput): Boolean =
        ingredient.kind == IngredientKind.SEASONING &&
            !SeasoningDictionary.isCookingOil(ingredient.name) &&
            !SeasoningDictionary.isAromatic(ingredient.name)

    /**
     * 找出步骤文字里提到的用料，按第一次出现的位置排序。
     * 较长的词优先占位，避免「冰糖」同时命中「糖」。
     */
    fun findIngredients(stepText: String, ingredients: List<IngredientInput>): List<Int> {
        data class Hit(val ingredient: Int, val start: Int, val end: Int)

        val candidates = mutableListOf<Hit>()
        ingredients.forEachIndexed { idx, ing ->
            val relevant = ing.kind == IngredientKind.SEASONING || SeasoningDictionary.isAromatic(ing.name)
            if (!relevant) return@forEachIndexed
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
