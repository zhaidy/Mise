package com.personal.mise.ui.bowl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.mise.MiseApp
import com.personal.mise.bowl.SauceBowlGenerator
import com.personal.mise.data.BowlEdit
import com.personal.mise.data.Ingredient
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.Step
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class EditItem(val key: Long, val ingredientId: Long, val name: String, val amount: String)

data class EditBowl(val key: Long, val stepId: Long?, val items: List<EditItem>)

class BowlEditViewModel(private val app: MiseApp, private val recipeId: Long) : ViewModel() {

    private val keys = AtomicLong(1)
    private fun nextKey() = keys.getAndIncrement()

    var loaded by mutableStateOf(false)
        private set
    var dirty by mutableStateOf(false)
        private set
    var steps by mutableStateOf<List<Step>>(emptyList())
        private set
    /** 可以放进调料碗的用料（调料在前） */
    var pool by mutableStateOf<List<Ingredient>>(emptyList())
        private set
    val bowls = mutableStateListOf<EditBowl>()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val full = app.repository.loadFull(recipeId) ?: return@launch
            steps = full.steps
            pool = full.ingredients.sortedBy { if (it.kind == IngredientKind.SEASONING) 0 else 1 }
            bowls.clear()
            bowls += full.bowls.map { b ->
                EditBowl(nextKey(), b.bowl.stepId, b.items.map { EditItem(nextKey(), it.ingredientId, it.name, it.amountText) })
            }
            dirty = false
            loaded = true
        }
    }

    fun label(index: Int) = SauceBowlGenerator.label(index)

    fun usageCount(ingredientId: Long) = bowls.sumOf { b -> b.items.count { it.ingredientId == ingredientId } }

    fun addBowl() {
        bowls += EditBowl(nextKey(), null, emptyList())
        dirty = true
    }

    fun removeBowl(bowlKey: Long) {
        bowls.removeAll { it.key == bowlKey }
        dirty = true
    }

    fun setStep(bowlKey: Long, stepId: Long?) = updateBowl(bowlKey) { it.copy(stepId = stepId) }

    /** 从用料里放一份到碗里（同一调料可以分进几个碗） */
    fun addFromPool(ingredient: Ingredient, bowlKey: Long) = updateBowl(bowlKey) { bowl ->
        bowl.copy(items = bowl.items + EditItem(nextKey(), ingredient.id, ingredient.name, ingredient.amountText))
    }

    /** 把一项移到另一个碗；toBowlKey 为空表示拿出调料碗 */
    fun moveItem(itemKey: Long, toBowlKey: Long?) {
        val fromIndex = bowls.indexOfFirst { b -> b.items.any { it.key == itemKey } }
        if (fromIndex < 0) return
        val item = bowls[fromIndex].items.first { it.key == itemKey }
        if (bowls[fromIndex].key == toBowlKey) return
        if (toBowlKey != null && bowls.none { it.key == toBowlKey }) return
        bowls[fromIndex] = bowls[fromIndex].copy(items = bowls[fromIndex].items.filterNot { it.key == itemKey })
        if (toBowlKey != null) updateBowl(toBowlKey) { it.copy(items = it.items + item) }
        dirty = true
    }

    fun setAmount(itemKey: Long, amount: String) {
        val index = bowls.indexOfFirst { b -> b.items.any { it.key == itemKey } }
        if (index < 0) return
        bowls[index] = bowls[index].copy(items = bowls[index].items.map { if (it.key == itemKey) it.copy(amount = amount) else it })
        dirty = true
    }

    private fun updateBowl(bowlKey: Long, change: (EditBowl) -> EditBowl) {
        val index = bowls.indexOfFirst { it.key == bowlKey }
        if (index < 0) return
        bowls[index] = change(bowls[index])
        dirty = true
    }

    fun regenerate() {
        viewModelScope.launch {
            app.repository.regenerateBowls(recipeId)
            reload()
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val edits = bowls.filter { it.items.isNotEmpty() }.mapIndexed { index, bowl ->
                BowlEdit(label(index), bowl.stepId, bowl.items.map { it.ingredientId to it.amount.trim() })
            }
            app.repository.saveBowls(recipeId, edits)
            dirty = false
            onSaved()
        }
    }
}
