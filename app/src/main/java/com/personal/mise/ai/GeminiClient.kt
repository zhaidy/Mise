package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

/**
 * 调用 Gemini，要求按 responseSchema 返回 JSON。
 * 免费额度用完（429）、模型下线（404）或太忙（500、503）时抛 [AiUnavailableException]，
 * 由 [SwitchableAiClient] 换下一个模型。当天额度用完的模型记下来，额度重置前不再去试。
 */
class GeminiClient(private val settings: AiSettings, private val usage: GeminiUsage, private val http: AiHttp) : AiProviderClient {

    override suspend fun generate(model: String, prompt: String, schema: JSONObject, images: List<ByteArray>): AiReply = withContext(Dispatchers.IO) {
        val key = settings.key(AiProvider.GEMINI)
        if (key.isBlank()) throw ImportException(NO_KEY)
        val now = System.currentTimeMillis()
        // 当天额度已经用完的模型，到太平洋时间零点额度重置前不再去试
        if (usage.exhaustedUntil(model, now) != null) throw AiUnavailableException(DAILY_QUOTA_USED)

        val request = Request.Builder()
            .url("$ENDPOINT/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(requestBody(prompt, schema, images).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val reply = http.execute(request, "Gemini")
        if (reply.isSuccessful) {
            usage.recordSuccess(model)
            return@withContext AiReply(replyJson(reply.body), displayName(model))
        }
        when (reply.code) {
            429 -> if (isDailyQuota(reply.body)) {
                usage.markExhausted(model, now)
                throw AiUnavailableException(DAILY_QUOTA_USED)
            } else {
                throw AiUnavailableException("请求太频繁")
            }
            404 -> throw AiUnavailableException("暂时不能用，可能下线了")
            500, 503 -> throw AiUnavailableException("服务器太忙")
            else -> throw ImportException(errorMessage(reply.code, reply.body))
        }
    }

    companion object {
        const val NO_KEY = "还没有填写 Gemini API Key"
        private const val DAILY_QUOTA_USED = "今天免费额度用完了（北京时间下午三四点重置）"

        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

        /** 可选的模型（API 名 to 显示名），也是默认的尝试顺序；免费版每个模型的额度是分开算的 */
        val MODELS = listOf(
            "gemini-3.8-flash" to "Gemini 3.8 Flash",
            "gemini-3-flash-preview" to "Gemini 3 Flash",
            "gemini-2.5-flash" to "Gemini 2.5 Flash",
            "gemini-2.5-flash-lite" to "Gemini 2.5 Flash Lite",
        )

        /** 模型的显示名，不在列表里的就用 API 名 */
        fun displayName(model: String): String = MODELS.firstOrNull { it.first == model }?.second ?: model

        /** 429 是不是当天的额度用完了（而不只是每分钟请求太多），Google 会在 quotaId 里写 PerDay */
        fun isDailyQuota(body: String): Boolean = body.contains("PerDay")

        /** 免费额度在太平洋时间零点重置 */
        private val QUOTA_ZONE = ZoneId.of("America/Los_Angeles")

        fun nextQuotaReset(nowMillis: Long): Long {
            val today = Instant.ofEpochMilli(nowMillis).atZone(QUOTA_ZONE).toLocalDate()
            return today.plusDays(1).atStartOfDay(QUOTA_ZONE).toInstant().toEpochMilli()
        }

        /** 免费额度算在哪一天（太平洋时间的日期，比如 2026-10-10） */
        fun quotaDay(nowMillis: Long): String = Instant.ofEpochMilli(nowMillis).atZone(QUOTA_ZONE).toLocalDate().toString()

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
                else -> "Gemini 出错了（HTTP $code）${detail?.let { "：$it" }.orEmpty()}"
            }
        }
    }
}

