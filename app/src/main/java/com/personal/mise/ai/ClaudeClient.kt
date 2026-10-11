package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** 调用 Claude Messages API，用 output_config.format 要求按 JSON Schema 返回。 */
class ClaudeClient(private val settings: AiSettings) : AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray>): JSONObject = withContext(Dispatchers.IO) {
        val key = settings.key(AiProvider.CLAUDE)
        if (key.isBlank()) throw ImportException(NO_KEY)

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-api-key", key)
            .header("anthropic-version", API_VERSION)
            // 被安全分类器拒绝时，由服务端自动换一个模型重试
            .header("anthropic-beta", "server-side-fallback-2026-07-01")
            .post(requestBody(prompt, schema, images).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = try {
            client.newCall(request).execute().use { response ->
                val s = response.body.string()
                if (!response.isSuccessful) throw ImportException(errorMessage(response.code, s))
                s
            }
        } catch (e: IOException) {
            throw ImportException("连接 Claude 失败：${e.message ?: "请检查网络"}")
        }
        replyJson(body)
    }

    companion object {
        const val NO_KEY = "还没有填写 Claude API Key"

        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL = "claude-opus-5-5"

        fun requestBody(prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): JSONObject = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 16000)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content(prompt, images))))
            .put(
                "output_config",
                JSONObject()
                    .put("effort", "medium")
                    .put("format", JSONObject().put("type", "json_schema").put("schema", schema)),
            )
            .put("fallbacks", "default")

        /** 没有图片时 content 直接是文字；有图片时图片放在前面，文字放最后 */
        private fun content(prompt: String, images: List<ByteArray>): Any {
            if (images.isEmpty()) return prompt
            val blocks = JSONArray()
            images.forEach { bytes ->
                blocks.put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject().put("type", "base64").put("media_type", "image/jpeg")
                            .put("data", Base64.getEncoder().encodeToString(bytes)),
                    ),
                )
            }
            return blocks.put(JSONObject().put("type", "text").put("text", prompt))
        }

        /** 从 Messages API 的响应里取出模型回复的 JSON 对象 */
        fun replyJson(body: String): JSONObject {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: throw ImportException("Claude 没有返回内容，请重试")
            when (json.optString("stop_reason")) {
                "refusal" -> throw ImportException("Claude 拒绝了这次请求，请换一段文字或改用 Gemini")
                "max_tokens" -> throw ImportException("Claude 返回的内容太长被截断了，请重试")
            }
            val content = json.optJSONArray("content")
            val reply = content?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("type") == "text" }?.optString("text")
            } ?: throw ImportException("Claude 没有返回内容，请重试")
            return runCatching { JSONObject(reply) }.getOrNull()
                ?: throw ImportException("Claude 返回的格式不对，请重试")
        }

        private fun errorMessage(code: Int, body: String): String {
            val detail = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
            return when (code) {
                401 -> "Claude API Key 不对，请检查后重新填写"
                403 -> "Claude 拒绝了请求，这个 API Key 没有权限${detail?.let { "（$it）" }.orEmpty()}"
                429 -> "Claude 请求太频繁或额度用完了，稍后再试"
                529 -> "Claude 服务繁忙，稍后再试"
                else -> "Claude 出错了（HTTP $code）${detail?.let { "：$it" }.orEmpty()}"
            }
        }
    }
}
