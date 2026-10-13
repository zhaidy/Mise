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
 * 都是照格式整理的活，effort 调到 low，少想一会、回得快也省钱；Haiku 不接受改 temperature，不设。
 */
class ClaudeClient(private val settings: AiSettings, private val http: AiHttp, private val usage: AiUsage) : AiProviderClient {

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
        if (!reply.isSuccessful) throw error(reply.code, errorDetail(reply.body))
        val json = replyJson(reply.body)
        usage.recordSuccess(model)
        AiReply(json, displayName(model))
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"

        /** 可选的模型（API 名 to 显示名），都能看图 */
        val MODELS = listOf("claude-haiku-5-5" to "Claude Haiku 5.5")

        fun displayName(model: String): String = MODELS.firstOrNull { it.first == model }?.second ?: modelDisplayName(model)

        fun requestBody(model: String, prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): JSONObject = JSONObject()
            .put("model", model)
            .put("max_tokens", 16000)
            .put(
                "output_config",
                JSONObject()
                    .put("effort", "low")
                    .put("format", JSONObject().put("type", "json_schema").put("schema", schema)),
            )
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content(prompt, images))))

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
            402 -> AiUnavailableException("余额不足，请到 platform.claude.com 充值")
            404 -> AiUnavailableException("暂时不能用，可能下线了或这个 Key 没有它的权限")
            429 -> AiUnavailableException("请求太频繁或额度用完了")
            500, 503, 529 -> AiUnavailableException("服务器繁忙")
            else -> ImportException("Claude 出错了（HTTP $code）${detail?.let { "：$it" }.orEmpty()}")
        }
    }
}
