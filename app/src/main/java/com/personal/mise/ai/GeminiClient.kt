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
import java.util.concurrent.TimeUnit

/** 调用 Gemini，要求按 responseSchema 返回 JSON。 */
class GeminiClient(private val settings: AiSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** 返回 Gemini 回复的原始响应体，用 [replyJson] 取出里面的 JSON */
    suspend fun generate(prompt: String, schema: JSONObject): String = withContext(Dispatchers.IO) {
        val key = settings.geminiKey
        if (key.isBlank()) throw ImportException(NO_KEY)

        val request = Request.Builder()
            .url("$ENDPOINT/$MODEL:generateContent")
            .header("x-goog-api-key", key)
            .post(requestBody(prompt, schema).toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val s = response.body.string()
                if (!response.isSuccessful) throw ImportException(errorMessage(response.code, s))
                s
            }
        } catch (e: IOException) {
            throw ImportException("连接 Gemini 失败：${e.message ?: "请检查网络"}")
        }
    }

    companion object {
        const val NO_KEY = "还没有填写 Gemini API Key"

        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-3.8-flash"

        fun requestBody(prompt: String, schema: JSONObject): JSONObject = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", schema),
            )

        /** 从 generateContent 的响应里取出模型回复的 JSON 对象 */
        fun replyJson(body: String): JSONObject {
            val reply = runCatching {
                JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }.getOrNull() ?: throw ImportException("Gemini 没有返回内容，请重试")
            return runCatching { JSONObject(reply) }.getOrNull()
                ?: throw ImportException("Gemini 返回的格式不对，请重试")
        }

        // responseSchema 的小工具
        fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject =
            JSONObject().put("type", "OBJECT")
                .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
                .put("required", JSONArray(required))

        fun array(items: JSONObject): JSONObject = JSONObject().put("type", "ARRAY").put("items", items)
        fun string(nullable: Boolean = false): JSONObject = JSONObject().put("type", "STRING").apply { if (nullable) put("nullable", true) }
        fun number(nullable: Boolean = false): JSONObject = JSONObject().put("type", "NUMBER").apply { if (nullable) put("nullable", true) }

        /** 做法里每一步的 schema，拆分步骤和整理笔记共用 */
        val stepSchema: JSONObject get() = obj("text" to string(), "timerMinutes" to number(nullable = true), required = listOf("text"))

        /** 解析 [stepSchema] 格式的步骤数组 */
        fun parseSteps(steps: JSONArray?): List<AiStep> {
            if (steps == null) return emptyList()
            return (0 until steps.length()).mapNotNull { i ->
                val item = steps.optJSONObject(i) ?: return@mapNotNull null
                val text = item.optString("text").trim()
                if (text.isEmpty()) null
                else AiStep(text, item.optDouble("timerMinutes", Double.NaN).takeIf { !it.isNaN() && it > 0 })
            }
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
