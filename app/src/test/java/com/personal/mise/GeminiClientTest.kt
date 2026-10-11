package com.personal.mise

import com.personal.mise.ai.AiSchema
import com.personal.mise.ai.GeminiClient
import com.personal.mise.importer.ImportException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiClientTest {

    private fun plain(v: Any?): Any? = when (v) {
        is JSONObject -> v.keys().asSequence().associateWith { plain(v.get(it)) }
        is JSONArray -> (0 until v.length()).map { plain(v.get(it)) }
        else -> v
    }

    @Test
    fun `标准 JSON Schema 转成 Gemini 的写法`() {
        val schema = AiSchema.obj("steps" to AiSchema.array(AiSchema.stepSchema), required = listOf("steps"))
        val expected = JSONObject(
            """{"type":"OBJECT","required":["steps"],"properties":{"steps":{"type":"ARRAY","items":{"type":"OBJECT","required":["text"],
            "properties":{"text":{"type":"STRING"},"timerMinutes":{"type":"NUMBER","nullable":true}}}}}}""",
        )
        // org.json 输出的字段顺序不固定，转成 Map 再比较
        assertEquals(plain(expected), plain(GeminiClient.toGeminiSchema(schema)))
    }

    @Test
    fun `取出回复里的 JSON`() {
        val body = """{"candidates":[{"content":{"parts":[{"text":${JSONObject.quote("""{"steps":[]}""")}}]}}]}"""
        assertEquals(0, GeminiClient.replyJson(body).getJSONArray("steps").length())
        assertThrows(ImportException::class.java) { GeminiClient.replyJson("{}") }
        assertThrows(ImportException::class.java) { GeminiClient.replyJson("""{"candidates":[{"content":{"parts":[{"text":"不是 JSON"}]}}]}""") }
    }
}
