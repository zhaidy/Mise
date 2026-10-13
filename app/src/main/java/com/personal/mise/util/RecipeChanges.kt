package com.personal.mise.util

import com.personal.mise.data.RecipeDraft

/** 把 AI 改菜谱前后的两份草稿对一下，列出改了哪些地方，给用户看 */
object RecipeChanges {

    fun describe(before: RecipeDraft, after: RecipeDraft): List<String> {
        val out = mutableListOf<String>()
        if (before.title.trim() != after.title.trim()) out += "菜名：${before.title.trim()} → ${after.title.trim()}"
        val oldServings = before.servings?.trim().orEmpty()
        val newServings = after.servings?.trim().orEmpty()
        if (oldServings != newServings) out += "份量：${oldServings.ifEmpty { "没写" }} → ${newServings.ifEmpty { "没写" }}"

        val oldIngredients = before.ingredients.filter { it.name.isNotBlank() }.associate { it.name.trim() to it.amountText.trim() }
        val newIngredients = after.ingredients.filter { it.name.isNotBlank() }.associate { it.name.trim() to it.amountText.trim() }
        for ((name, amount) in oldIngredients) {
            val now = newIngredients[name]
            when {
                name !in newIngredients -> out += "去掉用料：$name"
                now != amount -> out += "$name：${amount.ifEmpty { "没写用量" }} → ${now.orEmpty().ifEmpty { "没写用量" }}"
            }
        }
        for ((name, amount) in newIngredients) {
            if (name !in oldIngredients) out += "加了用料：$name" + if (amount.isEmpty()) "" else " $amount"
        }

        val oldSteps = before.steps.filter { it.text.isNotBlank() }
        val newSteps = after.steps.filter { it.text.isNotBlank() }
        if (oldSteps.size != newSteps.size) out += "步骤：${oldSteps.size} 步 → ${newSteps.size} 步"
        val oldTexts = oldSteps.map { it.text.trim() }
        val newTexts = newSteps.map { it.text.trim() }
        oldTexts.forEachIndexed { i, text ->
            if (text !in newTexts) out += "去掉原来第 ${i + 1} 步：$text"
        }
        newSteps.forEachIndexed { j, step ->
            val text = step.text.trim()
            val i = oldTexts.indexOf(text)
            if (i < 0) {
                out += "第 ${j + 1} 步改成：$text"
            } else if (oldSteps[i].timerSeconds != step.timerSeconds) {
                out += "第 ${j + 1} 步计时：${timer(oldSteps[i].timerSeconds)} → ${timer(step.timerSeconds)}"
            }
        }
        return out
    }

    private fun timer(seconds: Int?): String = when {
        seconds == null -> "不计时"
        seconds % 60 == 0 -> "${seconds / 60} 分钟"
        else -> "%.1f 分钟".format(seconds / 60.0)
    }
}
