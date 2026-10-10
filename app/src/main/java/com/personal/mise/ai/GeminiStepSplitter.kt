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

data class AiStep(val text: String, val timerMinutes: Double?)

/** 把下厨房上挤成一段的做法，交给 Gemini 拆成一步一步的清晰步骤。 */
class GeminiStepSplitter(private val settings: AiSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun split(rawSteps: List<String>): List<AiStep> = withContext(Dispatchers.IO) {
        val key = settings.geminiKey
        if (key.isBlank()) throw ImportException("还没有填写 Gemini API Key")
        val text = rawSteps.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        if (text.isEmpty()) throw ImportException("没有可以拆分的步骤文字")

        val request = Request.Builder()
            .url("$ENDPOINT/$MODEL:generateContent")
            .header("x-goog-api-key", key)
            .post(requestBody(text).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val body = try {
            client.newCall(request).execute().use { response ->
                val s = response.body.string()
                if (!response.isSuccessful) throw ImportException(errorMessage(response.code, s))
                s
            }
        } catch (e: IOException) {
            throw ImportException("连接 Gemini 失败：${e.message ?: "请检查网络"}")
        }
        parseResponse(body)
    }

    companion object {
        private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MODEL = "gemini-2.5-flash"

        private const val PROMPT = "下面是一份中文菜谱的做法，可能所有操作都挤在一两段里。" +
            "请把它拆成清晰的、按先后顺序的步骤：每一步只做一件主要的事（例如切配、腌制、焯水、炒制、调味、收汁、装盘分开）。" +
            "只整理原文，不要增加原文没有的食材、调料、用量或时间，保留原文的数字和用量。" +
            "如果原文明确写了某一步需要等待或加热的时长（如「焖10分钟」），把分钟数填进 timerMinutes，" +
            "多个时间或没有明确时长时留空。每步的 text 用简洁的一两句话，不要带序号。\n\n做法原文：\n"

        fun requestBody(text: String): JSONObject = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", PROMPT + text)))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
                    .put(
                        "responseSchema",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put(
                                "properties",
                                JSONObject().put(
                                    "steps",
                                    JSONObject().put("type", "ARRAY").put(
                                        "items",
                                        JSONObject().put("type", "OBJECT")
                                            .put(
                                                "properties",
                                                JSONObject()
                                                    .put("text", JSONObject().put("type", "STRING"))
                                                    .put("timerMinutes", JSONObject().put("type", "NUMBER").put("nullable", true)),
                                            )
                                            .put("required", JSONArray().put("text")),
                                    ),
                                ),
                            )
                            .put("required", JSONArray().put("steps")),
                    ),
            )

        fun parseResponse(body: String): List<AiStep> {
            val reply = runCatching {
                JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }.getOrNull() ?: throw ImportException("Gemini 没有返回内容，请重试")
            val steps = runCatching { JSONObject(reply).getJSONArray("steps") }.getOrNull()
                ?: throw ImportException("Gemini 返回的格式不对，请重试")
            val result = (0 until steps.length()).mapNotNull { i ->
                val item = steps.optJSONObject(i) ?: return@mapNotNull null
                val text = item.optString("text").trim()
                if (text.isEmpty()) null
                else AiStep(text, item.optDouble("timerMinutes", Double.NaN).takeIf { !it.isNaN() && it > 0 })
            }
            if (result.isEmpty()) throw ImportException("Gemini 没有拆出步骤，请重试")
            return result
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
