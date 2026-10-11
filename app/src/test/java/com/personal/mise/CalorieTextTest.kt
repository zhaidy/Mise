package com.personal.mise

import com.personal.mise.util.CalorieText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalorieTextTest {

    @Test
    fun servingsCount() {
        assertEquals(2, CalorieText.servingsCount("2 人"))
        assertEquals(3, CalorieText.servingsCount("3人份"))
        assertEquals(2, CalorieText.servingsCount("两人份"))
        assertEquals(2, CalorieText.servingsCount("2-3 人"))
        assertEquals(4, CalorieText.servingsCount("约4份"))
        assertNull(CalorieText.servingsCount("一大盘"))
        assertNull(CalorieText.servingsCount("500克"))
        assertNull(CalorieText.servingsCount(null))
    }

    @Test
    fun describe() {
        assertEquals("约 860 千卡 · 每人约 430", CalorieText.describe(860, "2 人"))
        assertEquals("约 860 千卡", CalorieText.describe(860, null))
        assertEquals("约 860 千卡", CalorieText.describe(860, "1人份"))
        assertEquals("约 1720 千卡（×2）", CalorieText.describe(860, "2 人", 2.0))
        assertEquals(430, CalorieText.scaled(860, 0.5))
    }
}
