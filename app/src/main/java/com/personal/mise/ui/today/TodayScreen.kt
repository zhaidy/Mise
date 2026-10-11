package com.personal.mise.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.personal.mise.ui.common.BottomActionBar
import com.personal.mise.ui.common.EmptyState
import com.personal.mise.ui.common.InfoPill
import com.personal.mise.ui.common.MiseHeader
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.OutlinePillButton
import com.personal.mise.ui.common.PillButton
import com.personal.mise.ui.common.RecipeCover
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.edit.AiKeyDialog
import com.personal.mise.importer.ImportException
import com.personal.mise.util.CalorieText
import kotlinx.coroutines.CancellationException
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
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
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.rememberApp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenRecipe: (Long) -> Unit,
    onPrep: () -> Unit,
    onShopping: () -> Unit,
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
            MiseHeader(
                "今日菜单",
                eyebrow = if (items.isEmpty()) "还没选菜" else "共 ${items.size} 道菜",
            ) {
                if (items.isNotEmpty()) {
                    CircleIconButton(
                        Icons.Filled.ShoppingCart,
                        "购物清单",
                        onShopping,
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) { Text("清空") }
                }
            }
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                // 外层已经有底部导航，这里不用再让出系统导航栏
                BottomActionBar(navigationBars = false) {
                    OutlinePillButton("备料清单", onPrep, Modifier.weight(1f))
                    PillButton("开始做菜", onCook, Modifier.weight(1f))
                }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                "菜",
                "今天还没选菜",
                "在菜谱列表里长按一道菜，或者在菜谱详情里点「加入今日菜单」。",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                item(key = "hint") {
                    Text(
                        "按住左侧把手拖动，调整做菜顺序",
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("今日热量", style = MaterialTheme.typography.labelMedium)
                Text(
                    if (known.isEmpty()) "还没算" else CalorieText.format(total),
                    style = MaterialTheme.typography.titleLarge,
                )
                val detail = when {
                    missing == 0 -> "${rows.size} 道菜，整道菜的热量加起来"
                    known.isEmpty() -> "可以让 AI 按用料估算每道菜的热量"
                    else -> "还有 $missing 道菜没算热量，没算进去"
                }
                Text(detail, style = MaterialTheme.typography.bodySmall)
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
    val app = rememberApp()
    val timers by app.timers.timers.collectAsStateWithLifecycle()
    val hasTimer = timers.any { it.recipeId == row.recipeId }
    val scale by animateFloatAsState(if (dragging) 1.03f else 1f, label = "drag")
    SoftCard(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(24.dp),
        shadow = if (dragging) 12.dp else 0.dp,
        onClick = onClick,
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}, modifier = handle.size(width = 32.dp, height = 48.dp)) {
                Icon(MiseIcons.DragDots, contentDescription = "拖动排序", tint = MaterialTheme.colorScheme.outline)
            }
            RecipeCover(row.title, row.coverImage, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)), initialSize = 30.sp)
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    val step = (row.currentStep + 1).coerceAtMost(row.stepCount)
                    Text(
                        when {
                            row.stepCount == 0 -> "没有步骤"
                            row.currentStep == 0 -> "共 ${row.stepCount} 步"
                            else -> "第 $step / ${row.stepCount} 步"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                LinearProgressIndicator(
                    progress = { if (row.stepCount == 0) 0f else (row.currentStep.coerceAtMost(row.stepCount) / row.stepCount.toFloat()) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                if (hasTimer || row.calories != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasTimer) {
                            InfoPill(
                                "计时中",
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        row.calories?.let {
                            Text(
                                CalorieText.describe(it, null, factor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "移出今日菜单", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
