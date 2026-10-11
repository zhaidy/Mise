package com.personal.mise.ai

import com.personal.mise.bowl.SeasoningDictionary
import com.personal.mise.data.IngredientDraft
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeDraft
import com.personal.mise.data.StepDraft
import com.personal.mise.importer.ImportException
import com.personal.mise.importer.ImportResult
import com.personal.mise.importer.UrlExtractor
import org.json.JSONObject

data class AiIngredient(val name: String, val amount: String)

data class AiRecipe(
    val title: String,
    val servings: String?,
    val ingredients: List<AiIngredient>,
    val steps: List<AiStep>,
)

/** 把手机笔记里随手记的菜谱（格式不固定）交给 AI，整理成菜名、用料和步骤。 */
class AiRecipeOrganizer(private val ai: AiClient) {

    suspend fun organize(note: String): ImportResult {
        val text = note.trim()
        if (text.isEmpty()) throw ImportException("没有可以整理的文字")
        if (text.length > MAX_LENGTH) throw ImportException("文字太长了（超过 $MAX_LENGTH 字），请只粘贴一道菜")
        return toImportResult(parseResponse(ai.generate(PROMPT + text, schema)), text)
    }

    companion object {
        const val MAX_LENGTH = 20_000

        private const val PROMPT = "下面是我在手机笔记里随手记的一道中文菜谱，格式不固定：可能没有标题、" +
            "用料和做法混在一起、用口语或缩写、有错别字、带表情符号或无关的闲聊。请把它整理成一份规范的菜谱。\n" +
            "要求：\n" +
            "1. title：菜名。原文没写菜名时，根据内容起一个简短常见的菜名。\n" +
            "2. servings：原文写了几人份或份量就填，没写留空。\n" +
            "3. ingredients：所有用到的食材和调料，每样一条，name 只写名称，amount 写用量原文（如「300克」「2勺」「适量」），" +
            "没写用量就留空字符串。做法里提到但用料里没列的也要收进来。同一样东西只列一次。\n" +
            "4. steps：按先后顺序的做法，每一步只做一件主要的事，text 用简洁的一两句话，不要带序号。" +
            "原文明确写了某一步要等待或加热的时长（如「焖10分钟」）时，把分钟数填进 timerMinutes，否则留空。" +
            "小贴士、注意事项放进相关的那一步里。\n" +
            "5. 只整理原文的内容，不要编造原文没有的食材、用量、时间或步骤，保留原文的数字。\n" +
            "6. 如果文字里有好几道菜，只整理第一道。如果完全不是菜谱，所有字段都返回空。\n\n" +
            "笔记原文：\n"

        private val schema
            get() = AiSchema.obj(
                "title" to AiSchema.string(),
                "servings" to AiSchema.string(nullable = true),
                "ingredients" to AiSchema.array(
                    AiSchema.obj("name" to AiSchema.string(), "amount" to AiSchema.string(), required = listOf("name", "amount")),
                ),
                "steps" to AiSchema.array(AiSchema.stepSchema),
                required = listOf("title", "ingredients", "steps"),
            )

        fun parseResponse(json: JSONObject): AiRecipe {
            val ingredients = json.optJSONArray("ingredients")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.optJSONObject(i) ?: return@mapNotNull null
                    val name = item.optString("name").trim()
                    if (name.isEmpty()) null else AiIngredient(name, item.optString("amount").trim().nullIfNull())
                }
            }.orEmpty()
            val steps = AiSchema.parseSteps(json.optJSONArray("steps"))
            if (ingredients.isEmpty() && steps.isEmpty()) throw ImportException("没能从这段文字里整理出菜谱，请确认内容是一道菜的做法")
            return AiRecipe(
                title = json.optString("title").trim().nullIfNull(),
                servings = json.optString("servings").trim().nullIfNull().takeIf { it.isNotEmpty() },
                ingredients = ingredients,
                steps = steps,
            )
        }

        fun toImportResult(recipe: AiRecipe, note: String): ImportResult {
            val warnings = mutableListOf<String>()
            if (recipe.title.isEmpty()) warnings += "没有整理出菜名，请手动填写"
            if (recipe.ingredients.isEmpty()) warnings += "没有整理出用料，请手动添加"
            if (recipe.steps.isEmpty()) warnings += "没有整理出步骤，请手动添加"
            val draft = RecipeDraft(
                title = recipe.title,
                sourceUrl = UrlExtractor.firstUrl(note),
                servings = recipe.servings,
                ingredients = recipe.ingredients.map {
                    IngredientDraft(
                        name = it.name,
                        amountText = it.amount,
                        kind = if (SeasoningDictionary.isSeasoning(it.name)) IngredientKind.SEASONING else IngredientKind.MAIN,
                    )
                },
                steps = recipe.steps.map { s ->
                    StepDraft(text = s.text, timerSeconds = s.timerMinutes?.let { (it * 60).toInt() }?.takeIf { it > 0 })
                },
            )
            return ImportResult(draft, warnings)
        }

        /** org.json 把 JSON null 读成字符串 "null" */
        private fun String.nullIfNull() = if (this == "null") "" else this
    }
}
