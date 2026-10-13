package com.personal.mise

import com.personal.mise.data.IngredientDraft
import com.personal.mise.data.RecipeDraft
import com.personal.mise.data.StepDraft
import com.personal.mise.util.RecipeChanges
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeChangesTest {

    private val before = RecipeDraft(
        title = "红烧肉",
        servings = "2人份",
        ingredients = listOf(IngredientDraft(name = "五花肉", amountText = "500克"), IngredientDraft(name = "冰糖", amountText = ""), IngredientDraft(name = "生抽", amountText = "2勺")),
        steps = listOf(StepDraft(text = "焯水"), StepDraft(text = "炒糖色"), StepDraft(text = "小火炖", timerSeconds = 2400)),
    )

    @Test
    fun `没改就没有改动`() {
        assertEquals(emptyList<String>(), RecipeChanges.describe(before, before.copy(ingredients = before.ingredients + IngredientDraft())))
    }

    @Test
    fun `列出用料和步骤的改动`() {
        val after = before.copy(
            ingredients = listOf(IngredientDraft(name = "五花肉", amountText = "500克"), IngredientDraft(name = "冰糖", amountText = "30克"), IngredientDraft(name = "老抽", amountText = "1勺")),
            steps = listOf(StepDraft(text = "焯水"), StepDraft(text = "冷锅冷油小火炒糖色，冒小泡就下肉"), StepDraft(text = "小火炖", timerSeconds = 3600), StepDraft(text = "大火收汁")),
        )
        assertEquals(
            listOf(
                "冰糖：没写用量 → 30克",
                "去掉用料：生抽",
                "加了用料：老抽 1勺",
                "步骤：3 步 → 4 步",
                "第 2 步改成：冷锅冷油小火炒糖色，冒小泡就下肉（原来：炒糖色）",
                "第 3 步计时：40 分钟 → 60 分钟",
                "加了第 4 步：大火收汁",
            ),
            RecipeChanges.describe(before, after),
        )
    }

    @Test
    fun `菜名和份量`() {
        assertEquals(
            listOf("菜名：红烧肉 → 毛氏红烧肉", "份量：2人份 → 没写"),
            RecipeChanges.describe(before, before.copy(title = "毛氏红烧肉", servings = null)),
        )
    }

    @Test
    fun `一步后面补了几句只写补了什么`() {
        val after = before.copy(steps = listOf(StepDraft(text = "焯水"), StepDraft(text = "炒糖色，冒小泡就下肉"), StepDraft(text = "小火炖", timerSeconds = 2400)))
        assertEquals(listOf("第 2 步补充：冒小泡就下肉"), RecipeChanges.describe(before, after))
    }

    @Test
    fun `去掉一步时后面的步骤标出原来是第几步`() {
        val after = before.copy(steps = listOf(StepDraft(text = "焯水"), StepDraft(text = "小火炖", timerSeconds = 2400)))
        assertEquals(listOf("步骤：3 步 → 2 步", "去掉原来第 2 步：炒糖色"), RecipeChanges.describe(before, after))
        assertEquals(listOf(0 to 0, 1 to null, 2 to 1), RecipeChanges.align(listOf("a", "b", "c"), listOf("a", "c")))
    }
}
