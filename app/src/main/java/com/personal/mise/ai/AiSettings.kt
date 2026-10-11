package com.personal.mise.ai

import android.content.Context

enum class AiProvider(val label: String, val keyHelp: String) {
    GEMINI("Gemini", "在 aistudio.google.com 免费申请"),
    CLAUDE("Claude", "在 platform.claude.com 申请，按用量付费"),
    DEEPSEEK("DeepSeek", "在 platform.deepseek.com 申请，预付费，余额用完就停、不会自动扣费"),
}

/** 用哪家的 AI 和各家的 API Key，只存在本机，不会跟菜谱一起导出 */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)

    var provider: AiProvider
        get() = prefs.getString(PROVIDER, null)?.let { name -> AiProvider.entries.firstOrNull { it.name == name } } ?: AiProvider.GEMINI
        set(value) {
            prefs.edit().putString(PROVIDER, value.name).apply()
        }

    fun key(provider: AiProvider): String = prefs.getString(keyName(provider), "").orEmpty()

    fun saveKey(provider: AiProvider, key: String) {
        prefs.edit().putString(keyName(provider), key.trim()).apply()
    }

    /** Wi-Fi 连不上 AI 服务器时，是否改用移动数据再试一次 */
    var cellularFallback: Boolean
        get() = prefs.getBoolean(CELLULAR_FALLBACK, true)
        set(value) {
            prefs.edit().putBoolean(CELLULAR_FALLBACK, value).apply()
        }

    /** 用 Gemini 时依次尝试的模型（API 名），前一个额度用完就换下一个 */
    var geminiModels: List<String>
        get() {
            val known = GeminiClient.MODELS.map { it.first }
            val saved = prefs.getString(GEMINI_MODELS, null) ?: return known
            return saved.split(',').filter { it in known }.ifEmpty { known }
        }
        set(value) {
            prefs.edit().putString(GEMINI_MODELS, value.joinToString(",")).apply()
        }

    /** Gemini 勾选的模型都用完后，是否改用 DeepSeek（需要填了 DeepSeek Key） */
    var deepSeekFallback: Boolean
        get() = prefs.getBoolean(DEEPSEEK_FALLBACK, true)
        set(value) {
            prefs.edit().putBoolean(DEEPSEEK_FALLBACK, value).apply()
        }

    /** 当前选的 AI 有没有填 Key */
    val hasKey: Boolean get() = key(provider).isNotBlank()

    private companion object {
        const val PROVIDER = "provider"
        const val CELLULAR_FALLBACK = "cellular_fallback"
        const val GEMINI_MODELS = "gemini_models"
        const val DEEPSEEK_FALLBACK = "deepseek_fallback"

        fun keyName(provider: AiProvider) = when (provider) {
            AiProvider.GEMINI -> "gemini_key"
            AiProvider.CLAUDE -> "claude_key"
            AiProvider.DEEPSEEK -> "deepseek_key"
        }
    }
}
