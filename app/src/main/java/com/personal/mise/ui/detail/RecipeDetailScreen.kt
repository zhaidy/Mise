package com.personal.mise.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.BowlFull
import com.personal.mise.data.Ingredient
import com.personal.mise.data.IngredientKind
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.hasImage
import com.personal.mise.importer.ImportException
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.edit.AiKeyDialog
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.CalorieText
import com.personal.mise.util.DayText
import com.personal.mise.util.TimeText
import com.personal.mise.util.UnitText
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onEditBowls: () -> Unit,
    onCook: () -> Unit,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val full by remember(recipeId) { app.repository.observeFull(recipeId) }.collectAsStateWithLifecycle(null)
    var confirmDelete by remember { mutableStateOf(false) }
    var scaleOpen by remember { mutableStateOf(false) }
    var estimatingCalories by remember { mutableStateOf(false) }
    var askingKey by remember { mutableStateOf(false) }
    var logOpen by remember { mutableStateOf(false) }
    val cookDates by remember(recipeId) { app.repository.observeCookDates(recipeId) }.collectAsStateWithLifecycle(emptyList())
    val factor = app.scaleOf(recipeId)

    fun estimateCalories() {
        val current = full ?: return
        if (estimatingCalories) return
        estimatingCalories = true
        scope.launch {
            val message = try {
                val kcal = app.calorieEstimator.estimate(current)
                app.repository.setCalories(recipeId, kcal)
                "AI 估算整道菜约 $kcal 千卡"
            } catch (e: ImportException) {
                e.message ?: "AI 估算热量失败"
            } catch (e: Exception) {
                "AI 估算热量失败：${e.message ?: e.javaClass.simpleName}"
            }
            estimatingCalories = false
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(full?.recipe?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val added = app.repository.addToToday(recipeId)
                                snackbar.showSnackbar(if (added) "已加入今日菜单" else "已经在今日菜单里了")
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("加入今日菜单") }
                    Button(
                        onClick = {
                            scope.launch {
                                app.repository.addToToday(recipeId)
                                onCook()
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("开始做") }
                }
            }
        },
    ) { padding ->
        val data = full ?: return@Scaffold
        val mains = data.ingredients.filter { it.kind != IngredientKind.SEASONING }
        val seasonings = data.ingredients.filter { it.kind == IngredientKind.SEASONING }

        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                // 大多数菜谱没有封面：没图就不占位，有图也只用一条矮横幅
                if (app.hasImage(data.recipe.coverImage)) {
                    LocalImage(data.recipe.coverImage, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
                data.recipe.servings?.let {
                    Text("份量：$it", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val kcal = data.recipe.calories
                    Text(
                        if (kcal == null) "热量：还没算" else "热量：${CalorieText.describe(kcal, data.recipe.servings, factor)}",
                        Modifier.weight(1f),
                        color = if (kcal == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (estimatingCalories) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else if (data.ingredients.isNotEmpty()) {
                        TextButton(onClick = { if (app.aiSettings.hasKey) estimateCalories() else askingKey = true }) {
                            Text(if (kcal == null) "AI 估算" else "重新估算")
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (cookDates.isEmpty()) "还没做过" else "做过 ${cookDates.size} 次 · 上次${DayText.ago(cookDates.first())}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { logOpen = true }) { Text("记一笔") }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("主料", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (factor != 1.0) {
                        TextButton(onClick = { app.scales.remove(recipeId) }) { Text("恢复原量") }
                    }
                    OutlinedButton(onClick = { scaleOpen = true }) {
                        Text(if (factor == 1.0) "换算" else "换算 ${AmountScaler.label(factor)}")
                    }
                }
            }
            item { IngredientTable(mains, factor) }
            item { SectionTitle("调料") }
            item { IngredientTable(seasonings, factor) }

            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp)) {
                    Text("调料碗", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onEditBowls) { Text("调整") }
                }
            }
            if (data.bowls.isEmpty()) {
                item {
                    Text(
                        "没有需要提前配的调料碗",
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                data.bowls.forEach { bowl ->
                    item(key = "bowl-${bowl.bowl.id}") {
                        val stepIndex = data.steps.indexOfFirst { it.id == bowl.bowl.stepId }
                        BowlCard(bowl, if (stepIndex >= 0) "第 ${stepIndex + 1} 步下锅" else null, factor)
                    }
                }
            }

            item { SectionTitle("步骤") }
            itemsIndexed(data.steps, key = { _, s -> "step-${s.id}" }) { index, step ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("第 ${index + 1} 步", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(step.text, style = MaterialTheme.typography.bodyLarge)
                    step.timerSeconds?.let {
                        Text("计时 ${TimeText.describe(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (app.hasImage(step.image)) {
                        Spacer(Modifier.height(8.dp))
                        LocalImage(step.image, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)))
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
            data.recipe.sourceUrl?.let { url ->
                item {
                    Text(
                        "来源：$url",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (askingKey) {
        AiKeyDialog(
            confirmLabel = "保存并估算",
            onDismiss = { askingKey = false },
            onConfirm = {
                askingKey = false
                estimateCalories()
            },
        )
    }

    if (scaleOpen) {
        ScaleDialog(
            current = factor,
            onDismiss = { scaleOpen = false },
            onPick = {
                if (it == 1.0) app.scales.remove(recipeId) else app.scales[recipeId] = it
                scaleOpen = false
            },
        )
    }

    if (logOpen) {
        LogDateDialog(
            onDismiss = { logOpen = false },
            onPick = { date ->
                logOpen = false
                scope.launch {
                    val logged = app.repository.logCooked(recipeId, date)
                    snackbar.showSnackbar(if (logged) "已记下：${DayText.label(date)}做了这道菜" else "${DayText.label(date)}已经记过了")
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除菜谱") },
            text = { Text("确定删除「${full?.recipe?.title.orEmpty()}」吗？图片也会一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.repository.delete(recipeId)
                        onBack()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun IngredientTable(items: List<Ingredient>, factor: Double) {
    if (items.isEmpty()) {
        Text("无", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        items.forEach { ing ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(ing.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(12.dp))
                Text(UnitText.display(AmountScaler.scale(ing.amountText, factor)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun BowlCard(bowl: BowlFull, subtitle: String?, factor: Double = 1.0, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(bowl.bowl.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(6.dp))
            bowl.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(item.name, Modifier.weight(1f))
                    Text(UnitText.display(AmountScaler.scale(item.amountText, factor)))
                }
            }
        }
    }
}

private val presetFactors = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0)

/** 选倍率：常用倍数直接点，也能自己输，如 1.25 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleDialog(current: Double, onDismiss: () -> Unit, onPick: (Double) -> Unit) {
    var custom by remember { mutableStateOf(if (current in presetFactors) "" else AmountScaler.format(current)) }
    val customValue = custom.trim().toDoubleOrNull()?.takeIf { it > 0 && it <= 100 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("换算用量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("所有主料、调料和调料碗的用量都按倍率显示，「适量」「少许」不变。")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetFactors.forEach { f ->
                        FilterChip(
                            selected = f == current,
                            onClick = { onPick(f) },
                            label = { Text(AmountScaler.label(f)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text("自定义倍率") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = custom.isNotBlank() && customValue == null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { customValue?.let(onPick) }, enabled = customValue != null) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** 选哪天做的，默认今天，不能选以后的日子。DatePicker 用的是 UTC 零点的毫秒数 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDateDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val todayMs = LocalDate.now().toEpochDay() * DAY_MS
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayMs,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs
            override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / DAY_MS).toString()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("记下") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state, title = { Text("哪天做的", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
