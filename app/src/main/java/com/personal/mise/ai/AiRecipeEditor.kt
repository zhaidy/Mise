package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import com.personal.mise.util.UnitText
import org.json.JSONArray
import org.json.JSONObject

/** AI 改菜谱时的一样用料；source：来自原来第几样用料（从 1 开始），新加的为空 */
data class EditIngredient(val name: String, val amount: String, val source: Int? = null)

/** 改菜谱时发给 AI、AI 改完返回的菜谱；步骤的 source 是来自原来第几步 */
data class EditableRecipe(
    val title: String,
    val servings: String,
    val ingredients: List<EditIngredient>,
    val steps: List<AiStep>,
)

/** AI 改好的菜谱和一句话说明改了什么 */
class AiRecipeEdit(val recipe: EditableRecipe, val summary: String)

/** 按用户的一句话（「改成4人份」「不要辣」「换成空气炸锅」）让 AI 修改整份菜谱 */
class AiRecipeEditor(private val ai: AiClient) {

    suspend fun edit(recipe: EditableRecipe, instruction: String): AiRecipeEdit {
        val ask = instruction.trim()
        if (ask.isEmpty()) throw ImportException("先写一句想怎么改")
        return parseResponse(ai.generate(prompt(recipe, ask), schema))
    }

    companion object {
        private const val PROMPT = "下面是一份中文菜谱（JSON 格式），用料和步骤都带了编号 no。请按用户的要求修改这份菜谱，返回改好后的完整菜谱。\n" +
            "要求：\n" +
            "1. 只改用户要求的、以及为了满足要求必须跟着改的地方（比如换了食材，步骤里对应的说法也要改；份数变了，所有用量按比例换算）；其余内容原样保留，不要顺手润色。\n" +
            "2. ingredients：name 只写食材名，amount 写用量（数字在前、单位在后，如「200克」「1/2茶匙」），没有用量就留空字符串。" +
            "source 填这一样来自原来第几样用料（no），新加的留空。\n" +
            "3. steps：按先后顺序，每步的 text 不要带序号。source 填这一步来自原来第几步（no），" +
            "原来一步拆成几步时都填这个编号，几步合成一步时填最前面那一步，新加的步骤留空。" +
            "timerMinutes 是这一步要等待或加热的分钟数，原来有就保留（时间改了就跟着改），没有明确时长留空。\n" +
            "4. servings 是份量，如「2人份」，原来没有又不用改就留空。title 一般不改，除非用户要求或菜已经变成别的菜。\n" +
            "5. summary：用一句简短的中文告诉用户改了什么。用户的要求做不到或看不懂时，菜谱原样返回，在 summary 里说明原因。\n\n"

        fun prompt(recipe: EditableRecipe, instruction: String): String =
            PROMPT + "用户的要求：$instruction\n\n菜谱：\n" + toJson(recipe).toString(2)

        fun toJson(recipe: EditableRecipe): JSONObject = JSONObject()
            .put("title", recipe.title)
            .put("servings", recipe.servings)
            .put(
                "ingredients",
                JSONArray(
                    recipe.ingredients.mapIndexed { i, it ->
                        JSONObject().put("no", i + 1).put("name", it.name).put("amount", it.amount)
                    },
                ),
            )
            .put(
                "steps",
                JSONArray(
                    recipe.steps.mapIndexed { i, it ->
                        JSONObject().put("no", i + 1).put("text", it.text).put("timerMinutes", it.timerMinutes ?: JSONObject.NULL)
                    },
                ),
            )

        private val schema
            get() = AiSchema.obj(
                "title" to AiSchema.string(),
                "servings" to AiSchema.string(nullable = true),
                "ingredients" to AiSchema.array(
                    AiSchema.obj(
                        "name" to AiSchema.string(),
                        "amount" to AiSchema.string(),
                        "source" to AiSchema.integer(nullable = true),
                        required = listOf("name", "amount"),
                    ),
                ),
                "steps" to AiSchema.array(
                    AiSchema.stepSchema.apply {
                        getJSONObject("properties").put("source", AiSchema.integer(nullable = true))
                    },
                ),
                "summary" to AiSchema.string(),
                required = listOf("title", "ingredients", "steps", "summary"),
            )

        fun parseResponse(reply: JSONObject): AiRecipeEdit {
            val ingredients = reply.optJSONArray("ingredients")
            val steps = reply.optJSONArray("steps")
            if (ingredients == null || steps == null) throw ImportException("AI 返回的格式不对，请重试")
            val parsedSteps = AiSchema.parseSteps(steps)
            if (parsedSteps.isEmpty()) throw ImportException("AI 改完没有步骤了，请换个说法再试")
            val recipe = EditableRecipe(
                title = reply.optString("title").trim().nullIfNull(),
                servings = reply.optString("servings").trim().nullIfNull(),
                ingredients = (0 until ingredients.length()).mapNotNull { i ->
                    val obj = ingredients.optJSONObject(i) ?: return@mapNotNull null
                    val name = obj.optString("name").trim().nullIfNull()
                    if (name.isEmpty()) null
                    else EditIngredient(
                        name,
                        UnitText.normalize(obj.optString("amount").trim().nullIfNull()),
                        obj.optInt("source", 0).takeIf { it > 0 },
                    )
                },
                steps = parsedSteps,
            )
            return AiRecipeEdit(recipe, reply.optString("summary").trim().nullIfNull())
        }

        /**
         * 改好的每一项对应原来的第几项（都从 0 开始）：先按 source，对不上再按名字找；
         * 原来每一项最多对应一项，拆成几步时只有第一步接着用原来的图片和编号。
         */
        fun match(sources: List<Int?>, oldCount: Int, sameAs: (newIndex: Int, oldIndex: Int) -> Boolean = { _, _ -> false }): Map<Int, Int> {
            val used = mutableSetOf<Int>()
            val map = mutableMapOf<Int, Int>()
            sources.forEachIndexed { j, source ->
                val i = source?.minus(1)
                if (i != null && i in 0 until oldCount && used.add(i)) map[j] = i
            }
            sources.indices.filter { it !in map }.forEach { j ->
                val i = (0 until oldCount).firstOrNull { it !in used && sameAs(j, it) }
                if (i != null) {
                    used += i
                    map[j] = i
                }
            }
            return map
        }

        /** org.json 把 JSON null 读成字符串 "null" */
        private fun String.nullIfNull() = if (this == "null") "" else this
    }
}
