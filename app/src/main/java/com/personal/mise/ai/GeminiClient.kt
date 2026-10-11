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

/** 调用 Gemini，要求按 responseSchema 返回 JSON。 */
class GeminiClient(private val settings: AiSettings) : AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(prompt: String, schema: JSONObject, images: List<ByteArray>): JSONObject = withContext(Dispatchers.IO) {
        val key = settings.key(AiProvider.GEMINI)
        if (key.isBlank()) throw ImportException(NO_KEY)

        val request = Request.Builder()
            .url("$ENDPOINT/$MODEL:generateContent")
            .header("x-goog-api-key", key)
            .post(requestBody(prompt, schema, images).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = try {
            client.newCall(request).execute().use { response ->
                val s = response.body.string()
                if (!response.isSuccessful) throw ImportException(errorMessage(response.code, s))
                s
            }
        } catch (e: IOException) {
            throw ImportException("连接 Gemini 失败：${networkErrorDetail(e)}")
        }
        replyJson(body)
    }

    companion object {
        const val NO_KEY = "还没有填写 Gemini API Key"

        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-3.8-flash"

        fun requestBody(prompt: String, schema: JSONObject, images: List<ByteArray> = emptyList()): JSONObject = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts(prompt, images))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", toGeminiSchema(schema)),
            )

        /** 图片在前、文字在后 */
        private fun parts(prompt: String, images: List<ByteArray>): JSONArray {
            val parts = JSONArray()
            images.forEach { bytes ->
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject().put("mimeType", "image/jpeg").put("data", Base64.getEncoder().encodeToString(bytes)),
                    ),
                )
            }
            return parts.put(JSONObject().put("text", prompt))
        }

        /**
         * [AiSchema] 写的是标准 JSON Schema，Gemini 的 responseSchema 是另一种写法：
         * 类型名大写、可空写 nullable、不认 additionalProperties。
         */
        fun toGeminiSchema(schema: JSONObject): JSONObject {
            schema.optJSONArray("anyOf")?.let { anyOf ->
                val types = (0 until anyOf.length()).map { anyOf.getJSONObject(it) }
                val nonNull = types.single { it.optString("type") != "null" }
                return toGeminiSchema(nonNull).put("nullable", true)
            }
            val out = JSONObject().put("type", schema.getString("type").uppercase())
            schema.optJSONObject("properties")?.let { props ->
                out.put("properties", JSONObject().apply { props.keys().forEach { put(it, toGeminiSchema(props.getJSONObject(it))) } })
            }
            schema.optJSONArray("required")?.let { out.put("required", it) }
            schema.optJSONObject("items")?.let { out.put("items", toGeminiSchema(it)) }
            return out
        }

        /** 从 generateContent 的响应里取出模型回复的 JSON 对象 */
        fun replyJson(body: String): JSONObject {
            val reply = runCatching {
                JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }.getOrNull() ?: throw ImportException("Gemini 没有返回内容，请重试")
            return runCatching { JSONObject(reply) }.getOrNull()
                ?: throw ImportException("Gemini 返回的格式不对，请重试")
        }

        private fun errorMessage(code: Int, body: String): String {
            val detail = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
            return when (code) {
                400, 403 -> "Gemini 拒绝了请求，请检查 API Key 是否正确${detail?.let { "（$it）" }.orEmpty()}"
                429 -> "Gemini 请求太频繁或额度用完了，稍后再试"
                else -> "Gemini 出错了（HTTP $code）${detail?.let { "：$it" }.orEmpty()}"
            }
        }
    }
}
