package com.personal.mise.ai

import com.personal.mise.data.CategoryNode
import com.personal.mise.data.RecipeFull
import com.personal.mise.importer.ImportException
import org.json.JSONObject

/** AI 挑的大类、小类和标签；挑不出时为空 */
data class Classification(val category: String?, val subcategory: String?, val tags: List<String>) {
    val isEmpty: Boolean get() = category == null && tags.isEmpty()

    /** 给人看的一句话，如「家常菜 › 炒菜 · #荤菜 #下饭」 */
    fun describe(): String = listOfNotNull(
        listOfNotNull(category, subcategory).joinToString(" › ").ifEmpty { null },
        tags.joinToString(" ") { "#$it" }.ifEmpty { null },
    ).joinToString(" · ")
}

/**
 * 让 AI 按已有的分类和标签给菜谱分类：只从已经用过的大类、小类、标签里挑，不另起新名字，
 * 免得分类越来越乱。一个分类、标签都还没有时，从几个常见的里挑。
 */
class AiClassifier(private val ai: AiClient) {

    suspend fun classify(
        title: String,
        ingredients: List<String>,
        steps: List<String>,
        tree: List<CategoryNode>,
        tags: List<String>,
    ): Classification {
        if (title.isBlank() && ingredients.none { it.isNotBlank() }) throw ImportException("还没有菜名和用料，没法分类")
        val reply = ai.generate(prompt(title, ingredients, steps, tree, tags), schema)
        return parseResponse(reply, tree, tags)
    }

    suspend fun classify(full: RecipeFull, tree: List<CategoryNode>, tags: List<String>): Classification =
        classify(full.recipe.title, full.ingredients.map { it.name }, full.steps.map { it.text }, tree, tags)

    companion object {
        /** 还没有任何大类时从这里挑 */
        val DEFAULT_CATEGORIES = listOf("家常菜", "汤", "凉菜", "主食", "小吃", "甜品", "烘焙", "饮品")

        /** 还没有任何标签时从这里挑 */
        val DEFAULT_TAGS = listOf("荤菜", "素菜", "快手菜", "下饭菜", "辣", "清淡")

        private const val MAX_TAGS = 3

        fun prompt(
            title: String,
            ingredients: List<String>,
            steps: List<String>,
            tree: List<CategoryNode>,
            tags: List<String>,
        ): String = buildString {
            append("下面是一道菜谱。请从给出的选项里给它挑一个大类、一个小类和几个标签。\n")
            append("要求：\n")
            append("1. category 只能从下面的大类里选一个，都不合适就填空字符串。\n")
            append("2. subcategory 只能从所选大类下面列出的小类里选，没有合适的或没有列出小类就填空字符串。\n")
            append("3. tags 从下面的标签里选 0 到 $MAX_TAGS 个最贴切的，不要自己编新的。\n\n")
            if (tree.isEmpty()) {
                append("大类：${DEFAULT_CATEGORIES.joinToString("、")}\n")
            } else {
                append("大类（冒号后面是它的小类）：\n")
                tree.forEach { node ->
                    append("- ${node.name}")
                    if (node.subcategories.isNotEmpty()) append("：${node.subcategories.joinToString("、")}")
                    append('\n')
                }
            }
            append("标签：${tags.ifEmpty { DEFAULT_TAGS }.joinToString("、")}\n\n")
            if (title.isNotBlank()) append("菜名：${title.trim()}\n")
            ingredients.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { append("用料：${it.joinToString("、") { s -> s.trim() }}\n") }
            steps.filter { it.isNotBlank() }.take(8).takeIf { it.isNotEmpty() }?.let { written ->
                append("做法：\n")
                written.forEachIndexed { i, text -> append("${i + 1}. ${text.trim().take(80)}\n") }
            }
        }

        private val schema
            get() = AiSchema.obj(
                "category" to AiSchema.string(),
                "subcategory" to AiSchema.string(),
                "tags" to AiSchema.array(AiSchema.string()),
                required = listOf("category", "subcategory", "tags"),
            )

        /** 只认给出的选项；选项外的名字丢掉 */
        fun parseResponse(reply: JSONObject, tree: List<CategoryNode>, tags: List<String>): Classification {
            if (!reply.has("category") && !reply.has("tags")) throw ImportException("AI 返回的格式不对，请重试")
            val categories = tree.map { it.name }.ifEmpty { DEFAULT_CATEGORIES }
            val category = clean(reply.optString("category"))?.takeIf { it in categories }
            val subs = tree.find { it.name == category }?.subcategories.orEmpty()
            val subcategory = clean(reply.optString("subcategory"))?.takeIf { category != null && it in subs }
            val allowed = tags.ifEmpty { DEFAULT_TAGS }
            val arr = reply.optJSONArray("tags")
            val picked = (0 until (arr?.length() ?: 0)).mapNotNull { clean(arr!!.optString(it)) }
                .map { it.removePrefix("#") }
                .filter { it in allowed }
                .distinct()
                .take(MAX_TAGS)
            return Classification(category, subcategory, picked)
        }

        private fun clean(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    }
}
