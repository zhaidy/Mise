package com.personal.mise.ai

import android.content.Context
import com.personal.mise.data.RecipeStamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class AiProvider(val label: String, val keyHelp: String) {
    GEMINI("Gemini", "在 aistudio.google.com 免费申请"),
    DEEPSEEK("DeepSeek", "在 platform.deepseek.com 申请，预付费，余额用完就停、不会自动扣费"),
    AGNES("Agnes", "在 platform.agnes-ai.com 申请"),
}

/** 一个可选的模型：哪一家、API 名、显示名 */
data class AiModel(val provider: AiProvider, val id: String, val label: String) {
    /** 存在设置里的名字，比如 GEMINI:gemini-3.8-flash */
    val key: String get() = "${provider.name}:$id"

    companion object {
        /** 所有可选的模型，也是默认的尝试顺序 */
        val ALL: List<AiModel> =
            GeminiClient.MODELS.map { (id, label) -> AiModel(AiProvider.GEMINI, id, label) } +
                AgnesClient.MODELS.map { (id, label) -> AiModel(AiProvider.AGNES, id, label) } +
                DeepSeekClient.MODELS.map { (id, label) -> AiModel(AiProvider.DEEPSEEK, id, label) }

        /** 默认勾选的：Gemini 全部、Agnes 和 DeepSeek 各一个；没填 Key 的会跳过，默认勾上也不碍事 */
        val DEFAULT: List<AiModel> = ALL.filter { it.provider == AiProvider.GEMINI || it == ALL.first { m -> m.provider == it.provider } }

        fun fromKey(key: String): AiModel? = ALL.firstOrNull { it.key == key }
    }
}

/** 用哪些 AI 模型、什么顺序，和各家的 API Key，存在本机，不会跟菜谱一起导出；加入共享厨房时都会同步给厨房里的人 */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)
    private val keysChanged = MutableStateFlow(0)
    private val modelsChanged = MutableStateFlow(0)

    init {
        // 以前支持过 Claude，去掉后把本机存的 Claude Key 也删掉
        if (prefs.contains(OLD_CLAUDE_KEY)) prefs.edit().remove(OLD_CLAUDE_KEY).remove(OLD_CLAUDE_KEY + UPDATED_AT_SUFFIX).apply()
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

    /** 勾选的模型，按顺序尝试：前一个额度用完、余额不足、连不上就换下一个；没填 Key 的跳过 */
    var models: List<AiModel>
        get() {
            val saved = prefs.getString(MODELS, null) ?: return migratedModels()
            return saved.split(',').mapNotNull(AiModel::fromKey).ifEmpty { AiModel.DEFAULT }
        }
        set(value) {
            // 没改就不动，免得每次确认 AI 设置都当成新改的同步出去
            val joined = value.joinToString(",") { it.key }
            if (joined == prefs.getString(MODELS, null)) return
            writeModels(joined, System.currentTimeMillis())
        }

    /** 模型列表上次修改的时间；还没改过（用的是默认或从旧设置推出来的）是 0 */
    val modelsUpdatedAt: Long get() = prefs.getLong(MODELS + UPDATED_AT_SUFFIX, 0)

    /** 存着的模型列表原样（AiModel.key 的列表），给共享厨房同步用；这个版本不认识的模型也留着，还给别的手机 */
    val savedModelKeys: List<String> get() = prefs.getString(MODELS, null)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

    /** 共享厨房里别人改了用哪些模型、什么顺序，同步到本机 */
    fun applyRemoteModels(keys: List<String>, updatedAt: Long) = writeModels(keys.joinToString(","), updatedAt)

    private fun writeModels(joined: String, updatedAt: Long) {
        prefs.edit()
            .putString(MODELS, joined)
            .putLong(MODELS + UPDATED_AT_SUFFIX, updatedAt)
            .apply()
        modelsChanged.update { it + 1 }
    }

    /** 改过模型列表时给出它的修改时间，uid 是 [MODELS_SYNC_ID]；给共享厨房同步用 */
    fun observeModelsStamps(): Flow<List<RecipeStamp>> = modelsChanged.map {
        listOfNotNull(RecipeStamp(MODELS_SYNC_ID, modelsUpdatedAt).takeIf { it.updatedAt > 0 })
    }

    /**
     * 还没存过模型列表时，从以前的设置推出来：以前选的是 DeepSeek 就只用 DeepSeek；
     * 选的是 Gemini 就用勾选的 Gemini 模型，打开了「改用 DeepSeek」的再把 DeepSeek 排在最后。
     */
    private fun migratedModels(): List<AiModel> {
        val gemini = prefs.getString(OLD_GEMINI_MODELS, null)
            ?.split(',')?.mapNotNull { AiModel.fromKey("${AiProvider.GEMINI.name}:$it") }
            ?.ifEmpty { null }
            ?: AiModel.DEFAULT.filter { it.provider == AiProvider.GEMINI }
        val agnes = AiModel.DEFAULT.filter { it.provider == AiProvider.AGNES }
        val deepSeek = AiModel.DEFAULT.filter { it.provider == AiProvider.DEEPSEEK }
        return when {
            prefs.getString(OLD_PROVIDER, null) == AiProvider.DEEPSEEK.name -> deepSeek + agnes
            prefs.getBoolean(OLD_DEEPSEEK_FALLBACK, true) -> gemini + agnes + deepSeek
            else -> gemini + agnes
        }
    }

    /** 刚填了某家的 Key 但它的模型一个都没勾时，把它的第一个模型勾上，免得填了 Key 也用不上 */
    fun enableProvider(provider: AiProvider) {
        val current = models
        if (current.none { it.provider == provider }) models = current + AiModel.ALL.first { it.provider == provider }
    }

    /** 勾选的模型里第一个填了 Key、会先去用的；一个都没有时是 null */
    val firstUsable: AiModel? get() = models.firstOrNull { key(it.provider).isNotBlank() }

    /** 有没有能用的模型（勾选了且填了 Key） */
    val hasKey: Boolean get() = firstUsable != null

    companion object {
        /** 模型列表在云端的文档 id */
        const val MODELS_SYNC_ID = "models"

        private const val CELLULAR_FALLBACK = "cellular_fallback"
        private const val MODELS = "models"
        private const val OLD_PROVIDER = "provider"
        private const val OLD_GEMINI_MODELS = "gemini_models"
        private const val OLD_DEEPSEEK_FALLBACK = "deepseek_fallback"
        private const val UPDATED_AT_SUFFIX = "_updated_at"
        private const val OLD_CLAUDE_KEY = "claude_key"

        private fun keyName(provider: AiProvider) = when (provider) {
            AiProvider.GEMINI -> "gemini_key"
            AiProvider.DEEPSEEK -> "deepseek_key"
            AiProvider.AGNES -> "agnes_key"
        }
    }
}
