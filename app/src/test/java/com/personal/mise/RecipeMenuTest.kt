package com.personal.mise

import com.personal.mise.data.Freshness
import com.personal.mise.data.MenuItem
import com.personal.mise.data.Recipe
import com.personal.mise.data.RecipeMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecipeMenuTest {

    private val today = LocalDate.of(2026, 10, 11)

    private fun recipe(title: String, category: String? = null, sub: String? = null) =
        Recipe(title = title, category = category, subcategory = sub, uid = "uid-$title")

    @Test
    fun `多久没做的颜色档`() {
        assertEquals(Freshness.FRESH, Freshness.of("2026-10-11", today))
        assertEquals(Freshness.FRESH, Freshness.of("2026-10-04", today))
        assertEquals(Freshness.NORMAL, Freshness.of("2026-10-03", today))
        assertEquals(Freshness.NORMAL, Freshness.of("2026-09-11", today))
        assertEquals(Freshness.STALE, Freshness.of("2026-09-10", today))
        assertEquals(Freshness.NEVER, Freshness.of(null, today))
        assertEquals(Freshness.NORMAL, Freshness.of("乱写的", today))
    }

    @Test
    fun `价格栏文字`() {
        val r = recipe("番茄炒蛋")
        assertEquals("3 天前", RecipeMenu.whenText(MenuItem(r, "2026-10-08", Freshness.FRESH), today))
        assertEquals("还没做过", RecipeMenu.whenText(MenuItem(r, null, Freshness.NEVER), today))
    }

    @Test
    fun `按分类排成菜单`() {
        val recipes = listOf(
            recipe("紫菜蛋花汤", "汤"),
            recipe("番茄炒蛋", "家常菜", "炒菜"),
            recipe("红烧肉", "家常菜", "炖菜"),
            recipe("凉拌黄瓜", "家常菜"),
            recipe("鱼香肉丝", "家常菜", "炒菜"),
            recipe("双皮奶"),
        )
        val sections = RecipeMenu.sections(recipes, mapOf("uid-番茄炒蛋" to "2026-10-10"), today)

        // 菜多的大类在前，没分类的放最后
        assertEquals(listOf("家常菜", "汤", RecipeMenu.OTHER), sections.map { it.name })
        val home = sections[0]
        assertEquals(4, home.count)
        // 没小类的排最前，小类按菜多少排，组内保持原来的顺序
        assertEquals(listOf(null, "炒菜", "炖菜"), home.groups.map { it.sub })
        assertEquals(listOf("番茄炒蛋", "鱼香肉丝"), home.groups[1].items.map { it.recipe.title })

        val tomato = home.groups[1].items[0]
        assertEquals("2026-10-10", tomato.date)
        assertEquals(Freshness.FRESH, tomato.freshness)
        val fish = home.groups[1].items[1]
        assertNull(fish.date)
        assertEquals(Freshness.NEVER, fish.freshness)
    }

    @Test
    fun `全没分类就只有其他`() {
        val sections = RecipeMenu.sections(listOf(recipe("a"), recipe("b")), emptyMap(), today)
        assertEquals(listOf(RecipeMenu.OTHER), sections.map { it.name })
        assertEquals(2, sections[0].count)
    }
}
