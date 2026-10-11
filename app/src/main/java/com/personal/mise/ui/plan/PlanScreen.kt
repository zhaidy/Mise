package com.personal.mise.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.PlanRow
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.MiseHeader
import com.personal.mise.ui.common.RecipeCover
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.common.miseFieldColors
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.common.rememberSavedView
import com.personal.mise.ui.history.CookCalendarView
import com.personal.mise.ui.list.ViewSwitch
import com.personal.mise.util.CalorieText
import com.personal.mise.util.CookCalendar
import com.personal.mise.util.DayText
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 一次往下多看几天 */
private const val DAYS_STEP = 7

/** 做菜计划：从今天往后一天一张卡片，也可以看月历；排哪天做哪道菜，每天可以提前备料、出买菜清单 */
@Composable
fun PlanScreen(
    onOpenRecipe: (Long) -> Unit,
    onPrep: (date: String) -> Unit,
    onShopping: (from: String, days: Int) -> Unit,
    onToday: () -> Unit,
) {
    val app = rememberApp()
    val plans by app.repository.observePlan().collectAsStateWithLifecycle(emptyList())
    val today = remember { DayText.today() }
    var shown by rememberSaveable { mutableStateOf(DAYS_STEP * 2) }
    val days = DayText.days(today, shown)
    val byDate = plans.groupBy { it.date }
    val weekCount = DayText.days(today, DAYS_STEP).sumOf { byDate[it].orEmpty().size }
    val snackbar = remember { SnackbarHostState() }
    var calendarView by rememberSavedView("plan_calendar")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MiseHeader("做菜计划", eyebrow = if (weekCount == 0) "这周还没排菜" else "接下来 7 天排了 $weekCount 道菜") {
                ViewSwitch(calendarView, onChange = { calendarView = it }, labels = "列表" to "日历")
                if (weekCount > 0) {
                    CircleIconButton(
                        Icons.Filled.ShoppingCart,
                        "这周的买菜清单",
                        onClick = { onShopping(today, DAYS_STEP) },
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                }
            }
        },
    ) { padding ->
        if (calendarView) {
            CookCalendarView(
                logs = emptyList(),
                plans = plans,
                onOpenRecipe = onOpenRecipe,
                onDelete = {},
                planDay = { date, rows ->
                    PlanDayCard(
                        date = date,
                        rows = rows,
                        snackbar = snackbar,
                        onOpenRecipe = onOpenRecipe,
                        onPrep = onPrep,
                        onShopping = onShopping,
                        onToday = onToday,
                        modifier = Modifier.padding(top = 16.dp),
                        showDate = false,
                    )
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
                planOnly = true,
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(days, key = { it }) { date ->
                PlanDayCard(
                    date = date,
                    rows = byDate[date].orEmpty(),
                    snackbar = snackbar,
                    onOpenRecipe = onOpenRecipe,
                    onPrep = onPrep,
                    onShopping = onShopping,
                    onToday = onToday,
                )
            }
            item(key = "more") {
                TextButton(onClick = { shown += DAYS_STEP }, modifier = Modifier.fillMaxWidth()) {
                    Text("再往后看一周")
                }
            }
        }
    }
}

/** 一天的计划卡片：日期、排的菜，下面是备料、买菜和放进今日菜单 */
@Composable
fun PlanDayCard(
    date: String,
    rows: List<PlanRow>,
    snackbar: SnackbarHostState,
    onOpenRecipe: (Long) -> Unit,
    onPrep: (date: String) -> Unit,
    onShopping: (from: String, days: Int) -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val today = DayText.today()
    val isToday = date == today
    SoftCard(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (isToday) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (showDate) {
                        val label = DayText.ahead(date)
                        // 今天、明天后面补上日期，别的本身就带日期
                        val parsed = DayText.parse(date)
                        val full = if (parsed != null && label.length <= 2) "$label · ${parsed.monthValue}月${parsed.dayOfMonth}日 ${DayText.weekday(date)}" else label
                        Text(full, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    } else {
                        Text("计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val total = CookCalendar.total(rows.map { it.calories })
                    val meta = when {
                        rows.isEmpty() -> "还没排菜"
                        total != null -> "${rows.size} 道 · ${CalorieText.format(total)}"
                        else -> "${rows.size} 道"
                    }
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" 加菜")
                }
            }
            rows.forEach { row ->
                PlanRecipeRow(
                    row = row,
                    onClick = row.recipeId?.let { id -> { onOpenRecipe(id) } },
                    onRemove = { scope.launch { app.repository.removeFromPlan(row.uid) } },
                )
            }
            if (rows.any { it.recipeId != null }) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onPrep(date) }) { Text("备料") }
                    TextButton(onClick = { onShopping(date, 1) }) { Text("买菜") }
                    if (isToday) {
                        TextButton(onClick = {
                            scope.launch {
                                val added = app.repository.planToToday(date)
                                if (added > 0) onToday() else snackbar.showSnackbar("都已经在今日菜单里了")
                            }
                        }) { Text("放进今日菜单") }
                    }
                }
            }
        }
    }

    if (picking) {
        RecipePickerDialog(
            title = "${DayText.ahead(date)}做什么",
            planned = rows.mapNotNull { it.recipeId }.toSet(),
            onToggle = { recipeId, planned ->
                scope.launch {
                    if (planned) {
                        rows.firstOrNull { it.recipeId == recipeId }?.let { app.repository.removeFromPlan(it.uid) }
                    } else {
                        app.repository.addToPlan(recipeId, date)
                    }
                }
            },
            onDismiss = { picking = false },
        )
    }
}

/** 菜谱已经删掉的计划点不开，菜名旁边注明 */
@Composable
private fun PlanRecipeRow(row: PlanRow, onClick: (() -> Unit)?, onRemove: () -> Unit) {
    SoftCard(
        Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeCover(row.title, row.coverImage, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), initialSize = 18.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = when {
                    onClick == null -> "菜谱已删除"
                    row.calories != null -> CalorieText.format(row.calories)
                    else -> null
                }
                meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "移出计划", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 挑菜：可以搜，点一下排上，再点一下取消；排好了点「完成」 */
@Composable
fun RecipePickerDialog(
    title: String,
    planned: Set<Long>,
    onToggle: (recipeId: Long, planned: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = rememberApp()
    var query by remember { mutableStateOf("") }
    val hits by remember(query) { app.repository.search(query) }.collectAsStateWithLifecycle(emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜菜名、分类或用料") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(hits, key = { it.recipe.id }) { hit ->
                        val selected = hit.recipe.id in planned
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onToggle(hit.recipe.id, selected) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RecipeCover(hit.recipe.title, hit.recipe.coverImage, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)), initialSize = 16.sp)
                            Text(
                                hit.recipe.title,
                                Modifier.weight(1f).padding(horizontal = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) Icon(Icons.Filled.Check, contentDescription = "已排上", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (hits.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                if (query.isBlank()) "还没有菜谱" else "没搜到",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** 排到哪天，默认明天，只能选今天和以后。DatePicker 用的是 UTC 零点的毫秒数 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDateDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val todayMs = LocalDate.now().toEpochDay() * DAY_MS
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayMs + DAY_MS,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMs
            override fun isSelectableYear(year: Int) = year >= LocalDate.now().year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / DAY_MS).toString()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("排上") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state, title = { Text("哪天做", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
