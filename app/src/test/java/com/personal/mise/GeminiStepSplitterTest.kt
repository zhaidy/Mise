package com.personal.mise

import com.personal.mise.ai.GeminiStepSplitter
import com.personal.mise.importer.ImportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiStepSplitterTest {

    private fun reply(inner: String) =
        """{"candidates":[{"content":{"parts":[{"text":${org.json.JSONObject.quote(inner)}}]}}]}"""

    @Test
    fun `解析 Gemini 返回的步骤和计时`() {
        val steps = GeminiStepSplitter.parseResponse(
            reply("""{"steps":[{"text":"鸡胸肉切丁"},{"text":"小火焖10分钟","timerMinutes":10},{"text":" "}]}"""),
        )
        assertEquals(2, steps.size)
        assertEquals("鸡胸肉切丁", steps[0].text)
        assertNull(steps[0].timerMinutes)
        assertEquals(10.0, steps[1].timerMinutes!!, 0.0)
    }

    @Test
    fun `返回内容不对时报错`() {
        assertThrows(ImportException::class.java) { GeminiStepSplitter.parseResponse("{}") }
        assertThrows(ImportException::class.java) { GeminiStepSplitter.parseResponse(reply("""{"steps":[]}""")) }
    }
}
