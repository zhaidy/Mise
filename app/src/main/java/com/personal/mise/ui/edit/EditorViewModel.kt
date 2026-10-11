package com.personal.mise.ui.edit

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.mise.MiseApp
import com.personal.mise.ai.AiAmountEstimator
import com.personal.mise.ai.AiStepSplitter
import com.personal.mise.bowl.SeasoningDictionary
import com.personal.mise.data.IngredientDraft
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeDraft
import com.personal.mise.data.StepDraft
import com.personal.mise.importer.ImportException
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.importer.XiaohongshuParser
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class IngredientRow(
    val key: Long,
    val id: Long = 0,
    val name: String = "",
    val amount: String = "",
    val kind: String = IngredientKind.MAIN,
    /** 用户手动点过分类后，不再按词典自动判断 */
    val kindManual: Boolean = false,
)

data class StepRow(
    val key: Long,
    val id: Long = 0,
    val text: String = "",
    val image: String? = null,
    /** 计时分钟数，空表示不计时 */
    val timerMinutes: String = "",
)

sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    /** needsKey：AI 整理时还没填 AI 的 API Key */
    data class Failed(val message: String, val needsKey: Boolean = false) : LoadState
}

/**
 * 菜谱编辑页和导入预览页共用的状态。
 * recipeId = 0 且 importText 不为空时是导入预览：aiNote = true 时 importText 是笔记文字，交给 AI 整理，
 * 否则是下厨房或小红书链接（小红书笔记也由 AI 整理）。
 */
class EditorViewModel(
    private val app: MiseApp,
    private val recipeId: Long,
    private val importText: String?,
    val aiNote: Boolean = false,
) : ViewModel() {

    private val keys = AtomicLong(1)
    private fun nextKey() = keys.getAndIncrement()

    var loadState by mutableStateOf<LoadState>(LoadState.Loading)
        private set
    var warnings by mutableStateOf<List<String>>(emptyList())
        private set
    var saving by mutableStateOf(false)
        private set
    var aiBusy by mutableStateOf(false)
        private set
    var aiMessage by mutableStateOf<String?>(null)
    /** 步骤已经用 AI 拆分过（导入时自动拆的或手动点的），界面上提示不用再点 */
    var stepsAiSplit by mutableStateOf(false)
        private set
    var estimating by mutableStateOf(false)
        private set
    var estimateMessage by mutableStateOf<String?>(null)
    var estimatingCalories by mutableStateOf(false)
        private set
    var caloriesMessage by mutableStateOf<String?>(null)

    var title by mutableStateOf("")
    var servings by mutableStateOf("")
    /** 整道菜的千卡，空表示还没算 */
    var calories by mutableStateOf("")
        private set
    /** 填热量时的用料；之后用料改过，就提示热量可能要重新估 */
    private var caloriesBasis: List<Pair<String, String>> = emptyList()
    var cover by mutableStateOf<String?>(null)
        private set
    private var sourceUrl: String? = null
    private var aiOrganized = false
    val ingredients = mutableStateListOf<IngredientRow>()
    val steps = mutableStateListOf<StepRow>()

    /** 这次编辑新加的图片；放弃或被替换时要删掉 */
    private val addedImages = mutableSetOf<String>()

    val isImport: Boolean get() = importText != null

    /** 小红书链接：抓到笔记后交给 AI 整理 */
    val isXhsLink: Boolean = !aiNote && importText?.let { UrlExtractor.firstUrl(it) }?.let { XiaohongshuParser.isNoteUrl(it) } == true

    /** 导入内容由 AI 整理（笔记文字或小红书），需要 Key */
    val aiImport: Boolean get() = aiNote || isXhsLink

    init {
        load()
    }

    fun load() {
        loadState = LoadState.Loading
        viewModelScope.launch {
            try {
                val draft = when {
                    importText != null -> {
                        // 没填 Key 时先别去抓网页、下图片
                        if (isXhsLink && !app.aiSettings.hasKey) throw ImportException("小红书笔记要用 AI 整理，请先填写 AI 的 API Key")
                        val result = if (aiNote) app.noteOrganizer.organize(importText) else app.importer.import(importText)
                        warnings = result.warnings
                        aiOrganized = aiNote || result.aiOrganized
                        addedImages += listOfNotNull(result.draft.coverImage) + result.draft.steps.mapNotNull { it.image }
                        result.draft
                    }
                    recipeId != 0L -> app.repository.loadDraft(recipeId) ?: throw ImportException("菜谱不存在")
                    else -> RecipeDraft(
                        ingredients = List(3) { IngredientDraft() },
                        steps = List(3) { StepDraft() },
                    )
                }
                fill(draft)
                loadState = LoadState.Ready
                // 导入的菜谱里有「适量」、没写用量或不是按克写的，有 Key 就顺手让 AI 估一下用量和克重，
                // 再按估好的用量算热量
                if (importText != null && hasAiKey) viewModelScope.launch {
                    if (canEstimate) estimateAmounts()
                    if (calories.isBlank()) estimateCalories()
                }
                // 下厨房链接导入的步骤常常几件事挤在一步里，有 Key 就自动用 AI 拆一下（AI 整理时已经分好步了）
                if (importText != null && !aiOrganized && hasAiKey && steps.any { it.text.isNotBlank() }) aiSplitSteps(auto = true)
            } catch (e: ImportException) {
                loadState = LoadState.Failed(e.message ?: "导入失败", needsKey = aiImport && !app.aiSettings.hasKey)
            } catch (e: Exception) {
                loadState = LoadState.Failed("出错了：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun fill(draft: RecipeDraft) {
        title = draft.title
        servings = draft.servings.orEmpty()
        cover = draft.coverImage
        sourceUrl = draft.sourceUrl
        ingredients.clear()
        ingredients += draft.ingredients.map {
            IngredientRow(nextKey(), it.id, it.name, it.amountText, it.kind, kindManual = it.id != 0L)
        }
        steps.clear()
        steps += draft.steps.map {
            StepRow(nextKey(), it.id, it.text, it.image, it.timerSeconds?.let { s -> formatMinutes(s) }.orEmpty())
        }
        calories = draft.calories?.toString().orEmpty()
        caloriesBasis = ingredientBasis()
    }

    // 用料

    fun addIngredient() {
        ingredients += IngredientRow(nextKey())
    }

    fun updateIngredientName(index: Int, name: String) {
        val row = ingredients[index]
        val kind = if (row.kindManual) row.kind
        else if (SeasoningDictionary.isSeasoning(name)) IngredientKind.SEASONING else IngredientKind.MAIN
        ingredients[index] = row.copy(name = name, kind = kind)
    }

    fun updateIngredientAmount(index: Int, amount: String) {
        ingredients[index] = ingredients[index].copy(amount = amount)
    }

    fun toggleIngredientKind(index: Int) {
        val row = ingredients[index]
        val kind = if (row.kind == IngredientKind.SEASONING) IngredientKind.MAIN else IngredientKind.SEASONING
        ingredients[index] = row.copy(kind = kind, kindManual = true)
    }

    fun removeIngredient(index: Int) {
        ingredients.removeAt(index)
    }

    // 步骤

    fun addStep() {
        steps += StepRow(nextKey())
    }

    fun updateStepText(index: Int, text: String) {
        steps[index] = steps[index].copy(text = text)
    }

    fun updateStepTimer(index: Int, minutes: String) {
        steps[index] = steps[index].copy(timerMinutes = minutes.filter { it.isDigit() || it == '.' }.take(5))
    }

    fun moveStep(index: Int, delta: Int) {
        val target = index + delta
        if (target !in steps.indices) return
        val row = steps.removeAt(index)
        steps.add(target, row)
    }

    fun removeStep(index: Int) {
        steps.removeAt(index)
    }

    val hasAiKey: Boolean get() = app.aiSettings.hasKey

    /** 有「适量」、「少许」、没写用量或不是按克写的用料 */
    val canEstimate: Boolean
        get() = ingredients.any { AiAmountEstimator.needsWork(it.name, it.amount) }

    /**
     * 让 AI 给「适量」或没写用量的用料估一个具体用量，标上「（估算）」；
     * 不是按克写的用量后面补上「≈X克」。估算期间改过的用量不覆盖。
     */
    fun aiEstimateAmounts() {
        viewModelScope.launch { estimateAmounts() }
    }

    private suspend fun estimateAmounts() {
        if (estimating) return
        estimating = true
        estimateMessage = null
        try {
            val snapshot = ingredients.toList()
            val result = app.amountEstimator.estimate(
                title,
                servings,
                snapshot.map { it.name to it.amount },
                steps.map { it.text },
            )
            var estimated = 0
            var grams = 0
            result.forEach { (i, amount) ->
                val old = snapshot[i]
                val index = ingredients.indexOfFirst { it.key == old.key }
                if (index >= 0 && ingredients[index].amount == old.amount) {
                    ingredients[index] = ingredients[index].copy(amount = amount)
                    if (AiAmountEstimator.needsEstimate(old.name, old.amount)) estimated++ else grams++
                }
            }
            estimateMessage = if (estimated + grams == 0) "估算期间用量改过了，没有覆盖" else listOfNotNull(
                "估算了 $estimated 样用料的用量，标了「估算」".takeIf { estimated > 0 },
                "给 $grams 样用料补了大概克重（≈）".takeIf { grams > 0 },
            ).joinToString("，", prefix = "AI ", postfix = "，可以再改")
        } catch (e: ImportException) {
            estimateMessage = e.message
        } catch (e: Exception) {
            estimateMessage = "AI 估算失败：${e.message ?: e.javaClass.simpleName}"
        } finally {
            estimating = false
        }
    }

    // 热量

    private fun ingredientBasis() = ingredients.filter { it.name.isNotBlank() }.map { it.name.trim() to it.amount.trim() }

    /** 填了热量之后用料又改过 */
    val caloriesStale: Boolean get() = calories.isNotBlank() && ingredientBasis() != caloriesBasis

    val canEstimateCalories: Boolean get() = ingredients.any { it.name.isNotBlank() }

    fun updateCalories(text: String) {
        calories = text.filter { it.isDigit() }.take(5)
        caloriesBasis = ingredientBasis()
    }

    /** 让 AI 按现在的用料估算整道菜的千卡 */
    fun aiEstimateCalories() {
        viewModelScope.launch { estimateCalories() }
    }

    private suspend fun estimateCalories() {
        if (estimatingCalories || !canEstimateCalories) return
        estimatingCalories = true
        caloriesMessage = null
        try {
            val basis = ingredientBasis()
            val kcal = app.calorieEstimator.estimate(title, servings, ingredients.map { it.name to it.amount }, steps.map { it.text })
            calories = kcal.toString()
            caloriesBasis = basis
            caloriesMessage = "AI 估算整道菜约 $kcal 千卡，可以再改"
        } catch (e: ImportException) {
            caloriesMessage = e.message
        } catch (e: Exception) {
            caloriesMessage = "AI 估算热量失败：${e.message ?: e.javaClass.simpleName}"
        } finally {
            estimatingCalories = false
        }
    }

    /**
     * 把现有步骤文字交给 AI 重新拆分。原来每步的图片和计时跟到拆出来的第一步；
     * 带图的步骤对应不上时不改动，免得把图弄丢。auto = 导入时自动拆分。
     */
    fun aiSplitSteps(auto: Boolean = false) {
        if (aiBusy) return
        aiBusy = true
        aiMessage = if (auto) "正在用 AI 自动拆分步骤…" else null
        viewModelScope.launch {
            try {
                val old = steps.toList()
                val result = app.stepSplitter.split(old.map { it.text })
                val carry = AiStepSplitter.carryOver(old.map { it.image != null }, result)
                if (carry == null) {
                    aiMessage = "AI 拆分后对不上原来的步骤图，${if (auto) "保留了下厨房原来的步骤" else "步骤没有改动"}"
                    return@launch
                }
                if (steps.toList() != old) {
                    // 拆分期间手动改过步骤，不覆盖
                    if (auto) aiMessage = "步骤已经改过，没有自动拆分，需要的话可以点「AI 拆分步骤」"
                    return@launch
                }
                val fromOld = carry.entries.associate { (i, j) -> j to old[i] }
                steps.clear()
                steps += result.mapIndexed { j, s ->
                    StepRow(
                        key = nextKey(),
                        text = s.text,
                        image = fromOld[j]?.image,
                        timerMinutes = s.timerMinutes?.let { formatMinutes((it * 60).toInt()) }
                            ?: fromOld[j]?.timerMinutes.orEmpty(),
                    )
                }
                stepsAiSplit = true
                aiMessage = if (auto) {
                    "✨ 导入时已自动用 AI 拆分：原来 ${old.size} 步 → 现在 ${result.size} 步，不用再点，检查一下就好"
                } else {
                    "已拆成 ${result.size} 步，请检查一下"
                }
            } catch (e: Exception) {
                val reason = if (e is ImportException) e.message else e.message ?: e.javaClass.simpleName
                aiMessage = if (auto) "自动 AI 拆分没成功（$reason），步骤保持原样" else if (e is ImportException) reason else "AI 拆分失败：$reason"
            } finally {
                aiBusy = false
            }
        }
    }

    fun removeStepImage(index: Int) {
        steps[index] = steps[index].copy(image = null)
    }

    fun removeCover() {
        cover = null
    }

    // 图片

    fun pickCover(uri: Uri) {
        viewModelScope.launch {
            app.images.importUri(uri)?.let {
                addedImages += it
                cover = it
            }
        }
    }

    fun pickStepImage(stepKey: Long, uri: Uri) {
        viewModelScope.launch {
            val path = app.images.importUri(uri) ?: return@launch
            addedImages += path
            val index = steps.indexOfFirst { it.key == stepKey }
            if (index >= 0) steps[index] = steps[index].copy(image = path)
        }
    }

    // 保存

    fun save(onSaved: (Long) -> Unit, onError: (String) -> Unit) {
        if (title.isBlank()) {
            onError("请填写菜名")
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val draft = toDraft()
                val id = app.repository.save(draft)
                val kept = listOfNotNull(draft.coverImage) + draft.steps.mapNotNull { it.image }
                (addedImages - kept.toSet()).forEach { app.images.delete(it) }
                addedImages.clear()
                onSaved(id)
            } catch (e: Exception) {
                onError("保存失败：${e.message ?: e.javaClass.simpleName}")
            } finally {
                saving = false
            }
        }
    }

    /** 放弃编辑：删掉这次新加的图片 */
    fun discard() {
        addedImages.forEach { app.images.delete(it) }
        addedImages.clear()
    }

    private fun toDraft() = RecipeDraft(
        id = recipeId,
        title = title,
        coverImage = cover,
        sourceUrl = sourceUrl,
        servings = servings,
        calories = calories.toIntOrNull()?.takeIf { it > 0 },
        ingredients = ingredients.map { IngredientDraft(it.id, it.name, it.amount, it.kind) },
        steps = steps.map { row ->
            val seconds = row.timerMinutes.toDoubleOrNull()?.let { (it * 60).toInt() }?.takeIf { it > 0 }
            StepDraft(row.id, row.text, row.image, seconds)
        },
    )

    private fun formatMinutes(seconds: Int): String =
        if (seconds % 60 == 0) (seconds / 60).toString() else "%.1f".format(seconds / 60.0)
}
