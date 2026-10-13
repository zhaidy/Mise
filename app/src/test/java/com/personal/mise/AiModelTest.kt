package com.personal.mise

import com.personal.mise.ai.AiModel
import com.personal.mise.ai.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiModelTest {

    @Test
    fun `默认先用 Gemini，再用 Agnes、DeepSeek，最后用 Claude`() {
        val providers = AiModel.DEFAULT.map { it.provider }.distinct()
        assertEquals(listOf(AiProvider.GEMINI, AiProvider.AGNES, AiProvider.DEEPSEEK, AiProvider.ANTHROPIC), providers)
        // Agnes、DeepSeek 和 Claude 默认只勾一个
        assertEquals(listOf("agnes-2.5-flash"), AiModel.DEFAULT.filter { it.provider == AiProvider.AGNES }.map { it.id })
        assertEquals(listOf("deepseek-flash"), AiModel.DEFAULT.filter { it.provider == AiProvider.DEEPSEEK }.map { it.id })
        assertEquals(listOf("claude-haiku-5-5"), AiModel.DEFAULT.filter { it.provider == AiProvider.ANTHROPIC }.map { it.id })
    }

    @Test
    fun `存的名字能读回来`() {
        AiModel.ALL.forEach { assertEquals(it, AiModel.fromKey(it.key)) }
        assertEquals("AGNES:agnes-2.5-flash", AiModel.fromKey("AGNES:agnes-2.5-flash")?.key)
        // 下架的模型或不认识的服务商忽略
        assertNull(AiModel.fromKey("GEMINI:gemini-1.0-pro"))
        assertNull(AiModel.fromKey("CLAUDE:claude"))
    }
}
