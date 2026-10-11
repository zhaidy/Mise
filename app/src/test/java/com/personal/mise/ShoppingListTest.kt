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
        assertEquals("共 5个", egg.total)
        assertEquals("共 5个 · 3个（番茄炒蛋）、2 个（蛋花汤）", egg.detail)
        assertEquals("", items.first { it.name == "盐" }.total)
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

    @Test
    fun totalAddsMatchingUnits() {
        assertEquals("共 350克", ShoppingList.total(listOf("150克", "200 克")))
        assertEquals("共 1200克", ShoppingList.total(listOf("200克", "1千克")))
        assertEquals("共 1.5千克", ShoppingList.total(listOf("1公斤", "0.5千克")))
        assertEquals("共 3个", ShoppingList.total(listOf("两个", "1个")))
        assertEquals("共 1.5汤匙 + 适量", ShoppingList.total(listOf("1汤匙", "½ 大勺", "适量")))
        assertEquals("共 300克 + 2个", ShoppingList.total(listOf("100克", "200克", "2个")))
    }

    @Test
    fun totalEmptyWhenNothingAdds() {
        assertEquals("", ShoppingList.total(listOf("适量", "少许")))
        assertEquals("", ShoppingList.total(listOf("200克", "2个")))
        assertEquals("", ShoppingList.total(listOf("3个", "适量")))
        assertEquals("", ShoppingList.total(listOf("2个 ≈100克", "1个")))
    }
}
