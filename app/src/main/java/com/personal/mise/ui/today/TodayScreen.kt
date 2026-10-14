package com.personal.mise.ui.today

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
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
import com.personal.mise.ui.chat.AskAiButton
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
import com.personal.mise.util.DayText
import com.personal.mise.data.CalorieBudget
import com.personal.mise.data.Diner
import com.personal.mise.data.DinerShare
import androidx.compose.material3.Checkbox
import com.personal.mise.data.ActivityLevel
import com.personal.mise.data.BodyProfile
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.platform.LocalContext
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
    onAskAi: () -> Unit,
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
    val diners by app.calorieBudget.diners.collectAsStateWithLifecycle()
    var editingDiners by remember { mutableStateOf(false) }
    val plans by app.repository.observePlan().collectAsStateWithLifecycle(emptyList())
    val today by remember { DayText.observeToday() }.collectAsStateWithLifecycle(DayText.today())
    val yesterday = DayText.plus(today, -1)
    // 昨天排了没做完、今天也还没排的菜，问一下要不要挪到今天；点了「不用」这天就不再问
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("today", Context.MODE_PRIVATE) }
    var leftoverDismissed by remember { mutableStateOf(prefs.getString(KEY_LEFTOVER_DISMISSED, null)) }
    val leftover = if (leftoverDismissed == today) emptyList() else {
        plans.filter { p -> p.date == yesterday && !p.done && p.recipeId != null && items.none { it.recipeId == p.recipeId } }
    }
    val moveLeftover: () -> Unit = { scope.launch { app.repository.moveToToday(leftover.map { it.uid }) } }
    val dismissLeftover: () -> Unit = {
        prefs.edit().putString(KEY_LEFTOVER_DISMISSED, today).apply()
        leftoverDismissed = today
    }

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
            app.repository.reorderToday(items.map { it.uid })
        }
    }

    Scaffold(
        topBar = {
            MiseHeader(
                "今日菜单",
                eyebrow = when {
                    items.isEmpty() -> "还没选菜"
                    items.any { it.done } -> "共 ${items.size} 道菜 · 做完 ${items.count { it.done }} 道"
                    else -> "共 ${items.size} 道菜"
                },
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
        // 放在这一页里，才会浮在「备料清单」「开始做菜」上面，不挡住它们
        floatingActionButton = { AskAiButton(onClick = onAskAi) },
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
            Column(Modifier.padding(padding)) {
                if (leftover.isNotEmpty()) {
                    LeftoverBanner(leftover.map { it.title }, moveLeftover, dismissLeftover, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
                EmptyState(
                    "菜",
                    "今天还没选菜",
                    "在菜谱列表里长按一道菜、在菜谱详情里点「加入今日菜单」，或者在「日历」里给今天排菜。",
                )
            }
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
                if (leftover.isNotEmpty()) {
                    item(key = "leftover") { LeftoverBanner(leftover.map { it.title }, moveLeftover, dismissLeftover) }
                }
                item(key = "calories") {
                    CaloriesSummary(
                        rows = items,
                        factorOf = app::scaleOf,
                        diners = diners,
                        onEditDiners = { editingDiners = true },
                        estimating = estimating,
                        message = calorieMessage,
                        onEstimate = { if (app.aiSettings.hasKey) estimateMissing() else askingKey = true },
                    )
                }
                if (items.count { !it.done && it.stepCount > 0 } >= 2) {
                    item(key = "timeline") { TimelineCard(items) }
                }
                items(items, key = { it.recipeId }) { row ->
                    ReorderableItem(reorderState, key = row.recipeId) { isDragging ->
                        TodayCard(
                            row = row,
                            factor = app.scaleOf(row.recipeId),
                            dragging = isDragging,
                            onClick = { onOpenRecipe(row.recipeId) },
                            onToggleDone = { scope.launch { app.repository.setTodayDone(row.recipeId, !row.done) } },
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

    if (editingDiners) {
        DinersDialog(
            current = diners,
            onDismiss = { editingDiners = false },
            onSave = {
                app.calorieBudget.save(it)
                editingDiners = false
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空今日菜单") },
            text = { Text("今天计划的菜都会移出，各道菜的做菜进度和备料勾选也会重置。") },
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

private const val KEY_LEFTOVER_DISMISSED = "leftover_dismissed"

/** 昨天没做完的菜，一键挪到今天 */
@Composable
private fun LeftoverBanner(titles: List<String>, onMove: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("昨天没做完", style = MaterialTheme.typography.labelMedium)
                Text(titles.joinToString("、"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDismiss) { Text("不用") }
            TextButton(onClick = onMove) { Text("挪到今天") }
        }
    }
}

/** 今日菜单一共多少千卡；按每道菜现在的换算倍率算，今天吃的人平分，和各自一天的消耗比，估算谁会胖或瘦多少 */
@Composable
private fun CaloriesSummary(
    rows: List<TodayRow>,
    factorOf: (Long) -> Double,
    diners: List<Diner>,
    onEditDiners: () -> Unit,
    estimating: Boolean,
    message: String?,
    onEstimate: () -> Unit,
) {
    val known = rows.filter { it.calories != null }
    val missing = rows.size - known.size
    val total = known.sumOf { CalorieText.scaled(it.calories!!, factorOf(it.recipeId)) }
    val shares = if (known.isEmpty()) emptyList() else CalorieBudget.split(total, diners)
    val anyOver = shares.any { it.diff > 0 }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (anyOver) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (anyOver) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            // 有菜没算热量时总数偏少，算出来会瘦的偏多，先不说
            shares.forEach { DinerShareRow(it, showLoss = missing == 0) }
            Row(
                Modifier.fillMaxWidth().padding(end = 10.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onEditDiners),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val eating = diners.filter { it.eating }
                Text(
                    if (eating.isEmpty()) "今天没人吃？" else "${eating.joinToString("、") { it.name }} 吃 · 平分",
                    Modifier.weight(1f).padding(vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("设置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 「小明　吃约 2000 / 上限 2200」，下面一行说会胖还是会瘦多少 */
@Composable
private fun DinerShareRow(share: DinerShare, showLoss: Boolean) {
    Column(Modifier.padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                share.diner.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("吃约 ${share.eaten} / 上限 ${share.diner.perDay}", style = MaterialTheme.typography.bodySmall)
        }
        when {
            share.diff > 0 -> Text(
                "多吃约 ${share.diff} 千卡，会胖${CalorieText.jin(share.diff)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            share.diff < 0 && showLoss -> Text(
                "少吃约 ${-share.diff} 千卡，今天只吃这些的话会瘦${CalorieText.jin(-share.diff)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 设谁来吃：每个人有名字和一天大约消耗多少千卡，勾上的是今天吃的 */
@Composable
private fun DinersDialog(current: List<Diner>, onDismiss: () -> Unit, onSave: (List<Diner>) -> Unit) {
    var diners by remember { mutableStateOf(current) }
    // 正在改第几个人；等于 diners.size 是新加的
    var editing by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("谁来吃") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "今日菜单按勾上的人平分，和每个人一天大约消耗的热量比，" +
                        "按 1 斤脂肪约 ${CalorieText.KCAL_PER_JIN} 千卡估算谁会胖、谁会瘦多少。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                diners.forEachIndexed { i, diner ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = i },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = diner.eating,
                            onCheckedChange = { checked -> diners = diners.mapIndexed { j, d -> if (j == i) d.copy(eating = checked) else d } },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(diner.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "上限 ${diner.perDay} 千卡" + if (diner.body != null) " · 按身高体重算的" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("改", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (diners.size < CalorieBudget.MAX_DINERS) {
                    TextButton(onClick = { editing = diners.size }) { Text("加一个人") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(diners) }, enabled = diners.any { it.eating }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    editing?.let { index ->
        val existing = diners.getOrNull(index)
        DinerDialog(
            initial = existing ?: Diner(CalorieBudget.defaultName(index), CalorieBudget.DEFAULT_PER_DAY),
            isNew = existing == null,
            canDelete = existing != null && diners.size > 1,
            onDismiss = { editing = null },
            onDelete = {
                diners = diners.filterIndexed { j, _ -> j != index }
                editing = null
            },
            onDone = { edited ->
                diners = if (existing == null) diners + edited else diners.mapIndexed { j, d -> if (j == index) edited else d }
                editing = null
            },
        )
    }
}

/** 改一个人的名字和上限；上限可以按身高体重算 */
@Composable
private fun DinerDialog(
    initial: Diner,
    isNew: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onDone: (Diner) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var perDay by remember { mutableStateOf(initial.perDay.toString()) }
    var body by remember { mutableStateOf(initial.body) }
    var calculating by remember { mutableStateOf(false) }
    val perDayValue = perDay.toIntOrNull()?.takeIf { it in 1..CalorieBudget.MAX_PER_DAY }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "加一个人" else "改 ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    label = { Text("名字") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = miseFieldColors(),
                )
                OutlinedTextField(
                    value = perDay,
                    onValueChange = {
                        perDay = it.filter { c -> c.isDigit() }.take(5)
                        // 手动改了数，就不再是按身高体重算的
                        if (perDay != body?.dailyBurn()?.toString()) body = null
                    },
                    label = { Text("一天上限（千卡）") },
                    supportingText = { Text("填一天大约消耗的热量，估得最准") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = perDayValue == null,
                )
                TextButton(onClick = { calculating = true }) { Text("按身高体重帮我算") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { perDayValue?.let { onDone(initial.copy(name = name.trim(), perDay = it, body = body)) } },
                enabled = perDayValue != null,
            ) { Text("好") }
        },
        dismissButton = {
            Row {
                if (canDelete) {
                    TextButton(onClick = onDelete) { Text("删掉", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
    if (calculating) {
        DailyBurnDialog(
            initial = body,
            onDismiss = { calculating = false },
            onUse = {
                body = it
                perDay = it.dailyBurn().toString()
                calculating = false
            },
        )
    }
}

/** 填性别、年龄、身高、体重、活动量，估一天大约消耗多少千卡，拿去当这个人的上限 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyBurnDialog(initial: BodyProfile?, onDismiss: () -> Unit, onUse: (BodyProfile) -> Unit) {
    var male by remember { mutableStateOf(initial?.male) }
    var age by remember { mutableStateOf(initial?.age?.toString().orEmpty()) }
    var height by remember { mutableStateOf(initial?.heightCm?.toString().orEmpty()) }
    var weight by remember { mutableStateOf(initial?.weightJin?.toString().orEmpty()) }
    var activity by remember { mutableStateOf(initial?.activity ?: ActivityLevel.SEDENTARY) }
    val profile = BodyProfile.parse(male, age, height, weight, activity)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("算一天消耗多少") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "按 Mifflin-St Jeor 公式估基础代谢，再乘平时的活动量；只存在这台手机上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = male == true, onClick = { male = true }, label = { Text("男") })
                    FilterChip(selected = male == false, onClick = { male = false }, label = { Text("女") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyNumberField(age, { age = it }, "年龄", BodyProfile.AGES, Modifier.weight(1f))
                    BodyNumberField(height, { height = it }, "身高 cm", BodyProfile.HEIGHTS, Modifier.weight(1f))
                    BodyNumberField(weight, { weight = it }, "体重 斤", BodyProfile.WEIGHTS, Modifier.weight(1f))
                }
                Text("平时活动量", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityLevel.entries.forEach {
                        FilterChip(selected = it == activity, onClick = { activity = it }, label = { Text(it.label) })
                    }
                }
                Text(
                    profile?.let { "一天大约消耗 ${it.dailyBurn()} 千卡" } ?: "填好上面几项就能算",
                    style = if (profile != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (profile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { profile?.let(onUse) }, enabled = profile != null) { Text("用这个数") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 只能填整数；填了但不在 [range] 里标红 */
@Composable
private fun BodyNumberField(value: String, onValueChange: (String) -> Unit, label: String, range: IntRange, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(3)) },
        modifier = modifier,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        shape = FieldShape,
        colors = miseFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.isNotEmpty() && value.toIntOrNull()?.let { it in range } != true,
    )
}

@Composable
private fun TodayCard(
    row: TodayRow,
    factor: Double,
    dragging: Boolean,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
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
            // 做完的菜淡一点，菜名划掉
            val fade = Modifier.graphicsLayer { alpha = if (row.done) 0.55f else 1f }
            RecipeCover(row.title, row.coverImage, fade.size(56.dp).clip(RoundedCornerShape(16.dp)), initialSize = 26.sp)
            Column(fade.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (row.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = {
                        when {
                            row.done -> 1f
                            row.stepCount == 0 -> 0f
                            else -> row.currentStep.coerceAtMost(row.stepCount) / row.stepCount.toFloat()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                // 步数放在下面一行，菜名独占一行不被挤掉
                val step = (row.currentStep + 1).coerceAtMost(row.stepCount)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasTimer) {
                        InfoPill(
                            "计时中",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Text(
                        listOfNotNull(
                            row.calories?.let { CalorieText.describe(it, null, factor) },
                            when {
                                row.done -> "做完了"
                                row.stepCount == 0 -> "没有步骤"
                                row.currentStep == 0 -> "共 ${row.stepCount} 步"
                                else -> "第 $step / ${row.stepCount} 步"
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DoneToggle(row.done, onToggleDone)
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "移出今日菜单", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 做完了打个勾，再点一下取消 */
@Composable
private fun DoneToggle(done: Boolean, onToggle: () -> Unit) {
    val accent = MaterialTheme.colorScheme.secondary
    val fill by animateColorAsState(if (done) accent else Color.Transparent, label = "done")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = if (done) "已做完，点一下取消" else "标记为做完" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, if (done) accent else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
