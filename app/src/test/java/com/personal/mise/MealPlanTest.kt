package com.personal.mise

import com.personal.mise.sync.SyncedPlanEntry
import com.personal.mise.sync.TodayKeys
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

    @Test
    fun `今日菜单的进度跟着计划同步`() {
        val entry = SyncedPlanEntry("r1_2026-10-11", "r1", "番茄炒蛋", "2026-10-11", emptyList(), 7L, false, order = 2, currentStep = 3, done = true)
        // Firestore 读回来时整数都是 Long
        val stored = entry.toMap().mapValues { (_, v) -> if (v is Int) v.toLong() else v }
        assertEquals(entry, SyncedPlanEntry.fromMap(entry.uid, stored))
    }

    @Test
    fun `旧版本传的计划没有顺序和进度`() {
        val old = SyncedPlanEntry("x", "r1", "汤", "2026-10-11", emptyList(), 1L, false)
        val map = old.toMap()
        assertEquals(false, "done" in map)
        val read = SyncedPlanEntry.fromMap("x", map)
        assertNull(read?.order)
        assertNull(read?.currentStep)
        assertNull(read?.done)
    }

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
}
