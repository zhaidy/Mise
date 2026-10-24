package com.personal.mise

import com.personal.mise.data.BowlFull
import com.personal.mise.data.BowlItemRow
import com.personal.mise.data.Ingredient
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.Recipe
import com.personal.mise.data.RecipeFull
import com.personal.mise.data.SauceBowl
import com.personal.mise.data.Step
import com.personal.mise.sync.KitchenCodes
import com.personal.mise.sync.SyncedBowl
import com.personal.mise.sync.SyncedBowlItem
import com.personal.mise.sync.SyncedRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Random

class SyncedRecipeTest {

    private val full = RecipeFull(
        recipe = Recipe(id = 7, title = "红烧肉", servings = "2 人", createdAt = 100, uid = "u-1", updatedAt = 200, bowlsManual = true, calories = 860, category = "家常菜", subcategory = "炖菜", tags = "荤菜,好吃"),
        ingredients = listOf(
            Ingredient(id = 31, recipeId = 7, name = "五花肉", amountText = "500 克", kind = IngredientKind.MAIN, order = 0),
            Ingredient(id = 32, recipeId = 7, name = "生抽", amountText = "1 汤匙", kind = IngredientKind.SEASONING, order = 1),
        ),
        steps = listOf(
            Step(id = 51, recipeId = 7, order = 0, text = "焯水", timerSeconds = 180),
            Step(id = 52, recipeId = 7, order = 1, text = "加生抽炖", images = listOf("images/a.jpg", "images/b.jpg")),
        ),
        bowls = listOf(
            BowlFull(
                SauceBowl(id = 9, recipeId = 7, label = "调料碗 A", stepId = 52, order = 0),
                listOf(BowlItemRow(id = 1, bowlId = 9, ingredientId = 32, name = "生抽", amountText = "1 汤匙")),
            ),
        ),
    )

    @Test
    fun `调料碗按下标引用用料和步骤`() {
        val synced = SyncedRecipe.from(full)
        assertEquals(listOf(SyncedBowl("调料碗 A", 1, listOf(SyncedBowlItem(1, "1 汤匙")))), synced.bowls)
        assertEquals("u-1", synced.uid)
        assertEquals(200L, synced.updatedAt)
    }

    @Test
    fun `转成文档再读回来不变`() {
        val synced = SyncedRecipe.from(full)
        assertEquals(synced, SyncedRecipe.fromMap("u-1", firestoreLike(synced.toMap())))
    }

    @Test
    fun `一步好几张图跟着同步，旧文档只有一张 image`() {
        val map = SyncedRecipe.from(full).toMap()
        assertEquals(listOf("images/a.jpg", "images/b.jpg"), SyncedRecipe.fromMap("u-1", firestoreLike(map))?.steps?.get(1)?.images)
        @Suppress("UNCHECKED_CAST")
        val old = map + ("steps" to (map["steps"] as List<Map<String, Any?>>).map { it - "images" })
        val steps = SyncedRecipe.fromMap("u-1", firestoreLike(old))?.steps
        assertEquals(listOf(emptyList(), listOf("images/a.jpg")), steps?.map { it.images })
    }

    @Test
    fun `已删除的只有标记`() {
        val tombstone = SyncedRecipe.from(RecipeFull(full.recipe.copy(deleted = true, coverImage = null), emptyList(), emptyList(), emptyList()))
        val back = SyncedRecipe.fromMap("u-1", firestoreLike(tombstone.toMap()))
        assertEquals(true, back?.deleted)
        assertEquals(emptyList<Any>(), back?.ingredients)
    }

    @Test
    fun `旧版本的文档没有热量`() {
        val map = SyncedRecipe.from(full).toMap()
        assertEquals(860, SyncedRecipe.fromMap("u-1", firestoreLike(map))?.calories)
        assertNull(SyncedRecipe.fromMap("u-1", firestoreLike(map - "calories"))?.calories)
    }

    @Test
    fun `翻译前的原文跟着同步，旧文档没有时为空`() {
        val original = """{"title":"Braised Pork","servings":null,"ingredients":[],"steps":["Simmer"]}"""
        val map = SyncedRecipe.from(full.copy(recipe = full.recipe.copy(original = original))).toMap()
        assertEquals(original, SyncedRecipe.fromMap("u-1", firestoreLike(map))?.original)
        assertNull(SyncedRecipe.fromMap("u-1", firestoreLike(map - "original"))?.original)
    }

    @Test
    fun `批注跟着同步，旧文档没有时为空`() {
        val map = SyncedRecipe.from(full.copy(recipe = full.recipe.copy(note = "盐少放一半"))).toMap()
        assertEquals("盐少放一半", SyncedRecipe.fromMap("u-1", firestoreLike(map))?.note)
        assertNull(SyncedRecipe.fromMap("u-1", firestoreLike(map - "note"))?.note)
    }

    @Test
    fun `AI 评价跟着同步，旧文档没有时为空`() {
        val json = """{"score":8,"summary":"靠谱","highlights":[],"concerns":[]}"""
        val map = SyncedRecipe.from(full.copy(recipe = full.recipe.copy(review = json))).toMap()
        assertEquals(json, SyncedRecipe.fromMap("u-1", firestoreLike(map))?.review)
        assertNull(SyncedRecipe.fromMap("u-1", firestoreLike(map - "review"))?.review)
    }

    @Test
    fun `AI 估的步骤时长跟着同步`() {
        val json = """{"切块":{"seconds":120,"handsOff":false}}"""
        val map = SyncedRecipe.from(full.copy(recipe = full.recipe.copy(stepTimes = json))).toMap()
        assertEquals(json, SyncedRecipe.fromMap("u-1", firestoreLike(map))?.stepTimes)
        assertNull(SyncedRecipe.fromMap("u-1", firestoreLike(map - "stepTimes"))?.stepTimes)
    }

    @Test
    fun `旧版本的文档没有分类`() {
        val map = SyncedRecipe.from(full).toMap()
        assertEquals("家常菜", SyncedRecipe.fromMap("u-1", firestoreLike(map))?.category)
        assertNull(SyncedRecipe.fromMap("u-1", firestoreLike(map - "category"))?.category)
    }

    @Test
    fun `小类和标签跟着同步，旧文档没有时为空`() {
        val map = SyncedRecipe.from(full).toMap()
        val back = SyncedRecipe.fromMap("u-1", firestoreLike(map))
        assertEquals("炖菜", back?.subcategory)
        assertEquals(listOf("荤菜", "好吃"), back?.tags)
        val old = SyncedRecipe.fromMap("u-1", firestoreLike(map - "subcategory" - "tags"))
        assertNull(old?.subcategory)
        assertEquals(emptyList<String>(), old?.tags)
    }

    @Test
    fun `格式不对或版本更新时忽略`() {
        val map = SyncedRecipe.from(full).toMap()
        assertNull(SyncedRecipe.fromMap("u-1", map - "title"))
        assertNull(SyncedRecipe.fromMap("u-1", map + ("schema" to 99L)))
        assertNull(SyncedRecipe.fromMap("u-1", map - "schema"))
    }

    @Test
    fun `越界的下标丢掉`() {
        val map = SyncedRecipe.from(full).toMap() + ("bowls" to listOf(
            mapOf("label" to "A", "stepIndex" to 5L, "items" to listOf(mapOf("ingredientIndex" to 9L, "amountText" to "x"))),
        ))
        val bowl = SyncedRecipe.fromMap("u-1", map)!!.bowls.single()
        assertNull(bowl.stepIndex)
        assertEquals(emptyList<SyncedBowlItem>(), bowl.items)
    }

    @Test
    fun `邀请码`() {
        val code = KitchenCodes.generate(Random(1))
        assertEquals(KitchenCodes.LENGTH, code.length)
        assertEquals(code, KitchenCodes.normalize(code.lowercase().chunked(4).joinToString(" - ")))
        assertNull(KitchenCodes.normalize("ABC"))
        assertNull(KitchenCodes.normalize("ABCDEFG0"))
        assertNotNull(KitchenCodes.normalize("abcdefgh"))
    }

    /** Firestore 读回来时整数都是 Long */
    private fun firestoreLike(value: Any?): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return convert(value) as Map<String, Any?>
    }

    private fun convert(value: Any?): Any? = when (value) {
        is Int -> value.toLong()
        is Map<*, *> -> value.entries.associate { (k, v) -> k as String to convert(v) }
        is List<*> -> value.map { convert(it) }
        else -> value
    }
}
