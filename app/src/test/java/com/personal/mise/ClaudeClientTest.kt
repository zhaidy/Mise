package com.personal.mise

import com.personal.mise.ai.AiSchema
import com.personal.mise.ai.ClaudeClient
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ClaudeClientTest {

    private fun reply(vararg blocks: String, stopReason: String = "end_turn") =
        """{"type":"message","role":"assistant","stop_reason":"$stopReason","content":[${blocks.joinToString(",")}]}"""

    private fun text(inner: String) = """{"type":"text","text":${JSONObject.quote(inner)}}"""

    @Test
    fun `请求里带上 JSON Schema`() {
        val schema = AiSchema.obj("title" to AiSchema.string(nullable = true), required = listOf("title"))
        val body = ClaudeClient.requestBody("整理一下", schema)
        assertEquals("整理一下", body.getJSONArray("messages").getJSONObject(0).getString("content"))
        val format = body.getJSONObject("output_config").getJSONObject("format")
        assertEquals("json_schema", format.getString("type"))
        assertEquals(schema.toString(), format.getJSONObject("schema").toString())
        // 新模型不接受 temperature
        assertFalse(body.has("temperature"))
    }

    @Test
    fun `可空字段用 anyOf，对象不允许多余字段`() {
        val schema = AiSchema.obj("n" to AiSchema.number(nullable = true))
        assertEquals(false, schema.get("additionalProperties"))
        val anyOf = schema.getJSONObject("properties").getJSONObject("n").getJSONArray("anyOf")
        assertEquals("number", anyOf.getJSONObject(0).getString("type"))
        assertEquals("null", anyOf.getJSONObject(1).getString("type"))
    }

    @Test
    fun `跳过思考块，取出文字里的 JSON`() {
        val body = reply("""{"type":"thinking","thinking":"","signature":"x"}""", text("""{"steps":[{"text":"切丁"}]}"""))
        assertEquals("切丁", ClaudeClient.replyJson(body).getJSONArray("steps").getJSONObject(0).getString("text"))
    }

    @Test
    fun `拒绝、截断和格式不对时报错`() {
        assertThrows(ImportException::class.java) { ClaudeClient.replyJson(reply(stopReason = "refusal")) }
        assertThrows(ImportException::class.java) { ClaudeClient.replyJson(reply(text("""{"steps":["""), stopReason = "max_tokens")) }
        assertThrows(ImportException::class.java) { ClaudeClient.replyJson(reply(text("不是 JSON"))) }
        assertThrows(ImportException::class.java) { ClaudeClient.replyJson(reply()) }
        assertThrows(ImportException::class.java) { ClaudeClient.replyJson("") }
    }
}
