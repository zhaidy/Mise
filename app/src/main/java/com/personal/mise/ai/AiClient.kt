package com.personal.mise.ai

import org.json.JSONArray
import org.json.JSONObject

/** 让大模型按 JSON Schema 返回 JSON；Gemini、Claude、DeepSeek 各有一个实现 */
interface AiClient {
    /** 返回模型回复里的 JSON 对象，出错时抛 ImportException；images 是一起发给模型看的 JPEG 图片 */
    suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): JSONObject
}

/** 按设置里选的服务商转给对应的客户端，每次调用时才读设置，切换后立即生效 */
class SwitchableAiClient(
    private val settings: AiSettings,
    private val gemini: AiClient,
    private val claude: AiClient,
    private val deepseek: AiClient,
) : AiClient {
    override suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray>): JSONObject =
        when (settings.provider) {
            AiProvider.GEMINI -> gemini
            AiProvider.CLAUDE -> claude
            AiProvider.DEEPSEEK -> deepseek
        }.generate(prompt, schema, images)
}

/**
 * 网络出错时的说明：OkHttp 只在最外层写 "Failed to connect to …"，
 * 真正原因（拒绝连接、网络不可达、超时）在 cause 和 suppressed 里，一起列出来方便排查。
 */
fun networkErrorDetail(e: Throwable): String {
    val seen = mutableSetOf<Throwable>()
    val parts = mutableListOf<String>()
    fun walk(t: Throwable?) {
        if (t == null || !seen.add(t)) return
        parts += t.message?.takeIf { it.isNotBlank() }?.let { "${t.javaClass.simpleName}: $it" } ?: t.javaClass.simpleName
        t.suppressed.forEach { walk(it) }
        walk(t.cause)
    }
    walk(e)
    return parts.distinct().joinToString("\n")
}

/** 标准 JSON Schema 的小工具；Claude 直接用，Gemini 由 [GeminiClient.toGeminiSchema] 转成它自己的格式 */
object AiSchema {
    fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject =
        JSONObject().put("type", "object")
            .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
            .put("required", JSONArray(required))
            .put("additionalProperties", false)

    fun array(items: JSONObject): JSONObject = JSONObject().put("type", "array").put("items", items)
    fun string(nullable: Boolean = false): JSONObject = scalar("string", nullable)
    fun integer(nullable: Boolean = false): JSONObject = scalar("integer", nullable)
    fun number(nullable: Boolean = false): JSONObject = scalar("number", nullable)

    private fun scalar(type: String, nullable: Boolean): JSONObject {
        val schema = JSONObject().put("type", type)
        return if (!nullable) schema
        else JSONObject().put("anyOf", JSONArray().put(schema).put(JSONObject().put("type", "null")))
    }

    /** 做法里每一步的 schema，拆分步骤和整理笔记共用 */
    val stepSchema: JSONObject get() = obj("text" to string(), "timerMinutes" to number(nullable = true), required = listOf("text"))

    /** 解析 [stepSchema] 格式的步骤数组 */
    fun parseSteps(steps: JSONArray?): List<AiStep> {
        if (steps == null) return emptyList()
        return (0 until steps.length()).mapNotNull { i ->
            val item = steps.optJSONObject(i) ?: return@mapNotNull null
            val text = item.optString("text").trim()
            if (text.isEmpty()) null
            else AiStep(
                text,
                item.optDouble("timerMinutes", Double.NaN).takeIf { !it.isNaN() && it > 0 },
                item.optInt("source", 0).takeIf { it > 0 },
            )
        }
    }
}
