package com.personal.mise.ai

import com.personal.mise.importer.ImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * 调用 DeepSeek 的 OpenAI 格式接口，用 JSON 模式；还能查账户余额。
 * 思考模式默认开着，思考过程也算在 max_tokens 里，分调料碗这种要琢磨的活会把额度想光、
 * 正式回复被截断；这些都是照格式整理的活，关掉思考。
 */
class DeepSeekClient(settings: AiSettings, private val http: AiHttp) :
    OpenAiCompatibleClient(AiProvider.DEEPSEEK, ENDPOINT, settings, http) {

    override val jsonMode = true

    override val disableThinking = true

    override fun error(code: Int, detail: String?): ImportException = errorMessage(code, detail)

    /** 查账户余额，返回给人看的文字，比如「余额 ¥110.00」；出错时抛 ImportException */
    suspend fun balance(key: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(BALANCE_ENDPOINT)
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        val reply = http.execute(request, "DeepSeek")
        if (!reply.isSuccessful) throw errorMessage(reply.code, OpenAiCompatibleClient.errorDetail(reply.body))
        balanceText(reply.body)
    }

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val BALANCE_ENDPOINT = "https://api.deepseek.com/user/balance"

        /** 可选的模型（API 名 to 显示名） */
        val MODELS = listOf("deepseek-flash" to "DeepSeek Flash")

        /** 把 /user/balance 的响应变成「余额 ¥110.00」；人民币和美元都有余额时两个都列出来 */
        fun balanceText(body: String): String {
            val json = runCatching { JSONObject(body) }.getOrNull() ?: throw ImportException("DeepSeek 返回的余额格式不对")
            val infos = json.optJSONArray("balance_infos") ?: JSONArray()
            val amounts = (0 until infos.length()).mapNotNull { i ->
                val info = infos.optJSONObject(i) ?: return@mapNotNull null
                val total = info.optString("total_balance").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                when (val currency = info.optString("currency")) {
                    "CNY" -> "¥$total"
                    "USD" -> "$$total"
                    else -> "$total $currency"
                }
            }
            if (amounts.isEmpty()) throw ImportException("DeepSeek 没有返回余额")
            val text = "余额 " + amounts.joinToString("、")
            return if (json.optBoolean("is_available", true)) text else "$text，不够用了，请到 platform.deepseek.com 充值"
        }

        private fun errorMessage(code: Int, detail: String?): ImportException = when (code) {
            401 -> ImportException("DeepSeek API Key 不对，请检查")
            402 -> AiUnavailableException("余额不足，请到 platform.deepseek.com 充值")
            429 -> AiUnavailableException("请求太频繁")
            500, 503 -> AiUnavailableException("服务器繁忙")
            else -> ImportException("DeepSeek 出错了（HTTP $code）${detail?.let { "：$it" }.orEmpty()}")
        }
    }
}
