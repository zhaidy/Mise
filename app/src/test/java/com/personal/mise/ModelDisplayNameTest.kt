package com.personal.mise

import com.personal.mise.ai.DeepSeekClient
import com.personal.mise.ai.GeminiClient
import com.personal.mise.ai.modelDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDisplayNameTest {

    @Test
    fun prettifiesApiModelNames() {
        assertEquals("DeepSeek V4", modelDisplayName("deepseek-v4"))
        assertEquals("Model 4.5", modelDisplayName("model-4-5-20251001"))
        assertEquals("DeepSeek Flash", modelDisplayName("deepseek-flash"))
        assertEquals("Gemini 3.8 Flash", GeminiClient.displayName("gemini-3.8-flash"))
        assertEquals("gemini-9-pro", GeminiClient.displayName("gemini-9-pro"))
    }

    @Test
    fun readsModelFromReply() {
        assertEquals("deepseek-pro", DeepSeekClient.replyModel("""{"model":"deepseek-pro","choices":[]}"""))
        assertEquals("deepseek-flash", DeepSeekClient.replyModel("不是 JSON"))
    }
}
