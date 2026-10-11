package com.personal.mise.ai

import android.content.Context
import com.personal.mise.data.RecipeStamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class AiProvider(val label: String, val keyHelp: String) {
    GEMINI("Gemini", "在 aistudio.google.com 免费申请"),
    CLAUDE("Claude", "在 platform.claude.com 申请，按用量付费"),
    DEEPSEEK("DeepSeek", "在 platform.deepseek.com 申请，预付费，余额用完就停、不会自动扣费"),
}

/** 用哪家的 AI 和各家的 API Key，存在本机，不会跟菜谱一起导出；加入共享厨房时 Key 会同步给厨房里的人 */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)
    private val keysChanged = MutableStateFlow(0)

    var provider: AiProvider
        get() = prefs.getString(PROVIDER, null)?.let { name -> AiProvider.entries.firstOrNull { it.name == name } } ?: AiProvider.GEMINI
        set(value) {
            prefs.edit().putString(PROVIDER, value.name).apply()
        }

    fun key(provider: AiProvider): String = prefs.getString(keyName(provider), "").orEmpty()

    /** Key 上次修改的时间；改之前就填好的 Key 是 0 */
    fun keyUpdatedAt(provider: AiProvider): Long = prefs.getLong(keyName(provider) + UPDATED_AT_SUFFIX, 0)

    /** 没改就不动，免得 AI 设置每次确认都把没碰过的 Key 当成新改的同步出去 */
    fun saveKey(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        if (trimmed == key(provider)) return
        writeKey(provider, trimmed, System.currentTimeMillis())
    }

    /** 共享厨房里别人改了 Key，同步到本机 */
    fun applyRemoteKey(provider: AiProvider, key: String, updatedAt: Long) = writeKey(provider, key.trim(), updatedAt)

    private fun writeKey(provider: AiProvider, key: String, updatedAt: Long) {
        prefs.edit()
            .putString(keyName(provider), key)
            .putLong(keyName(provider) + UPDATED_AT_SUFFIX, updatedAt)
            .apply()
        keysChanged.update { it + 1 }
    }

    /** 填过（包括清空过）的 Key 和修改时间，uid 是服务商的名字；给共享厨房同步用 */
    fun observeKeyStamps(): Flow<List<RecipeStamp>> = keysChanged.map {
        AiProvider.entries.mapNotNull { p ->
            val at = keyUpdatedAt(p)
            RecipeStamp(p.name, at).takeIf { at > 0 || key(p).isNotBlank() }
        }
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
        const val UPDATED_AT_SUFFIX = "_updated_at"

        fun keyName(provider: AiProvider) = when (provider) {
            AiProvider.GEMINI -> "gemini_key"
            AiProvider.CLAUDE -> "claude_key"
            AiProvider.DEEPSEEK -> "deepseek_key"
        }
    }
}
