package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import com.personal.mise.util.ShoppingList
import org.json.JSONObject

/**
 * 让 AI 整理购物清单：名字不同其实是同一样东西的合成一项（葱、葱花、小葱），
 * 再按超市里的区域分好。用量不让 AI 算，合并后照原来的规则加总、列明细。
 */
class AiShoppingOrganizer(private val ai: AiClient) {

    /** 返回 AI 的分组（按 [items] 的序号），由 [ShoppingList.regroup] 套到清单上 */
    suspend fun organize(items: List<ShoppingList.Item>): List<ShoppingList.AiGroup> {
        if (items.size < 2) throw ImportException("要买的东西太少，不用整理")
        return parseResponse(ai.generate(prompt(items), schema), items)
    }

    companion object {
        fun prompt(items: List<ShoppingList.Item>): String = buildString {
            append("下面是一份买菜清单，每行开头是序号。请帮我整理：\n")
            append("1. 名字不同但买的是同一样东西的合成一组，比如「葱」「葱花」「小葱」、「生抽」「酱油（生抽）」、「鸡蛋」「蛋」。")
            append("切法不同（如「姜片」「姜丝」）也算同一样；不是同一样的（如「生抽」和「老抽」、「鸡胸肉」和「鸡腿」）不要合。\n")
            append("2. 每组的 name 写在超市里买的时候的叫法，简短，如「小葱」「生姜」「鸡蛋」。\n")
            append("3. section 从这几个里选一个：${ShoppingList.SECTIONS.joinToString("、")}。\n")
            append("4. items 填这一组包括的序号，每个序号只能出现在一组里，每个序号都要出现。\n\n")
            items.forEachIndexed { i, item ->
                append("${i + 1}. ${item.name}")
                if (item.seasoning) append("（调料）")
                item.detail.takeIf { it.isNotBlank() }?.let { append("：$it") }
                append('\n')
            }
        }

        private val schema
            get() = AiSchema.obj(
                "groups" to AiSchema.array(
                    AiSchema.obj(
                        "name" to AiSchema.string(),
                        "section" to AiSchema.string(),
                        "items" to AiSchema.array(AiSchema.integer()),
                        required = listOf("name", "section", "items"),
                    ),
                ),
                required = listOf("groups"),
            )

        fun parseResponse(reply: JSONObject, items: List<ShoppingList.Item>): List<ShoppingList.AiGroup> {
            val arr = reply.optJSONArray("groups") ?: throw ImportException("AI 返回的格式不对，请重试")
            val groups = (0 until arr.length()).mapNotNull { i ->
                val g = arr.optJSONObject(i) ?: return@mapNotNull null
                val members = g.optJSONArray("items") ?: return@mapNotNull null
                ShoppingList.AiGroup(
                    name = g.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" },
                    section = g.optString("section").trim(),
                    items = (0 until members.length()).map { members.optInt(it, 0) - 1 },
                )
            }
            if (groups.none { g -> g.items.any { it in items.indices } }) throw ImportException("AI 没有整理出来，请重试")
            return groups
        }
    }
}
