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
        for (pair in align(oldTexts, newTexts)) {
            val i = pair.first
            val j = pair.second
            when {
                i == null -> out += "加了第 ${j!! + 1} 步：${newTexts[j]}"
                j == null -> out += "去掉原来第 ${i + 1} 步：${oldTexts[i]}"
                else -> {
                    val label = if (i == j) "第 ${j + 1} 步" else "第 ${j + 1} 步（原来第 ${i + 1} 步）"
                    if (oldTexts[i] != newTexts[j]) out += stepEdit(label, oldTexts[i], newTexts[j])
                    if (oldSteps[i].timerSeconds != newSteps[j].timerSeconds) {
                        out += "${label}计时：${timer(oldSteps[i].timerSeconds)} → ${timer(newSteps[j].timerSeconds)}"
                    }
                }
            }
        }
        return out
    }

    /** 一步改了文字：只在后面补了几句就写补了什么，不然写改成什么、原来是什么 */
    private fun stepEdit(label: String, old: String, new: String): String {
        val base = old.trimEnd('。', '，', '；', '.', ',', ';', '！', '!')
        if (base.isNotEmpty() && new.startsWith(base)) {
            val added = new.substring(base.length).trimStart('。', '，', '；', '.', ',', ';', ' ')
            if (added.isNotEmpty()) return "${label}补充：$added"
        }
        return "${label}改成：$new（原来：$old）"
    }

    /**
     * 把前后两份步骤对齐：文字一样的按最长公共子序列对上，
     * 两个对上的步骤之间剩下的按先后一一配成「改了」，多出来的算新加或去掉。
     * 返回（原来第几步, 现在第几步）的列表，按现在的顺序排，没有对应的一边为空。
     */
    fun align(old: List<String>, new: List<String>): List<Pair<Int?, Int?>> {
        val n = old.size
        val m = new.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lcs[i][j] = if (old[i] == new[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = mutableListOf<Pair<Int?, Int?>>()
        val gapOld = mutableListOf<Int>()
        val gapNew = mutableListOf<Int>()
        fun flush() {
            val paired = minOf(gapOld.size, gapNew.size)
            for (k in 0 until paired) out += gapOld[k] to gapNew[k]
            for (k in paired until gapOld.size) out += gapOld[k] to null
            for (k in paired until gapNew.size) out += null to gapNew[k]
            gapOld.clear()
            gapNew.clear()
        }
        var i = 0
        var j = 0
        while (i < n || j < m) {
            when {
                i < n && j < m && old[i] == new[j] -> {
                    flush()
                    out += i to j
                    i++
                    j++
                }
                j >= m || (i < n && lcs[i + 1][j] >= lcs[i][j + 1]) -> gapOld += i++
                else -> gapNew += j++
            }
        }
        flush()
        return out
    }

    private fun timer(seconds: Int?): String = when {
        seconds == null -> "不计时"
        seconds % 60 == 0 -> "${seconds / 60} 分钟"
        else -> "%.1f 分钟".format(seconds / 60.0)
    }
}
