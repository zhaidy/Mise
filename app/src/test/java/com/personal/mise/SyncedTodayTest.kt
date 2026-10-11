package com.personal.mise

import com.personal.mise.sync.SyncedTodayEntry
import com.personal.mise.sync.TodayKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncedTodayTest {

    private val ingredientIds = listOf(31L, 32L, 33L)
    private val bowlIds = listOf(9L, 10L)

    @Test
    fun `勾选项本地 id 和下标互换`() {
        assertEquals("i1", TodayKeys.toPortable("i32", ingredientIds, bowlIds))
        assertEquals("b0", TodayKeys.toPortable("b9", ingredientIds, bowlIds))
        assertEquals("i32", TodayKeys.fromPortable("i1", ingredientIds, bowlIds))
        assertEquals("b10", TodayKeys.fromPortable("b1", ingredientIds, bowlIds))
    }

    @Test
    fun `对不上的勾选项丢掉`() {
        assertNull(TodayKeys.toPortable("i99", ingredientIds, bowlIds))
        assertNull(TodayKeys.toPortable("x1", ingredientIds, bowlIds))
        assertNull(TodayKeys.toPortable("i", ingredientIds, bowlIds))
        assertNull(TodayKeys.fromPortable("b5", ingredientIds, bowlIds))
        assertNull(TodayKeys.fromPortable("", ingredientIds, bowlIds))
    }

    @Test
    fun `转成文档再读回来不变`() {
        val entry = SyncedTodayEntry("u-1", order = 2, currentStep = 3, checked = listOf("b0", "i1"), updatedAt = 500, removed = false, done = true)
        // Firestore 读回来时整数都是 Long
        val stored = entry.toMap().mapValues { (_, v) -> if (v is Int) v.toLong() else v }
        assertEquals(entry, SyncedTodayEntry.fromMap("u-1", stored))
    }

    @Test
    fun `旧版本没有 done 字段时当作没做完`() {
        val map = SyncedTodayEntry("u-1", 0, 0, emptyList(), 1, false, done = true).toMap() - "done"
        assertEquals(false, SyncedTodayEntry.fromMap("u-1", map)?.done)
    }

    @Test
    fun `格式不对时忽略`() {
        val map = SyncedTodayEntry("u-1", 0, 0, emptyList(), 1, true).toMap()
        assertNull(SyncedTodayEntry.fromMap("u-1", map - "updatedAt"))
        assertNull(SyncedTodayEntry.fromMap("u-1", map + ("checked" to "i1")))
    }
}
