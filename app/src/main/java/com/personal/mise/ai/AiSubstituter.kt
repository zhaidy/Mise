package com.personal.mise.ai

import com.personal.mise.data.RecipeFull
import com.personal.mise.importer.ImportException
import org.json.JSONObject

/** 一种替代办法：用什么、用多少、味道或做法上要注意什么 */
data class Substitute(val name: String, val amount: String, val note: String) {
    /** 记进批注的一句话，如「没有生抽：用老抽 半勺 + 盐少许（颜色会深一点）」 */
    fun noteLine(missing: String): String =
        "没有$missing：${if (name.startsWith("不")) "" else "用"}${name}${amount.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}" +
            note.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
}

/** 做菜时缺了某样用料，让 AI 说用什么代替、用多少 */
class AiSubstituter(private val ai: AiClient) {

    suspend fun suggest(full: RecipeFull, ingredient: String, amount: String): List<Substitute> {
        if (ingredient.isBlank()) throw ImportException("没有用料名")
        val reply = ai.generate(prompt(full.recipe.title, ingredient, amount, full.ingredients.map { it.name to it.amountText }, full.steps.map { it.text }), schema)
        return parseResponse(reply)
    }

    companion object {
        private const val MAX = 3

        fun prompt(
            title: String,
            missing: String,
            amount: String,
            ingredients: List<Pair<String, String>>,
            steps: List<String>,
        ): String = buildString {
            append("我正在做「${title.trim()}」，可是家里没有「${missing.trim()}」")
            if (amount.isNotBlank()) append("（菜谱里要 ${amount.trim()}）")
            append("。请给 1 到 $MAX 个家里常有的替代办法，最常见、最接近原味的放前面。\n")
            append("要求：\n")
            append("1. name 写用什么代替，可以是几样配在一起，如「老抽 + 盐」；实在没法代替、少了也不太影响的，可以给一条「不放」。\n")
            append("2. amount 写按这道菜的份量要用多少，如「半勺」「1小勺 + 一小撮」。\n")
            append("3. note 一句话说味道、颜色会有什么不同，或者做法上要怎么调整（比如什么时候放、要不要少放盐），20 字以内。\n")
            append("4. 用简体中文，不要客套话。\n\n")
            val named = ingredients.filter { it.first.isNotBlank() }
            if (named.isNotEmpty()) append("这道菜的用料：${named.joinToString("、") { (n, a) -> if (a.isBlank()) n.trim() else "${n.trim()} ${a.trim()}" }}\n")
            steps.filter { it.isNotBlank() }.take(10).takeIf { it.isNotEmpty() }?.let { written ->
                append("做法：\n")
                written.forEachIndexed { i, text -> append("${i + 1}. ${text.trim().take(80)}\n") }
            }
        }

        private val schema
            get() = AiSchema.obj(
                "options" to AiSchema.array(
                    AiSchema.obj(
                        "name" to AiSchema.string(),
                        "amount" to AiSchema.string(),
                        "note" to AiSchema.string(),
                        required = listOf("name", "amount", "note"),
                    ),
                ),
                required = listOf("options"),
            )

        fun parseResponse(reply: JSONObject): List<Substitute> {
            val arr = reply.optJSONArray("options") ?: throw ImportException("AI 返回的格式不对，请重试")
            fun text(obj: JSONObject, key: String) = obj.optString(key).trim().takeIf { it != "null" }.orEmpty()
            val options = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = text(o, "name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Substitute(name, text(o, "amount"), text(o, "note"))
            }.distinctBy { it.name }.take(MAX)
            if (options.isEmpty()) throw ImportException("AI 没想出替代办法，请重试")
            return options
        }
    }
}
