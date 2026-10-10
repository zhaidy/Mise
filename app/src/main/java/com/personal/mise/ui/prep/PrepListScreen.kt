package com.personal.mise.ui.prep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeFull
import com.personal.mise.data.TodayRow
import com.personal.mise.data.parseChecked
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.util.UnitText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class PrepItem(val key: String, val title: String, val detail: String)

private data class PrepSection(val name: String, val items: List<PrepItem>)

private data class PrepRecipe(val row: TodayRow, val sections: List<PrepSection>) {
    val allKeys: List<String> get() = sections.flatMap { s -> s.items.map { it.key } }
}

/** 一道菜的备料项：主料、调料碗、没进调料碗的其他调料 */
private fun buildPrep(row: TodayRow, full: RecipeFull): PrepRecipe {
    val inBowls = full.bowls.flatMap { b -> b.items.map { it.ingredientId } }.toSet()
    val mains = full.ingredients.filter { it.kind != IngredientKind.SEASONING && it.id !in inBowls }
        .map { PrepItem("i${it.id}", it.name, UnitText.display(it.amountText)) }
    val bowls = full.bowls.map { b ->
        val stepIndex = full.steps.indexOfFirst { it.id == b.bowl.stepId }
        val detail = b.items.joinToString("、") { "${it.name} ${UnitText.display(it.amountText)}".trim() }
        PrepItem("b${b.bowl.id}", b.bowl.label + if (stepIndex >= 0) "（第 ${stepIndex + 1} 步）" else "", detail)
    }
    val others = full.ingredients.filter { it.kind == IngredientKind.SEASONING && it.id !in inBowls }
        .map { PrepItem("i${it.id}", it.name, UnitText.display(it.amountText)) }
    return PrepRecipe(
        row,
        listOf(PrepSection("备菜", mains), PrepSection("调料碗", bowls), PrepSection("其他调料", others))
            .filter { it.items.isNotEmpty() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepListScreen(onBack: () -> Unit, onCook: () -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val rows by app.repository.observeToday().collectAsStateWithLifecycle(emptyList())
    val ids = rows.map { it.recipeId }
    val fulls by remember(ids) {
        if (ids.isEmpty()) flowOf(emptyList<RecipeFull?>())
        else combine(ids.map { app.repository.observeFull(it) }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())
    var showDone by remember { mutableStateOf(false) }

    val recipes = rows.mapNotNull { row ->
        fulls.firstOrNull { it?.recipe?.id == row.recipeId }?.let { buildPrep(row, it) }
    }
    val total = recipes.sumOf { it.allKeys.size }
    val done = recipes.sumOf { r -> parseChecked(r.row.bowlsChecked).count { it in r.allKeys } }

    fun toggle(recipe: PrepRecipe, key: String) {
        val current = parseChecked(recipe.row.bowlsChecked).filter { it in recipe.allKeys }.toSet()
        val next = if (key in current) current - key else current + key
        val willBeDone = done - current.size + next.size
        scope.launch { app.repository.setChecked(recipe.row.recipeId, next) }
        if (willBeDone == total && total > 0 && key !in current) showDone = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备料清单") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onCook,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                ) { Text("开始做菜") }
            }
        },
    ) { padding ->
        if (recipes.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("今日菜单是空的", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("已备好 $done / $total", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            recipes.forEach { recipe ->
                val checked = parseChecked(recipe.row.bowlsChecked)
                item(key = "title-${recipe.row.recipeId}") {
                    Text(
                        recipe.row.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }
                recipe.sections.forEach { section ->
                    item(key = "section-${recipe.row.recipeId}-${section.name}") {
                        Text(
                            section.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                    }
                    section.items.forEach { prep ->
                        item(key = "item-${recipe.row.recipeId}-${prep.key}") {
                            val isChecked = prep.key in checked
                            Row(
                                Modifier.fillMaxWidth().clickable { toggle(recipe, prep.key) }.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = { toggle(recipe, prep.key) })
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        prep.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (section.name == "调料碗") FontWeight.Bold else FontWeight.Normal,
                                        textDecoration = if (isChecked) TextDecoration.LineThrough else null,
                                    )
                                    if (prep.detail.isNotBlank()) {
                                        Text(prep.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 56.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDone) {
        AlertDialog(
            onDismissRequest = { showDone = false },
            title = { Text("都备好了") },
            text = { Text("所有用料和调料碗都准备好了，开始做菜吧！") },
            confirmButton = { TextButton(onClick = { showDone = false; onCook() }) { Text("开始做菜") } },
            dismissButton = { TextButton(onClick = { showDone = false }) { Text("再看看") } },
        )
    }
}
