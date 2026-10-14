package com.personal.mise

import com.personal.mise.data.ActivityLevel
import com.personal.mise.data.BodyProfile
import com.personal.mise.data.CalorieBudget
import com.personal.mise.data.Diner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DinersTest {

    @Test
    fun splitEvenlyAmongEating() {
        val me = Diner("小明", 2200)
        val her = Diner("小红", 1700)
        val away = Diner("爸爸", 2400, eating = false)
        val shares = CalorieBudget.split(4000, listOf(me, her, away))
        assertEquals(listOf("小明", "小红"), shares.map { it.diner.name })
        assertEquals(listOf(2000, 2000), shares.map { it.eaten })
        assertEquals(listOf(-200, 300), shares.map { it.diff })
        assertTrue(CalorieBudget.split(4000, listOf(away)).isEmpty())
    }

    @Test
    fun cleanFillsNamesAndClamps() {
        val cleaned = CalorieBudget.clean(listOf(Diner("  ", 0), Diner(" 小红 ", 99999)))
        assertEquals(listOf("1 号", "小红"), cleaned.map { it.name })
        assertEquals(listOf(1, CalorieBudget.MAX_PER_DAY), cleaned.map { it.perDay })
    }

    @Test
    fun jsonRoundTrip() {
        val diners = listOf(
            Diner("小明", 2270, body = BodyProfile(true, 30, 175, 140, ActivityLevel.LIGHT)),
            Diner("小红", 1700, eating = false),
        )
        assertEquals(diners, CalorieBudget.fromJson(CalorieBudget.toJson(diners)))
    }
}
