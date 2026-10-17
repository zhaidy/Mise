package com.personal.mise

import com.personal.mise.data.ExtraItem
import com.personal.mise.data.ExtraShopping
import com.personal.mise.sync.SyncedExtraItem
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
}
