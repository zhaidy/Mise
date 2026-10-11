package com.personal.mise

import com.personal.mise.ai.AiRecipeTranslator
import com.personal.mise.ai.RecipeText
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecipeTranslatorTest {

    private val english = RecipeText(
        title = "Garlic Butter Shrimp",
        servings = "4 servings",
        ingredients = listOf("shrimp, peeled" to "1 lb", "garlic" to "4 cloves", "salt" to ""),
        steps = listOf("Melt the butter in a large skillet over medium heat.", "Add shrimp and cook for 2 minutes."),
    )

    @Test
    fun `英文菜谱要翻译`() {
        assertTrue(AiRecipeTranslator.isEnglish(english))
    }

    @Test
    fun `中文菜谱和夹着英文单位的中文菜谱不翻译`() {
        val chinese = RecipeText("可乐鸡翅", null, listOf("鸡翅" to "500g", "可乐" to "330ml"), listOf("鸡翅两面划刀，冷水下锅焯水", "加可乐小火焖15分钟"))
        assertFalse(AiRecipeTranslator.isEnglish(chinese))
        assertFalse(AiRecipeTranslator.isEnglish(RecipeText("", null, emptyList(), emptyList())))
    }

    @Test
    fun `按原来的顺序换成翻译`() {
        val reply = JSONObject(
            """{"title":"蒜香黄油虾","servings":"4人份",
               "ingredients":[{"name":"虾","amount":"1磅（去壳）"},{"name":"大蒜","amount":"4瓣"},{"name":"盐","amount":""}],
               "steps":["中火把黄油在大平底锅里化开","放入虾煮2分钟"]}""",
        )
        val out = AiRecipeTranslator.parseResponse(reply, english)
        assertEquals("蒜香黄油虾", out.title)
        assertEquals("4人份", out.servings)
        assertEquals(listOf("虾" to "1磅（去壳）", "大蒜" to "4瓣", "盐" to ""), out.ingredients)
        assertEquals(listOf("中火把黄油在大平底锅里化开", "放入虾煮2分钟"), out.steps)
    }

    @Test
    fun `空着的项保留原文`() {
        val reply = JSONObject(
            """{"title":"","servings":null,"ingredients":[{"name":"虾","amount":"1磅"},{"name":"","amount":"4瓣"},{"name":"盐","amount":""}],
               "steps":["化开黄油",""]}""",
        )
        val out = AiRecipeTranslator.parseResponse(reply, english)
        assertEquals("Garlic Butter Shrimp", out.title)
        assertEquals("4 servings", out.servings)
        assertEquals("garlic", out.ingredients[1].first)
        assertEquals("Add shrimp and cook for 2 minutes.", out.steps[1])
    }

    @Test
    fun `条数对不上时报错`() {
        val reply = JSONObject("""{"title":"蒜香黄油虾","ingredients":[],"steps":["化开黄油","放入虾"]}""")
        assertThrows(ImportException::class.java) { AiRecipeTranslator.parseResponse(reply, english) }
    }
}
