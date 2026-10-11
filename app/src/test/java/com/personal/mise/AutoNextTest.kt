package com.personal.mise

import com.personal.mise.data.Step
import com.personal.mise.timer.AutoNext
import com.personal.mise.timer.CookingTimer
import com.personal.mise.timer.NextStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `提示说清楚跳到哪步、接着计多久`() {
        val timer = CookingTimer(1, 1, "红烧肉", 1, 900, 0, autoNext = true)
        assertEquals("第 2 步的时间到了", AutoNext.message(timer, null))
        assertEquals("第 2 步的时间到了，已跳到第 4 步", AutoNext.message(timer, NextStep(3, null)))
        assertEquals(
            "第 2 步的时间到了，已跳到第 3 步，接着计时 2 分钟",
            AutoNext.message(timer, NextStep(2, 120)),
        )
    }
}
