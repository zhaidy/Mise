package com.personal.mise

import com.personal.mise.data.IngredientName
import com.personal.mise.data.Recipe
import com.personal.mise.data.RecipeSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeSearchTest {
    private val tomatoEgg = Recipe(id = 1, title = "番茄炒蛋", tags = "快手")
    private val braisedPork = Recipe(id = 2, title = "红烧肉", category = "家常菜", tags = "荤菜")
    private val eggSoup = Recipe(id = 3, title = "紫菜汤")
    private val recipes = listOf(tomatoEgg, braisedPork, eggSoup)
    private val ingredients = listOf(
        IngredientName(1, "番茄"),
        IngredientName(1, "鸡蛋"),
        IngredientName(2, "五花肉"),
        IngredientName(2, "冰糖"),
        IngredientName(3, "紫菜"),
        IngredientName(3, "鸡蛋"),
    )

    private fun ids(query: String) = RecipeSearch.search(query, recipes, ingredients).map { it.recipe.id }

    @Test
    fun `空搜索返回全部`() {
        assertEquals(listOf(1L, 2L, 3L), ids("  "))
    }

    @Test
    fun `按用料搜，带上对上的用料`() {
        val hits = RecipeSearch.search("鸡蛋", recipes, ingredients)
        assertEquals(listOf(1L, 3L), hits.map { it.recipe.id })
        assertEquals(listOf("鸡蛋"), hits.first().matchedIngredients)
    }

    @Test
    fun `多样用料要都有`() {
        assertEquals(listOf(1L), ids("鸡蛋 番茄"))
        assertEquals(listOf(3L), ids("鸡蛋，紫菜"))
        assertEquals(emptyList<Long>(), ids("鸡蛋 五花肉"))
    }

    @Test
    fun `菜名、分类、标签照样能搜`() {
        assertEquals(listOf(2L), ids("红烧"))
        assertEquals(listOf(2L), ids("家常菜"))
        assertEquals(listOf(2L), ids("#荤菜"))
        assertEquals(emptyList<String>(), RecipeSearch.search("红烧", recipes, ingredients).first().matchedIngredients)
    }

    @Test
    fun `菜名和用料可以混着搜`() {
        assertEquals(listOf(2L), ids("红烧 冰糖"))
    }
}
