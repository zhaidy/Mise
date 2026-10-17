package com.personal.mise

import com.personal.mise.data.ExtraItem
import com.personal.mise.data.ExtraShopping
import com.personal.mise.sync.SyncedExtraItem
import com.personal.mise.util.ShoppingList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtraShoppingTest {

    private val milk = ExtraItem("a", "牛奶", bought = false, addedAt = 2L, updatedAt = 2L)
    private val tissue = ExtraItem("b", "纸巾", bought = true, addedAt = 1L, updatedAt = 5L)
    private val gone = ExtraItem("c", "电池", bought = false, addedAt = 3L, updatedAt = 6L, deleted = true)

    @Test
    fun visibleHidesDeletedAndPutsBoughtLast() {
        val late = ExtraItem("d", "面包", bought = false, addedAt = 4L, updatedAt = 4L)
        assertEquals(listOf("牛奶", "面包", "纸巾"), ExtraShopping.visible(listOf(tissue, late, gone, milk)).map { it.name })
    }

    @Test
    fun jsonRoundTrip() {
        val entries = listOf(milk, tissue, gone)
        assertEquals(entries, ExtraShopping.fromJson(ExtraShopping.toJson(entries)))
    }

    @Test
    fun syncRoundTrip() {
        listOf(milk, tissue, gone).forEach { assertEquals(it, SyncedExtraItem.fromMap(it.id, SyncedExtraItem.toMap(it))) }
    }

    @Test
    fun syncRejectsBadData() {
        assertNull(SyncedExtraItem.fromMap("a", mapOf("name" to "牛奶", "updatedAt" to 1L)))
    }

    @Test
    fun parseSplitsPastedList() {
        assertEquals(listOf("纸巾", "牛奶", "鸡蛋", "面包"), ShoppingList.parseExtras("纸巾、牛奶，鸡蛋\n\n 面包 \n牛奶"))
        assertEquals(
            listOf("洗洁精", "电池", "保鲜膜", "垃圾袋", "1.5升可乐", "2个西瓜"),
            ShoppingList.parseExtras("- 洗洁精\n• 电池\n1. 保鲜膜\n2、垃圾袋\n[ ] 1.5升可乐\n2个西瓜"),
        )
    }

    @Test
    fun parseReadsSharedListBack() {
        val shared = "购物清单\n【蔬菜水果】\n□ 小葱 共 3根\n【另外要买】\n□ 纸巾"
        assertEquals(listOf("小葱 共 3根", "纸巾"), ShoppingList.parseExtras(shared))
    }
}
