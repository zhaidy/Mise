package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import org.json.JSONArray
import org.json.JSONObject

/** 让大模型按 JSON Schema 返回 JSON；按设置里的模型顺序转给 Gemini、Agnes、DeepSeek、Claude */
interface AiClient {
    /** 返回模型回复里的 JSON 对象，出错时抛 ImportException；images 是一起发给模型看的 JPEG 图片 */
    suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): JSONObject
}

/** 某一家服务商的回复：模型返回的 JSON 和实际用的模型（显示名） */
class AiReply(val json: JSONObject, val model: String)

/** 某一家服务商的客户端，回复里带上实际用的是哪个模型 */
interface AiProviderClient {
    /** [model] 是 API 名；额度用完、余额不足这类换个模型能解决的问题抛 [AiUnavailableException] */
    suspend fun generate(model: String, prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): AiReply
}

/** 这个模型这次用不了（额度用完、余额不足、模型下线、服务器忙），可以换下一个模型；消息是简短的原因，比如「今天免费额度用完了」 */
class AiUnavailableException(reason: String) : ImportException(reason)

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
 * 按设置里勾选的模型从上往下试，每次调用时才读设置，改了立即生效。
 * 没填 Key 的跳过；额度用完、余额不足、服务器忙就换下一个；连不上某一家时，这家剩下的模型也跳过。
 * Key 不对、回复格式不对这类问题直接报错，不换模型，免得把问题悄悄盖过去。
 * 每次成功后用 [onModelUsed] 告诉界面这次用的是哪个模型。
 */
class SwitchableAiClient(
    private val settings: AiSettings,
    private val clients: Map<AiProvider, AiProviderClient>,
    /** 每次调用成功后调用，参数是给用户看的提示，比如「AI 模型：Gemini 3.8 Flash」 */
    private val onModelUsed: (String) -> Unit = {},
) : AiClient {
    override suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray>): JSONObject {
        val candidates = settings.models.filter { settings.key(it.provider).isNotBlank() }
        if (candidates.isEmpty()) throw ImportException(NO_KEY)
        // 换掉的模型：给提示用的简短原因，和报错用的详细原因
        val skipped = mutableListOf<String>()
        val details = mutableListOf<String>()
        val unreachable = mutableSetOf<AiProvider>()
        for (model in candidates) {
            if (model.provider in unreachable) continue
            val reply = try {
                clients.getValue(model.provider).generate(model.id, prompt, schema, images)
            } catch (e: AiUnavailableException) {
                skipped += "${model.label} ${e.message}"
                details += "${model.label}：${e.message}"
                continue
            } catch (e: AiNetworkException) {
                unreachable += model.provider
                skipped += "连不上 ${model.provider.label}"
                details += e.message.orEmpty()
                continue
            }
            onModelUsed(if (skipped.isEmpty()) "AI 模型：${reply.model}" else "${skipped.first()}，这次用的是 ${reply.model}")
            return reply.json
        }
        throw ImportException("勾选的模型都用不了：\n" + details.joinToString("\n"))
    }

    companion object {
        const val NO_KEY = "勾选的 AI 模型都还没填 API Key，请在 AI 设置里填写"
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

/** 标准 JSON Schema 的小工具；OpenAI 格式的写进提示里，Gemini 由 [GeminiClient.toGeminiSchema] 转成它自己的格式 */
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
    fun boolean(): JSONObject = scalar("boolean", false)

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
