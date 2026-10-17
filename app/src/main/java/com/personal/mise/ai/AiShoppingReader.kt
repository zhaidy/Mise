package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import org.json.JSONObject

/** 让 AI 看购物清单的照片（多半是手写的），一样一样列出要买的东西 */
class AiShoppingReader(private val ai: AiClient) {

    suspend fun read(image: ByteArray): List<String> = parseResponse(ai.generate(PROMPT, schema, listOf(image)))

    companion object {
        const val PROMPT = "照片里是一张购物清单，多半是手写的。请把上面要买的东西一样一样列出来：\n" +
            "1. 每样一项，写清单上的叫法；字迹潦草时按常见的东西猜，比如「酱由」写成「酱油」。\n" +
            "2. 写了数量或备注的跟在后面，比如「鸡蛋 2盒」「牛奶（低脂）」。\n" +
            "3. 划掉的、打了勾的（已经买了）、标题、日期、店名都不要。\n" +
            "4. 照片里没有购物清单时 items 返回空数组。"

        private val schema
            get() = AiSchema.obj("items" to AiSchema.array(AiSchema.string()), required = listOf("items"))

        fun parseResponse(reply: JSONObject): List<String> {
            val arr = reply.optJSONArray("items") ?: throw ImportException("AI 返回的格式不对，请重试")
            val items = (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() && it != "null" }
            if (items.isEmpty()) throw ImportException("照片里没认出要买的东西，换一张清楚点的再试")
            return items
        }
    }
}
