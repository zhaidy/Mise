package com.personal.mise.ai

import com.personal.mise.importer.ImportException

data class AiStep(val text: String, val timerMinutes: Double?)

/** 把下厨房上挤成一段的做法，交给 Gemini 拆成一步一步的清晰步骤。 */
class GeminiStepSplitter(private val gemini: GeminiClient) {

    suspend fun split(rawSteps: List<String>): List<AiStep> {
        val text = rawSteps.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        if (text.isEmpty()) throw ImportException("没有可以拆分的步骤文字")
        return parseResponse(gemini.generate(PROMPT + text, schema))
    }

    companion object {
        private const val PROMPT = "下面是一份中文菜谱的做法，可能所有操作都挤在一两段里。" +
            "请把它拆成清晰的、按先后顺序的步骤：每一步只做一件主要的事（例如切配、腌制、焯水、炒制、调味、收汁、装盘分开）。" +
            "只整理原文，不要增加原文没有的食材、调料、用量或时间，保留原文的数字和用量。" +
            "如果原文明确写了某一步需要等待或加热的时长（如「焖10分钟」），把分钟数填进 timerMinutes，" +
            "多个时间或没有明确时长时留空。每步的 text 用简洁的一两句话，不要带序号。\n\n做法原文：\n"

        private val schema
            get() = GeminiClient.obj("steps" to GeminiClient.array(GeminiClient.stepSchema), required = listOf("steps"))

        fun parseResponse(body: String): List<AiStep> {
            val steps = GeminiClient.replyJson(body).optJSONArray("steps")
                ?: throw ImportException("Gemini 返回的格式不对，请重试")
            return GeminiClient.parseSteps(steps).ifEmpty { throw ImportException("Gemini 没有拆出步骤，请重试") }
        }
    }
}
