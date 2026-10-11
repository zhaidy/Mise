package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import org.json.JSONObject

/**
 * 原文写「适量」「少许」或没写用量的用料，交给 AI 按菜的份量估一个具体用量。
 * 估出来的用量写成「约 3 克（估算）」，一眼能看出不是原文。
 */
class AiAmountEstimator(private val ai: AiClient) {

    /** [ingredients] 是 名称 to 用量；返回 用料下标 to 估算后的用量文字，只包含需要估算且估出来的 */
    suspend fun estimate(
        title: String,
        servings: String,
        ingredients: List<Pair<String, String>>,
        steps: List<String>,
    ): Map<Int, String> {
        val targets = ingredients.indices.filter { needsEstimate(ingredients[it].first, ingredients[it].second) }
        if (targets.isEmpty()) throw ImportException("没有需要估算的用量")
        val reply = ai.generate(prompt(title, servings, ingredients, steps, targets), schema)
        return parseResponse(reply, targets)
    }

    companion object {
        const val MARK = "（估算）"

        private val vague = Regex("^(适量|少许|少量|一点|一点点|一些|若干|酌量|随意|根据口味|按口味|依口味|看口味|看个人口味|个人口味)$")

        /** 名称不为空、用量是空的或「适量」这类模糊说法 */
        fun needsEstimate(name: String, amount: String): Boolean {
            if (name.isBlank()) return false
            val a = amount.trim()
            return a.isEmpty() || vague.matches(a)
        }

        fun format(estimate: String): String {
            val e = estimate.trim().removeSuffix(MARK).removePrefix("约").trim()
            return "约$e$MARK"
        }

        private const val PROMPT = "下面是一道中文菜的用料和做法，其中有些用料原文只写了「适量」或没写用量。" +
            "请按菜的份量和家常做法，给标了【待估】的用料估一个具体用量。\n" +
            "要求：\n" +
            "1. 只估【待估】的用料，ingredient 填它的序号（从 1 开始）。\n" +
            "2. amount 写一个具体、好量的用量，用克、毫升、勺、个、根、瓣、片等常用单位，例如「3克」「1勺」「2瓣」「15毫升」；" +
            "不要写「适量」，不要带「约」或解释。\n" +
            "3. 参考已写明用量的主料来估，原文写了几人份就按几人份估，没写就按 2～3 人份。\n" +
            "4. 实在没法估（比如炸东西的油、焯水的水）就把 amount 留空字符串。\n\n"

        fun prompt(
            title: String,
            servings: String,
            ingredients: List<Pair<String, String>>,
            steps: List<String>,
            targets: List<Int>,
        ): String = buildString {
            append(PROMPT)
            if (title.isNotBlank()) append("菜名：${title.trim()}\n")
            if (servings.isNotBlank()) append("份量：${servings.trim()}\n")
            append("用料：\n")
            ingredients.forEachIndexed { i, (name, amount) ->
                append("${i + 1}. $name")
                if (i in targets) {
                    append("【待估】")
                    if (amount.isNotBlank()) append("（原文：$amount）")
                } else if (amount.isNotBlank()) {
                    append("：$amount")
                }
                append('\n')
            }
            val written = steps.map { it.trim() }.filter { it.isNotEmpty() }
            if (written.isNotEmpty()) {
                append("\n做法：\n")
                written.forEachIndexed { i, text -> append("${i + 1}. $text\n") }
            }
        }

        private val schema
            get() = AiSchema.obj(
                "estimates" to AiSchema.array(
                    AiSchema.obj(
                        "ingredient" to AiSchema.number(),
                        "amount" to AiSchema.string(),
                        required = listOf("ingredient", "amount"),
                    ),
                ),
                required = listOf("estimates"),
            )

        fun parseResponse(reply: JSONObject, targets: List<Int>): Map<Int, String> {
            val arr = reply.optJSONArray("estimates")
                ?: throw ImportException("AI 返回的格式不对，请重试")
            val result = linkedMapOf<Int, String>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val index = item.optDouble("ingredient", Double.NaN).takeIf { !it.isNaN() }?.toInt()?.minus(1) ?: continue
                val amount = item.optString("amount").trim()
                if (index !in targets || amount.isEmpty() || amount == "null" || vague.matches(amount)) continue
                result[index] = format(amount)
            }
            if (result.isEmpty()) throw ImportException("AI 没有估出用量，请重试")
            return result
        }
    }
}
