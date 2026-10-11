package com.personal.mise

import com.personal.mise.ai.AiAmountEstimator
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAmountEstimatorTest {

    private fun reply(json: String) = JSONObject(json)

    @Test
    fun needsEstimateOnlyForVagueOrMissingAmounts() {
        assertTrue(AiAmountEstimator.needsEstimate("盐", "适量"))
        assertTrue(AiAmountEstimator.needsEstimate("葱", ""))
        assertTrue(AiAmountEstimator.needsEstimate("糖", " 少许 "))
        assertFalse(AiAmountEstimator.needsEstimate("猪肉", "500克"))
        assertFalse(AiAmountEstimator.needsEstimate("番茄", "一个半"))
        assertFalse(AiAmountEstimator.needsEstimate("", "适量"))
        assertFalse(AiAmountEstimator.needsEstimate("盐", "约3克（估算）"))
    }

    @Test
    fun needsGramsForConcreteNonGramAmounts() {
        assertTrue(AiAmountEstimator.needsGrams("生抽", "15毫升"))
        assertTrue(AiAmountEstimator.needsGrams("鸡蛋", "2个"))
        assertTrue(AiAmountEstimator.needsGrams("猪肉", "半斤"))
        assertTrue(AiAmountEstimator.needsGrams("糖", "约1勺（估算）"))
        assertFalse(AiAmountEstimator.needsGrams("猪肉", "500克"))
        assertFalse(AiAmountEstimator.needsGrams("面粉", "200 g"))
        assertFalse(AiAmountEstimator.needsGrams("牛肉", "1kg"))
        assertFalse(AiAmountEstimator.needsGrams("生抽", "15毫升 ≈18克"))
        assertFalse(AiAmountEstimator.needsGrams("盐", "适量"))
        assertFalse(AiAmountEstimator.needsGrams("", "2个"))
    }

    @Test
    fun promptMarksOnlyTargets() {
        val prompt = AiAmountEstimator.prompt(
            "猪肉炖粉条", "", listOf("猪肉" to "500克", "盐" to "适量", "生抽" to "1勺"), listOf("炖熟"),
        )
        assertTrue(prompt.contains("1. 猪肉：500克\n"))
        assertTrue(prompt.contains("2. 盐【待估】（原文：适量）\n"))
        assertTrue(prompt.contains("3. 生抽：1勺【补克重】\n"))
    }

    @Test
    fun parsesEstimatesAndGrams() {
        val ingredients = listOf(
            "猪肉" to "500克", "盐" to "适量", "葱" to "", "糖" to "少许", "生抽" to "15毫升", "鸡蛋" to "2个", "油" to "",
        )
        val body = reply(
            """{"estimates":[""" +
                """{"ingredient":1,"amount":"","grams":500},""" +
                """{"ingredient":2,"amount":"3克","grams":3},""" +
                """{"ingredient":3,"amount":"约1根","grams":20},""" +
                """{"ingredient":4,"amount":"","grams":5},""" +
                """{"ingredient":5,"amount":"","grams":18},""" +
                """{"ingredient":6,"amount":"","grams":0},""" +
                """{"ingredient":7,"amount":"","grams":0}]}""",
        )
        val result = AiAmountEstimator.parseResponse(body, ingredients)
        assertEquals(
            mapOf(
                1 to "约3克（估算）",
                2 to "约1根（估算） ≈20克",
                3 to "约5克（估算）",
                4 to "15毫升 ≈18克",
            ),
            result,
        )
    }

    @Test
    fun throwsWhenNothingEstimated() {
        assertThrows(ImportException::class.java) {
            AiAmountEstimator.parseResponse(reply("""{"estimates":[]}"""), listOf("盐" to "适量"))
        }
    }
}
