package com.personal.mise

import com.personal.mise.ai.RecipeReview
import com.personal.mise.chat.CardStatus
import com.personal.mise.chat.ChatCard
import com.personal.mise.chat.ChatPrompts
import com.personal.mise.chat.ChatRecipeInfo
import com.personal.mise.chat.ProposalKind
import com.personal.mise.chat.RecipeTools
import com.personal.mise.data.Ingredient
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.Recipe
import com.personal.mise.data.RecipeFull
import com.personal.mise.data.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChatPromptsTest {

    private val today = LocalDate.of(2026, 10, 13)

    private fun recipe(
        title: String = "红烧肉",
        ingredients: List<Pair<String, String>> = listOf("五花肉" to "500克", "冰糖" to "30克", "盐" to "适量"),
        steps: List<String> = listOf("五花肉切块焯水", "小火炒糖色，下肉翻炒上色", "加热水没过肉，小火炖40分钟"),
        review: RecipeReview? = null,
    ) = RecipeFull(
        Recipe(id = 1, title = title, servings = "3人份", review = review?.toJson()?.toString()),
        ingredients.mapIndexed { i, (name, amount) ->
            Ingredient(i + 1L, 1, name, amount, if (name == "五花肉") IngredientKind.MAIN else IngredientKind.SEASONING, i)
        },
        steps.mapIndexed { i, text -> Step(i + 1L, 1, i, text, timerSeconds = if (i == 2) 2400 else null) },
        emptyList(),
    )

    @Test
    fun `菜谱对话的提示里有菜谱内容和换算后的用量`() {
        val prompt = ChatPrompts.recipe(recipe(), factor = 2.0, today = today)
        assertTrue(prompt.contains("今天是 2026-10-13"))
        assertTrue(prompt.contains("菜名：红烧肉"))
        assertTrue(prompt.contains("- 五花肉：1000克"))
        assertTrue(prompt.contains("3. 加热水没过肉，小火炖40分钟（计时 40 分钟）"))
        assertTrue(prompt.contains("<<edit:"))
        assertTrue(prompt.contains("<<plan: 日期>>"))
    }

    @Test
    fun `只列出这道菜用得上的功能`() {
        val plain = RecipeTools.of(recipe())
        assertTrue(plain.amounts)
        assertFalse(plain.fix)
        assertFalse(plain.split)
        assertFalse(plain.translate)
        val prompt = ChatPrompts.recipe(recipe(), 1.0, today)
        assertTrue(prompt.contains("<<amounts>>"))
        assertFalse(prompt.contains("<<fix>>"))
        assertFalse(prompt.contains("<<translate>>"))

        val reviewed = recipe(review = RecipeReview(7, "偏甜", emptyList(), listOf("糖太多")))
        assertTrue(RecipeTools.of(reviewed).fix)
        assertTrue(ChatPrompts.recipe(reviewed, 1.0, today).contains("- 要注意：糖太多"))

        val english = recipe(
            title = "Braised Pork Belly",
            ingredients = listOf("pork belly" to "1 lb", "soy sauce" to "2 tbsp", "brown sugar" to "1 tbsp"),
            steps = listOf("Cut the pork into cubes and blanch them in boiling water for two minutes, then drain well and set aside for later."),
        )
        assertTrue(RecipeTools.of(english).translate)
        assertTrue(RecipeTools.of(english).split)
    }

    @Test
    fun `全局对话列出菜谱和计划`() {
        val prompt = ChatPrompts.global(
            listOf(ChatRecipeInfo(12, "番茄炒蛋", "家常菜", listOf("快手"), listOf("番茄", "鸡蛋"), 420, "2026-10-10")),
            listOf(
                com.personal.mise.chat.ChatPlanInfo("2026-10-14", "红烧肉", false),
                com.personal.mise.chat.ChatPlanInfo("2026-10-01", "过去的菜", true),
            ),
            today,
        )
        assertTrue(prompt.contains("#12 番茄炒蛋｜家常菜｜#快手｜番茄、鸡蛋｜约420千卡｜上次做：3 天前"))
        assertTrue(prompt.contains("2026-10-14（周三）：红烧肉"))
        assertFalse(prompt.contains("过去的菜"))
        assertTrue(prompt.contains("<<plan: 2026-10-14 12,34>>"))
    }

    @Test
    fun `卡片存进数据库再读回来不变`() {
        val cards = listOf(
            ChatCard.Proposal(ProposalKind.EDIT, "修改方案", "改成4人份", listOf("五花肉：500克 → 670克"), CardStatus.APPLIED),
            ChatCard.Review(RecipeReview(8, "酱香浓", listOf("先煸出油"), listOf("没焯水"))),
            ChatCard.Recipes(listOf(12, 34), listOf("番茄炒蛋", "家常豆腐")),
            ChatCard.Plan("2026-10-14", listOf(12), listOf("番茄炒蛋")),
            ChatCard.Import("https://www.xiachufang.com/recipe/1/", link = true),
            ChatCard.Error("网络不通", retry = "再试试"),
            ChatCard.Error("没做成", action = "edit|改成4人份"),
        )
        cards.forEach { assertEquals(it, ChatCard.fromJson(it.toJson().toString())) }
        assertNull(ChatCard.fromJson("""{"type":"unknown"}"""))
        assertNull(ChatCard.fromJson("not json"))
    }
}
