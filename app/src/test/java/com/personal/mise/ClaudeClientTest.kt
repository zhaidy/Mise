package com.personal.mise

import com.personal.mise.ai.AiSchema
import com.personal.mise.ai.AiUnavailableException
import com.personal.mise.ai.ClaudeClient
import com.personal.mise.importer.ImportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeClientTest {

    @Test
    fun `请求里带 JSON Schema，effort 用 medium，不设 temperature`() {
        val schema = AiSchema.obj("name" to AiSchema.string(), required = listOf("name"))
        val body = ClaudeClient.requestBody("claude-haiku-5-5", "整理", schema)
        assertEquals("claude-haiku-5-5", body.getString("model"))
        assertFalse(body.has("temperature"))
        val config = body.getJSONObject("output_config")
        assertEquals("medium", config.getString("effort"))
        assertEquals("json_schema", config.getJSONObject("format").getString("type"))
        assertEquals(schema.toString(), config.getJSONObject("format").getJSONObject("schema").toString())
    }

    @Test
    fun `图片放在文字前面`() {
        val body = ClaudeClient.requestBody("claude-haiku-5-5", "看图整理", AiSchema.obj(), listOf(byteArrayOf(1, 2, 3)))
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(2, content.length())
        val source = content.getJSONObject(0).getJSONObject("source")
        assertEquals("image/jpeg", source.getString("media_type"))
        assertEquals("AQID", source.getString("data"))
        assertEquals("看图整理", content.getJSONObject(1).getString("text"))
    }

    @Test
    fun `跳过思考块取 text 块里的 JSON`() {
        val body = """{"model":"claude-haiku-5-5","stop_reason":"end_turn","content":[
            {"type":"thinking","thinking":"","signature":"x"},{"type":"text","text":"{\"name\":\"红烧肉\"}"}]}"""
        assertEquals("红烧肉", ClaudeClient.replyJson(body).getString("name"))
    }

    @Test
    fun `被截断或拒绝时报错`() {
        val truncated = """{"stop_reason":"max_tokens","content":[{"type":"text","text":"{\"name\""}]}"""
        assertTrue(assertThrows(ImportException::class.java) { ClaudeClient.replyJson(truncated) }.message!!.contains("截断"))
        val refused = """{"stop_reason":"refusal","content":[]}"""
        assertThrows(ImportException::class.java) { ClaudeClient.replyJson(refused) }
    }

    @Test
    fun `余额不足、限流、服务器忙换下一个模型，Key 不对直接报错`() {
        listOf(402, 429, 529).forEach { assertTrue(ClaudeClient.error(it, null) is AiUnavailableException) }
        assertFalse(ClaudeClient.error(401, null) is AiUnavailableException)
        assertEquals("Claude Haiku 5.5", ClaudeClient.displayName("claude-haiku-5-5"))
        assertEquals("Claude Sonnet 5.5", ClaudeClient.displayName("claude-sonnet-5-5"))
    }
}
