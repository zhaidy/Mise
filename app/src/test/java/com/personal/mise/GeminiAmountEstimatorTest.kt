package com.personal.mise

import com.personal.mise.ai.GeminiAmountEstimator
import com.personal.mise.importer.ImportException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAmountEstimatorTest {

    private fun reply(json: String) = JSONObject()
        .put("candidates", JSONArray().put(JSONObject().put("content", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", json))))))
        .toString()

    @Test
    fun needsEstimateOnlyForVagueOrMissingAmounts() {
        assertTrue(GeminiAmountEstimator.needsEstimate("盐", "适量"))
        assertTrue(GeminiAmountEstimator.needsEstimate("葱", ""))
        assertTrue(GeminiAmountEstimator.needsEstimate("糖", " 少许 "))
        assertFalse(GeminiAmountEstimator.needsEstimate("猪肉", "500克"))
        assertFalse(GeminiAmountEstimator.needsEstimate("番茄", "一个半"))
        assertFalse(GeminiAmountEstimator.needsEstimate("", "适量"))
        assertFalse(GeminiAmountEstimator.needsEstimate("盐", "约3克（估算）"))
    }

    @Test
    fun promptMarksOnlyTargets() {
        val prompt = GeminiAmountEstimator.prompt("猪肉炖粉条", "", listOf("猪肉" to "500克", "盐" to "适量"), listOf("炖熟"), listOf(1))
        assertTrue(prompt.contains("1. 猪肉：500克\n"))
        assertTrue(prompt.contains("2. 盐【待估】（原文：适量）\n"))
    }

    @Test
    fun parsesEstimatesAndMarksThem() {
        val body = reply(
            """{"estimates":[{"ingredient":2,"amount":"3克"},{"ingredient":1,"amount":"100克"},""" +
                """{"ingredient":3,"amount":""},{"ingredient":4,"amount":"约1勺"}]}""",
        )
        val result = GeminiAmountEstimator.parseResponse(body, listOf(1, 2, 3))
        assertEquals(mapOf(1 to "约3克（估算）", 3 to "约1勺（估算）"), result)
    }

    @Test
    fun throwsWhenNothingEstimated() {
        assertThrows(ImportException::class.java) {
            GeminiAmountEstimator.parseResponse(reply("""{"estimates":[]}"""), listOf(0))
        }
    }
}
