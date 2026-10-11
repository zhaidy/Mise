package com.personal.mise.ai

import com.personal.mise.data.RecipeFull
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import kotlin.math.roundToInt

/** 交给 AI 按用料估算整道菜（按菜谱原文的份量）大约多少千卡：逐样估，再在本地加起来 */
class AiCalorieEstimator(private val ai: AiClient) {

    /** [ingredients] 是 名称 to 用量；返回整道菜的千卡 */
    suspend fun estimate(
        title: String,
        servings: String,
        ingredients: List<Pair<String, String>>,
        steps: List<String>,
    ): Int {
        if (ingredients.none { it.first.isNotBlank() }) throw ImportException("还没有用料，没法估算热量")
        val reply = ai.generate(prompt(title, servings, ingredients, steps), schema)
        return parseResponse(reply, ingredients)
    }

    suspend fun estimate(full: RecipeFull): Int = estimate(
        full.recipe.title,
        full.recipe.servings.orEmpty(),
        full.ingredients.map { it.name to it.amountText },
        full.steps.map { it.text },
    )

    companion object {
        private const val PROMPT = "下面是一道中文菜的用料和做法。请估算整道菜（按原文写的份量，不是每人份）一共大约多少千卡热量。\n" +
            "要求：\n" +
            "1. 逐样用料估算，ingredient 填它的序号（从 1 开始），kcal 填这样用料按写明的用量大约多少千卡（数字）。\n" +
            "2. 按生重和常见营养数据估，例如 100 克五花肉约 500 千卡、1 个鸡蛋约 70 千卡、15 毫升食用油约 135 千卡、" +
            "100 克米饭约 116 千卡。\n" +
            "3. 写「适量」或没写用量的，按家常做法估一个用量再算；盐、酱油、醋、香料这类热量很低的可以填很小的数或 0。\n" +
            "4. 炸东西的油只算吃进去的部分（大约用油量的一成到两成），焯水、泡发用的水填 0。\n\n"

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
                if (name.isBlank()) return@forEachIndexed
                append("${i + 1}. ${name.trim()}")
                append(if (amount.isBlank()) "（没写用量）" else "：${amount.trim()}")
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
                "items" to AiSchema.array(
                    AiSchema.obj(
                        "ingredient" to AiSchema.number(),
                        "kcal" to AiSchema.number(),
                        required = listOf("ingredient", "kcal"),
                    ),
                ),
                required = listOf("items"),
            )

        /** 每样用料只算一次，序号不对或热量不是正常数字的跳过 */
        fun parseResponse(reply: JSONObject, ingredients: List<Pair<String, String>>): Int {
            val arr = reply.optJSONArray("items") ?: throw ImportException("AI 返回的格式不对，请重试")
            val perIngredient = linkedMapOf<Int, Double>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val index = item.optDouble("ingredient", Double.NaN).takeIf { !it.isNaN() }?.toInt()?.minus(1) ?: continue
                if (index !in ingredients.indices || ingredients[index].first.isBlank()) continue
                val kcal = item.optDouble("kcal", Double.NaN).takeIf { !it.isNaN() && !it.isInfinite() && it >= 0 } ?: continue
                perIngredient.putIfAbsent(index, kcal)
            }
            val total = perIngredient.values.sum().roundToInt()
            if (total <= 0) throw ImportException("AI 没有估出热量，请重试")
            return total
        }
    }
}
