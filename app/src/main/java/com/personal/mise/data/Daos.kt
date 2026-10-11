package com.personal.mise.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT recipeId, name FROM ingredient WHERE recipeId IN (SELECT id FROM recipe WHERE deleted = 0)")
    fun observeIngredientNames(): Flow<List<IngredientName>>

    /** 每个大类、小类组合用了几次，排序交给 Categories.tree */
    @Query(
        """
        SELECT category, subcategory, COUNT(*) AS count FROM recipe
        WHERE deleted = 0 AND category IS NOT NULL AND category != ''
        GROUP BY category, subcategory
        """
    )
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("SELECT tags FROM recipe WHERE deleted = 0 AND tags != ''")
    fun observeTagStrings(): Flow<List<String>>

    @Query("SELECT * FROM recipe WHERE id = :id AND deleted = 0")
    fun observe(id: Long): Flow<Recipe?>

    @Query("SELECT * FROM recipe WHERE id = :id AND deleted = 0")
    suspend fun get(id: Long): Recipe?

    /** 包括已删除的，同步时用 */
    @Query("SELECT * FROM recipe WHERE uid = :uid")
    suspend fun getByUid(uid: String): Recipe?

    /** 包括已删除的，同步时比较两边谁新 */
    @Query("SELECT uid, updatedAt FROM recipe")
    fun observeStamps(): Flow<List<RecipeStamp>>

    /** 没删除的菜谱用到的所有图片，同步图片用 */
    @Query(
        """
        SELECT coverImage FROM recipe WHERE deleted = 0 AND coverImage IS NOT NULL
        UNION
        SELECT s.image FROM step s JOIN recipe r ON r.id = s.recipeId WHERE r.deleted = 0 AND s.image IS NOT NULL
        """
    )
    fun observeImagePaths(): Flow<List<String>>

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    /** 只留下 uid 和删除标记，内容由调用方另行清掉 */
    @Query("UPDATE recipe SET deleted = 1, coverImage = NULL, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: Long, now: Long)

    @Query("UPDATE recipe SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE recipe SET calories = :calories, updatedAt = :now WHERE id = :id AND deleted = 0")
    suspend fun setCalories(id: Long, calories: Int?, now: Long)

    @Query("DELETE FROM ingredient WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: Long)

    @Query("DELETE FROM step WHERE recipeId = :recipeId")
    suspend fun deleteSteps(recipeId: Long)

    @Query("UPDATE recipe SET bowlsManual = :manual WHERE id = :id")
    suspend fun setBowlsManual(id: Long, manual: Boolean)

    // 用料

    @Query("SELECT * FROM ingredient WHERE recipeId = :recipeId ORDER BY sortOrder")
    fun observeIngredients(recipeId: Long): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredient WHERE recipeId = :recipeId ORDER BY sortOrder")
    suspend fun getIngredients(recipeId: Long): List<Ingredient>

    @Insert
    suspend fun insertIngredient(ingredient: Ingredient): Long

    @Upsert
    suspend fun upsertIngredient(ingredient: Ingredient)

    @Query("DELETE FROM ingredient WHERE recipeId = :recipeId AND id NOT IN (:keep)")
    suspend fun deleteIngredientsExcept(recipeId: Long, keep: List<Long>)

    // 步骤

    @Query("SELECT * FROM step WHERE recipeId = :recipeId ORDER BY sortOrder")
    fun observeSteps(recipeId: Long): Flow<List<Step>>

    @Query("SELECT * FROM step WHERE recipeId = :recipeId ORDER BY sortOrder")
    suspend fun getSteps(recipeId: Long): List<Step>

    @Query("SELECT COUNT(*) FROM step WHERE recipeId = :recipeId")
    suspend fun countSteps(recipeId: Long): Int

    @Insert
    suspend fun insertStep(step: Step): Long

    @Upsert
    suspend fun upsertStep(step: Step)

    @Query("DELETE FROM step WHERE recipeId = :recipeId AND id NOT IN (:keep)")
    suspend fun deleteStepsExcept(recipeId: Long, keep: List<Long>)

    // 调料碗

    @Query("SELECT * FROM sauce_bowl WHERE recipeId = :recipeId ORDER BY sortOrder")
    fun observeBowls(recipeId: Long): Flow<List<SauceBowl>>

    @Query("SELECT * FROM sauce_bowl WHERE recipeId = :recipeId ORDER BY sortOrder")
    suspend fun getBowls(recipeId: Long): List<SauceBowl>

    @Query(
        """
        SELECT it.id AS id, it.bowlId AS bowlId, it.ingredientId AS ingredientId,
               g.name AS name, it.amountText AS amountText
        FROM sauce_bowl_item it
        JOIN ingredient g ON g.id = it.ingredientId
        JOIN sauce_bowl b ON b.id = it.bowlId
        WHERE b.recipeId = :recipeId
        ORDER BY it.id
        """
    )
    fun observeBowlItems(recipeId: Long): Flow<List<BowlItemRow>>

    @Query(
        """
        SELECT it.id AS id, it.bowlId AS bowlId, it.ingredientId AS ingredientId,
               g.name AS name, it.amountText AS amountText
        FROM sauce_bowl_item it
        JOIN ingredient g ON g.id = it.ingredientId
        JOIN sauce_bowl b ON b.id = it.bowlId
        WHERE b.recipeId = :recipeId
        ORDER BY it.id
        """
    )
    suspend fun getBowlItems(recipeId: Long): List<BowlItemRow>

    @Insert
    suspend fun insertBowl(bowl: SauceBowl): Long

    @Insert
    suspend fun insertBowlItem(item: SauceBowlItem): Long

    @Query("DELETE FROM sauce_bowl WHERE recipeId = :recipeId")
    suspend fun deleteBowls(recipeId: Long)
}

@Dao
interface TodayDao {
    @Query(
        """
        SELECT t.recipeId AS recipeId, t.sortOrder AS position, t.currentStep AS currentStep,
               t.bowlsChecked AS bowlsChecked, r.title AS title, r.coverImage AS coverImage,
               r.calories AS calories,
               (SELECT COUNT(*) FROM step s WHERE s.recipeId = t.recipeId) AS stepCount
        FROM today_menu t JOIN recipe r ON r.id = t.recipeId
        WHERE t.removed = 0
        ORDER BY t.sortOrder, t.recipeId
        """
    )
    fun observe(): Flow<List<TodayRow>>

    /** 包括已移出的 */
    @Query("SELECT * FROM today_menu WHERE recipeId = :recipeId")
    suspend fun get(recipeId: Long): TodayMenu?

    /** 包括已移出的，同步时比较两边谁新 */
    @Query("SELECT r.uid AS uid, t.updatedAt AS updatedAt FROM today_menu t JOIN recipe r ON r.id = t.recipeId")
    fun observeStamps(): Flow<List<RecipeStamp>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM today_menu WHERE removed = 0")
    suspend fun maxOrder(): Int

    @Upsert
    suspend fun upsert(entry: TodayMenu)

    @Query("UPDATE today_menu SET removed = 1, updatedAt = :now WHERE recipeId = :recipeId AND removed = 0")
    suspend fun markRemoved(recipeId: Long, now: Long)

    @Query("UPDATE today_menu SET removed = 1, updatedAt = :now WHERE removed = 0")
    suspend fun markAllRemoved(now: Long)

    // 下面几个值没变就不写，免得每次打开页面都算一次改动、触发同步

    @Query("UPDATE today_menu SET sortOrder = :order, updatedAt = :now WHERE recipeId = :recipeId AND sortOrder != :order")
    suspend fun setOrder(recipeId: Long, order: Int, now: Long)

    @Query("UPDATE today_menu SET currentStep = :step, updatedAt = :now WHERE recipeId = :recipeId AND currentStep != :step")
    suspend fun setCurrentStep(recipeId: Long, step: Int, now: Long)

    @Query("UPDATE today_menu SET bowlsChecked = :checked, updatedAt = :now WHERE recipeId = :recipeId AND bowlsChecked != :checked")
    suspend fun setChecked(recipeId: Long, checked: String, now: Long)
}

@Dao
interface CookLogDao {
    /** 菜谱还在就用现在的菜名 */
    @Query(
        """
        SELECT c.uid AS uid, c.date AS date, COALESCE(r.title, c.title) AS title,
               r.id AS recipeId, r.coverImage AS coverImage
        FROM cook_log c LEFT JOIN recipe r ON r.uid = c.recipeUid AND r.deleted = 0
        WHERE c.deleted = 0
        ORDER BY c.date DESC, c.updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<CookLogRow>>

    /** 这道菜做过的日子，新的在前 */
    @Query("SELECT date FROM cook_log WHERE recipeUid = :recipeUid AND deleted = 0 ORDER BY date DESC")
    fun observeDates(recipeUid: String): Flow<List<String>>

    /** 包括已删除的 */
    @Query("SELECT * FROM cook_log WHERE uid = :uid")
    suspend fun get(uid: String): CookLog?

    /** 包括已删除的，同步时比较两边谁新 */
    @Query("SELECT uid, updatedAt FROM cook_log")
    fun observeStamps(): Flow<List<RecipeStamp>>

    @Upsert
    suspend fun upsert(log: CookLog)

    @Query("UPDATE cook_log SET deleted = 1, updatedAt = :now WHERE uid = :uid AND deleted = 0")
    suspend fun markDeleted(uid: String, now: Long)
}
