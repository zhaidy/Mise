package com.personal.mise.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.TodayRow
import com.personal.mise.importer.ImportException
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.edit.AiKeyDialog
import com.personal.mise.util.CalorieText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenRecipe: (Long) -> Unit,
    onPrep: () -> Unit,
    onCook: () -> Unit,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val rows by app.repository.observeToday().collectAsStateWithLifecycle(emptyList())
    var items by remember { mutableStateOf(rows) }
    LaunchedEffect(rows) { items = rows }
    var confirmClear by remember { mutableStateOf(false) }
    var estimating by remember { mutableStateOf(false) }
    var calorieMessage by remember { mutableStateOf<String?>(null) }
    var askingKey by remember { mutableStateOf(false) }

    /** 让 AI 把还没算热量的菜逐道估一下 */
    fun estimateMissing() {
        if (estimating) return
        val missing = items.filter { it.calories == null }
        estimating = true
        calorieMessage = null
        scope.launch {
            var done = 0
            var error: String? = null
            missing.forEach { row ->
                try {
                    val full = app.repository.loadFull(row.recipeId) ?: return@forEach
                    if (full.ingredients.isEmpty()) return@forEach
                    app.repository.setCalories(row.recipeId, app.calorieEstimator.estimate(full))
                    done++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ImportException) {
                    error = e.message
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                }
            }
            estimating = false
            calorieMessage = error?.let { "估算了 $done 道，有的没成功：$it" }
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = items.indexOfFirst { it.recipeId == from.key }
        val toIndex = items.indexOfFirst { it.recipeId == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            items = items.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            app.repository.reorderToday(items.map { it.recipeId })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日菜单") },
                actions = {
                    if (items.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("清空") }
                },
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onPrep, modifier = Modifier.weight(1f).height(56.dp)) { Text("备料清单") }
                        Button(onClick = onCook, modifier = Modifier.weight(1f).height(56.dp)) { Text("开始做菜") }
                    }
                }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "今天还没选菜。在菜谱列表里长按一道菜，或者在菜谱详情里点「加入今日菜单」。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "calories") {
                    CaloriesSummary(
                        rows = items,
                        factorOf = app::scaleOf,
                        estimating = estimating,
                        message = calorieMessage,
                        onEstimate = { if (app.aiSettings.hasKey) estimateMissing() else askingKey = true },
                    )
                }
                items(items, key = { it.recipeId }) { row ->
                    ReorderableItem(reorderState, key = row.recipeId) { isDragging ->
                        TodayCard(
                            row = row,
                            factor = app.scaleOf(row.recipeId),
                            dragging = isDragging,
                            onClick = { onOpenRecipe(row.recipeId) },
                            onRemove = { scope.launch { app.repository.removeFromToday(row.recipeId) } },
                            handle = Modifier.draggableHandle(),
                        )
                    }
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
                estimateMissing()
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空今日菜单") },
            text = { Text("清空后各道菜的做菜进度和备料勾选都会重置。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { app.repository.clearToday() }
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

/** 今日菜单一共多少千卡；按每道菜现在的换算倍率算 */
@Composable
private fun CaloriesSummary(
    rows: List<TodayRow>,
    factorOf: (Long) -> Double,
    estimating: Boolean,
    message: String?,
    onEstimate: () -> Unit,
) {
    val known = rows.filter { it.calories != null }
    val missing = rows.size - known.size
    val total = known.sumOf { CalorieText.scaled(it.calories!!, factorOf(it.recipeId)) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (known.isEmpty()) "今日热量：还没算" else "今日共${CalorieText.format(total)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                val detail = when {
                    missing == 0 -> "${rows.size} 道菜，整道菜的热量加起来"
                    known.isEmpty() -> "可以让 AI 按用料估算每道菜的热量"
                    else -> "还有 $missing 道菜没算热量，没算进去"
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            if (estimating) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            } else if (missing > 0) {
                TextButton(onClick = onEstimate) { Text("AI 估算") }
            }
        }
    }
}

@Composable
private fun TodayCard(
    row: TodayRow,
    factor: Double,
    dragging: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    handle: Modifier,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 1.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}, modifier = handle) {
                Icon(Icons.Filled.Menu, contentDescription = "拖动排序")
            }
            LocalImage(row.coverImage, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), placeholder = "")
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val progress = if (row.stepCount == 0) "没有步骤" else "进度 ${(row.currentStep + 1).coerceAtMost(row.stepCount)}/${row.stepCount}"
                Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.calories?.let {
                    Text(
                        CalorieText.describe(it, null, factor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "移出今日菜单") }
        }
    }
}
