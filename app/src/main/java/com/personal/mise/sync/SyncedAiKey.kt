package com.personal.mise.sync

import com.personal.mise.ai.AiProvider
import com.personal.mise.ai.AiSettings

/** 一家 AI 的 API Key 在云端的样子，文档 id 是服务商的名字（GEMINI、DEEPSEEK、AGNES、ANTHROPIC）；Key 为空表示清空了 */
data class SyncedAiKey(
    val provider: AiProvider,
    val key: String,
    val updatedAt: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "key" to key,
        "updatedAt" to updatedAt,
    )

    companion object {
        /** 不认识的服务商或格式不对时返回 null */
        fun fromMap(id: String, data: Map<String, Any?>): SyncedAiKey? = runCatching {
            SyncedAiKey(
                provider = AiProvider.valueOf(id),
                key = data["key"] as String,
                updatedAt = (data["updatedAt"] as Number).toLong(),
            )
        }.getOrNull()

        fun local(settings: AiSettings, id: String): SyncedAiKey? {
            val provider = AiProvider.entries.firstOrNull { it.name == id } ?: return null
            return SyncedAiKey(provider, settings.key(provider), settings.keyUpdatedAt(provider))
        }
    }
}
