package com.personal.mise

import com.personal.mise.ai.AiCalorieEstimator
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCalorieEstimatorTest {

    private val ingredients = listOf("五花肉" to "500克", "" to "", "冰糖" to "", "生抽" to "2勺")

    @Test
    fun promptListsIngredientsWithAmounts() {
        val prompt = AiCalorieEstimator.prompt("红烧肉", "3人份", ingredients, listOf("焯水", " "))
        assertTrue(prompt.contains("菜名：红烧肉\n"))
        assertTrue(prompt.contains("份量：3人份\n"))
        assertTrue(prompt.contains("1. 五花肉：500克\n"))
        assertTrue(prompt.contains("3. 冰糖（没写用量）\n"))
        assertTrue(prompt.contains("4. 生抽：2勺\n"))
        assertFalse(prompt.contains("2. \n"))
        assertTrue(prompt.contains("做法：\n1. 焯水\n"))
    }

    @Test
    fun sumsEachIngredientOnce() {
        val reply = JSONObject(
            """{"items":[""" +
                """{"ingredient":1,"kcal":2500},""" +
                """{"ingredient":1,"kcal":9999},""" +
                """{"ingredient":2,"kcal":100},""" +
                """{"ingredient":3,"kcal":59.6},""" +
                """{"ingredient":4,"kcal":-5},""" +
                """{"ingredient":9,"kcal":300},""" +
                """{"ingredient":4,"kcal":20.2}""" +
                """]}""",
        )
        // 1 只算第一次；2 名称为空；4 的负数跳过、取后面的 20.2；9 越界
        assertEquals(2580, AiCalorieEstimator.parseResponse(reply, ingredients))
    }

    @Test
    fun throwsWhenNothingUsable() {
        assertThrows(ImportException::class.java) {
            AiCalorieEstimator.parseResponse(JSONObject("""{"items":[{"ingredient":1,"kcal":0}]}"""), ingredients)
        }
        assertThrows(ImportException::class.java) {
            AiCalorieEstimator.parseResponse(JSONObject("""{"foo":1}"""), ingredients)
        }
    }
}
