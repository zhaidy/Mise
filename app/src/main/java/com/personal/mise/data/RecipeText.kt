package com.personal.mise.data

import org.json.JSONArray
import org.json.JSONObject

/** 菜谱的文字部分：菜名、份量、用料（名称, 用量原文）和每一步的文字。翻译英文菜谱时用，翻译后原文也这样存着 */
data class RecipeText(
    val title: String,
    val servings: String?,
    val ingredients: List<Pair<String, String>>,
    val steps: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("servings", servings ?: JSONObject.NULL)
        .put("ingredients", JSONArray(ingredients.map { (name, amount) -> JSONObject().put("name", name).put("amount", amount) }))
        .put("steps", JSONArray(steps))

    companion object {
        /** 存在数据库和云端的原文；格式不对时返回 null，不影响菜谱本身 */
        fun fromJson(json: String?): RecipeText? = json?.takeIf { it.isNotBlank() }?.let {
            runCatching {
                val obj = JSONObject(it)
                val ingredients = obj.getJSONArray("ingredients")
                val steps = obj.getJSONArray("steps")
                RecipeText(
                    title = obj.optString("title"),
                    servings = obj.optString("servings").takeIf { s -> s.isNotEmpty() && !obj.isNull("servings") },
                    ingredients = (0 until ingredients.length()).map { i ->
                        val item = ingredients.getJSONObject(i)
                        item.optString("name") to item.optString("amount")
                    },
                    steps = (0 until steps.length()).map { i -> steps.optString(i) },
                )
            }.getOrNull()
        }
    }
}
