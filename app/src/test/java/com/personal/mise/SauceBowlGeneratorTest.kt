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

    private fun names(ingredients: List<IngredientInput>, bowl: SauceBowlGenerator.GeneratedBowl) =
        bowl.items.map { ingredients[it.ingredientIndex].name }

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

        // 两步连续下锅，合成一个碗
        assertEquals(1, bowls.size)
        val bowl = bowls[0]
        assertEquals("调料碗 A", bowl.label)
        assertEquals(0, bowl.stepIndex)
        assertEquals(listOf("五花肉", "生抽", "老抽", "白糖"), names(ingredients, bowl))
        assertEquals(listOf("500克", "2 汤匙", "1 茶匙", "1 汤匙"), bowl.items.map { it.amountText })
    }

    @Test
    fun `只有一种用料的步骤也配碗`() {
        val ingredients = listOf(ing("青菜", "300克"), ing("盐", "1 茶匙"))
        val bowls = SauceBowlGenerator.generate(ingredients, listOf("青菜下锅翻炒", "加盐出锅"))
        assertEquals(1, bowls.size)
        assertEquals(listOf("青菜", "盐"), names(ingredients, bowls[0]))
    }

    @Test
    fun `先下锅炒一会儿的用料单独一碗，下锅步骤不算切配`() {
        val ingredients = listOf(
            ing("菜花", "1 个"),
            ing("五花肉", "100克"),
            ing("干辣椒", "5克"),
            ing("蒜", "3 瓣"),
            ing("姜", "5克"),
            ing("盐", "3克"),
            ing("生抽", "1 勺"),
        )
        val steps = listOf(
            "菜花削成小朵",
            "五花肉切薄片",
            "干辣椒剪段，姜蒜切片",
            "烧热锅",
            "五花肉入锅小火慢慢煸炒出油，将肉推至一边",
            "干红辣椒与姜、蒜放入煸炒出味",
            "放入菜花大火翻炒",
            "加盐、生抽炒匀出锅",
        )
        val bowls = SauceBowlGenerator.generate(ingredients, steps)
        assertEquals(
            listOf(setOf("五花肉"), setOf("干辣椒", "姜", "蒜"), setOf("菜花", "盐", "生抽")),
            bowls.map { names(ingredients, it).toSet() },
        )
        assertEquals(listOf(4, 5, 6), bowls.map { it.stepIndex })
        // 葱姜蒜在切配步骤里出现过，不算分次加入
        assertTrue(bowls.flatMap { it.items }.none { it.amountText.startsWith("分次加入") })
    }

    @Test
    fun `只切配的步骤也是一个碗，配料和主料都算`() {
        val ingredients = listOf(ing("胡萝卜", "1 根"), ing("香菜", "2 根"), ing("葱", "1 根"), ing("姜", "3 片"), ing("蒜", "3 瓣"))
        val bowls = SauceBowlGenerator.generate(ingredients, listOf("胡萝卜、香菜和葱姜分别切丝，蒜切片备用"))
        assertEquals(1, bowls.size)
        assertEquals(listOf("胡萝卜", "香菜", "葱", "姜", "蒜"), names(ingredients, bowls[0]))
    }

    @Test
    fun `没有用料的步骤把前后分成两个碗`() {
        val ingredients = listOf(ing("蒜末", "3 瓣"), ing("蚝油", "1 汤匙"), ing("青菜", "300克"), ing("盐", "1 茶匙"))
        val steps = listOf("下蒜末、蚝油炒香", "转大火烧热锅", "下青菜，加盐翻炒")
        val bowls = SauceBowlGenerator.generate(ingredients, steps)
        assertEquals(2, bowls.size)
        assertEquals(listOf("蒜末", "蚝油"), names(ingredients, bowls[0]))
        assertEquals(listOf("青菜", "盐"), names(ingredients, bowls[1]))
        assertEquals(2, bowls[1].stepIndex)
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
        val names = names(ingredients, bowls[0])
        assertEquals(listOf("蒜末", "蚝油", "生抽"), names)
        assertFalse("食用油" in names)
    }

    @Test
    fun `带计时的步骤收尾，同一调料分几个碗时标为分次加入`() {
        val ingredients = listOf(ing("生抽", "3 汤匙"), ing("料酒", "1 汤匙"), ing("白糖", "1 茶匙"))
        val steps = listOf("肉用生抽和料酒腌 10 分钟", "下锅后加生抽、白糖")
        val bowls = SauceBowlGenerator.generate(ingredients, steps, listOf(600, null))
        assertEquals(2, bowls.size)
        assertEquals("调料碗 A", bowls[0].label)
        assertEquals("调料碗 B", bowls[1].label)
        assertEquals("分次加入，共 3 汤匙", bowls[0].items.first { ingredients[it.ingredientIndex].name == "生抽" }.amountText)
        assertEquals("1 汤匙", bowls[0].items.first { ingredients[it.ingredientIndex].name == "料酒" }.amountText)
    }

    @Test
    fun `主料只进最后用到它的碗`() {
        val ingredients = listOf(ing("胡萝卜", "1 根"), ing("盐", "1 茶匙"))
        val steps = listOf("胡萝卜切丝备用", "烧热锅", "下胡萝卜丝，加盐")
        val bowls = SauceBowlGenerator.generate(ingredients, steps)
        assertEquals(1, bowls.size)
        assertEquals(2, bowls[0].stepIndex)
        assertEquals(listOf("胡萝卜", "盐"), names(ingredients, bowls[0]))
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
