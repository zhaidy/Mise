package com.personal.mise.ai

import com.personal.mise.importer.ImportException

/** source：拆分时这一步来自原文第几步（从 1 开始），整理笔记时为空 */
data class AiStep(val text: String, val timerMinutes: Double?, val source: Int? = null)

/** 把下厨房上挤成一段的做法，交给 Gemini 拆成一步一步的清晰步骤。 */
class GeminiStepSplitter(private val gemini: GeminiClient) {

    suspend fun split(rawSteps: List<String>): List<AiStep> {
        val text = numbered(rawSteps)
        if (text.isEmpty()) throw ImportException("没有可以拆分的步骤文字")
        return parseResponse(gemini.generate(PROMPT + text, schema))
    }

    companion object {
        private const val PROMPT = "下面是一份中文菜谱的做法，每行开头是原来的步骤编号，可能所有操作都挤在一两段里。" +
            "请把它拆成清晰的、按先后顺序的步骤：每一步只做一件主要的事（例如切配、腌制、焯水、炒制、调味、收汁、装盘分开）。" +
            "原来已经只做一件事的步骤保持原样。" +
            "只整理原文，不要增加原文没有的食材、调料、用量或时间，保留原文的数字和用量。" +
            "如果原文明确写了某一步需要等待或加热的时长（如「焖10分钟」），把分钟数填进 timerMinutes，" +
            "多个时间或没有明确时长时留空。每步的 text 用简洁的一两句话，不要带序号。" +
            "source 填这一步的内容来自原文第几步（来自好几步时填最前面那一步）。\n\n做法原文：\n"

        private val schema
            get() = GeminiClient.obj(
                "steps" to GeminiClient.array(
                    GeminiClient.stepSchema.apply {
                        getJSONObject("properties").put("source", GeminiClient.integer(nullable = true))
                    },
                ),
                required = listOf("steps"),
            )

        /** 空步骤不发给 Gemini，但编号保持原来的位置，source 才对得上 */
        fun numbered(rawSteps: List<String>): String = rawSteps.mapIndexedNotNull { i, s ->
            s.trim().takeIf { it.isNotEmpty() }?.let { "${i + 1}. ${it.replace('\n', ' ')}" }
        }.joinToString("\n")

        fun parseResponse(body: String): List<AiStep> {
            val steps = GeminiClient.replyJson(body).optJSONArray("steps")
                ?: throw ImportException("Gemini 返回的格式不对，请重试")
            return GeminiClient.parseSteps(steps).ifEmpty { throw ImportException("Gemini 没有拆出步骤，请重试") }
        }

        /**
         * 原来第 i 步的图片、计时该放到拆分后的哪一步：来自这一步的第一个新步骤。
         * 原来只有一步时直接放到第一步。返回 null 表示有带图的原步骤找不到对应的新步骤（图会丢）。
         */
        fun carryOver(oldHasImage: List<Boolean>, steps: List<AiStep>): Map<Int, Int>? {
            if (oldHasImage.size <= 1) return if (steps.isEmpty()) emptyMap() else mapOf(0 to 0)
            val map = mutableMapOf<Int, Int>()
            oldHasImage.indices.forEach { i ->
                val j = steps.indexOfFirst { it.source == i + 1 }
                if (j >= 0) map[i] = j else if (oldHasImage[i]) return null
            }
            return map
        }
    }
}
