package com.personal.mise

import com.personal.mise.util.CutPlan
import com.personal.mise.util.CutPlan.Ingredient
import com.personal.mise.util.CutPlan.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CutPlanTest {

    private fun methods(names: List<String>, vararg steps: String): Map<String, List<String>> {
        val ingredients = names.mapIndexed { i, n -> Ingredient(i.toLong(), n, "") }
        return CutPlan.methods(ingredients, steps.toList()).mapKeys { names[it.key] }
    }

    @Test
    fun `用料名后面跟着切法`() {
        assertEquals(
            mapOf("葱" to listOf("切段"), "姜" to listOf("切片")),
            methods(listOf("葱", "姜"), "葱切段、姜切片备用"),
        )
    }

    @Test
    fun `几样用料共用一个切法`() {
        assertEquals(
            mapOf("姜" to listOf("切末"), "蒜" to listOf("切末")),
            methods(listOf("姜", "蒜"), "姜、蒜切末"),
        )
    }

    @Test
    fun `切成多大多细`() {
        assertEquals(listOf("切细丝"), methods(listOf("土豆"), "土豆去皮后切成细丝")["土豆"])
        assertEquals(listOf("切段"), methods(listOf("豆角"), "豆角切成 5 厘米左右的段")["豆角"])
        assertEquals(listOf("拍碎"), methods(listOf("黄瓜"), "黄瓜洗净拍碎")["黄瓜"])
        assertEquals(listOf("剁碎"), methods(listOf("猪肉"), "猪肉剁碎")["猪肉"])
    }

    @Test
    fun `切法在下一句`() {
        assertEquals(listOf("切丝"), methods(listOf("土豆"), "土豆洗净去皮，切丝")["土豆"])
    }

    @Test
    fun `下一句不是一开头就切的不算`() {
        val result = methods(listOf("五花肉", "葱", "姜"), "冷水下锅，加葱姜，煮好后切块")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `切好后下锅不算切片`() {
        assertTrue(methods(listOf("青菜"), "青菜切好后放入锅中翻炒片刻").isEmpty())
    }

    @Test
    fun `葱花蒜末这种叫法也算`() {
        assertEquals(
            mapOf("小葱" to listOf("切葱花"), "大蒜" to listOf("切末")),
            methods(listOf("小葱", "大蒜"), "出锅前撒上葱花", "爆香蒜末"),
        )
    }

    @Test
    fun `名字本身带切法`() {
        assertEquals(mapOf("葱花" to listOf("切葱花")), methods(listOf("葱花"), "出锅撒葱花"))
    }

    @Test
    fun `同一样用料几种切法`() {
        assertEquals(listOf("切段", "切末"), methods(listOf("葱"), "葱白切段，葱叶切末")["葱"])
    }

    @Test
    fun `长名字优先，短名字不抢`() {
        val result = methods(listOf("红辣椒", "花椒"), "花椒备用，红辣椒切段")
        assertEquals(mapOf("红辣椒" to listOf("切段")), result)
    }

    @Test
    fun `几道菜的同一样用料合在一起`() {
        val items = CutPlan.build(
            listOf(
                Recipe("a", "红烧肉", listOf(Ingredient(1, "五花肉", "500克"), Ingredient(2, "葱", "2根")), listOf("五花肉切块，葱切段")),
                Recipe("b", "麻婆豆腐", listOf(Ingredient(3, "豆腐", "1块"), Ingredient(4, "葱", "1根")), listOf("豆腐切丁，葱切末")),
                Recipe("c", "葱油面", listOf(Ingredient(5, "葱", "3根")), listOf("葱切段")),
            )
        )
        assertEquals(listOf("五花肉", "葱", "豆腐"), items.map { it.name })
        val scallion = items[1]
        assertEquals("切段 5根、切末 1根", scallion.summary)
        assertEquals(listOf("切段 2根（红烧肉）", "切末 1根（麻婆豆腐）", "切段 3根（葱油面）"), scallion.detail)
        assertEquals("切块 500克", items[0].summary)
    }

    @Test
    fun `按菜看时每样用料的切法`() {
        val recipe = Recipe("a", "炒土豆丝", listOf(Ingredient(7, "土豆", "2个"), Ingredient(8, "盐", "适量")), listOf("土豆切细丝", "加盐炒"))
        assertEquals(mapOf(7L to "切细丝"), CutPlan.methodsById(recipe))
    }
}
