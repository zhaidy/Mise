package com.personal.mise

import com.personal.mise.ai.AiRecipeEditor
import com.personal.mise.ai.AiStep
import com.personal.mise.ai.EditIngredient
import com.personal.mise.ai.EditableRecipe
import com.personal.mise.data.IngredientDraft
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeDraft
import com.personal.mise.data.StepDraft
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

    private val draft = RecipeDraft(
        id = 7,
        title = "麻婆豆腐",
        servings = "2人份",
        calories = 500,
        tags = listOf("川菜"),
        ingredients = listOf(
            IngredientDraft(1, "豆腐", "1块", IngredientKind.MAIN),
            IngredientDraft(2, "花椒", "1茶匙", IngredientKind.SEASONING),
            IngredientDraft(3, "", "", IngredientKind.MAIN),
        ),
        steps = listOf(
            StepDraft(10, "豆腐切块", listOf("a.jpg"), null),
            StepDraft(11, "炒香花椒", listOf("b.jpg"), 120),
            StepDraft(12, "小火煮", emptyList(), 300),
        ),
    )

    @Test
    fun `发给 AI 的菜谱不带空行，计时换成分钟`() {
        val recipe = AiRecipeEditor.editable(draft)
        assertEquals(listOf("豆腐", "花椒"), recipe.ingredients.map { it.name })
        assertEquals(listOf(null, 2.0, 5.0), recipe.steps.map { it.timerMinutes })
    }

    @Test
    fun `改好的菜谱接着用原来的编号、分类和步骤图`() {
        val edited = EditableRecipe(
            title = "",
            servings = "4人份",
            ingredients = listOf(
                EditIngredient("豆腐", "2块", 1),
                EditIngredient("黑胡椒", "1茶匙", 2),
                EditIngredient("盐", "2克", null),
                EditIngredient("葱", "1根", null),
            ),
            steps = listOf(AiStep("豆腐切块", null, 1), AiStep("小火煮", 6.0, 3)),
        )
        val after = AiRecipeEditor.apply(draft, edited)
        assertEquals("麻婆豆腐", after.title)
        assertEquals("4人份", after.servings)
        assertEquals(listOf("川菜"), after.tags)
        assertEquals(500, after.calories)
        assertEquals(listOf(1L, 2L, 0L, 0L), after.ingredients.map { it.id })
        assertEquals(IngredientKind.SEASONING, after.ingredients[1].kind)
        assertEquals(IngredientKind.SEASONING, after.ingredients[2].kind)
        assertEquals(IngredientKind.MAIN, after.ingredients[3].kind)
        assertEquals(listOf(StepDraft(10, "豆腐切块", listOf("a.jpg"), null), StepDraft(12, "小火煮", emptyList(), 360)), after.steps)
        assertEquals(setOf("b.jpg"), AiRecipeEditor.droppedImages(draft, after))
    }

    @Test
    fun `没改就没有修改建议，改了用料提醒重估热量`() {
        val same = AiRecipeEditor.editable(draft)
        assertNull(AiRecipeEditor.propose(draft, com.personal.mise.ai.AiRecipeEdit(same, "不用改")))
        val changed = same.copy(
            ingredients = listOf(EditIngredient("豆腐", "2块", 1), EditIngredient("花椒", "1茶匙", 2)),
            steps = same.steps.mapIndexed { i, step -> step.copy(source = i + 1) },
        )
        val proposal = AiRecipeEditor.propose(draft, com.personal.mise.ai.AiRecipeEdit(changed, "豆腐加倍"), fromReview = true)!!
        assertEquals("豆腐加倍", proposal.summary)
        assertEquals(listOf("豆腐：1块 → 2块", "用料变了，热量可以重新估算"), proposal.changes)
        assertTrue(proposal.fromReview)
        assertEquals("2块", proposal.after.ingredients.first().amountText)
    }
}
