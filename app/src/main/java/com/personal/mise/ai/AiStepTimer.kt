package com.personal.mise.ai

import com.personal.mise.data.RecipeFull
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import kotlin.math.roundToInt

/** 一步大约要多久，和这段时间能不能走开（炖着、腌着不用一直盯） */
data class StepTime(val seconds: Int, val handsOff: Boolean)

/**
 * AI 估的每一步时长，按步骤文字对应：步骤改了字，这一步的估算就不算数了，回到按文字猜。
 * 存在菜谱的 stepTimes 里（JSON），会同步。
 */
data class StepTimes(val byText: Map<String, StepTime>) {
    fun of(text: String): StepTime? = byText[key(text)]

    /** 这些步骤里还有没估过的 */
    fun missing(steps: List<String>): Boolean = steps.any { it.isNotBlank() && of(it) == null }

    fun toJson(): JSONObject = JSONObject().apply {
        byText.forEach { (text, t) -> put(text, JSONObject().put("seconds", t.seconds).put("handsOff", t.handsOff)) }
    }

    companion object {
        fun key(text: String): String = text.trim()

        /** 格式不对时返回 null，不影响菜谱本身 */
        fun fromJson(json: String?): StepTimes? = json?.takeIf { it.isNotBlank() }?.let {
            runCatching {
                val obj = JSONObject(it)
                StepTimes(obj.keys().asSequence().mapNotNull { text ->
                    val t = obj.optJSONObject(text) ?: return@mapNotNull null
                    val seconds = t.optInt("seconds", 0).takeIf { s -> s > 0 } ?: return@mapNotNull null
                    text to StepTime(seconds, t.optBoolean("handsOff", false))
                }.toMap())
            }.getOrNull()
        }
    }
}

/** 让 AI 估每一步要多久、能不能走开，多菜时间线按它排 */
class AiStepTimer(private val ai: AiClient) {

    /** 返回按步骤文字对应的估算；空步骤不发 */
    suspend fun estimate(title: String, steps: List<String>): StepTimes {
        if (steps.none { it.isNotBlank() }) throw ImportException("还没有步骤，没法估时间")
        return parseResponse(ai.generate(prompt(title, steps), schema), steps)
    }

    suspend fun estimate(full: RecipeFull): StepTimes = estimate(full.recipe.title, full.steps.map { it.text })

    companion object {
        /** 一步最多算这么久，免得 AI 把「腌一夜」算进今天的时间线 */
        const val MAX_MINUTES = 240.0

        private const val PROMPT = "下面是一道中文菜的做法。请估算一个熟练的家庭厨师做每一步大约要几分钟，" +
            "并判断这段时间里能不能走开去做别的事。\n" +
            "要求：\n" +
            "1. step 填步骤序号（从 1 开始），minutes 填这一步从开始到做完大约几分钟（可以有小数），" +
            "包括洗、切、腌、等水开、炖煮的时间；步骤里写了时长的照写的算。\n" +
            "2. handsOff：这一步大部分时间可以走开、不用一直盯着（炖、焖、蒸、烤、腌、泡发、静置、煮饭）填 true；" +
            "要一直动手或盯着（洗切、炒、煎、炸、调味、收汁、装盘）填 false。\n" +
            "3. 要腌一夜、冷藏几小时这种提前做的，最多按 ${MAX_MINUTES} 分钟算。\n\n"

        fun prompt(title: String, steps: List<String>): String = buildString {
            append(PROMPT)
            if (title.isNotBlank()) append("菜名：${title.trim()}\n")
            append("做法：\n")
            steps.forEachIndexed { i, text ->
                if (text.isNotBlank()) append("${i + 1}. ${text.trim().replace('\n', ' ')}\n")
            }
        }

        private val schema
            get() = AiSchema.obj(
                "steps" to AiSchema.array(
                    AiSchema.obj(
                        "step" to AiSchema.integer(),
                        "minutes" to AiSchema.number(),
                        "handsOff" to AiSchema.boolean(),
                        required = listOf("step", "minutes", "handsOff"),
                    ),
                ),
                required = listOf("steps"),
            )

        /** 序号不对、时长不是正数的跳过；一步只算一次 */
        fun parseResponse(reply: JSONObject, steps: List<String>): StepTimes {
            val arr = reply.optJSONArray("steps") ?: throw ImportException("AI 返回的格式不对，请重试")
            val out = linkedMapOf<String, StepTime>()
            val seen = mutableSetOf<Int>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val index = item.optDouble("step", Double.NaN).takeIf { !it.isNaN() }?.toInt()?.minus(1) ?: continue
                if (index !in steps.indices || steps[index].isBlank() || !seen.add(index)) continue
                val minutes = item.optDouble("minutes", Double.NaN).takeIf { !it.isNaN() && !it.isInfinite() && it > 0 } ?: continue
                val seconds = (minutes.coerceAtMost(MAX_MINUTES) * 60).roundToInt().coerceAtLeast(30)
                out[StepTimes.key(steps[index])] = StepTime(seconds, item.optBoolean("handsOff", false))
            }
            if (out.isEmpty()) throw ImportException("AI 没有估出时间，请重试")
            return StepTimes(out)
        }
    }
}
