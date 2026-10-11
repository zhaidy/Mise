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
 * 调用 DeepSeek 的 OpenAI 格式接口。它只支持 JSON 模式（json_object），不认 JSON Schema，
 * 所以把 schema 写进系统提示，让模型照着返回。
 */
class DeepSeekClient(private val settings: AiSettings, private val http: AiHttp) : AiClient {

    override suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray>): JSONObject = withContext(Dispatchers.IO) {
        val key = settings.key(AiProvider.DEEPSEEK)
        if (key.isBlank()) throw ImportException(NO_KEY)

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $key")
            .post(requestBody(prompt, schema, images).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val reply = http.execute(request, "DeepSeek")
        if (!reply.isSuccessful) throw ImportException(errorMessage(reply.code, reply.body))
        replyJson(reply.body)
    }

    companion object {
        const val NO_KEY = "还没有填写 DeepSeek API Key"

        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val MODEL = "deepseek-flash"

        fun requestBody(prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): JSONObject = JSONObject()
            .put("model", MODEL)
            .put("temperature", 0.2)
            .put("max_tokens", 8000)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "只回复一个 JSON 对象，不要其他文字，格式符合下面的 JSON Schema：\n$schema"))
                    .put(JSONObject().put("role", "user").put("content", content(prompt, images))),
            )

        /** 没图时直接给文字；有图时图片在前、文字在后 */
        private fun content(prompt: String, images: List<ByteArray>): Any {
            if (images.isEmpty()) return prompt
            val parts = JSONArray()
            images.forEach { bytes ->
                parts.put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)),
                    ),
                )
            }
            return parts.put(JSONObject().put("type", "text").put("text", prompt))
        }

        /** 从 chat/completions 的响应里取出模型回复的 JSON 对象 */
        fun replyJson(body: String): JSONObject {
            val choice = runCatching { JSONObject(body).getJSONArray("choices").getJSONObject(0) }.getOrNull()
                ?: throw ImportException("DeepSeek 没有返回内容，请重试")
            if (choice.optString("finish_reason") == "length") throw ImportException("DeepSeek 的回复太长被截断了，请重试")
            val reply = choice.optJSONObject("message")?.optString("content").orEmpty()
            if (reply.isBlank()) throw ImportException("DeepSeek 没有返回内容，请重试")
            return runCatching { JSONObject(reply) }.getOrNull()
                ?: throw ImportException("DeepSeek 返回的格式不对，请重试")
        }

        private fun errorMessage(code: Int, body: String): String {
            val detail = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
            return when (code) {
                401 -> "DeepSeek API Key 不对，请检查"
                402 -> "DeepSeek 余额不足，请到 platform.deepseek.com 充值"
                429 -> "DeepSeek 请求太频繁，稍后再试"
                500, 503 -> "DeepSeek 服务器繁忙，稍后再试"
                else -> "DeepSeek 出错了（HTTP $code）${detail?.let { "：$it" }.orEmpty()}"
            }
        }
    }
}
