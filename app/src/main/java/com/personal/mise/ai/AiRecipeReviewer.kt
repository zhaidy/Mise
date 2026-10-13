package com.personal.mise.ai

import com.personal.mise.data.RecipeFull
import com.personal.mise.importer.ImportException
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** AI 对一道菜谱的评价：1–10 分、一句话总评、亮点、要注意的地方 */
data class RecipeReview(
    val score: Int,
    val summary: String,
    val highlights: List<String>,
    val concerns: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("score", score)
        .put("summary", summary)
        .put("highlights", JSONArray(highlights))
        .put("concerns", JSONArray(concerns))

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
            return RecipeReview(score, summary, lines(obj.optJSONArray("highlights")), lines(obj.optJSONArray("concerns")))
        }

        private fun lines(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() && it != "null" }.distinct().take(MAX_LINES)
        }

        private const val MAX_LINES = 4
    }
}

/** 导入菜谱时让 AI 像有经验的厨师一样评价一下：靠不靠谱、好不好做、要注意什么 */
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
            append("按下面这份评价里提到的问题和建议，改进这份菜谱：\n")
            review.concerns.forEach { append("- $it\n") }
            append("只改评价里提到的地方，其余照原样；某条做不到或不用改就不改，在 summary 里说一下。")
        }

        private const val PROMPT = "下面是一道菜谱。请你像一位有经验的家常菜厨师一样，评价这份菜谱写得怎么样、照着做能不能做好吃。\n" +
            "要求：\n" +
            "1. score 填 1 到 10 的整数：用料和做法是否合理、步骤是否清楚完整、照着做成功的把握大不大。" +
            "普通靠谱的家常菜谱 7 分左右，写得很好、细节到位的 8–9 分，缺步骤、用量离谱或做法有明显问题的 5 分以下。\n" +
            "2. summary 用一句话（30 字以内）总评，比如「经典做法，步骤清楚，新手也能做成」。\n" +
            "3. highlights 列 1–3 条这份菜谱的亮点，每条一句短话。\n" +
            "4. concerns 列 0–3 条要注意的地方或改进建议，比如用量可疑、漏了步骤、火候没说清、容易失败的环节；没有就给空数组。\n" +
            "5. 只评价菜谱本身，用简体中文，不要客套话。\n\n"

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
                required = listOf("score", "summary", "highlights", "concerns"),
            )
    }
}
