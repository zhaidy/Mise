package com.personal.mise

import com.personal.mise.ai.AiUnavailableException
import com.personal.mise.ai.ChatTurn
import com.personal.mise.ai.ClaudeClient
import com.personal.mise.ai.GeminiClient
import com.personal.mise.ai.OpenAiCompatibleClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 四家流式聊天的请求和回复格式 */
class ChatStreamTest {

    private val turns = listOf(ChatTurn(true, "冰糖没了怎么办"), ChatTurn(false, "用白砂糖"), ChatTurn(true, "要放多少"))

    @Test
    fun `Claude 聊天请求：流式、系统提示单独放、effort 用 low`() {
        val body = ClaudeClient.chatBody("claude-haiku-5-5", "你是做菜助手", turns)
        assertTrue(body.getBoolean("stream"))
        assertEquals("你是做菜助手", body.getString("system"))
        assertEquals("low", body.getJSONObject("output_config").getString("effort"))
        assertFalse(body.has("temperature"))
        val messages = body.getJSONArray("messages")
        assertEquals(listOf("user", "assistant", "user"), (0 until messages.length()).map { messages.getJSONObject(it).getString("role") })
        assertEquals("要放多少", messages.getJSONObject(2).getString("content"))
    }

    @Test
    fun `Claude 流里只取文字增量`() {
        val text = JSONObject("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"减到"}}""")
        val thinking = JSONObject("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"想想"}}""")
        val start = JSONObject("""{"type":"message_start","message":{}}""")
        assertEquals("减到", ClaudeClient.streamText(text))
        assertNull(ClaudeClient.streamText(thinking))
        assertNull(ClaudeClient.streamText(start))
    }

    @Test
    fun `Claude 流到一半服务器忙可以换模型`() {
        val error = ClaudeClient.streamError(JSONObject("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
        assertTrue(error is AiUnavailableException)
        assertNull(ClaudeClient.streamError(JSONObject("""{"type":"ping"}""")))
    }

    @Test
    fun `Gemini 聊天请求：系统提示放 systemInstruction，AI 的话角色是 model`() {
        val body = GeminiClient.chatBody("gemini-3.8-flash", "你是做菜助手", turns)
        assertEquals("你是做菜助手", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val contents = body.getJSONArray("contents")
        assertEquals(listOf("user", "model", "user"), (0 until contents.length()).map { contents.getJSONObject(it).getString("role") })
        assertFalse(body.getJSONObject("generationConfig").has("responseMimeType"))
        assertEquals("low", body.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }

    @Test
    fun `Gemini 流里跳过思考的部分`() {
        val event = JSONObject("""{"candidates":[{"content":{"parts":[{"text":"想想","thought":true},{"text":"放 25 克"}],"role":"model"}}]}""")
        assertEquals("放 25 克", GeminiClient.streamText(event))
        assertNull(GeminiClient.streamText(JSONObject("""{"usageMetadata":{}}""")))
        assertTrue(GeminiClient.streamBlocked(JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}""")))
        assertFalse(GeminiClient.streamBlocked(event))
    }

    @Test
    fun `OpenAI 格式聊天请求：系统提示是第一条`() {
        val body = OpenAiCompatibleClient.chatBody("deepseek-flash", "你是做菜助手", turns, disableThinking = true)
        assertTrue(body.getBoolean("stream"))
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertFalse(body.has("response_format"))
        val messages = body.getJSONArray("messages")
        assertEquals(listOf("system", "user", "assistant", "user"), (0 until messages.length()).map { messages.getJSONObject(it).getString("role") })
    }

    @Test
    fun `OpenAI 格式流里只取正文，不要思考过程`() {
        assertEquals("放", OpenAiCompatibleClient.streamText(JSONObject("""{"choices":[{"delta":{"content":"放"}}]}""")))
        assertNull(OpenAiCompatibleClient.streamText(JSONObject("""{"choices":[{"delta":{"reasoning_content":"想","content":null}}]}""")))
        assertNull(OpenAiCompatibleClient.streamText(JSONObject("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""")))
    }
}
