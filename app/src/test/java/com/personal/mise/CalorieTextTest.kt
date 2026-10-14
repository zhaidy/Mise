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

    @Test
    fun perPerson() {
        assertEquals(250, CalorieText.perPerson(500, 2))
        assertEquals(500, CalorieText.perPerson(500, 0))
    }

    @Test
    fun jin() {
        assertEquals("约 0.06 斤", CalorieText.jin(250))
        assertEquals("约 0.26 斤", CalorieText.jin(1000))
        assertEquals("约 0.1 斤", CalorieText.jin(385))
        assertEquals("约 1 斤", CalorieText.jin(3850))
        assertEquals("约 1 斤", CalorieText.jin(3840))
        assertEquals("约 1.3 斤", CalorieText.jin(5000))
        assertEquals("约 2 斤", CalorieText.jin(7700))
        assertEquals("不到 0.01 斤", CalorieText.jin(10))
        assertEquals("不到 0.01 斤", CalorieText.jin(0))
    }
}
