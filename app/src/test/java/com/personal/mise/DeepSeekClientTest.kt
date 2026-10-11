package com.personal.mise

import com.personal.mise.ai.AiSchema
import com.personal.mise.ai.DeepSeekClient
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekClientTest {

    @Test
    fun `用 JSON 模式并把 schema 写进系统提示`() {
        val schema = AiSchema.obj("steps" to AiSchema.array(AiSchema.stepSchema), required = listOf("steps"))
        val body = DeepSeekClient.requestBody("拆分步骤", schema)
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        val messages = body.getJSONArray("messages")
        val system = messages.getJSONObject(0)
        assertEquals("system", system.getString("role"))
        // JSON 模式要求提示里出现 json 字样
        assertTrue(system.getString("content").contains("JSON"))
        assertTrue(system.getString("content").contains("timerMinutes"))
        assertEquals("拆分步骤", messages.getJSONObject(1).getString("content"))
    }

    @Test
    fun `图片放在文字前面`() {
        val body = DeepSeekClient.requestBody("看图整理", AiSchema.obj(), listOf(byteArrayOf(1, 2, 3)))
        val parts = body.getJSONArray("messages").getJSONObject(1).getJSONArray("content")
        assertEquals(2, parts.length())
        assertEquals("image_url", parts.getJSONObject(0).getString("type"))
        assertEquals("data:image/jpeg;base64,AQID", parts.getJSONObject(0).getJSONObject("image_url").getString("url"))
        assertEquals("看图整理", parts.getJSONObject(1).getString("text"))
    }

    @Test
    fun `取出回复里的 JSON`() {
        fun reply(content: String, finish: String = "stop") =
            """{"choices":[{"finish_reason":"$finish","message":{"role":"assistant","content":${JSONObject.quote(content)}}}]}"""
        assertEquals(0, DeepSeekClient.replyJson(reply("""{"steps":[]}""")).getJSONArray("steps").length())
        assertThrows(ImportException::class.java) { DeepSeekClient.replyJson("{}") }
        assertThrows(ImportException::class.java) { DeepSeekClient.replyJson(reply("不是 JSON")) }
        assertThrows(ImportException::class.java) { DeepSeekClient.replyJson(reply("""{"steps":[""", finish = "length")) }
    }
}
