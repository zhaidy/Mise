package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import com.personal.mise.util.AmountScaler

/**
 * 交给 Gemini 补全用料的用量，一次请求做两件事：
 * - 原文写「适量」「少许」或没写用量的，按菜的份量估一个具体用量，写成「约3克（估算）」，一眼能看出不是原文；
 * - 用量不是按克写的（「15毫升」「1勺」「2个」），在后面补上大概克重，写成「15毫升 ≈15克」。
 */
class GeminiAmountEstimator(private val gemini: GeminiClient) {

    /** [ingredients] 是 名称 to 用量；返回 用料下标 to 补全后的用量文字，只包含改动了的 */
    suspend fun estimate(
        title: String,
        servings: String,
        ingredients: List<Pair<String, String>>,
        steps: List<String>,
    ): Map<Int, String> {
        val targets = targets(ingredients)
        if (targets.isEmpty()) throw ImportException("没有需要估算的用量或克重")
        val body = gemini.generate(prompt(title, servings, ingredients, steps), schema)
        return parseResponse(body, ingredients)
    }

    companion object {
        const val MARK = "（估算）"
        const val GRAMS = "≈"

        private val vague = Regex("^(适量|少许|少量|一点|一点点|一些|若干|酌量|随意|根据口味|按口味|依口味|看口味|看个人口味|个人口味)$")

        // 已经按克（千克）写了，或已经补过克重
        private val inGrams = Regex("(?i)克|公斤|(?<=[\\d.½¼¾\\s])(g|gs|grams?|kg|kgs)(?![A-Za-z])")

        /** 名称不为空、用量是空的或「适量」这类模糊说法 */
        fun needsEstimate(name: String, amount: String): Boolean {
            if (name.isBlank()) return false
            val a = amount.trim()
            return a.isEmpty() || vague.matches(a)
        }

        /** 有具体用量，但不是按克写的，如「15毫升」「1勺」「2个」「半斤」 */
        fun needsGrams(name: String, amount: String): Boolean {
            if (name.isBlank() || needsEstimate(name, amount)) return false
            return GRAMS !in amount && !inGrams.containsMatchIn(amount)
        }

        fun needsWork(name: String, amount: String): Boolean = needsEstimate(name, amount) || needsGrams(name, amount)

        fun targets(ingredients: List<Pair<String, String>>): List<Int> =
            ingredients.indices.filter { needsWork(ingredients[it].first, ingredients[it].second) }

        fun format(estimate: String): String {
            val e = estimate.trim().removeSuffix(MARK).removePrefix("约").trim()
            return "约$e$MARK"
        }

        /** 「15毫升」+ 15 →「15毫升 ≈15克」 */
        fun withGrams(amount: String, grams: Double): String = "${amount.trim()} $GRAMS${AmountScaler.format(grams)}克"

        private const val PROMPT = "下面是一道中文菜的用料和做法。请帮忙补全用料的用量，让每样用料都有大概的克重。\n" +
            "标了【待估】的用料原文只写了「适量」或没写用量；标了【补克重】的用料写了用量，但不是按克写的。\n" +
            "要求：\n" +
            "1. 只回答标了【待估】或【补克重】的用料，ingredient 填它的序号（从 1 开始）。\n" +
            "2. 【待估】的用料：amount 写一个具体、好量的用量，能称重的尽量用克，液体可以用毫升，例如「3克」「15毫升」「2瓣」；" +
            "不要写「适量」，不要带「约」或解释。按菜的份量和家常做法估，参考已写明用量的主料，" +
            "原文写了几人份就按几人份估，没写就按 2～3 人份。\n" +
            "3. 【补克重】的用料：amount 留空字符串，只填 grams。\n" +
            "4. grams 填这个用量大概是多少克（数字），按这样用料常见的密度和个头换算，" +
            "例如 15 毫升生抽约 18 克、1 勺盐约 6 克、1 个鸡蛋约 50 克、1 瓣蒜约 5 克、半斤 250 克。\n" +
            "5. 实在没法估（比如炸东西的油、焯水的水）就把 amount 留空字符串、grams 填 0。\n\n"

        fun prompt(
            title: String,
            servings: String,
            ingredients: List<Pair<String, String>>,
            steps: List<String>,
        ): String = buildString {
            append(PROMPT)
            if (title.isNotBlank()) append("菜名：${title.trim()}\n")
            if (servings.isNotBlank()) append("份量：${servings.trim()}\n")
            append("用料：\n")
            ingredients.forEachIndexed { i, (name, amount) ->
                append("${i + 1}. $name")
                when {
                    needsEstimate(name, amount) -> {
                        append("【待估】")
                        if (amount.isNotBlank()) append("（原文：$amount）")
                    }
                    needsGrams(name, amount) -> append("：$amount【补克重】")
                    amount.isNotBlank() -> append("：$amount")
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
            get() = GeminiClient.obj(
                "estimates" to GeminiClient.array(
                    GeminiClient.obj(
                        "ingredient" to GeminiClient.number(),
                        "amount" to GeminiClient.string(),
                        "grams" to GeminiClient.number(),
                        required = listOf("ingredient", "amount", "grams"),
                    ),
                ),
                required = listOf("estimates"),
            )

        fun parseResponse(body: String, ingredients: List<Pair<String, String>>): Map<Int, String> {
            val arr = GeminiClient.replyJson(body).optJSONArray("estimates")
                ?: throw ImportException("Gemini 返回的格式不对，请重试")
            val result = linkedMapOf<Int, String>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val index = item.optDouble("ingredient", Double.NaN).takeIf { !it.isNaN() }?.toInt()?.minus(1) ?: continue
                if (index !in ingredients.indices) continue
                val (name, original) = ingredients[index]
                val grams = item.optDouble("grams", Double.NaN).takeIf { !it.isNaN() && it > 0 }
                val amount = item.optString("amount").trim().takeUnless { it.isEmpty() || it == "null" || vague.matches(it) }
                when {
                    needsEstimate(name, original) -> {
                        val text = when {
                            amount != null -> format(amount)
                            grams != null -> format("${AmountScaler.format(grams)}克")
                            else -> continue
                        }
                        result[index] = if (grams != null && needsGrams(name, text)) withGrams(text, grams) else text
                    }
                    needsGrams(name, original) -> if (grams != null) result[index] = withGrams(original, grams)
                }
            }
            if (result.isEmpty()) throw ImportException("Gemini 没有估出用量，请重试")
            return result
        }
    }
}
