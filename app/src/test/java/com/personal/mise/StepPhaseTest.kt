package com.personal.mise

import com.personal.mise.util.StepPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepPhaseTest {

    @Test
    fun `第一个动火的步骤之前都是备料`() {
        val steps = listOf(
            "菜花掰成小朵，用淡盐水浸泡",
            "冲洗干净后充分晾干水分",
            "五花肉切片",
            "干辣椒切段，大蒜拍散后切小块，姜切丝",
            "锅内放少许油，以中小火将花椒炒变色出香味",
            "五花肉入锅小火慢慢煸炒出油",
        )
        assertEquals(4, StepPhase.cookStart(steps))
        assertTrue(StepPhase.isPrep(3, 4))
        assertFalse(StepPhase.isPrep(4, 4))
        assertEquals("第 2 步备料时用", StepPhase.bowlStepLabel(1, 4))
        assertEquals("第 5 步下锅", StepPhase.bowlStepLabel(4, 4))
    }

    @Test
    fun `第一步就开火或者一直不开火时不分段`() {
        assertNull(StepPhase.cookStart(listOf("热锅倒油", "下肉丝翻炒")))
        assertNull(StepPhase.cookStart(listOf("黄瓜拍碎", "淋上香油拌匀")))
        assertEquals("第 1 步下锅", StepPhase.bowlStepLabel(0, null))
    }

    @Test
    fun `用料名里带火、烤的字不算开火`() {
        assertFalse(StepPhase.isHeatStep("火腿切丁"))
        assertFalse(StepPhase.isHeatStep("调入蒸鱼豉油拌匀"))
        assertTrue(StepPhase.isHeatStep("火腿下锅煎香"))
    }

    @Test
    fun `腌制步骤的调料碗显示腌制`() {
        val steps = listOf("鸡腿切丁，用生抽、料酒腌 15 分钟", "热锅倒油，下鸡丁翻炒")
        val cookStart = StepPhase.cookStart(steps)
        assertEquals("第 1 步腌制", StepPhase.bowlStepLabel(0, cookStart, steps[0]))
        assertEquals("第 2 步下锅", StepPhase.bowlStepLabel(1, cookStart, steps[1]))
        // 腌好的肉下锅不算腌制
        assertFalse(StepPhase.isMarinateStep("腌好的肉下锅炒散"))
    }
}
