package com.personal.mise

import com.personal.mise.ai.AiRecipeReviewer
import com.personal.mise.ai.RecipeReview
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecipeReviewerTest {

    @Test
    fun `提示里写上菜名、用料和做法，空的跳过`() {
        val prompt = AiRecipeReviewer.prompt("红烧肉", "3人份", listOf("五花肉" to "500克", "" to "", "冰糖" to ""), listOf("焯水", " "))
        assertTrue(prompt.contains("菜名：红烧肉\n"))
        assertTrue(prompt.contains("份量：3人份\n"))
        assertTrue(prompt.contains("- 五花肉：500克\n"))
        assertTrue(prompt.contains("- 冰糖（没写用量）\n"))
        assertFalse(prompt.contains("- \n"))
        assertTrue(prompt.contains("做法：\n1. 焯水\n"))
        assertTrue(prompt.endsWith("做法：\n1. 焯水\n"))
    }

    @Test
    fun `解析评价：分数取整，空行和重复的去掉`() {
        val review = RecipeReview.parse(
            JSONObject("""{"score":7.6,"summary":" 经典做法 ","highlights":["步骤清楚","","步骤清楚"],"concerns":[" 糖色容易炒糊 "]}"""),
        )
        assertEquals(RecipeReview(8, "经典做法", listOf("步骤清楚"), listOf("糖色容易炒糊")), review)
    }

    @Test
    fun `分数不对或没有总评时不算`() {
        assertNull(RecipeReview.parse(JSONObject("""{"score":0,"summary":"还行"}""")))
        assertNull(RecipeReview.parse(JSONObject("""{"score":11,"summary":"还行"}""")))
        assertNull(RecipeReview.parse(JSONObject("""{"score":7,"summary":" "}""")))
        assertNull(RecipeReview.parse(JSONObject("""{"summary":"还行"}""")))
    }

    @Test
    fun `存成 JSON 再读回来一样，格式不对时为空`() {
        val review = RecipeReview(9, "很靠谱", listOf("火候写得细"), emptyList())
        assertEquals(review, RecipeReview.fromJson(review.toJson().toString()))
        assertNull(RecipeReview.fromJson("not json"))
        assertNull(RecipeReview.fromJson(null))
    }

    @Test
    fun `照评价修改的要求列出要注意的地方`() {
        val ask = AiRecipeReviewer.fixInstruction(RecipeReview(6, "还行", listOf("步骤清楚"), listOf("糖色容易炒糊", "没写炖多久")))
        assertTrue(ask.contains("- 糖色容易炒糊\n- 没写炖多久\n"))
        assertFalse(ask.contains("步骤清楚"))
    }
}
