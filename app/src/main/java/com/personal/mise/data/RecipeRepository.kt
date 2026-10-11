package com.personal.mise.data

import androidx.room.withTransaction
import com.personal.mise.bowl.SauceBowlGenerator
import com.personal.mise.image.ImageStore
import com.personal.mise.sync.SyncedRecipe
import com.personal.mise.sync.SyncedTodayEntry
import com.personal.mise.sync.TodayKeys
import com.personal.mise.util.DayText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class IngredientDraft(
    val id: Long = 0,
    val name: String = "",
    val amountText: String = "",
    val kind: String = IngredientKind.MAIN,
)

data class StepDraft(
    val id: Long = 0,
    val text: String = "",
    val image: String? = null,
    val timerSeconds: Int? = null,
)

data class RecipeDraft(
    val id: Long = 0,
    val title: String = "",
    val coverImage: String? = null,
    val sourceUrl: String? = null,
    val servings: String? = null,
    /** 整道菜的千卡 */
    val calories: Int? = null,
    /** 分类，空表示未分类 */
    val category: String? = null,
    val ingredients: List<IngredientDraft> = emptyList(),
    val steps: List<StepDraft> = emptyList(),
)

/** 手动编辑后的调料碗 */
data class BowlEdit(
    val label: String,
    val stepId: Long?,
    val items: List<Pair<Long, String>>,
)

class RecipeRepository(
    private val db: MiseDatabase,
    private val images: ImageStore,
) {
    private val dao = db.recipeDao()
    private val todayDao = db.todayDao()
    private val cookLogDao = db.cookLogDao()

    fun search(query: String): Flow<List<Recipe>> = dao.search(query.trim())

    fun observeCategories(): Flow<List<String>> = dao.observeCategories()

    fun observeFull(id: Long): Flow<RecipeFull?> = combine(
        dao.observe(id),
        dao.observeIngredients(id),
        dao.observeSteps(id),
        dao.observeBowls(id),
        dao.observeBowlItems(id),
    ) { recipe, ingredients, steps, bowls, items ->
        recipe?.let { r ->
            RecipeFull(r, ingredients, steps, bowls.map { b -> BowlFull(b, items.filter { it.bowlId == b.id }) })
        }
    }

    suspend fun loadFull(id: Long): RecipeFull? {
        val recipe = dao.get(id) ?: return null
        val items = dao.getBowlItems(id)
        return RecipeFull(
            recipe,
            dao.getIngredients(id),
            dao.getSteps(id),
            dao.getBowls(id).map { b -> BowlFull(b, items.filter { it.bowlId == b.id }) },
        )
    }

    suspend fun loadDraft(id: Long): RecipeDraft? {
        val full = loadFull(id) ?: return null
        return RecipeDraft(
            id = full.recipe.id,
            title = full.recipe.title,
            coverImage = full.recipe.coverImage,
            sourceUrl = full.recipe.sourceUrl,
            servings = full.recipe.servings,
            calories = full.recipe.calories,
            category = full.recipe.category,
            ingredients = full.ingredients.map { IngredientDraft(it.id, it.name, it.amountText, it.kind) },
            steps = full.steps.map { StepDraft(it.id, it.text, it.image, it.timerSeconds) },
        )
    }

    /** 保存菜谱，返回 id。没手动调整过调料碗的菜谱会重新自动生成调料碗。 */
    suspend fun save(draft: RecipeDraft): Long {
        val oldImages = if (draft.id != 0L) imagesOf(draft.id) else emptySet()
        val id = db.withTransaction {
            val recipeId = if (draft.id == 0L) {
                dao.insert(
                    Recipe(
                        title = draft.title.trim(),
                        coverImage = draft.coverImage,
                        sourceUrl = draft.sourceUrl,
                        servings = draft.servings?.takeIf { it.isNotBlank() },
                        calories = draft.calories,
                        category = draft.category?.trim()?.takeIf { it.isNotEmpty() },
                    )
                )
            } else {
                val existing = requireNotNull(dao.get(draft.id)) { "菜谱不存在" }
                dao.update(
                    existing.copy(
                        title = draft.title.trim(),
                        coverImage = draft.coverImage,
                        sourceUrl = draft.sourceUrl,
                        servings = draft.servings?.takeIf { it.isNotBlank() },
                        calories = draft.calories,
                        category = draft.category?.trim()?.takeIf { it.isNotEmpty() },
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                draft.id
            }

            val keepIngredients = draft.ingredients
                .filter { it.name.isNotBlank() }
                .mapIndexed { index, d ->
                    val row = Ingredient(d.id, recipeId, d.name.trim(), d.amountText.trim(), d.kind, index)
                    if (d.id == 0L) dao.insertIngredient(row) else { dao.upsertIngredient(row); d.id }
                }
            dao.deleteIngredientsExcept(recipeId, keepIngredients)

            val keepSteps = draft.steps
                .filter { it.text.isNotBlank() || it.image != null }
                .mapIndexed { index, d ->
                    val row = Step(d.id, recipeId, index, d.text.trim(), d.image, d.timerSeconds)
                    if (d.id == 0L) dao.insertStep(row) else { dao.upsertStep(row); d.id }
                }
            dao.deleteStepsExcept(recipeId, keepSteps)

            if (dao.get(recipeId)?.bowlsManual != true) generateBowls(recipeId)
            recipeId
        }
        val newImages = imagesOf(id)
        (oldImages - newImages).forEach { images.delete(it) }
        return id
    }

    suspend fun delete(id: Long) {
        val paths = imagesOf(id)
        db.withTransaction {
            todayDao.markRemoved(id, System.currentTimeMillis())
            dao.deleteBowls(id)
            dao.deleteIngredients(id)
            dao.deleteSteps(id)
            dao.markDeleted(id, System.currentTimeMillis())
        }
        paths.forEach { images.delete(it) }
    }

    /** 只改热量，不动用料和调料碗；会同步到别的手机 */
    suspend fun setCalories(id: Long, calories: Int?) = dao.setCalories(id, calories, System.currentTimeMillis())

    private suspend fun imagesOf(id: Long): Set<String> {
        val recipe = dao.get(id) ?: return emptySet()
        return (listOf(recipe.coverImage) + dao.getSteps(id).map { it.image }).filterNotNull().toSet()
    }

    // 调料碗

    /** 按规则重新自动生成调料碗，覆盖手动调整 */
    suspend fun regenerateBowls(recipeId: Long) = db.withTransaction {
        generateBowls(recipeId)
        dao.setBowlsManual(recipeId, false)
        dao.touch(recipeId, System.currentTimeMillis())
    }

    private suspend fun generateBowls(recipeId: Long) {
        val ingredients = dao.getIngredients(recipeId)
        val steps = dao.getSteps(recipeId)
        val generated = SauceBowlGenerator.generate(
            ingredients.map { SauceBowlGenerator.IngredientInput(it.name, it.amountText, it.kind) },
            steps.map { it.text },
            steps.map { it.timerSeconds },
        )
        dao.deleteBowls(recipeId)
        generated.forEachIndexed { index, bowl ->
            val bowlId = dao.insertBowl(SauceBowl(recipeId = recipeId, label = bowl.label, stepId = steps[bowl.stepIndex].id, order = index))
            bowl.items.forEach { item ->
                dao.insertBowlItem(SauceBowlItem(bowlId = bowlId, ingredientId = ingredients[item.ingredientIndex].id, amountText = item.amountText))
            }
        }
    }

    /** 保存手动调整的调料碗，之后不再被自动结果覆盖 */
    suspend fun saveBowls(recipeId: Long, bowls: List<BowlEdit>) = db.withTransaction {
        dao.deleteBowls(recipeId)
        bowls.forEachIndexed { index, bowl ->
            val bowlId = dao.insertBowl(SauceBowl(recipeId = recipeId, label = bowl.label, stepId = bowl.stepId, order = index))
            bowl.items.forEach { (ingredientId, amount) ->
                dao.insertBowlItem(SauceBowlItem(bowlId = bowlId, ingredientId = ingredientId, amountText = amount))
            }
        }
        dao.setBowlsManual(recipeId, true)
        dao.touch(recipeId, System.currentTimeMillis())
    }

    // 同步

    fun observeStamps(): Flow<List<RecipeStamp>> = dao.observeStamps()

    fun observeImagePaths(): Flow<List<String>> = dao.observeImagePaths()

    /** 要上传的样子；已删除的菜谱只有标记，没有内容 */
    suspend fun loadForSync(uid: String): SyncedRecipe? {
        val recipe = dao.getByUid(uid) ?: return null
        val items = dao.getBowlItems(recipe.id)
        val full = RecipeFull(
            recipe,
            dao.getIngredients(recipe.id),
            dao.getSteps(recipe.id),
            dao.getBowls(recipe.id).map { b -> BowlFull(b, items.filter { it.bowlId == b.id }) },
        )
        return SyncedRecipe.from(full)
    }

    /** 用云端的版本整个替换本地这道菜（按 uid 对应），本地 id 不变 */
    suspend fun applyRemote(remote: SyncedRecipe) {
        val oldImages = dao.getByUid(remote.uid)?.let { imagesOf(it.id) } ?: emptySet()
        val id = db.withTransaction {
            val existing = dao.getByUid(remote.uid)
            val row = Recipe(
                id = existing?.id ?: 0,
                title = remote.title,
                coverImage = remote.coverImage,
                sourceUrl = remote.sourceUrl,
                servings = remote.servings,
                createdAt = remote.createdAt,
                bowlsManual = remote.bowlsManual,
                uid = remote.uid,
                updatedAt = remote.updatedAt,
                deleted = remote.deleted,
                calories = remote.calories,
                category = remote.category,
            )
            val recipeId = if (existing == null) dao.insert(row) else { dao.update(row); existing.id }

            dao.deleteBowls(recipeId)
            dao.deleteIngredients(recipeId)
            dao.deleteSteps(recipeId)
            if (remote.deleted) {
                todayDao.markRemoved(recipeId, System.currentTimeMillis())
                return@withTransaction recipeId
            }
            val ingredientIds = remote.ingredients.mapIndexed { index, g ->
                dao.insertIngredient(Ingredient(recipeId = recipeId, name = g.name, amountText = g.amountText, kind = g.kind, order = index))
            }
            val stepIds = remote.steps.mapIndexed { index, s ->
                dao.insertStep(Step(recipeId = recipeId, order = index, text = s.text, image = s.image, timerSeconds = s.timerSeconds))
            }
            remote.bowls.forEachIndexed { index, bowl ->
                val bowlId = dao.insertBowl(
                    SauceBowl(recipeId = recipeId, label = bowl.label, stepId = bowl.stepIndex?.let { stepIds.getOrNull(it) }, order = index)
                )
                bowl.items.forEach { item ->
                    ingredientIds.getOrNull(item.ingredientIndex)?.let {
                        dao.insertBowlItem(SauceBowlItem(bowlId = bowlId, ingredientId = it, amountText = item.amountText))
                    }
                }
            }
            recipeId
        }
        (oldImages - imagesOf(id)).forEach { images.delete(it) }
    }

    // 今日菜单

    fun observeToday(): Flow<List<TodayRow>> = todayDao.observe()

    suspend fun isInToday(recipeId: Long): Boolean = todayDao.get(recipeId)?.removed == false

    /** 加入今日菜单；已经在菜单里返回 false */
    suspend fun addToToday(recipeId: Long): Boolean = db.withTransaction {
        if (isInToday(recipeId)) return@withTransaction false
        todayDao.upsert(TodayMenu(recipeId = recipeId, order = todayDao.maxOrder() + 1))
        true
    }

    suspend fun removeFromToday(recipeId: Long) = todayDao.markRemoved(recipeId, System.currentTimeMillis())

    suspend fun clearToday() = todayDao.markAllRemoved(System.currentTimeMillis())

    suspend fun reorderToday(recipeIds: List<Long>) = db.withTransaction {
        val now = System.currentTimeMillis()
        recipeIds.forEachIndexed { index, id -> todayDao.setOrder(id, index, now) }
    }

    suspend fun setCurrentStep(recipeId: Long, step: Int) =
        todayDao.setCurrentStep(recipeId, step, System.currentTimeMillis())

    suspend fun setChecked(recipeId: Long, checked: Set<String>) =
        todayDao.setChecked(recipeId, checked.sorted().joinToString(","), System.currentTimeMillis())

    // 今日菜单同步

    fun observeTodayStamps(): Flow<List<RecipeStamp>> = todayDao.observeStamps()

    /** 要上传的样子；勾选项换成按下标的写法，对方手机才认得 */
    suspend fun loadTodayForSync(uid: String): SyncedTodayEntry? {
        val recipe = dao.getByUid(uid) ?: return null
        val entry = todayDao.get(recipe.id) ?: return null
        val full = loadFull(recipe.id)
        return SyncedTodayEntry(
            uid = uid,
            order = entry.order,
            currentStep = entry.currentStep,
            checked = parseChecked(entry.bowlsChecked)
                .mapNotNull { TodayKeys.toPortable(it, full?.ingredients.orEmpty().map { g -> g.id }, full?.bowls.orEmpty().map { b -> b.bowl.id }) }
                .sorted(),
            updatedAt = entry.updatedAt,
            removed = entry.removed,
        )
    }

    /** 用云端的版本替换本地这一项；这道菜还没同步过来时什么也不做 */
    suspend fun applyRemoteToday(remote: SyncedTodayEntry) {
        val recipe = dao.getByUid(remote.uid) ?: return
        val full = loadFull(recipe.id)
        val checked = remote.checked.mapNotNull {
            TodayKeys.fromPortable(it, full?.ingredients.orEmpty().map { g -> g.id }, full?.bowls.orEmpty().map { b -> b.bowl.id })
        }
        todayDao.upsert(
            TodayMenu(
                recipeId = recipe.id,
                order = remote.order,
                currentStep = remote.currentStep,
                bowlsChecked = checked.sorted().joinToString(","),
                updatedAt = remote.updatedAt,
                removed = remote.removed || recipe.deleted,
            )
        )
    }

    // 做菜记录

    fun observeCookLog(): Flow<List<CookLogRow>> = cookLogDao.observeAll()

    /** 这道菜做过的日子，新的在前 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCookDates(recipeId: Long): Flow<List<String>> =
        dao.observe(recipeId).flatMapLatest { r ->
            if (r == null) flowOf(emptyList()) else cookLogDao.observeDates(r.uid)
        }

    /** 记一笔：这天做了这道菜。这天已经记过返回 false */
    suspend fun logCooked(recipeId: Long, date: String = DayText.today()): Boolean = db.withTransaction {
        val recipe = dao.get(recipeId) ?: return@withTransaction false
        val uid = CookLog.uidOf(recipe.uid, date)
        if (cookLogDao.get(uid)?.deleted == false) return@withTransaction false
        cookLogDao.upsert(CookLog(uid = uid, recipeUid = recipe.uid, title = recipe.title, date = date))
        true
    }

    suspend fun deleteCookLog(uid: String) = cookLogDao.markDeleted(uid, System.currentTimeMillis())

    fun observeCookLogStamps(): Flow<List<RecipeStamp>> = cookLogDao.observeStamps()

    suspend fun loadCookLogForSync(uid: String): CookLog? = cookLogDao.get(uid)

    suspend fun applyRemoteCookLog(remote: CookLog) = cookLogDao.upsert(remote)
}

fun parseChecked(value: String): Set<String> =
    value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
