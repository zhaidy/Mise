package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import org.json.JSONArray
import org.json.JSONObject

/** 让大模型按 JSON Schema 返回 JSON；Gemini、DeepSeek 各有一个实现 */
interface AiClient {
    /** 返回模型回复里的 JSON 对象，出错时抛 ImportException；images 是一起发给模型看的 JPEG 图片 */
    suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): JSONObject
}

/** 某一家服务商的回复：模型返回的 JSON 和实际用的模型（显示名） */
class AiReply(val json: JSONObject, val model: String)

/** 某一家服务商的客户端，回复里带上实际用的是哪个模型 */
interface AiProviderClient {
    suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): AiReply
}

/** 把 API 返回的模型名变成好读的名字，比如 deepseek-flash → DeepSeek Flash */
fun modelDisplayName(id: String): String {
    val words = id.split('-').filter { it.isNotEmpty() && !(it.length == 8 && it.all(Char::isDigit)) }
    val out = mutableListOf<String>()
    for (w in words) {
        val last = out.lastOrNull()
        if (w.all(Char::isDigit) && last != null && last.first().isDigit() && '.' !in last) out[out.size - 1] = "$last.$w"
        else out += when (w) {
            "deepseek" -> "DeepSeek"
            else -> w.replaceFirstChar { it.uppercase() }
        }
    }
    return out.joinToString(" ").ifEmpty { id }
}

/**
 * 按设置里选的服务商转给对应的客户端，每次调用时才读设置，切换后立即生效。
 * 选的是 Gemini 且勾选的模型都用完时，按设置改用 DeepSeek。
 * 每次成功后用 [onModelUsed] 告诉界面这次用的是哪个模型。
 */
class SwitchableAiClient(
    private val settings: AiSettings,
    private val gemini: AiProviderClient,
    private val deepseek: AiProviderClient,
    /** 每次调用成功后调用，参数是给用户看的提示，比如「AI 模型：Gemini 3.8 Flash」 */
    private val onModelUsed: (String) -> Unit = {},
) : AiClient {
    override suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray>): JSONObject {
        val reply = when (settings.provider) {
            AiProvider.GEMINI -> try {
                gemini.generate(prompt, schema, images)
            } catch (e: GeminiQuotaException) {
                if (!settings.deepSeekFallback) throw e
                if (settings.key(AiProvider.DEEPSEEK).isBlank()) throw ImportException("${e.message}。在 AI 设置里填好 DeepSeek Key 后会自动改用 DeepSeek")
                val fallback = deepseek.generate(prompt, schema, images)
                onModelUsed("Gemini 免费额度用完了，这次用的是 ${fallback.model}")
                return fallback.json
            }
            AiProvider.DEEPSEEK -> deepseek.generate(prompt, schema, images)
        }
        onModelUsed("AI 模型：${reply.model}")
        return reply.json
    }
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

/** 标准 JSON Schema 的小工具；DeepSeek 写进提示里，Gemini 由 [GeminiClient.toGeminiSchema] 转成它自己的格式 */
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
