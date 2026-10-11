package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import com.personal.mise.util.UnitText
import org.json.JSONArray
import org.json.JSONObject

/** 要翻译的菜谱：菜名、份量、用料（名称, 用量原文）和每一步的文字 */
data class RecipeText(
    val title: String,
    val servings: String?,
    val ingredients: List<Pair<String, String>>,
    val steps: List<String>,
)

/** 把英文网站上的菜谱交给 AI 翻译成中文；用料和步骤一条对一条，步骤图才对得上。 */
class AiRecipeTranslator(private val ai: AiClient) {

    suspend fun translate(recipe: RecipeText): RecipeText =
        parseResponse(ai.generate(PROMPT + toJson(recipe).toString(2), schema), recipe)

    companion object {
        private const val PROMPT = "下面是从英文网站导入的一道菜谱（JSON 格式）。请把它翻译成简体中文，用中国人做饭时习惯的说法。\n" +
            "要求：\n" +
            "1. title：翻译成简短、常见的中文菜名，有通行中文名的菜用通行的名字。\n" +
            "2. servings：翻译成「2人份」这样的写法，原来为空就留空。\n" +
            "3. ingredients：条数和顺序跟原来完全一样，一条对一条。name 翻译成常见的中文食材名，" +
            "只写名称，切法、备注之类放进 amount 后面的括号里。amount 保留原来的数字，单位换成中文" +
            "（cup→杯、tablespoon/tbsp→汤匙、teaspoon/tsp→茶匙、g→克、ml→毫升、clove→瓣、pinch→一小撮）；" +
            "磅（lb、pound）和盎司（oz、ounce）不要保留，换算成克（1磅≈454克、1盎司≈28克），取整数，比如「1 lb」写成「454克」；" +
            "液体的 fl oz 换算成毫升（1液量盎司≈30毫升）；" +
            "分数写成「1/2」这样，数字在前、单位在后；原来没有用量就留空字符串。\n" +
            "4. steps：条数和顺序跟原来完全一样，一条对一条，不要合并、拆分或增删，不要带序号。" +
            "温度换算成摄氏度（如「200°C」），原来写了华氏度的保留在括号里。\n" +
            "5. 只翻译，不要增加原来没有的食材、用量、时间或步骤。原来已经是中文的部分保持原样。\n\n" +
            "菜谱：\n"

        private val schema
            get() = AiSchema.obj(
                "title" to AiSchema.string(),
                "servings" to AiSchema.string(nullable = true),
                "ingredients" to AiSchema.array(
                    AiSchema.obj("name" to AiSchema.string(), "amount" to AiSchema.string(), required = listOf("name", "amount")),
                ),
                "steps" to AiSchema.array(AiSchema.string()),
                required = listOf("title", "ingredients", "steps"),
            )

        private val cjk = Regex("[\\u4e00-\\u9fff]")
        private val latin = Regex("[A-Za-z]")

        /** 菜谱主要是英文：英文字母明显比汉字多（个别英文单位、品牌名不算） */
        fun isEnglish(recipe: RecipeText): Boolean {
            val text = buildString {
                append(recipe.title).append('\n')
                recipe.ingredients.forEach { (name, amount) -> append(name).append(' ').append(amount).append('\n') }
                recipe.steps.forEach { append(it).append('\n') }
            }
            val letters = latin.findAll(text).count()
            val hanzi = cjk.findAll(text).count()
            return letters >= MIN_LETTERS && hanzi * 10 < letters
        }

        /** 英文单词大约 5 个字母，太少的不像一份菜谱 */
        private const val MIN_LETTERS = 20

        fun toJson(recipe: RecipeText): JSONObject = JSONObject()
            .put("title", recipe.title)
            .put("servings", recipe.servings ?: JSONObject.NULL)
            .put("ingredients", JSONArray(recipe.ingredients.map { (name, amount) -> JSONObject().put("name", name).put("amount", amount) }))
            .put("steps", JSONArray(recipe.steps))

        /** 条数对不上时报错，免得步骤图、用料错位 */
        fun parseResponse(reply: JSONObject, original: RecipeText): RecipeText {
            val ingredients = reply.optJSONArray("ingredients")
            val steps = reply.optJSONArray("steps")
            if (ingredients == null || steps == null ||
                ingredients.length() != original.ingredients.size || steps.length() != original.steps.size
            ) throw ImportException("AI 翻译的条数对不上原来的菜谱")
            return RecipeText(
                title = reply.optString("title").trim().nullIfNull().ifEmpty { original.title },
                servings = reply.optString("servings").trim().nullIfNull().takeIf { it.isNotEmpty() } ?: original.servings,
                ingredients = original.ingredients.mapIndexed { i, (name, amount) ->
                    val obj = ingredients.optJSONObject(i)
                    val newName = obj?.optString("name")?.trim()?.nullIfNull().orEmpty().ifEmpty { name }
                    val newAmount = obj?.optString("amount")?.trim()?.nullIfNull()?.let { UnitText.normalize(it) } ?: amount
                    newName to newAmount
                },
                steps = original.steps.mapIndexed { i, text ->
                    steps.optString(i).trim().nullIfNull().ifEmpty { text }
                },
            )
        }

        /** org.json 把 JSON null 读成字符串 "null" */
        private fun String.nullIfNull() = if (this == "null") "" else this
    }
}
