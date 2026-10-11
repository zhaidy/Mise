package com.personal.mise.data

import androidx.room.withTransaction
import com.personal.mise.bowl.SauceBowlGenerator
import com.personal.mise.image.ImageStore
import com.personal.mise.sync.SyncedPlanEntry
import com.personal.mise.sync.SyncedRecipe
import com.personal.mise.sync.SyncedTodayEntry
import com.personal.mise.sync.TodayKeys
import com.personal.mise.util.DayText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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
    /** 大类，空表示未分类 */
    val category: String? = null,
    val subcategory: String? = null,
    val tags: List<String> = emptyList(),
    /** 翻译前的原文，没翻译过为空 */
    val original: RecipeText? = null,
    val ingredients: List<IngredientDraft> = emptyList(),
    val steps: List<StepDraft> = emptyList(),
)

private val RecipeDraft.cleanCategory: String? get() = category?.trim()?.takeIf { it.isNotEmpty() }

/** 没有大类时小类也不留 */
private val RecipeDraft.cleanSubcategory: String?
    get() = subcategory?.trim()?.takeIf { it.isNotEmpty() && cleanCategory != null }

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
    private val planDao = db.mealPlanDao()

    /** 按菜名、分类、标签或用料搜，见 RecipeSearch */
    fun search(query: String): Flow<List<SearchHit>> =
        combine(dao.observeAll(), dao.observeIngredientNames()) { recipes, ingredients ->
            RecipeSearch.search(query, recipes, ingredients)
        }

    fun observeCategories(): Flow<List<CategoryNode>> = dao.observeCategoryCounts().map(Categories::tree)

    fun observeTags(): Flow<List<String>> = dao.observeTagStrings().map(Categories::tagsByUse)

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
            subcategory = full.recipe.subcategory,
            tags = full.recipe.tagList,
            original = RecipeText.fromJson(full.recipe.original),
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
                        category = draft.cleanCategory,
                        subcategory = draft.cleanSubcategory,
                        tags = RecipeTags.format(draft.tags),
                        original = draft.original?.toJson()?.toString(),
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
                        category = draft.cleanCategory,
                        subcategory = draft.cleanSubcategory,
                        tags = RecipeTags.format(draft.tags),
                        original = draft.original?.toJson()?.toString(),
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

    // 分类和标签

    /** 只改分类和标签，不进编辑页；会同步到别的手机 */
    suspend fun setClassification(id: Long, category: String?, subcategory: String?, tags: List<String>) {
        val draft = RecipeDraft(category = category, subcategory = subcategory)
        dao.setClassification(id, draft.cleanCategory, draft.cleanSubcategory, RecipeTags.format(tags), System.currentTimeMillis())
    }

    /** 所有用到这个大类的菜谱一起改名；新名字为空就是删除（菜谱变成未分类） */
    suspend fun renameCategory(old: String, newName: String) =
        dao.renameCategory(old, newName.trim().takeIf { it.isNotEmpty() }, System.currentTimeMillis())

    /** 新名字为空就是删除小类，菜谱还留在大类里 */
    suspend fun renameSubcategory(category: String, old: String, newName: String) =
        dao.renameSubcategory(category, old, newName.trim().takeIf { it.isNotEmpty() }, System.currentTimeMillis())

    /** 所有带这个标签的菜谱一起改；新名字为空就是删除，改成已有的标签就是合并 */
    suspend fun renameTag(old: String, newName: String) = db.withTransaction {
        val now = System.currentTimeMillis()
        dao.withTagLike(old).forEach { recipe ->
            RecipeTags.replace(recipe.tagList, old, newName)?.let { dao.setTags(recipe.id, RecipeTags.format(it), now) }
        }
    }

    /** 只改热量，不动用料和调料碗；会同步到别的手机 */
    suspend fun setCalories(id: Long, calories: Int?) = dao.setCalories(id, calories, System.currentTimeMillis())

    /** 只改批注；空白就是删掉。会同步到别的手机 */
    suspend fun setNote(id: Long, note: String) =
        dao.setNote(id, note.trim().takeIf { it.isNotEmpty() }, System.currentTimeMillis())

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
                subcategory = remote.subcategory,
                tags = RecipeTags.format(remote.tags),
                original = remote.original,
                note = remote.note,
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

    /** 加入今日菜单，同时排进今天的计划；已经在菜单里返回 false */
    suspend fun addToToday(recipeId: Long): Boolean = db.withTransaction {
        addToPlan(recipeId, DayText.today())
        if (isInToday(recipeId)) return@withTransaction false
        todayDao.upsert(TodayMenu(recipeId = recipeId, order = todayDao.maxOrder() + 1))
        true
    }

    /** 今日菜单里的菜都排进今天的计划；有计划之前加进今日菜单的菜靠这个补上 */
    suspend fun todayToPlan() = db.withTransaction {
        val today = DayText.today()
        todayDao.observe().first().forEach { addToPlan(it.recipeId, today) }
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

    /** 标成做完时顺手记一笔今天做过；取消做完时把今天这笔也撤掉 */
    suspend fun setTodayDone(recipeId: Long, done: Boolean) = db.withTransaction {
        todayDao.setDone(recipeId, done, System.currentTimeMillis())
        if (done) {
            logCooked(recipeId)
        } else {
            val recipe = dao.get(recipeId) ?: return@withTransaction
            val uid = CookLog.uidOf(recipe.uid, DayText.today())
            if (cookLogDao.get(uid)?.deleted == false) deleteCookLog(uid)
        }
    }

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
            done = entry.done,
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
                done = remote.done,
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
    /** 菜谱 uid → 最后一次做的日子 */
    fun observeLastCooked(): Flow<Map<String, String>> =
        cookLogDao.observeLastCooked().map { rows -> rows.associate { it.recipeUid to it.date } }

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

    // 做菜计划

    fun observePlan(): Flow<List<PlanRow>> = planDao.observeAll()

    /** 这道菜排在哪些日子，早的在前 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePlanDates(recipeId: Long): Flow<List<String>> =
        dao.observe(recipeId).flatMapLatest { r ->
            if (r == null) flowOf(emptyList()) else planDao.observeDates(r.uid)
        }

    /** 把这道菜排到这天；这天已经排了返回 false */
    suspend fun addToPlan(recipeId: Long, date: String): Boolean = db.withTransaction {
        val recipe = dao.get(recipeId) ?: return@withTransaction false
        val uid = MealPlan.uidOf(recipe.uid, date)
        if (planDao.get(uid)?.deleted == false) return@withTransaction false
        planDao.upsert(MealPlan(uid = uid, recipeUid = recipe.uid, title = recipe.title, date = date))
        true
    }

    suspend fun removeFromPlan(uid: String) = planDao.markDeleted(uid, System.currentTimeMillis())

    suspend fun setPlanChecked(uid: String, checked: Set<String>) =
        planDao.setChecked(uid, checked.sorted().joinToString(","), System.currentTimeMillis())

    /**
     * 把这天计划的菜都放进今日菜单，返回新放进去的道数。
     * 提前备料勾过的项目一起带过去，备料清单里接着勾。
     */
    suspend fun planToToday(date: String): Int = db.withTransaction {
        var added = 0
        planDao.onDate(date).forEach { plan ->
            val recipe = dao.getByUid(plan.recipeUid)?.takeIf { !it.deleted } ?: return@forEach
            if (isInToday(recipe.id)) return@forEach
            todayDao.upsert(TodayMenu(recipeId = recipe.id, order = todayDao.maxOrder() + 1, bowlsChecked = plan.checked))
            added++
        }
        added
    }

    fun observePlanStamps(): Flow<List<RecipeStamp>> = planDao.observeStamps()

    /** 要上传的样子；勾选项换成按下标的写法，对方手机才认得 */
    suspend fun loadPlanForSync(uid: String): SyncedPlanEntry? {
        val plan = planDao.get(uid) ?: return null
        val full = dao.getByUid(plan.recipeUid)?.let { loadFull(it.id) }
        return SyncedPlanEntry(
            uid = plan.uid,
            recipeUid = plan.recipeUid,
            title = plan.title,
            date = plan.date,
            checked = parseChecked(plan.checked)
                .mapNotNull { TodayKeys.toPortable(it, full?.ingredients.orEmpty().map { g -> g.id }, full?.bowls.orEmpty().map { b -> b.bowl.id }) }
                .sorted(),
            updatedAt = plan.updatedAt,
            deleted = plan.deleted,
        )
    }

    /** 用云端的版本替换本地这一条；这道菜还没同步过来时勾选项先丢掉 */
    suspend fun applyRemotePlan(remote: SyncedPlanEntry) {
        val full = dao.getByUid(remote.recipeUid)?.let { loadFull(it.id) }
        val checked = remote.checked.mapNotNull {
            TodayKeys.fromPortable(it, full?.ingredients.orEmpty().map { g -> g.id }, full?.bowls.orEmpty().map { b -> b.bowl.id })
        }
        planDao.upsert(
            MealPlan(
                uid = remote.uid,
                recipeUid = remote.recipeUid,
                title = remote.title,
                date = remote.date,
                checked = checked.sorted().joinToString(","),
                updatedAt = remote.updatedAt,
                deleted = remote.deleted,
            )
        )
    }
}

fun parseChecked(value: String): Set<String> =
    value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
