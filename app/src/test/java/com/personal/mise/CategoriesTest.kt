package com.personal.mise

import com.personal.mise.data.Categories
import com.personal.mise.data.CategoryCount
import com.personal.mise.data.CategoryNode
import com.personal.mise.data.Recipe
import com.personal.mise.data.RecipeFilter
import com.personal.mise.data.RecipeTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    @Test
    fun `标签按逗号、空格、井号拆开并去重`() {
        assertEquals(listOf("荤菜", "烘焙", "甜品", "好吃"), RecipeTags.parse("#荤菜 #烘焙，甜品、好吃 #荤菜"))
        assertEquals(emptyList<String>(), RecipeTags.parse("  # , "))
        assertEquals("荤菜,好吃", RecipeTags.format(listOf("#荤菜", "好吃", "荤菜")))
    }

    @Test
    fun `大类按菜谱数排，小类挂在大类下`() {
        val tree = Categories.tree(
            listOf(
                CategoryCount("汤", null, 1),
                CategoryCount("家常菜", "炒菜", 3),
                CategoryCount("家常菜", "炖菜", 1),
                CategoryCount("家常菜", null, 1),
            )
        )
        assertEquals(listOf(CategoryNode("家常菜", listOf("炒菜", "炖菜")), CategoryNode("汤", emptyList())), tree)
    }

    @Test
    fun `标签按用得多少排`() {
        assertEquals(listOf("好吃", "荤菜", "甜品"), Categories.tagsByUse(listOf("荤菜,好吃", "甜品,好吃", "荤菜")))
    }

    private val stew = Recipe(title = "红烧肉", category = "家常菜", subcategory = "炖菜", tags = "荤菜,好吃")
    private val cake = Recipe(title = "戚风", category = "甜点", tags = "烘焙,甜品")
    private val plain = Recipe(title = "白粥")

    @Test
    fun `按大类、小类、标签筛选`() {
        val all = listOf(stew, cake, plain)
        assertEquals(all, all.filter(RecipeFilter()::matches))
        assertEquals(listOf(stew), all.filter(RecipeFilter("家常菜")::matches))
        assertEquals(listOf(stew), all.filter(RecipeFilter("家常菜", "炖菜")::matches))
        assertEquals(emptyList<Recipe>(), all.filter(RecipeFilter("家常菜", "炒菜")::matches))
        assertEquals(listOf(plain), all.filter(RecipeFilter(Categories.NONE)::matches))
        assertEquals(listOf(cake), all.filter(RecipeFilter(tags = setOf("烘焙"))::matches))
        // 多个标签要全都带上
        assertTrue(RecipeFilter(tags = setOf("荤菜", "好吃")).matches(stew))
        assertFalse(RecipeFilter(tags = setOf("荤菜", "甜品")).matches(stew))
    }

    @Test
    fun `分类和标签没了就去掉对应条件`() {
        val tree = listOf(CategoryNode("家常菜", listOf("炒菜")))
        assertEquals(RecipeFilter("家常菜", null, setOf("好吃")), RecipeFilter("家常菜", "炖菜", setOf("好吃", "旧标签")).prune(tree, listOf("好吃")))
        assertEquals(RecipeFilter(), RecipeFilter("汤", "清汤").prune(tree, emptyList()))
        assertEquals(RecipeFilter(Categories.NONE), RecipeFilter(Categories.NONE).prune(tree, emptyList()))
    }
}
