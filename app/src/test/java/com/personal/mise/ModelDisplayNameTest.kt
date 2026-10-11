package com.personal.mise

import com.personal.mise.ai.ClaudeClient
import com.personal.mise.ai.DeepSeekClient
import com.personal.mise.ai.GeminiClient
import com.personal.mise.ai.modelDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDisplayNameTest {

    @Test
    fun prettifiesApiModelNames() {
        assertEquals("Claude Opus 5.5", modelDisplayName("claude-opus-5-5"))
        assertEquals("Claude Haiku 4.5", modelDisplayName("claude-haiku-4-5-20251001"))
        assertEquals("DeepSeek Flash", modelDisplayName("deepseek-flash"))
        assertEquals("Gemini 3.8 Flash", GeminiClient.displayName("gemini-3.8-flash"))
        assertEquals("gemini-9-pro", GeminiClient.displayName("gemini-9-pro"))
    }

    @Test
    fun readsModelFromReply() {
        assertEquals("claude-sonnet-5-5", ClaudeClient.replyModel("""{"model":"claude-sonnet-5-5","content":[]}"""))
        assertEquals("claude-opus-5-5", ClaudeClient.replyModel("{}"))
        assertEquals("deepseek-flash", DeepSeekClient.replyModel("不是 JSON"))
    }
}
