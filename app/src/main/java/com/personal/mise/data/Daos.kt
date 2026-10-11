package com.personal.mise.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe WHERE deleted = 0 AND title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<Recipe>>

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

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    /** 只留下 uid 和删除标记，内容由调用方另行清掉 */
    @Query("UPDATE recipe SET deleted = 1, coverImage = NULL, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: Long, now: Long)

    @Query("UPDATE recipe SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

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
