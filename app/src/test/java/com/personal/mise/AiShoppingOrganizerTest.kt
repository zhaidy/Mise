package com.personal.mise

import com.personal.mise.ai.AiShoppingOrganizer
import com.personal.mise.data.IngredientKind
import com.personal.mise.importer.ImportException
import com.personal.mise.util.ShoppingList
import com.personal.mise.util.ShoppingList.AiGroup
import com.personal.mise.util.ShoppingList.Source
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiShoppingOrganizerTest {

    private val items = ShoppingList.build(
        listOf(
            Source("红烧肉", "五花肉", "500克", IngredientKind.MAIN),
            Source("红烧肉", "葱", "2根", IngredientKind.MAIN),
            Source("蛋花汤", "葱花", "1根", IngredientKind.MAIN),
            Source("蛋花汤", "鸡蛋", "2个", IngredientKind.MAIN),
            Source("红烧肉", "生抽", "1勺", IngredientKind.SEASONING),
        ),
    )

    @Test
    fun promptListsItemsWithAmounts() {
        val prompt = AiShoppingOrganizer.prompt(items)
        assertTrue(prompt.contains("2. 葱：2根\n"))
        assertTrue(prompt.contains("5. 生抽（调料）：1勺\n"))
    }

    @Test
    fun mergesAndSortsBySection() {
        val groups = ShoppingList.regroup(
            items,
            listOf(
                AiGroup("小葱", "蔬菜水果", listOf(1, 2)),
                AiGroup("五花肉", "肉禽蛋", listOf(0, 1)), // 1 已经用过，跳过
                AiGroup(null, "火星区", listOf(3)), // 区域不认识，放进「其他」
            ),
        )
        assertEquals(listOf("蔬菜水果", "肉禽蛋", "调料", "其他"), groups.map { it.section })
        val onion = groups[0].items.single()
        assertEquals("小葱", onion.name)
        assertEquals(setOf("葱", "葱花"), onion.memberKeys)
        // 用量照原来的规则加总
        assertEquals("共 3根 · 2根（红烧肉）、1根（蛋花汤）", onion.detail)
        // AI 漏掉的生抽照原样放进调料
        assertEquals("生抽", groups[2].items.single().name)
        assertEquals("鸡蛋", groups[3].items.single().name)
    }

    @Test
    fun mergedItemCountsAsOwnedWhenEveryOriginalWasTicked() {
        val onion = ShoppingList.regroup(items, listOf(AiGroup("小葱", "蔬菜水果", listOf(1, 2)))).first().items.single()
        assertTrue(onion.ownedIn(setOf("葱", "葱花")))
        assertTrue(onion.ownedIn(setOf("小葱")))
        assertFalse(onion.ownedIn(setOf("葱")))
    }

    @Test
    fun sameNameFromTwoGroupsBecomesOneItem() {
        val groups = ShoppingList.regroup(items, listOf(AiGroup("葱", "蔬菜水果", listOf(1)), AiGroup("葱", "蔬菜水果", listOf(2))))
        assertEquals(1, groups.first { it.section == "蔬菜水果" }.items.size)
    }

    @Test
    fun shareTextHasSections() {
        val groups = ShoppingList.regroup(items, listOf(AiGroup("小葱", "蔬菜水果", listOf(1, 2))))
        val text = ShoppingList.shareText(groups.flatMap { it.items }, groups)
        assertTrue(text.contains("【蔬菜水果】\n□ 小葱 共 3根"))
        assertTrue(text.contains("【调料】\n□ 生抽 1勺"))
    }

    @Test
    fun parseResponse() {
        val reply = JSONObject("""{"groups":[{"name":"小葱","section":"蔬菜水果","items":[2,3]}]}""")
        assertEquals(listOf(AiGroup("小葱", "蔬菜水果", listOf(1, 2))), AiShoppingOrganizer.parseResponse(reply, items))
        assertThrows(ImportException::class.java) {
            AiShoppingOrganizer.parseResponse(JSONObject("""{"groups":[{"name":"x","section":"其他","items":[99]}]}"""), items)
        }
    }
}
