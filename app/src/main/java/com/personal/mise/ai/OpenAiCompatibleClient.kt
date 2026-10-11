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
 * OpenAI 格式（chat/completions）的服务商，DeepSeek 和 Agnes 都是这种。
 * 它们不一定认 JSON Schema，所以把 schema 写进系统提示，让模型照着返回。
 */
abstract class OpenAiCompatibleClient(
    private val provider: AiProvider,
    private val endpoint: String,
    private val settings: AiSettings,
    private val http: AiHttp,
) : AiProviderClient {
    /** 是否用 JSON 模式（response_format: json_object），服务商支持才开 */
    protected abstract val jsonMode: Boolean

    /** 是否在请求里关掉思考模式（thinking: disabled），服务商认这个参数才开 */
    protected open val disableThinking = false

    /** 出错时的异常；额度、余额、服务器忙这类换个模型能解决的用 [AiUnavailableException] */
    protected abstract fun error(code: Int, detail: String?): ImportException

    override suspend fun generate(model: String, prompt: String, schema: JSONObject, images: List<ByteArray>): AiReply = withContext(Dispatchers.IO) {
        val key = settings.key(provider)
        if (key.isBlank()) throw ImportException("还没有填写 ${provider.label} API Key")

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .post(requestBody(model, prompt, schema, images, jsonMode, disableThinking).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val reply = http.execute(request, provider.label)
        if (!reply.isSuccessful) throw error(reply.code, errorDetail(reply.body))
        AiReply(replyJson(provider.label, reply.body), modelDisplayName(replyModel(reply.body, model)))
    }

    companion object {
        fun requestBody(
            model: String,
            prompt: String,
            schema: JSONObject,
            images: List<ByteArray> = emptyList(),
            jsonMode: Boolean = true,
            disableThinking: Boolean = false,
        ): JSONObject = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("max_tokens", 8000)
            .apply { if (disableThinking) put("thinking", JSONObject().put("type", "disabled")) }
            .apply { if (jsonMode) put("response_format", JSONObject().put("type", "json_object")) }
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

        /** 响应里写的实际模型，没有就用请求的模型 */
        fun replyModel(body: String, requested: String): String =
            runCatching { JSONObject(body).optString("model") }.getOrNull()?.takeIf { it.isNotBlank() } ?: requested

        /**
         * 从 chat/completions 的响应里取出模型回复的 JSON 对象；[name] 是服务商名字，用在出错提示里。
         * 不开 JSON 模式时模型可能在 JSON 外面包一层 ```json，取第一个 { 到最后一个 } 之间的部分。
         */
        fun replyJson(name: String, body: String): JSONObject {
            val choice = runCatching { JSONObject(body).getJSONArray("choices").getJSONObject(0) }.getOrNull()
                ?: throw ImportException("$name 没有返回内容，请重试")
            if (choice.optString("finish_reason") == "length") throw ImportException("$name 的回复太长被截断了，请重试")
            val reply = choice.optJSONObject("message")?.optString("content").orEmpty().trim()
            if (reply.isBlank()) throw ImportException("$name 没有返回内容，请重试")
            return runCatching { JSONObject(reply) }.getOrNull()
                ?: runCatching { JSONObject(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1)) }.getOrNull()
                ?: throw ImportException("$name 返回的格式不对，请重试")
        }

        /** 错误响应里的说明 */
        fun errorDetail(body: String): String? =
            runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
    }
}
