package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * 调用 Claude 的 Messages API，用 output_config.format 按 JSON Schema 返回 JSON。
 * effort 用 medium，分调料碗、估热量这种要琢磨的活也想得够；Haiku 和 Sonnet 都不接受改 temperature，不设。
 */
class ClaudeClient(private val settings: AiSettings, private val http: AiHttp, private val usage: AiUsage) : AiProviderClient, AiChatProvider {

    override suspend fun generate(model: String, prompt: String, schema: JSONObject, images: List<ByteArray>): AiReply = withContext(Dispatchers.IO) {
        val key = settings.key(AiProvider.ANTHROPIC)
        if (key.isBlank()) throw ImportException("还没有填写 Claude API Key")

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", key)
            .header("anthropic-version", API_VERSION)
            .post(requestBody(model, prompt, schema, images).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val reply = http.execute(request, "Claude")
        if (!reply.isSuccessful) {
            // 余额不足记下来，AI 设置里在 Claude 那几行显示；查不到余额，只能靠这次的报错
            if (reply.code == 402) usage.markNoBalance(AiProvider.ANTHROPIC, key)
            throw error(reply.code, errorDetail(reply.body))
        }
        val json = replyJson(reply.body)
        usage.recordSuccess(model)
        usage.clearNoBalance(AiProvider.ANTHROPIC)
        AiReply(json, displayName(model))
    }

    override suspend fun chat(model: String, system: String, turns: List<ChatTurn>, onText: (String) -> Unit): String {
        val key = settings.key(AiProvider.ANTHROPIC)
        if (key.isBlank()) throw ImportException("还没有填写 Claude API Key")
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", key)
            .header("anthropic-version", API_VERSION)
            .post(chatBody(model, system, turns).toString().toRequestBody("application/json".toMediaType()))
            .build()
        var stop: String? = null
        val reply = http.stream(request, "Claude") { line ->
            val event = ChatTurns.sseData(line)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@stream
            streamError(event)?.let { throw it }
            streamText(event)?.let(onText)
            event.optJSONObject("delta")?.optString("stop_reason")?.takeIf { it.isNotEmpty() && it != "null" }?.let { stop = it }
        }
        if (!reply.isSuccessful) {
            if (reply.code == 402) usage.markNoBalance(AiProvider.ANTHROPIC, key)
            throw error(reply.code, errorDetail(reply.body))
        }
        when (stop) {
            "max_tokens" -> throw ImportException("Claude 的回复太长被截断了")
            "refusal" -> throw ImportException("Claude 拒绝回答这次请求")
        }
        usage.recordSuccess(model)
        usage.clearNoBalance(AiProvider.ANTHROPIC)
        return displayName(model)
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        const val NO_BALANCE = "余额不足，请到 platform.claude.com 充值"

        /** 可选的模型（API 名 to 显示名），都能看图 */
        val MODELS = listOf(
            "claude-haiku-5-5" to "Claude Haiku 5.5",
            "claude-sonnet-5-5" to "Claude Sonnet 5.5",
        )

        fun displayName(model: String): String = MODELS.firstOrNull { it.first == model }?.second ?: modelDisplayName(model)

        fun requestBody(model: String, prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): JSONObject = JSONObject()
            .put("model", model)
            .put("max_tokens", 16000)
            .put(
                "output_config",
                JSONObject()
                    .put("effort", "medium")
                    .put("format", JSONObject().put("type", "json_schema").put("schema", schema)),
            )
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content(prompt, images))))

        /** 聊天用的流式请求；聊天要回得快，effort 用 low */
        fun chatBody(model: String, system: String, turns: List<ChatTurn>): JSONObject = JSONObject()
            .put("model", model)
            .put("max_tokens", 8000)
            .put("stream", true)
            .put("output_config", JSONObject().put("effort", "low"))
            .put("system", system)
            .put(
                "messages",
                JSONArray(turns.map { JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text) }),
            )

        /** 流里一个事件带的文字，只认 text_delta；思考块之类的不要 */
        fun streamText(event: JSONObject): String? {
            if (event.optString("type") != "content_block_delta") return null
            val delta = event.optJSONObject("delta") ?: return null
            return if (delta.optString("type") == "text_delta") delta.optString("text") else null
        }

        /** 流到一半服务器发来的错误事件，比如 overloaded_error */
        fun streamError(event: JSONObject): ImportException? {
            if (event.optString("type") != "error") return null
            val error = event.optJSONObject("error")
            return when (error?.optString("type")) {
                "overloaded_error" -> AiUnavailableException("服务器繁忙")
                "rate_limit_error" -> AiUnavailableException("请求太频繁或额度用完了")
                else -> ImportException("Claude 出错了：${error?.optString("message").orEmpty()}")
            }
        }

        /** 图片在前、文字在后 */
        private fun content(prompt: String, images: List<ByteArray>): JSONArray {
            val parts = JSONArray()
            images.forEach { bytes ->
                parts.put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject().put("type", "base64").put("media_type", "image/jpeg")
                            .put("data", Base64.getEncoder().encodeToString(bytes)),
                    ),
                )
            }
            return parts.put(JSONObject().put("type", "text").put("text", prompt))
        }

        /** 从响应里取出模型回复的 JSON 对象；开着思考时前面可能有 thinking 块，取第一个 text 块 */
        fun replyJson(body: String): JSONObject {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: throw ImportException("Claude 没有返回内容，请重试")
            when (json.optString("stop_reason")) {
                "max_tokens" -> throw ImportException("Claude 的回复太长被截断了，请重试")
                "refusal" -> throw ImportException("Claude 拒绝回答这次请求")
            }
            val blocks = json.optJSONArray("content") ?: JSONArray()
            val text = (0 until blocks.length()).asSequence()
                .mapNotNull { blocks.optJSONObject(it) }
                .firstOrNull { it.optString("type") == "text" }
                ?.optString("text").orEmpty().trim()
            if (text.isBlank()) throw ImportException("Claude 没有返回内容，请重试")
            return runCatching { JSONObject(text) }.getOrNull() ?: throw ImportException("Claude 返回的格式不对，请重试")
        }

        /** 错误响应里的说明 */
        fun errorDetail(body: String): String? =
            runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()

        fun error(code: Int, detail: String?): ImportException = when (code) {
            401 -> ImportException("Claude API Key 不对，请检查")
            402 -> AiUnavailableException(NO_BALANCE)
            404 -> AiUnavailableException("暂时不能用，可能下线了或这个 Key 没有它的权限")
            429 -> AiUnavailableException("请求太频繁或额度用完了")
            500, 503, 529 -> AiUnavailableException("服务器繁忙")
            else -> ImportException("Claude 出错了（HTTP $code）${detail?.let { "：$it" }.orEmpty()}")
        }
    }
}
