package com.personal.mise

import com.personal.mise.util.CookCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CookCalendarTest {

    @Test
    fun `月历从周一开始补齐整周`() {
        // 2026 年 10 月 1 日是周四
        val grid = CookCalendar.grid(YearMonth.of(2026, 10))
        assertEquals(0, grid.size % 7)
        assertEquals(listOf(null, null, null), grid.take(3))
        assertEquals(LocalDate.of(2026, 10, 1), grid[3])
        assertEquals(LocalDate.of(2026, 10, 31), grid.filterNotNull().last())
        assertEquals(35, grid.size)
    }

    @Test
    fun `周一开头的月份前面不补空格`() {
        // 2026 年 6 月 1 日是周一，30 天
        val grid = CookCalendar.grid(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), grid.first())
        assertEquals(35, grid.size)
    }

    @Test
    fun `热量合计跳过不知道的`() {
        assertEquals(1300, CookCalendar.total(listOf(800, null, 500)))
        assertNull(CookCalendar.total(listOf(null, null)))
        assertNull(CookCalendar.total(emptyList()))
    }

    @Test
    fun `格子里的简写`() {
        assertEquals("860", CookCalendar.short(860))
        assertEquals("1.2k", CookCalendar.short(1234))
        assertEquals("2k", CookCalendar.short(2000))
    }
}
