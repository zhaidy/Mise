package com.personal.mise

import com.personal.mise.ai.StepTime
import com.personal.mise.util.CookTimeline
import com.personal.mise.util.CookTimeline.Kind
import com.personal.mise.util.CookTimeline.Step
import com.personal.mise.util.StepPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class CookTimelineTest {

    private val soup = CookTimeline.dish(
        1, "炖汤",
        listOf(Step("排骨洗净切块", null), Step("冷水下锅焯水", 300), Step("小火炖 45 分钟", null)),
    )
    private val greens = CookTimeline.dish(
        2, "炒青菜",
        listOf(Step("青菜洗净切段", null), Step("蒜切片", null), Step("热锅下油炒 2 分钟", null)),
    )

    @Test
    fun `每道菜分备料和开火两段，没写时间的按默认`() {
        assertEquals(3, soup.prepMinutes)
        assertEquals(50, soup.cookMinutes)
        assertEquals(6, greens.prepMinutes)
        assertEquals(2, greens.cookMinutes)
    }

    @Test
    fun `做到一半的菜只算剩下的步骤`() {
        val half = CookTimeline.dish(1, "炖汤", listOf(Step("排骨切块", null), Step("焯水", 300), Step("炖 45 分钟", null)), fromStep = 2)
        assertEquals(0, half.prepMinutes)
        assertEquals(45, half.cookMinutes)
    }

    @Test
    fun `最长的先开始，其余倒推同时出锅`() {
        val plan = CookTimeline.plan(listOf(greens, soup))
        assertEquals(53, plan.totalMinutes)
        assertEquals(listOf("炖汤" to 0, "炒青菜" to 45), plan.slots.map { it.dish.title to it.startMinute })
        assertEquals(plan.totalMinutes, plan.slots.map { it.startMinute + it.dish.totalMinutes }.distinct().single())
    }

    @Test
    fun `一句话说先后`() {
        val plan = CookTimeline.plan(listOf(greens, soup))
        assertEquals("先做「炖汤」，45 分钟后开始备「炒青菜」", CookTimeline.summary(plan))
    }

    @Test
    fun `时间线按时间排`() {
        val events = CookTimeline.events(CookTimeline.plan(listOf(greens, soup)))
        assertEquals(
            listOf(0 to Kind.START, 3 to Kind.FIRE, 45 to Kind.START, 51 to Kind.FIRE, 53 to Kind.SERVE),
            events.map { it.minute to it.kind },
        )
        assertEquals("开始备「炒青菜」", events[2].text)
        assertEquals("一起出锅", events.last().text)
    }

    @Test
    fun `两道都要一直动手的菜错开做，一个人忙得过来`() {
        val plan = CookTimeline.plan(listOf(greens, greens.copy(id = 3, title = "炒白菜")))
        // 两道各 8 分钟都得动手，一个人做要 16 分钟
        assertEquals(16, plan.totalMinutes)
        assertEquals(plan.totalMinutes, plan.slots.maxOf { it.doneMinute })
    }

    @Test
    fun `炖着的时候可以去做别的，不算重叠`() {
        val stew = CookTimeline.dish(4, "炖牛肉", listOf(Step("牛肉切块", null), Step("下锅炒香", 120), Step("小火炖 1 小时", null)))
        val plan = CookTimeline.plan(listOf(stew, greens))
        assertEquals(65, plan.totalMinutes)
        assertEquals(listOf("炖牛肉" to 0, "炒青菜" to 57), plan.slots.map { it.dish.title to it.startMinute })
    }

    @Test
    fun `AI 估的时长和能不能走开优先于按文字猜`() {
        val dish = CookTimeline.dish(
            5, "蒸鱼",
            listOf(
                Step("鱼洗净划刀", null, StepTime(600, handsOff = false)),
                Step("上锅蒸", null, StepTime(480, handsOff = true)),
                Step("淋热油", 60),
            ),
        )
        assertEquals(10, dish.prepMinutes)
        assertEquals(9, dish.cookMinutes)
        assertEquals(listOf(false, true, false), dish.tasks.map { it.handsOff })
    }

    @Test
    fun `按文字猜能不能走开`() {
        assertEquals(true, StepPhase.isHandsOff("小火炖 45 分钟", 2700))
        assertEquals(true, StepPhase.isHandsOff("腌制 20 分钟", 1200))
        assertEquals(false, StepPhase.isHandsOff("炖到收汁，不停翻炒", 600))
        assertEquals(false, StepPhase.isHandsOff("蒸一下", 60))
        assertEquals(false, StepPhase.isHandsOff("切丝", 600))
    }

    @Test
    fun `先做好的菜在时间线上标出来`() {
        val plan = CookTimeline.plan(listOf(greens, greens.copy(id = 3, title = "炒白菜")))
        val done = CookTimeline.events(plan).filter { it.kind == Kind.DONE }
        assertEquals(1, done.size)
        assertEquals(true, done.single().minute < plan.totalMinutes)
    }
}
