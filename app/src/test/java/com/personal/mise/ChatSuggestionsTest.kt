package com.personal.mise

import com.personal.mise.chat.ChatPlanInfo
import com.personal.mise.chat.ChatRecipeInfo
import com.personal.mise.chat.ChatSuggestions
import com.personal.mise.data.Ingredient
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.Recipe
import com.personal.mise.data.RecipeFull
import com.personal.mise.data.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class ChatSuggestionsTest {

    private val evening = LocalDateTime.of(2026, 10, 13, 18, 30) // 周二
    private val recipes = listOf(
        ChatRecipeInfo(1, "糖醋排骨", null, emptyList(), listOf("排骨", "冰糖"), 1298, "2026-08-01"),
        ChatRecipeInfo(2, "炒包菜", null, emptyList(), listOf("包菜", "干辣椒"), 400, "2026-10-12"),
        ChatRecipeInfo(3, "芒果雪酪", null, emptyList(), listOf("芒果"), 300, null),
    )

    @Test
    fun `今天没排菜时先问这顿吃什么`() {
        val groups = ChatSuggestions.global(recipes, emptyList(), evening, Random(1))
        assertEquals(listOf("吃什么", "排菜单"), groups.map { it.label })
        assertEquals("晚饭吃什么？", groups[0].asks.first())
        assertEquals("帮我排一下这周的菜单", groups[1].asks.first())
        groups.forEach { assertTrue(it.asks.size <= 3) }
    }

    @Test
    fun `今天排了菜就问先做哪个`() {
        val plan = listOf(
            ChatPlanInfo("2026-10-13", "糖醋排骨", false),
            ChatPlanInfo("2026-10-13", "炒包菜", false),
            ChatPlanInfo("2026-10-14", "芒果雪酪", false),
        )
        val groups = ChatSuggestions.global(recipes, plan, evening, Random(1))
        assertEquals("今天的「糖醋排骨」和「炒包菜」，先做哪个？", groups[0].asks.first())
        assertEquals("这周还有 5 天没排菜，帮我补上", groups[1].asks.first())
    }

    @Test
    fun `用的是自己菜谱里的菜名，每次换一批`() {
        val all = (1..30).flatMap { seed -> ChatSuggestions.global(recipes, emptyList(), evening, Random(seed)).flatMap { it.asks } }.toSet()
        assertTrue(all.any { it.contains("好久没做「糖醋排骨」") })
        assertTrue(all.any { it.contains("「芒果雪酪」排进这周") })
        assertTrue(all.size > 6)
    }

    @Test
    fun `按时间换说法`() {
        assertEquals("早饭", ChatSuggestions.mealOf(7))
        assertEquals("午饭", ChatSuggestions.mealOf(12))
        assertEquals("夜宵", ChatSuggestions.mealOf(23))
    }

    @Test
    fun `菜谱对话按这道菜的用料和批注出`() {
        val full = RecipeFull(
            Recipe(id = 1, title = "红烧肉", servings = "2人份", note = "糖少放一点"),
            listOf(
                Ingredient(1, 1, "五花肉", "500克", IngredientKind.MAIN, 0),
                Ingredient(2, 1, "生抽", "2勺", IngredientKind.SEASONING, 1),
            ),
            (0 until 4).map { Step(it.toLong() + 1, 1, it, "第 $it 步") },
            emptyList(),
        )
        val asks = (1..20).flatMap { seed -> ChatSuggestions.recipe(full, Random(seed)).flatMap { it.asks } }.toSet()
        assertTrue(asks.contains("没有生抽，用什么代替？"))
        assertTrue(asks.contains("按我的批注改菜谱：糖少放一点"))
        assertTrue(asks.contains("改成 4 人份"))
        assertTrue(asks.contains("这道菜大概多少热量？"))
    }
}
