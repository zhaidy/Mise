package com.personal.mise.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.CookLogRow
import com.personal.mise.data.PlanRow
import com.personal.mise.ui.plan.PlanDayCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.EmptyState
import com.personal.mise.ui.common.MiseHeader
import com.personal.mise.ui.common.RecipeCover
import com.personal.mise.ui.common.SectionHeader
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.common.rememberSavedView
import com.personal.mise.ui.list.ViewSwitch
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.util.CalorieText
import com.personal.mise.util.CookCalendar
import com.personal.mise.util.DayText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 做菜记录：哪天做过哪道菜，可以按月份、日期分组列出来，也可以看月历；新的在前。
 * 月历上也标出做菜计划，点一天可以给那天排菜。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenRecipe: (Long) -> Unit,
    onPrep: (date: String) -> Unit,
    onShopping: (from: String, days: Int) -> Unit,
    onToday: () -> Unit,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val logs by app.repository.observeCookLog().collectAsStateWithLifecycle(emptyList())
    val plans by app.repository.observePlan().collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf<CookLogRow?>(null) }
    var calendarView by rememberSavedView("history_calendar")
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MiseHeader("做菜记录", eyebrow = if (logs.isEmpty()) "还没有记录" else "一共做了 ${logs.size} 次") {
                if (logs.isNotEmpty() || plans.isNotEmpty()) {
                    ViewSwitch(calendarView, onChange = { calendarView = it }, labels = "列表" to "日历")
                }
            }
        },
    ) { padding ->
        if (logs.isEmpty() && (!calendarView || plans.isEmpty())) {
            EmptyState(
                "记",
                "还没有记录",
                "做菜时翻到最后一步点「做好了」，或者在菜谱详情里点「记一笔」。",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        val onDelete: (CookLogRow) -> Unit = { confirmDelete = it }
        if (calendarView) {
            CookCalendarView(
                logs = logs,
                plans = plans,
                onOpenRecipe = onOpenRecipe,
                onDelete = onDelete,
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
            )
        } else {
            ListView(logs, onOpenRecipe, onDelete, Modifier.padding(padding).fillMaxSize())
        }
    }

    confirmDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除记录") },
            text = { Text("删除「${DayText.label(log.date)}做了${log.title}」这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { app.repository.deleteCookLog(log.uid) }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}

/** 「做了 3 次 · 约 1860 千卡」，都不知道热量时只写次数 */
private fun summary(logs: List<CookLogRow>): String {
    val total = CookCalendar.total(logs.map { it.calories })
    return listOfNotNull("做了 ${logs.size} 次", total?.let(CalorieText::format)).joinToString(" · ")
}

@Composable
private fun ListView(
    logs: List<CookLogRow>,
    onOpenRecipe: (Long) -> Unit,
    onDelete: (CookLogRow) -> Unit,
    modifier: Modifier,
) {
    val months = logs.groupBy { DayText.month(it.date) }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
        months.forEach { (month, monthLogs) ->
            item(key = "month-$month") {
                SectionHeader(
                    month,
                    Modifier.padding(top = 20.dp, bottom = 4.dp),
                ) {
                    Text(
                        summary(monthLogs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            monthLogs.groupBy { it.date }.forEach { (date, dayLogs) ->
                item(key = "day-$date") { DayHeading(date, dayLogs) }
                logRows(dayLogs, onOpenRecipe, onDelete)
            }
        }
    }
}

/** 日期，后面跟着这天一共多少千卡 */
@Composable
private fun DayHeading(date: String, dayLogs: List<CookLogRow>, modifier: Modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 6.dp)) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            DayText.label(date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        CookCalendar.total(dayLogs.map { it.calories })?.let {
            Text(
                "  ·  ${CalorieText.format(it)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyListScope.logRows(
    dayLogs: List<CookLogRow>,
    onOpenRecipe: (Long) -> Unit,
    onDelete: (CookLogRow) -> Unit,
) {
    items(dayLogs, key = { it.uid }) { log ->
        LogRow(
            log = log,
            onClick = log.recipeId?.let { id -> { onOpenRecipe(id) } },
            onDelete = { onDelete(log) },
        )
    }
}

/**
 * 月历：一格一天，做过菜的格子写几道菜、多少千卡，排了计划的格子写计划几道；
 * 点一天在下面列出那天做的菜和计划。planOnly 是做菜计划页用的，只看计划
 */
@Composable
internal fun CookCalendarView(
    logs: List<CookLogRow>,
    plans: List<PlanRow>,
    onOpenRecipe: (Long) -> Unit,
    onDelete: (CookLogRow) -> Unit,
    planDay: @Composable (date: String, rows: List<PlanRow>) -> Unit,
    modifier: Modifier,
    planOnly: Boolean = false,
) {
    val today = remember { LocalDate.now() }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthText)
    val byDate = remember(logs) { logs.groupBy { it.date } }
    val plansByDate = remember(plans) { plans.groupBy { it.date } }
    val monthLogs = logs.filter { it.date.startsWith("$monthText-") }
    val dayLogs = byDate[selected].orEmpty()

    LazyColumn(modifier, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
        item(key = "month") {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    "上个月",
                    onClick = { monthText = month.minusMonths(1).toString() },
                    container = MaterialTheme.colorScheme.surfaceContainerLowest,
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleLarge)
                    val monthPlans = plans.count { it.date.startsWith("$monthText-") }
                    Text(
                        when {
                            planOnly -> if (monthPlans == 0) "这个月还没排菜" else "这个月排了 $monthPlans 道菜"
                            monthLogs.isEmpty() -> "这个月还没做过菜"
                            else -> summary(monthLogs)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CircleIconButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    "下个月",
                    onClick = { monthText = month.plusMonths(1).toString() },
                    container = MaterialTheme.colorScheme.surfaceContainerLowest,
                )
            }
        }
        item(key = "weekdays") {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item(key = "grid") {
            Column {
                CookCalendar.grid(month).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).aspectRatio(0.78f).padding(2.dp)) {
                                if (day != null) {
                                    val key = day.toString()
                                    DayCell(
                                        day = day,
                                        logs = byDate[key].orEmpty(),
                                        planned = plansByDate[key].orEmpty().size,
                                        isToday = day == today,
                                        isSelected = key == selected,
                                        onClick = { selected = key },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "selected-$selected") {
            DayHeading(selected, dayLogs, Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp))
        }
        // 今天和以后的日子主要看计划，没做过就不用再说一遍
        val past = selected < today.toString()
        val dayPlans = plansByDate[selected].orEmpty()
        if (planOnly) {
            if (past && dayPlans.isEmpty()) {
                item(key = "selected-empty") {
                    Text(
                        "这天没有排菜",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
            }
        } else if (dayLogs.isEmpty() && past) {
            item(key = "selected-empty") {
                Text(
                    "这天没有做菜的记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        } else {
            logRows(dayLogs, onOpenRecipe, onDelete)
        }
        if (!past || dayPlans.isNotEmpty()) {
            item(key = "plan-$selected") { planDay(selected, dayPlans) }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, logs: List<CookLogRow>, planned: Int, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val ink = LocalMiseColors.current
    val cooked = logs.isNotEmpty()
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        isSelected -> ink.ink
        cooked -> MaterialTheme.colorScheme.primaryContainer
        planned > 0 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> Color.Transparent
    }
    val content = when {
        isSelected -> ink.onInk
        cooked -> MaterialTheme.colorScheme.onPrimaryContainer
        planned > 0 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier.fillMaxSize()
            .clip(shape)
            .background(background)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 字号都配上行高：不配的话会沿用主题 bodyLarge 的 26sp 行高，格子装不下，"几道" 被切掉
        Text(
            "${day.dayOfMonth}",
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = if (cooked || planned > 0 || isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday && !isSelected && !cooked && planned == 0) MaterialTheme.colorScheme.primary else content,
        )
        if (cooked) {
            Spacer(Modifier.height(1.dp))
            Text("${logs.size} 道", fontSize = 10.sp, lineHeight = 12.sp, color = content, maxLines = 1)
        }
        // 做过又排了计划的格子放不下三行，热量让给计划
        if (planned > 0) {
            if (!cooked) Spacer(Modifier.height(1.dp))
            Text("计划 $planned", fontSize = 10.sp, lineHeight = 12.sp, color = content.copy(alpha = if (cooked) 0.75f else 1f), maxLines = 1)
        } else if (cooked) {
            CookCalendar.total(logs.map { it.calories })?.let {
                Text(CookCalendar.short(it), fontSize = 10.sp, lineHeight = 12.sp, color = content.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}

/** 菜谱已经删掉的记录点不开，菜名旁边注明 */
@Composable
private fun LogRow(log: CookLogRow, onClick: (() -> Unit)?, onDelete: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Row(
            Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecipeCover(log.title, log.coverImage, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), initialSize = 22.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    log.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = when {
                    onClick == null -> "菜谱已删除"
                    log.calories != null -> CalorieText.format(log.calories)
                    else -> null
                }
                meta?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "删除记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
