package com.personal.mise

import com.personal.mise.ai.AiRecipeOrganizer
import com.personal.mise.data.IngredientKind
import com.personal.mise.importer.ImportException
import com.personal.mise.importer.UrlExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecipeOrganizerTest {

    private fun reply(inner: String) = org.json.JSONObject(inner)

    @Test
    fun `解析 AI 整理出的菜谱并生成草稿`() {
        val recipe = AiRecipeOrganizer.parseResponse(
            reply(
                """{"title":"红烧肉","servings":null,
                   "ingredients":[{"name":"五花肉","amount":"500克"},{"name":"生抽","amount":""},{"name":" ","amount":"1勺"}],
                   "steps":[{"text":"五花肉切块焯水"},{"text":"小火炖40分钟","timerMinutes":40}]}""",
            ),
        )
        assertEquals("红烧肉", recipe.title)
        assertNull(recipe.servings)
        assertEquals(2, recipe.ingredients.size)

        val draft = AiRecipeOrganizer.toImportResult(recipe, "红烧肉笔记 https://example.com/v/1").draft
        assertEquals("https://example.com/v/1", draft.sourceUrl)
        assertEquals(IngredientKind.SEASONING, draft.ingredients[1].kind)
        assertEquals(IngredientKind.MAIN, draft.ingredients[0].kind)
        assertNull(draft.steps[0].timerSeconds)
        assertEquals(2400, draft.steps[1].timerSeconds)
    }

    @Test
    fun `没有菜名时给出提醒`() {
        val recipe = AiRecipeOrganizer.parseResponse(reply("""{"title":"","ingredients":[],"steps":[{"text":"炒熟"}]}"""))
        val warnings = AiRecipeOrganizer.toImportResult(recipe, "炒熟").warnings
        assertTrue(warnings.any { it.contains("菜名") })
        assertTrue(warnings.any { it.contains("用料") })
    }

    @Test
    fun `不是菜谱时报错`() {
        assertThrows(ImportException::class.java) {
            AiRecipeOrganizer.parseResponse(reply("""{"title":"","ingredients":[],"steps":[]}"""))
        }
        assertThrows(ImportException::class.java) { AiRecipeOrganizer.parseResponse(reply("{}")) }
    }

    @Test
    fun `区分链接分享和笔记文字`() {
        assertTrue(UrlExtractor.isLinkShare("【下厨房】宫保鸡丁的做法 https://www.xiachufang.com/recipe/100403615/"))
        assertFalse(UrlExtractor.isLinkShare("番茄炒蛋：鸡蛋3个打散，番茄2个切块，先炒蛋盛出，再炒番茄出汁，加盐糖，倒回鸡蛋翻匀"))
        assertFalse(
            UrlExtractor.isLinkShare(
                "可乐鸡翅（参考 https://example.com/v/1）\n鸡翅10个两面划刀，冷水下锅焯水捞出，" +
                    "锅里少油把鸡翅煎到两面金黄，倒入一罐可乐、两勺生抽、一勺老抽，大火烧开转小火焖15分钟，最后大火收汁",
            ),
        )
    }
}
