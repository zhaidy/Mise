package com.personal.mise.ui.edit

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.mise.MiseApp
import com.personal.mise.bowl.SeasoningDictionary
import com.personal.mise.data.IngredientDraft
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeDraft
import com.personal.mise.data.StepDraft
import com.personal.mise.importer.ImportException
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
    data class Failed(val message: String) : LoadState
}

/**
 * 菜谱编辑页和导入预览页共用的状态。
 * recipeId = 0 且 importText 不为空时是导入预览。
 */
class EditorViewModel(
    private val app: MiseApp,
    private val recipeId: Long,
    private val importText: String?,
) : ViewModel() {

    private val keys = AtomicLong(1)
    private fun nextKey() = keys.getAndIncrement()

    var loadState by mutableStateOf<LoadState>(LoadState.Loading)
        private set
    var warnings by mutableStateOf<List<String>>(emptyList())
        private set
    var saving by mutableStateOf(false)
        private set

    var title by mutableStateOf("")
    var servings by mutableStateOf("")
    var cover by mutableStateOf<String?>(null)
        private set
    private var sourceUrl: String? = null
    val ingredients = mutableStateListOf<IngredientRow>()
    val steps = mutableStateListOf<StepRow>()

    /** 这次编辑新加的图片；放弃或被替换时要删掉 */
    private val addedImages = mutableSetOf<String>()

    val isImport: Boolean get() = importText != null

    init {
        load()
    }

    fun load() {
        loadState = LoadState.Loading
        viewModelScope.launch {
            try {
                val draft = when {
                    importText != null -> {
                        val result = app.importer.import(importText)
                        warnings = result.warnings
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
            } catch (e: ImportException) {
                loadState = LoadState.Failed(e.message ?: "导入失败")
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
        ingredients = ingredients.map { IngredientDraft(it.id, it.name, it.amount, it.kind) },
        steps = steps.map { row ->
            val seconds = row.timerMinutes.toDoubleOrNull()?.let { (it * 60).toInt() }?.takeIf { it > 0 }
            StepDraft(row.id, row.text, row.image, seconds)
        },
    )

    private fun formatMinutes(seconds: Int): String =
        if (seconds % 60 == 0) (seconds / 60).toString() else "%.1f".format(seconds / 60.0)
}
