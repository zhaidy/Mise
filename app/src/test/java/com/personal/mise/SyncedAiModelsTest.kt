package com.personal.mise

import com.personal.mise.ai.AiSettings
import com.personal.mise.sync.SyncedAiKey
import com.personal.mise.sync.SyncedAiModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncedAiModelsTest {

    @Test
    fun `转成文档再读回来不变`() {
        val models = SyncedAiModels(listOf("AGNES:agnes-2.5-flash", "GEMINI:gemini-3.8-flash"), updatedAt = 42)
        assertEquals(models, SyncedAiModels.fromMap(AiSettings.MODELS_SYNC_ID, models.toMap()))
    }

    @Test
    fun `别的文档或格式不对时忽略`() {
        val map = SyncedAiModels(listOf("GEMINI:gemini-3.8-flash"), 1).toMap()
        assertNull(SyncedAiModels.fromMap("other", map))
        assertNull(SyncedAiModels.fromMap(AiSettings.MODELS_SYNC_ID, map - "updatedAt"))
        assertNull(SyncedAiModels.fromMap(AiSettings.MODELS_SYNC_ID, map + ("models" to "GEMINI:gemini-3.8-flash")))
        assertNull(SyncedAiModels.fromMap(AiSettings.MODELS_SYNC_ID, map + ("models" to listOf(1, 2))))
    }

    @Test
    fun `模型列表不会被当成 API Key`() {
        val map = SyncedAiModels(listOf("GEMINI:gemini-3.8-flash"), 1).toMap()
        assertNull(SyncedAiKey.fromMap(AiSettings.MODELS_SYNC_ID, map))
    }
}
