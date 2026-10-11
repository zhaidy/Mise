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
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
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

    @Test
    fun `余额显示成人看的文字`() {
        val cny = """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00","granted_balance":"10.00","topped_up_balance":"100.00"}]}"""
        assertEquals("余额 ¥110.00", DeepSeekClient.balanceText(cny))
        val both = """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"1.00"},{"currency":"USD","total_balance":"2.50"}]}"""
        assertEquals("余额 ¥1.00、$2.50", DeepSeekClient.balanceText(both))
        val empty = """{"is_available":false,"balance_infos":[{"currency":"CNY","total_balance":"0.00"}]}"""
        assertTrue(DeepSeekClient.balanceText(empty).startsWith("余额 ¥0.00，不够用了"))
        assertThrows(ImportException::class.java) { DeepSeekClient.balanceText("{}") }
        assertThrows(ImportException::class.java) { DeepSeekClient.balanceText("不是 JSON") }
    }
}
