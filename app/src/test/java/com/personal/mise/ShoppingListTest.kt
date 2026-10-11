package com.personal.mise

import com.personal.mise.data.IngredientKind.MAIN
import com.personal.mise.data.IngredientKind.SEASONING
import com.personal.mise.util.ShoppingList
import com.personal.mise.util.ShoppingList.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingListTest {

    private val sources = listOf(
        Source("番茄炒蛋", "番茄", "2个", MAIN),
        Source("番茄炒蛋", "鸡蛋", "3个", MAIN),
        Source("番茄炒蛋", "盐", "适量", SEASONING),
        Source("蛋花汤", "鸡蛋", "2 个", MAIN),
        Source("蛋花汤", " 盐", "少许", SEASONING),
        Source("蛋花汤", "葱", "", MAIN),
    )

    @Test
    fun mergesSameNameAcrossRecipes() {
        val items = ShoppingList.build(sources)
        assertEquals(listOf("番茄", "鸡蛋", "葱", "盐"), items.map { it.name })
        val egg = items.first { it.name == "鸡蛋" }
        assertEquals("3个（番茄炒蛋）、2 个（蛋花汤）", egg.detail)
        assertEquals("2个", items.first { it.name == "番茄" }.detail)
        assertEquals(true, items.first { it.name == "盐" }.seasoning)
    }

    @Test
    fun keyIgnoresSpacesAndCase() {
        assertEquals(ShoppingList.keyOf("Olive Oil"), ShoppingList.keyOf("olive oil "))
        assertEquals("盐", ShoppingList.keyOf(" 盐"))
    }

    @Test
    fun shareText() {
        val items = ShoppingList.build(sources).filter { it.name == "番茄" || it.name == "葱" }
        assertEquals("购物清单\n□ 番茄 2个\n□ 葱", ShoppingList.shareText(items))
    }
}
