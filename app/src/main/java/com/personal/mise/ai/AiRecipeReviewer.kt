package com.personal.mise.ai

import com.personal.mise.data.RecipeFull
import com.personal.mise.importer.ImportException
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** AI 从口味上对一道菜谱的评价：1–10 分（做出来多好吃）、一句话说味道、口味亮点、可能让味道打折的地方 */
data class RecipeReview(
    val score: Int,
    val summary: String,
    val highlights: List<String>,
    val concerns: List<String>,
    /** 照 [concerns] 改完大约能到几分；比现在高才有，老评价没有 */
    val improvedScore: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("score", score)
        .put("summary", summary)
        .put("highlights", JSONArray(highlights))
        .put("concerns", JSONArray(concerns))
        .put("improvedScore", improvedScore ?: JSONObject.NULL)

    companion object {
        /** 存在数据库和云端的评价；格式不对时返回 null，不影响菜谱本身 */
        fun fromJson(json: String?): RecipeReview? = json?.takeIf { it.isNotBlank() }?.let {
            runCatching { parse(JSONObject(it)) }.getOrNull()
        }

        /** 分数不在 1–10 或总评为空时返回 null */
        fun parse(obj: JSONObject): RecipeReview? {
            val score = obj.optDouble("score", Double.NaN).takeIf { !it.isNaN() && !it.isInfinite() }?.roundToInt() ?: return null
            if (score !in 1..10) return null
            val summary = obj.optString("summary").trim().takeIf { it.isNotEmpty() && it != "null" } ?: return null
            val concerns = lines(obj.optJSONArray("concerns"))
            val improved = obj.optDouble("improvedScore", Double.NaN).takeIf { !it.isNaN() && !it.isInfinite() }?.roundToInt()
                ?.takeIf { it in (score + 1)..10 && concerns.isNotEmpty() }
            return RecipeReview(score, summary, lines(obj.optJSONArray("highlights")), concerns, improved)
        }

        private fun lines(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() && it != "null" }.distinct().take(MAX_LINES)
        }

        private const val MAX_LINES = 4
    }
}

/** 导入菜谱时让 AI 从口味上评价一下：照着做出来好不好吃、怎么改更好吃 */
class AiRecipeReviewer(private val ai: AiClient) {

    suspend fun review(
        title: String,
        servings: String,
        ingredients: List<Pair<String, String>>,
        steps: List<String>,
    ): RecipeReview {
        if (ingredients.none { it.first.isNotBlank() } && steps.none { it.isNotBlank() }) {
            throw ImportException("还没有用料和做法，没法评价")
        }
        val reply = ai.generate(prompt(title, servings, ingredients, steps), schema)
        return RecipeReview.parse(reply) ?: throw ImportException("AI 返回的格式不对，请重试")
    }

    suspend fun review(full: RecipeFull): RecipeReview = review(
        full.recipe.title,
        full.recipe.servings.orEmpty(),
        full.ingredients.map { it.name to it.amountText },
        full.steps.map { it.text },
    )

    companion object {
        /** 「照评价修改」时交给 [AiRecipeEditor] 的要求：按评价里要注意的地方改 */
        fun fixInstruction(review: RecipeReview): String = buildString {
            append("按下面这份口味评价里提到的问题和建议，把这份菜谱改得更好吃：\n")
            review.concerns.forEach { append("- $it\n") }
            append("只改评价里提到的地方，其余照原样；某条做不到或不用改就不改，在 summary 里说一下。")
        }

        private const val PROMPT = "下面是一道菜谱。请你像一位懂吃的老厨师一样，从口味上评价：照这个用料和做法做出来好不好吃。\n" +
            "要求：\n" +
            "1. 只看味道和口感，不评价菜谱写得详不详细、步骤清不清楚。从用料搭配、调味比例（咸淡、甜酸、香辣是否平衡）、" +
            "火候和做法对口感的影响（嫩不嫩、香不香、腥不腥、入不入味）来判断。\n" +
            "2. score 填 1 到 10 的整数：做出来会有多好吃。家常好吃的 7 分左右，味道出彩、让人想再做的 8–9 分，" +
            "调味明显失衡、容易腥、柴、寡淡的 5 分以下。\n" +
            "3. summary 用一句话（30 字以内）说说做出来是什么味道，比如「咸甜适中，酱香浓，肉软糯入味」。\n" +
            "4. highlights 列 1–3 条口味上的亮点，比如某样用料提鲜、某步让肉更嫩，每条一句短话。\n" +
            "5. concerns 列 0–3 条可能让味道打折的地方和怎么改得更好吃，比如糖太多会腻、少了去腥的料酒姜片、" +
            "火太大肉会柴；没有就给空数组。\n" +
            "6. improvedScore 填照 concerns 里的建议改完以后，大约能到几分（1 到 10 的整数，不低于 score）；concerns 为空就填和 score 一样。\n" +
            "7. 用简体中文，不要客套话。\n\n"

        fun prompt(
            title: String,
            servings: String,
            ingredients: List<Pair<String, String>>,
            steps: List<String>,
        ): String = buildString {
            append(PROMPT)
            if (title.isNotBlank()) append("菜名：${title.trim()}\n")
            if (servings.isNotBlank()) append("份量：${servings.trim()}\n")
            val named = ingredients.filter { it.first.isNotBlank() }
            if (named.isNotEmpty()) {
                append("用料：\n")
                named.forEach { (name, amount) ->
                    append("- ${name.trim()}")
                    append(if (amount.isBlank()) "（没写用量）" else "：${amount.trim()}")
                    append('\n')
                }
            }
            val written = steps.map { it.trim() }.filter { it.isNotEmpty() }
            if (written.isNotEmpty()) {
                append("\n做法：\n")
                written.forEachIndexed { i, text -> append("${i + 1}. $text\n") }
            }
        }

        private val schema
            get() = AiSchema.obj(
                "score" to AiSchema.integer(),
                "summary" to AiSchema.string(),
                "highlights" to AiSchema.array(AiSchema.string()),
                "concerns" to AiSchema.array(AiSchema.string()),
                "improvedScore" to AiSchema.integer(),
                required = listOf("score", "summary", "highlights", "concerns", "improvedScore"),
            )
    }
}
