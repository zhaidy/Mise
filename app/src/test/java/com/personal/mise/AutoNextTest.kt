package com.personal.mise

import com.personal.mise.data.Step
import com.personal.mise.timer.AutoNext
import com.personal.mise.timer.CookingTimer
import com.personal.mise.timer.NextStep
import com.personal.mise.timer.TimerFinished
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoNextTest {
    private fun step(order: Int, text: String, seconds: Int? = null) =
        Step(recipeId = 1, order = order, text = text, timerSeconds = seconds)

    private val steps = listOf(
        step(0, "焯水 3 分钟"),
        step(1, "小火焖", seconds = 900),
        step(2, "大火收汁 2 分钟"),
        step(3, "装盘"),
    )

    @Test
    fun `下一步设了时长就用设的`() {
        assertEquals(NextStep(1, 900), AutoNext.after(steps, 0))
    }

    @Test
    fun `下一步没设时长就从文字里猜`() {
        assertEquals(NextStep(2, 120), AutoNext.after(steps, 1))
    }

    @Test
    fun `下一步没有时间只跳不计时`() {
        assertEquals(NextStep(3, null), AutoNext.after(steps, 2))
    }

    @Test
    fun `最后一步不跳`() {
        assertNull(AutoNext.after(steps, 3))
    }

    @Test
    fun `翻到下一步了就不响铃不弹窗`() {
        val timer = CookingTimer(1, 1, "红烧肉", 1, 900, 0, autoNext = true)
        assertTrue(TimerFinished(timer, NextStep(2, 120)).silent)
        assertTrue(TimerFinished(timer, NextStep(3, null)).silent)
    }

    @Test
    fun `没翻成或者只是闹钟就照常响铃`() {
        val auto = CookingTimer(1, 1, "红烧肉", 1, 900, 0, autoNext = true)
        val alarm = CookingTimer(2, 1, "红烧肉", 1, 900, 0)
        assertFalse(TimerFinished(auto, null).silent)
        assertFalse(TimerFinished(alarm, null).silent)
        assertEquals("第 2 步的时间到了", TimerFinished(alarm, null).message)
    }
}
