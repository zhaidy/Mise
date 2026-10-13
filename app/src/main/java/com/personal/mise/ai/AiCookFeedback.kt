package com.personal.mise.ai

import com.personal.mise.data.RecipeFull
import com.personal.mise.importer.ImportException
import org.json.JSONObject

/** AI 整理好的新批注，和一句话说改了什么 */
data class NoteUpdate(val note: String, val summary: String)

/**
 * 做完一道菜写一句感受（「偏咸，鸡腿多炖了 5 分钟才熟」），让 AI 把它整理进这道菜的批注：
 * 和原来的批注合并、去重，跟某一步有关的写成「第 N 步：…」，做菜翻到那一步时会提醒。
 */
class AiCookFeedback(private val ai: AiClient) {

    suspend fun merge(full: RecipeFull, feedback: String): NoteUpdate {
        if (feedback.isBlank()) throw ImportException("还没写感受")
        val reply = ai.generate(prompt(full.recipe.title, full.recipe.note.orEmpty(), feedback, full.steps.map { it.text }), schema)
        return parseResponse(reply)
    }

    companion object {
        const val MAX_NOTE = 500

        fun prompt(title: String, note: String, feedback: String, steps: List<String>): String = buildString {
            append("我刚照菜谱做完「${title.trim()}」，下面是我做完的感受。请把它整理进这道菜的批注里，下次做的时候看。\n")
            append("要求：\n")
            append("1. 把感受变成下次怎么做的提醒，比如「偏咸」→「生抽少放一点」，「鸡腿多炖了 5 分钟才熟」→「第 4 步：鸡腿多炖 5 分钟」。\n")
            append("2. 和某一步有关的，行首写「第 N 步：」（N 是下面做法的序号）；整道菜的写在前面，不加序号。\n")
            append("3. 原来的批注要保留；新感受和原来的说的是一回事就合成一条，说法相反的以新感受为准。\n")
            append("4. 每条一行，一句短话，不加序号和符号；整份批注不超过 ${MAX_NOTE / 2} 字。感受里只是夸好吃、没有要改的，就照原来的批注不动。\n")
            append("5. summary 用一句话说这次改了什么，比如「加了一条：生抽少放一点」。\n\n")
            append("原来的批注：${note.trim().ifEmpty { "（没有）" }}\n")
            append("这次的感受：${feedback.trim()}\n")
            val written = steps.map { it.trim() }.filter { it.isNotEmpty() }
            if (written.isNotEmpty()) {
                append("做法：\n")
                written.forEachIndexed { i, text -> append("${i + 1}. ${text.take(80)}\n") }
            }
        }

        private val schema
            get() = AiSchema.obj(
                "note" to AiSchema.string(),
                "summary" to AiSchema.string(),
                required = listOf("note", "summary"),
            )

        fun parseResponse(reply: JSONObject): NoteUpdate {
            if (!reply.has("note")) throw ImportException("AI 返回的格式不对，请重试")
            val note = reply.optString("note").takeIf { it != "null" }.orEmpty()
                .lines().map { it.trim().removePrefix("- ").removePrefix("• ").trim() }.filter { it.isNotEmpty() }
                .joinToString("\n").take(MAX_NOTE)
            val summary = reply.optString("summary").trim().takeIf { it != "null" }.orEmpty()
            return NoteUpdate(note, summary)
        }
    }
}
