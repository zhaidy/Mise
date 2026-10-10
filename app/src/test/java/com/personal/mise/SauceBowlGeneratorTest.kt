package com.personal.mise

import com.personal.mise.bowl.SauceBowlGenerator
import com.personal.mise.bowl.SauceBowlGenerator.IngredientInput
import com.personal.mise.bowl.SeasoningDictionary
import com.personal.mise.data.IngredientKind.MAIN
import com.personal.mise.data.IngredientKind.SEASONING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SauceBowlGeneratorTest {

    private fun ing(name: String, amount: String, kind: String? = null) =
        IngredientInput(name, amount, kind ?: if (SeasoningDictionary.isSeasoning(name)) SEASONING else MAIN)

    @Test
    fun `生抽老抽糖在同一步合成一个碗，用量取用料表原文`() {
        val ingredients = listOf(
            ing("五花肉", "500克"),
            ing("生抽", "2 汤匙"),
            ing("老抽", "1 茶匙"),
            ing("白糖", "1 汤匙"),
        )
        val steps = listOf("五花肉切块焯水", "加入生抽、老抽、糖，翻炒均匀")

        val bowls = SauceBowlGenerator.generate(ingredients, steps)

        assertEquals(1, bowls.size)
        val bowl = bowls[0]
        assertEquals("调料碗 A", bowl.label)
        assertEquals(1, bowl.stepIndex)
        assertEquals(listOf("生抽", "老抽", "白糖"), bowl.items.map { ingredients[it.ingredientIndex].name })
        assertEquals(listOf("2 汤匙", "1 茶匙", "1 汤匙"), bowl.items.map { it.amountText })
    }

    @Test
    fun `只有一种调料的步骤不生成调料碗`() {
        val ingredients = listOf(ing("青菜", "300克"), ing("盐", "1 茶匙"))
        val bowls = SauceBowlGenerator.generate(ingredients, listOf("青菜下锅炒软", "加盐出锅"))
        assertTrue(bowls.isEmpty())
    }

    @Test
    fun `炒菜的油不进碗，葱姜蒜和调料同步下锅时一起进碗`() {
        val ingredients = listOf(
            ing("食用油", "适量"),
            ing("蒜末", "3 瓣"),
            ing("蚝油", "1 汤匙"),
            ing("生抽", "1 汤匙"),
        )
        val bowls = SauceBowlGenerator.generate(ingredients, listOf("热锅倒油，下蒜末、蚝油和生抽炒香"))
        assertEquals(1, bowls.size)
        val names = bowls[0].items.map { ingredients[it.ingredientIndex].name }
        assertEquals(listOf("蒜末", "蚝油", "生抽"), names)
        assertFalse("食用油" in names)
    }

    @Test
    fun `只有葱姜蒜和一种调料不配碗`() {
        val ingredients = listOf(ing("葱", "1 根"), ing("姜", "3 片"), ing("料酒", "1 汤匙"))
        assertTrue(SauceBowlGenerator.generate(ingredients, listOf("放葱姜和料酒")).isEmpty())
    }

    @Test
    fun `同一调料分几步加入时标为分次加入`() {
        val ingredients = listOf(ing("生抽", "3 汤匙"), ing("料酒", "1 汤匙"), ing("白糖", "1 茶匙"))
        val steps = listOf("肉用生抽和料酒腌 10 分钟", "下锅后加生抽、白糖")
        val bowls = SauceBowlGenerator.generate(ingredients, steps)
        assertEquals(2, bowls.size)
        assertEquals("调料碗 A", bowls[0].label)
        assertEquals("调料碗 B", bowls[1].label)
        assertEquals("分次加入，共 3 汤匙", bowls[0].items.first { ingredients[it.ingredientIndex].name == "生抽" }.amountText)
        assertEquals("1 汤匙", bowls[0].items.first { ingredients[it.ingredientIndex].name == "料酒" }.amountText)
    }

    @Test
    fun `较长的调料名优先匹配`() {
        val ingredients = listOf(ing("冰糖", "20克"), ing("白糖", "5克"), ing("老抽", "1 汤匙"))
        val found = SauceBowlGenerator.findIngredients("加冰糖和老抽", ingredients)
        assertEquals(listOf(0, 2), found)
    }

    @Test
    fun `调料词典分类`() {
        assertTrue(SeasoningDictionary.isSeasoning("生抽"))
        assertTrue(SeasoningDictionary.isSeasoning("李锦记蚝油"))
        assertTrue(SeasoningDictionary.isSeasoning("水淀粉"))
        assertFalse(SeasoningDictionary.isSeasoning("鸡胸肉"))
        assertTrue(SeasoningDictionary.isAromatic("小葱"))
        assertFalse(SeasoningDictionary.isAromatic("洋葱"))
    }
}
