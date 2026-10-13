package com.personal.mise

import com.personal.mise.ai.AiRecipeEditor
import com.personal.mise.ai.AiStep
import com.personal.mise.ai.EditIngredient
import com.personal.mise.ai.EditableRecipe
import com.personal.mise.importer.ImportException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecipeEditorTest {

    @Test
    fun `解析 AI 改好的菜谱`() {
        val edit = AiRecipeEditor.parseResponse(
            JSONObject(
                """{"title":"番茄炒蛋","servings":"4人份",
                "ingredients":[{"name":"番茄","amount":"4个","source":1},{"name":" ","amount":"1克"},{"name":"葱","amount":"克5","source":null}],
                "steps":[{"text":"番茄切块","source":1},{"text":"炒蛋","timerMinutes":2,"source":2}],
                "summary":"份量翻倍了"}""",
            ),
        )
        assertEquals("番茄炒蛋", edit.recipe.title)
        assertEquals("4人份", edit.recipe.servings)
        assertEquals(listOf(EditIngredient("番茄", "4个", 1), EditIngredient("葱", "5克", null)), edit.recipe.ingredients)
        assertEquals(listOf(1, 2), edit.recipe.steps.map { it.source })
        assertEquals(2.0, edit.recipe.steps[1].timerMinutes!!, 0.0)
        assertEquals("份量翻倍了", edit.summary)
    }

    @Test
    fun `份量为 null 时是空的`() {
        val edit = AiRecipeEditor.parseResponse(
            JSONObject("""{"title":"a","servings":null,"ingredients":[],"steps":[{"text":"炒"}],"summary":"好"}"""),
        )
        assertEquals("", edit.recipe.servings)
    }

    @Test
    fun `返回内容不对时报错`() {
        assertThrows(ImportException::class.java) { AiRecipeEditor.parseResponse(JSONObject("{}")) }
        assertThrows(ImportException::class.java) {
            AiRecipeEditor.parseResponse(JSONObject("""{"title":"a","ingredients":[],"steps":[]}"""))
        }
    }

    @Test
    fun `按来源对上原来的项，拆开时只有第一项接着用`() {
        assertEquals(mapOf(0 to 0, 2 to 1), AiRecipeEditor.match(listOf(1, 1, 2, null), 2))
        // 编号超出范围的不算
        assertEquals(emptyMap<Int, Int>(), AiRecipeEditor.match(listOf(0, 3), 2))
    }

    @Test
    fun `来源对不上时按名字找`() {
        val old = listOf("盐", "糖")
        val new = listOf("糖", "盐", "醋")
        val map = AiRecipeEditor.match(listOf(null, 1, null), old.size) { j, i -> new[j] == old[i] }
        assertEquals(mapOf(0 to 1, 1 to 0), map)
    }

    @Test
    fun `提示里带上要求和编号`() {
        val prompt = AiRecipeEditor.prompt(
            EditableRecipe("番茄炒蛋", "", listOf(EditIngredient("番茄", "2个")), listOf(AiStep("炒", 3.0))),
            "改成4人份",
        )
        assertTrue("改成4人份" in prompt)
        val recipe = AiRecipeEditor.toJson(EditableRecipe("a", "", emptyList(), listOf(AiStep("炒", null))))
        assertEquals(1, recipe.getJSONArray("steps").getJSONObject(0).getInt("no"))
        assertNull(recipe.getJSONArray("steps").getJSONObject(0).opt("timerMinutes").takeIf { it != JSONObject.NULL })
    }
}
