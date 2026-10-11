package com.personal.mise.sync

import com.personal.mise.ai.AiSettings

/** 用哪些 AI 模型、什么顺序，在云端的样子：kitchens/{邀请码}/aiSettings/models；models 是勾选的模型（AiModel.key），按尝试顺序排 */
data class SyncedAiModels(
    val models: List<String>,
    val updatedAt: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "models" to models,
        "updatedAt" to updatedAt,
    )

    companion object {
        /** 不是模型列表的文档或格式不对时返回 null */
        fun fromMap(id: String, data: Map<String, Any?>): SyncedAiModels? = runCatching {
            if (id != AiSettings.MODELS_SYNC_ID) return null
            SyncedAiModels(
                models = (data["models"] as List<*>).map { it as String },
                updatedAt = (data["updatedAt"] as Number).toLong(),
            )
        }.getOrNull()

        fun local(settings: AiSettings, id: String): SyncedAiModels? {
            if (id != AiSettings.MODELS_SYNC_ID || settings.modelsUpdatedAt == 0L) return null
            return SyncedAiModels(settings.savedModelKeys, settings.modelsUpdatedAt)
        }
    }
}
