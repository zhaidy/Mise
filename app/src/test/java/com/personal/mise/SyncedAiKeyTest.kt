package com.personal.mise

import com.personal.mise.ai.AiProvider
import com.personal.mise.sync.SyncedAiKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncedAiKeyTest {

    @Test
    fun `转成文档再读回来不变`() {
        val key = SyncedAiKey(AiProvider.DEEPSEEK, "sk-123", updatedAt = 500)
        assertEquals(key, SyncedAiKey.fromMap("DEEPSEEK", key.toMap()))
    }

    @Test
    fun `清空的 Key 也能同步`() {
        val key = SyncedAiKey(AiProvider.GEMINI, "", updatedAt = 7)
        assertEquals(key, SyncedAiKey.fromMap("GEMINI", key.toMap()))
    }

    @Test
    fun `不认识的服务商或格式不对时忽略`() {
        val map = SyncedAiKey(AiProvider.GEMINI, "k", 1).toMap()
        assertNull(SyncedAiKey.fromMap("OPENAI", map))
        // 以前支持过的 Claude 已经去掉，云端留下的旧 Key 不再理会；现在的 Claude 存成 ANTHROPIC
        assertNull(SyncedAiKey.fromMap("CLAUDE", map))
        assertNull(SyncedAiKey.fromMap("GEMINI", map - "updatedAt"))
        assertNull(SyncedAiKey.fromMap("GEMINI", map + ("key" to 3)))
    }
}
