package com.personal.mise

import com.personal.mise.sync.SyncedPlanEntry
import com.personal.mise.util.DayText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MealPlanTest {
    private val today = LocalDate.of(2026, 10, 11)

    @Test
    fun `计划日期写成今天明天后天`() {
        assertEquals("今天", DayText.ahead("2026-10-11", today))
        assertEquals("明天", DayText.ahead("2026-10-12", today))
        assertEquals("后天", DayText.ahead("2026-10-13", today))
        assertEquals("10月14日 周三", DayText.ahead("2026-10-14", today))
        assertEquals("昨天", DayText.ahead("2026-10-10", today))
    }

    @Test
    fun `连续几天跨月`() {
        assertEquals(listOf("2026-10-30", "2026-10-31", "2026-11-01"), DayText.days("2026-10-30", 3))
        assertEquals("2026-11-01", DayText.plus("2026-10-31", 1))
        assertEquals("周日", DayText.weekday("2026-10-11"))
    }

    @Test
    fun `计划同步来回不丢东西`() {
        val entry = SyncedPlanEntry("r1_2026-10-12", "r1", "番茄炒蛋", "2026-10-12", listOf("b0", "i1"), 42L, false)
        assertEquals(entry, SyncedPlanEntry.fromMap(entry.uid, entry.toMap()))
    }

    @Test
    fun `旧数据没有勾选项也能读`() {
        val data = mapOf("recipeUid" to "r1", "title" to "汤", "date" to "2026-10-12", "updatedAt" to 1L, "deleted" to true)
        assertEquals(emptyList<String>(), SyncedPlanEntry.fromMap("x", data)?.checked)
        assertNull(SyncedPlanEntry.fromMap("x", data - "date"))
    }
}
