package com.personal.mise

import com.personal.mise.ai.AiStep
import com.personal.mise.ai.AiStepSplitter
import com.personal.mise.importer.ImportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AiStepSplitterTest {

    private fun reply(inner: String) = org.json.JSONObject(inner)

    @Test
    fun `解析 AI 返回的步骤和计时`() {
        val steps = AiStepSplitter.parseResponse(
            reply("""{"steps":[{"text":"鸡胸肉切丁"},{"text":"小火焖10分钟","timerMinutes":10},{"text":" "}]}"""),
        )
        assertEquals(2, steps.size)
        assertEquals("鸡胸肉切丁", steps[0].text)
        assertNull(steps[0].timerMinutes)
        assertEquals(10.0, steps[1].timerMinutes!!, 0.0)
    }

    @Test
    fun `返回内容不对时报错`() {
        assertThrows(ImportException::class.java) { AiStepSplitter.parseResponse(reply("{}")) }
        assertThrows(ImportException::class.java) { AiStepSplitter.parseResponse(reply("""{"steps":[]}""")) }
    }

    @Test
    fun `解析原文步骤编号`() {
        val steps = AiStepSplitter.parseResponse(
            reply("""{"steps":[{"text":"切丁","source":1},{"text":"炒","source":0},{"text":"装盘"}]}"""),
        )
        assertEquals(listOf(1, null, null), steps.map { it.source })
    }

    @Test
    fun `空步骤不发送但保留编号`() {
        assertEquals("1. 切丁\n3. 下锅 翻炒", AiStepSplitter.numbered(listOf("切丁", " ", "下锅\n翻炒")))
    }

    @Test
    fun `原步骤的图片跟到拆出来的第一步`() {
        val steps = listOf(AiStep("a", null, 1), AiStep("b", null, 1), AiStep("c", null, 2), AiStep("d", null, 3))
        assertEquals(mapOf(0 to 0, 1 to 2, 2 to 3), AiStepSplitter.carryOver(listOf(true, false, true), steps))
    }

    @Test
    fun `带图的步骤对不上时并到前一步，前面没有才返回 null`() {
        val steps = listOf(AiStep("a", null, 1), AiStep("b", null, null))
        assertEquals(mapOf(0 to 0, 1 to 0), AiStepSplitter.carryOver(listOf(false, true), steps))
        assertEquals(mapOf(0 to 0), AiStepSplitter.carryOver(listOf(true, false), steps))
        assertNull(AiStepSplitter.carryOver(listOf(true, false), listOf(AiStep("a", null, 2))))
        // 原来只有一步时不看 source
        assertEquals(mapOf(0 to 0), AiStepSplitter.carryOver(listOf(true), steps))
    }

    @Test
    fun `并进来的原步骤排在后面`() {
        val carry = mapOf(2 to 1, 0 to 0, 1 to 0)
        assertEquals(mapOf(0 to listOf("a", "b"), 1 to listOf("c")), AiStepSplitter.sourcesOf(listOf("a", "b", "c"), carry))
    }
}
