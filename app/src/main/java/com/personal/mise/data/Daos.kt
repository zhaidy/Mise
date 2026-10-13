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
    @Query("UPDATE recipe SET deleted = 1, coverImage = NULL, original = NULL, note = NULL, review = NULL, stepTimes = NULL, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: Long, now: Long)

    @Query("UPDATE recipe SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE recipe SET calories = :calories, updatedAt = :now WHERE id = :id AND deleted = 0")
    suspend fun setCalories(id: Long, calories: Int?, now: Long)

    @Query("UPDATE recipe SET note = :note, updatedAt = :now WHERE id = :id AND deleted = 0")
    suspend fun setNote(id: Long, note: String?, now: Long)

    @Query("UPDATE recipe SET review = :review, updatedAt = :now WHERE id = :id AND deleted = 0")
    suspend fun setReview(id: Long, review: String?, now: Long)

    @Query("UPDATE recipe SET stepTimes = :stepTimes, updatedAt = :now WHERE id = :id AND deleted = 0")
    suspend fun setStepTimes(id: Long, stepTimes: String?, now: Long)

    @Query("UPDATE recipe SET category = :category, subcategory = :subcategory, tags = :tags, updatedAt = :now WHERE id = :id AND deleted = 0")
    suspend fun setClassification(id: Long, category: String?, subcategory: String?, tags: String, now: Long)

    /** 改名或删除大类；删除时（newName 为空）小类一起去掉 */
    @Query(
        """
        UPDATE recipe SET category = :newName,
            subcategory = CASE WHEN :newName IS NULL THEN NULL ELSE subcategory END,
            updatedAt = :now
        WHERE deleted = 0 AND category = :old
        """
    )
    suspend fun renameCategory(old: String, newName: String?, now: Long)

    @Query("UPDATE recipe SET subcategory = :newName, updatedAt = :now WHERE deleted = 0 AND category = :category AND subcategory = :old")
    suspend fun renameSubcategory(category: String, old: String, newName: String?, now: Long)

    /** 可能带这个标签的菜谱；LIKE 只是粗筛，调用方再按标签精确比对 */
    @Query("SELECT * FROM recipe WHERE deleted = 0 AND tags LIKE '%' || :tag || '%'")
    suspend fun withTagLike(tag: String): List<Recipe>

    @Query("UPDATE recipe SET tags = :tags, updatedAt = :now WHERE id = :id")
    suspend fun setTags(id: Long, tags: String, now: Long)

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

    /** 用料改了用量时，碗里原样记着整份用量的跟着改；分几碗各放一部分的不动 */
    @Query("UPDATE sauce_bowl_item SET amountText = :newAmount WHERE ingredientId = :ingredientId AND amountText = :oldAmount")
    suspend fun updateBowlItemAmount(ingredientId: Long, oldAmount: String, newAmount: String)

    @Query("DELETE FROM sauce_bowl WHERE recipeId = :recipeId")
    suspend fun deleteBowls(recipeId: Long)
}

@Dao
interface CookLogDao {
    /** 菜谱还在就用现在的菜名 */
    @Query(
        """
        SELECT c.uid AS uid, c.date AS date, COALESCE(r.title, c.title) AS title,
               r.id AS recipeId, r.coverImage AS coverImage, r.calories AS calories
        FROM cook_log c LEFT JOIN recipe r ON r.uid = c.recipeUid AND r.deleted = 0
        WHERE c.deleted = 0
        ORDER BY c.date DESC, c.updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<CookLogRow>>

    /** 这道菜做过的日子，新的在前 */
    @Query("SELECT date FROM cook_log WHERE recipeUid = :recipeUid AND deleted = 0 ORDER BY date DESC")
    fun observeDates(recipeUid: String): Flow<List<String>>

    /** 每道菜最后一次做的日子 */
    @Query("SELECT recipeUid, MAX(date) AS date FROM cook_log WHERE deleted = 0 GROUP BY recipeUid")
    fun observeLastCooked(): Flow<List<LastCooked>>

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

@Dao
interface MealPlanDao {
    /** 菜谱还在就用现在的菜名；同一天先排的在前 */
    @Query(
        """
        SELECT p.uid AS uid, p.date AS date, COALESCE(r.title, p.title) AS title, p.checked AS checked,
               p.done AS done, r.id AS recipeId, r.coverImage AS coverImage, r.calories AS calories
        FROM meal_plan p LEFT JOIN recipe r ON r.uid = p.recipeUid AND r.deleted = 0
        WHERE p.deleted = 0
        ORDER BY p.date, p.sortOrder, p.uid
        """
    )
    fun observeAll(): Flow<List<PlanRow>>

    /** 这天的今日菜单：菜谱删了的不算 */
    @Query(
        """
        SELECT p.uid AS uid, r.id AS recipeId, p.sortOrder AS position, p.currentStep AS currentStep,
               p.checked AS checked, r.title AS title, r.coverImage AS coverImage,
               r.calories AS calories, p.done AS done,
               (SELECT COUNT(*) FROM step s WHERE s.recipeId = r.id) AS stepCount
        FROM meal_plan p JOIN recipe r ON r.uid = p.recipeUid AND r.deleted = 0
        WHERE p.deleted = 0 AND p.date = :date
        ORDER BY p.sortOrder, p.uid
        """
    )
    fun observeDay(date: String): Flow<List<TodayRow>>

    /** 这道菜排在哪些日子 */
    @Query("SELECT date FROM meal_plan WHERE recipeUid = :recipeUid AND deleted = 0 ORDER BY date")
    fun observeDates(recipeUid: String): Flow<List<String>>

    /** 包括已移出的 */
    @Query("SELECT * FROM meal_plan WHERE uid = :uid")
    suspend fun get(uid: String): MealPlan?

    @Query("SELECT * FROM meal_plan WHERE date = :date AND deleted = 0 ORDER BY sortOrder, uid")
    suspend fun onDate(date: String): List<MealPlan>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM meal_plan WHERE date = :date AND deleted = 0")
    suspend fun maxOrder(date: String): Int

    /** 包括已移出的，同步时比较两边谁新 */
    @Query("SELECT uid, updatedAt FROM meal_plan")
    fun observeStamps(): Flow<List<RecipeStamp>>

    @Upsert
    suspend fun upsert(plan: MealPlan)

    @Query("UPDATE meal_plan SET deleted = 1, updatedAt = :now WHERE uid = :uid AND deleted = 0")
    suspend fun markDeleted(uid: String, now: Long)

    @Query("UPDATE meal_plan SET deleted = 1, updatedAt = :now WHERE date = :date AND deleted = 0")
    suspend fun markDayDeleted(date: String, now: Long)

    // 下面几个值没变就不写，免得每次打开页面都算一次改动、触发同步

    @Query("UPDATE meal_plan SET checked = :checked, updatedAt = :now WHERE uid = :uid AND checked != :checked")
    suspend fun setChecked(uid: String, checked: String, now: Long)

    @Query("UPDATE meal_plan SET sortOrder = :order, updatedAt = :now WHERE uid = :uid AND sortOrder != :order")
    suspend fun setOrder(uid: String, order: Int, now: Long)

    @Query("UPDATE meal_plan SET currentStep = :step, updatedAt = :now WHERE uid = :uid AND currentStep != :step")
    suspend fun setCurrentStep(uid: String, step: Int, now: Long)

    @Query("UPDATE meal_plan SET done = :done, updatedAt = :now WHERE uid = :uid AND done != :done")
    suspend fun setDone(uid: String, done: Boolean, now: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM ai_chat_message WHERE thread = :thread ORDER BY id")
    suspend fun thread(thread: String): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE ai_chat_message SET card = :card WHERE id = :id")
    suspend fun setCard(id: Long, card: String?)

    @Query("DELETE FROM ai_chat_message WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ai_chat_message WHERE thread = :thread")
    suspend fun clear(thread: String)
}
