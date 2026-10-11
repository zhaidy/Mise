package com.personal.mise

import com.personal.mise.ai.AiSchema
import com.personal.mise.ai.GeminiClient
import com.personal.mise.importer.ImportException
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `图片放在文字前面`() {
        val body = GeminiClient.requestBody("看图整理", AiSchema.obj(), listOf(byteArrayOf(1, 2, 3)))
        val parts = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(2, parts.length())
        val image = parts.getJSONObject(0).getJSONObject("inlineData")
        assertEquals("image/jpeg", image.getString("mimeType"))
        assertEquals("AQID", image.getString("data"))
        assertEquals("看图整理", parts.getJSONObject(1).getString("text"))
    }

    @Test
    fun `取出回复里的 JSON`() {
        val body = """{"candidates":[{"content":{"parts":[{"text":${JSONObject.quote("""{"steps":[]}""")}}]}}]}"""
        assertEquals(0, GeminiClient.replyJson(body).getJSONArray("steps").length())
        assertThrows(ImportException::class.java) { GeminiClient.replyJson("{}") }
        assertThrows(ImportException::class.java) { GeminiClient.replyJson("""{"candidates":[{"content":{"parts":[{"text":"不是 JSON"}]}}]}""") }
    }

    @Test
    fun `分清当天额度用完和每分钟请求太多`() {
        val daily = """{"error":{"code":429,"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
            "violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}"""
        val perMinute = """{"error":{"code":429,"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
            "violations":[{"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}]}]}}"""
        assertTrue(GeminiClient.isDailyQuota(daily))
        assertFalse(GeminiClient.isDailyQuota(perMinute))
    }

    @Test
    fun `额度在太平洋时间零点重置`() {
        // 北京时间 2026-10-11 10:00 = 太平洋时间（夏令时）10-10 19:00，下次重置是 10-11 00:00 PDT = 07:00 UTC
        val now = Instant.parse("2026-10-11T02:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-11T07:00:00Z").toEpochMilli(), GeminiClient.nextQuotaReset(now))
        // 冬令时 PST 是 UTC-8
        val winter = Instant.parse("2026-12-01T09:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-12-02T08:00:00Z").toEpochMilli(), GeminiClient.nextQuotaReset(winter))
    }

    @Test
    fun `用量按太平洋时间的日期算`() {
        // 北京时间 10-11 10:00 在太平洋时间还是 10-10
        assertEquals("2026-10-10", GeminiClient.quotaDay(Instant.parse("2026-10-11T02:00:00Z").toEpochMilli()))
        assertEquals("2026-10-11", GeminiClient.quotaDay(Instant.parse("2026-10-11T07:00:00Z").toEpochMilli()))
    }

    @Test
    fun `默认先用最新的模型`() {
        assertEquals("gemini-3.8-flash", GeminiClient.MODELS.first().first)
    }
}
