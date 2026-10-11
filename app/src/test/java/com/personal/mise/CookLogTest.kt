package com.personal.mise

import com.personal.mise.data.CookLog
import com.personal.mise.sync.SyncedCookLog
import com.personal.mise.util.DayText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CookLogTest {

    private val today = LocalDate.of(2026, 10, 11)

    @Test
    fun `日期标题`() {
        assertEquals("今天", DayText.label("2026-10-11", today))
        assertEquals("昨天", DayText.label("2026-10-10", today))
        assertEquals("10月3日 周六", DayText.label("2026-10-03", today))
        assertEquals("2025年12月31日 周三", DayText.label("2025-12-31", today))
        assertEquals("乱写的", DayText.label("乱写的", today))
    }

    @Test
    fun `多久以前`() {
        assertEquals("今天", DayText.ago("2026-10-11", today))
        assertEquals("昨天", DayText.ago("2026-10-10", today))
        assertEquals("8 天前", DayText.ago("2026-10-03", today))
        assertEquals("8月1日", DayText.ago("2026-08-01", today))
        assertEquals("2025年8月1日", DayText.ago("2025-08-01", today))
    }

    @Test
    fun `月份标题`() {
        assertEquals("2026年10月", DayText.month("2026-10-03"))
    }

    @Test
    fun `同一道菜同一天只有一条`() {
        assertEquals(CookLog.uidOf("r-1", "2026-10-11"), CookLog.uidOf("r-1", "2026-10-11"))
        assertEquals("r-1_2026-10-11", CookLog.uidOf("r-1", "2026-10-11"))
    }

    @Test
    fun `转成文档再读回来不变`() {
        val log = CookLog(uid = "r-1_2026-10-11", recipeUid = "r-1", title = "红烧肉", date = "2026-10-11", updatedAt = 500, deleted = false)
        assertEquals(log, SyncedCookLog.fromMap(log.uid, SyncedCookLog.toMap(log)))
    }

    @Test
    fun `格式不对时忽略`() {
        val map = SyncedCookLog.toMap(CookLog("u", "r", "菜", "2026-10-11", 1, false))
        assertNull(SyncedCookLog.fromMap("u", map - "date"))
        assertNull(SyncedCookLog.fromMap("u", map + ("updatedAt" to "1")))
    }
}
