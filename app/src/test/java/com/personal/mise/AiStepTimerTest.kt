package com.personal.mise

import com.personal.mise.ai.AiStepTimer
import com.personal.mise.ai.StepTime
import com.personal.mise.ai.StepTimes
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStepTimerTest {

    private val steps = listOf("排骨切块", " ", "小火炖 40 分钟", "撒葱花")

    @Test
    fun promptNumbersStepsAndSkipsBlank() {
        val prompt = AiStepTimer.prompt("炖排骨", steps)
        assertTrue(prompt.contains("菜名：炖排骨\n"))
        assertTrue(prompt.contains("1. 排骨切块\n"))
        assertTrue(prompt.contains("3. 小火炖 40 分钟\n"))
        assertFalse(prompt.contains("2. \n"))
    }

    @Test
    fun parsesEachStepOnceByText() {
        val reply = JSONObject(
            """{"steps":[""" +
                """{"step":1,"minutes":5,"handsOff":false},""" +
                """{"step":1,"minutes":99,"handsOff":true},""" +
                """{"step":2,"minutes":3,"handsOff":false},""" +
                """{"step":3,"minutes":600,"handsOff":true},""" +
                """{"step":4,"minutes":-1,"handsOff":false},""" +
                """{"step":9,"minutes":2,"handsOff":false}""" +
                """]}""",
        )
        val times = AiStepTimer.parseResponse(reply, steps)
        assertEquals(StepTime(300, false), times.of("排骨切块"))
        // 一步最多按 4 小时算
        assertEquals(StepTime(14400, true), times.of(" 小火炖 40 分钟 "))
        assertNull(times.of("撒葱花"))
        assertTrue(times.missing(steps))
        assertFalse(times.missing(steps.take(3)))
    }

    @Test
    fun throwsWhenNothingUsable() {
        assertThrows(ImportException::class.java) {
            AiStepTimer.parseResponse(JSONObject("""{"steps":[{"step":2,"minutes":3,"handsOff":false}]}"""), steps)
        }
        assertThrows(ImportException::class.java) { AiStepTimer.parseResponse(JSONObject("{}"), steps) }
    }

    @Test
    fun jsonRoundTrip() {
        val times = StepTimes(mapOf("切块" to StepTime(120, false), "炖" to StepTime(2400, true)))
        assertEquals(times, StepTimes.fromJson(times.toJson().toString()))
        assertNull(StepTimes.fromJson("不是 JSON"))
        assertNull(StepTimes.fromJson(null))
    }
}
