package com.personal.mise.data

import androidx.room.withTransaction
import com.personal.mise.bowl.SauceBowlGenerator
import com.personal.mise.image.ImageStore
import com.personal.mise.sync.SyncedRecipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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

    fun search(query: String): Flow<List<Recipe>> = dao.search(query.trim())

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
            todayDao.remove(id)
            dao.deleteBowls(id)
            dao.deleteIngredients(id)
            dao.deleteSteps(id)
            dao.markDeleted(id, System.currentTimeMillis())
        }
        paths.forEach { images.delete(it) }
    }

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
            )
            val recipeId = if (existing == null) dao.insert(row) else { dao.update(row); existing.id }

            dao.deleteBowls(recipeId)
            dao.deleteIngredients(recipeId)
            dao.deleteSteps(recipeId)
            if (remote.deleted) {
                todayDao.remove(recipeId)
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

    suspend fun isInToday(recipeId: Long): Boolean = todayDao.get(recipeId) != null

    /** 加入今日菜单；已经在菜单里返回 false */
    suspend fun addToToday(recipeId: Long): Boolean = db.withTransaction {
        if (todayDao.get(recipeId) != null) return@withTransaction false
        todayDao.insert(TodayMenu(recipeId = recipeId, order = todayDao.maxOrder() + 1))
        true
    }

    suspend fun removeFromToday(recipeId: Long) = todayDao.remove(recipeId)

    suspend fun clearToday() = todayDao.clear()

    suspend fun reorderToday(recipeIds: List<Long>) = db.withTransaction {
        recipeIds.forEachIndexed { index, id -> todayDao.setOrder(id, index) }
    }

    suspend fun setCurrentStep(recipeId: Long, step: Int) = todayDao.setCurrentStep(recipeId, step)

    suspend fun setChecked(recipeId: Long, checked: Set<String>) =
        todayDao.setChecked(recipeId, checked.sorted().joinToString(","))
}

fun parseChecked(value: String): Set<String> =
    value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
