package com.personal.mise.ai

import com.personal.mise.bowl.SeasoningDictionary
import com.personal.mise.data.IngredientDraft
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeDraft
import com.personal.mise.data.StepDraft
import com.personal.mise.importer.ImportException
import com.personal.mise.importer.ImportResult
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.util.UnitText
import org.json.JSONObject

data class AiIngredient(val name: String, val amount: String)

data class AiRecipe(
    val title: String,
    val servings: String?,
    val ingredients: List<AiIngredient>,
    val steps: List<AiStep>,
)

/** 把手机笔记里随手记的菜谱、小红书笔记（格式不固定）交给 AI，整理成菜名、用料和步骤。 */
class AiRecipeOrganizer(private val ai: AiClient) {

    suspend fun organize(note: String): ImportResult {
        val text = note.trim()
        if (text.isEmpty()) throw ImportException("没有可以整理的文字")
        if (text.length > MAX_LENGTH) throw ImportException("文字太长了（超过 $MAX_LENGTH 字），请只粘贴一道菜")
        return toImportResult(parseResponse(ai.generate(PROMPT + text, schema)), text)
    }

    /** 小红书笔记：标题、正文，加上笔记配图（用料和步骤常常只写在图上） */
    suspend fun organizeXhs(title: String, desc: String, images: List<ByteArray>): AiRecipe {
        val text = xhsText(title, desc)
        if (text.isEmpty() && images.isEmpty()) throw ImportException("这篇笔记没有文字也没有图片")
        return parseResponse(ai.generate(xhsPrompt(images.size) + text, schema, images))
    }

    /** 从相册选的图片（截图、菜谱书页、手写笔记等），按选择的顺序发给 AI 整理 */
    suspend fun organizeImages(images: List<ByteArray>): AiRecipe {
        if (images.isEmpty()) throw ImportException("没有可以整理的图片")
        return parseResponse(ai.generate(imagesPrompt(images.size), schema, images))
    }

    companion object {
        const val MAX_LENGTH = 20_000

        /** 一次最多发给 AI 的图片数 */
        const val MAX_IMAGES = 9

        private const val PROMPT = "下面是我在手机笔记里随手记的一道中文菜谱，格式不固定：可能没有标题、" +
            "用料和做法混在一起、用口语或缩写、有错别字、带表情符号或无关的闲聊。请把它整理成一份规范的菜谱。\n" +
            "要求：\n" +
            "1. title：菜名。原文没写菜名时，根据内容起一个简短常见的菜名。\n" +
            "2. servings：原文写了几人份或份量就填，没写留空。\n" +
            "3. ingredients：所有用到的食材和调料，每样一条，name 只写名称，amount 写用量原文（如「300克」「2勺」「适量」），数字在前、单位在后，不要写成「克300」「勺2」，" +
            "没写用量就留空字符串。做法里提到但用料里没列的也要收进来。同一样东西只列一次。\n" +
            "4. steps：按先后顺序的做法，每一步只做一件主要的事，text 用简洁的一两句话，不要带序号。" +
            "原文明确写了某一步要等待或加热的时长（如「焖10分钟」）时，把分钟数填进 timerMinutes，否则留空。" +
            "小贴士、注意事项放进相关的那一步里。\n" +
            "5. 只整理原文的内容，不要编造原文没有的食材、用量、时间或步骤，保留原文的数字。\n" +
            "6. 如果文字里有好几道菜，只整理第一道。如果完全不是菜谱，所有字段都返回空。\n\n" +
            "笔记原文：\n"

        private const val XHS_PROMPT = "下面是一篇小红书上的中文菜谱笔记。小红书的排版很随意：标题常常是「绝了！」「一口气吃三碗」这类吸引眼球的话，" +
            "正文夹杂大量表情、话题标签（#xxx）、口语和与做法无关的闲聊，用料和步骤可能写在正文里，也可能只写在配图上。" +
            "请把它整理成一份规范的菜谱。\n" +
            "要求：\n" +
            "1. title：规范、简短的菜名（如「可乐鸡翅」），不要照抄标题里的感叹、形容和表情。\n" +
            "2. servings：写了几人份或份量就填，没写留空。\n" +
            "3. ingredients：所有用到的食材和调料，每样一条，name 只写名称，amount 写用量原文（如「300克」「2勺」「适量」），数字在前、单位在后，不要写成「克300」「勺2」，" +
            "没写用量就留空字符串。做法里提到但用料里没列的也要收进来。同一样东西只列一次。\n" +
            "4. steps：按先后顺序的做法，每一步只做一件主要的事，text 用简洁的一两句话，不要带序号和表情。" +
            "明确写了某一步要等待或加热的时长（如「焖10分钟」）时，把分钟数填进 timerMinutes，否则留空。" +
            "小贴士、注意事项放进相关的那一步里。\n" +
            "5. 只整理笔记里写了的内容（正文和图片上的文字），不要编造没有写的食材、用量、时间或步骤，保留原来的数字。" +
            "不要根据成品图或步骤图的画面去猜用料和用量。\n" +
            "6. 正文和图片上的文字有出入时，以图片上写的用料和用量为准。去掉话题标签、@、求关注和广告。\n" +
            "7. 如果笔记里有好几道菜，只整理第一道。如果完全不是菜谱，所有字段都返回空。\n\n"

        /** 有图时告诉模型图片是按顺序排的笔记配图 */
        private fun xhsPrompt(imageCount: Int) = XHS_PROMPT +
            (if (imageCount > 0) "前面的 $imageCount 张图片是这篇笔记的配图，按笔记里的顺序排列。\n\n" else "") +
            "笔记原文：\n"

        private const val IMAGES_PROMPT = "下面是一道中文菜谱的图片，可能是网页或 App 的截图、菜谱书的照片、手写的笔记，" +
            "也可能夹杂成品图或步骤图。请读出图片上的文字，整理成一份规范的菜谱。\n" +
            "要求：\n" +
            "1. title：图片上写的菜名，规范、简短（如「可乐鸡翅」）。没写菜名时根据内容起一个简短常见的菜名。\n" +
            "2. servings：写了几人份或份量就填，没写留空。\n" +
            "3. ingredients：所有用到的食材和调料，每样一条，name 只写名称，amount 写用量原文（如「300克」「2勺」「适量」），数字在前、单位在后，不要写成「克300」「勺2」，" +
            "没写用量就留空字符串。做法里提到但用料里没列的也要收进来。同一样东西只列一次。\n" +
            "4. steps：按先后顺序的做法，每一步只做一件主要的事，text 用简洁的一两句话，不要带序号和表情。" +
            "明确写了某一步要等待或加热的时长（如「焖10分钟」）时，把分钟数填进 timerMinutes，否则留空。" +
            "小贴士、注意事项放进相关的那一步里。\n" +
            "5. 只整理图片上写了的内容，不要编造没有写的食材、用量、时间或步骤，保留原来的数字。" +
            "不要根据成品图或步骤图的画面去猜用料和用量。\n" +
            "6. 多张图片是同一道菜的不同部分（比如一张写用料、一张写做法，或一篇长文的连续截图），按顺序合在一起整理，" +
            "图片之间重复的内容只保留一次。忽略截图里的状态栏、按钮、评论、广告等与做法无关的内容。\n" +
            "7. 如果图片里有好几道菜，只整理第一道。如果完全不是菜谱，所有字段都返回空。\n\n"

        private fun imagesPrompt(imageCount: Int) = IMAGES_PROMPT +
            if (imageCount > 1) "一共 $imageCount 张图片，按顺序排列。" else "只有这一张图片。"

        fun xhsText(title: String, desc: String): String =
            listOf(title.trim().takeIf { it.isNotEmpty() }?.let { "标题：$it" }, desc.trim().takeIf { it.isNotEmpty() })
                .filterNotNull().joinToString("\n")

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
                    if (name.isEmpty()) null else AiIngredient(name, UnitText.normalize(item.optString("amount").trim().nullIfNull()))
                }
            }.orEmpty()
            val steps = AiSchema.parseSteps(json.optJSONArray("steps"))
            if (ingredients.isEmpty() && steps.isEmpty()) throw ImportException("没能整理出菜谱，请确认内容是一道菜的做法")
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
