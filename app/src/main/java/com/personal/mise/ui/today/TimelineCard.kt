package com.personal.mise.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.ai.StepTimes
import com.personal.mise.data.RecipeFull
import com.personal.mise.importer.ImportException
import com.personal.mise.data.TodayRow
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.theme.SerifFamily
import com.personal.mise.util.CookTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 今日菜单里还没做完的菜排一条时间线：从开饭往前倒推，要动手的步骤错开，同时出锅。
 * 不设开饭时间就是现在开始；设了就倒推每道菜几点开始。两道以上没做完的菜才显示。
 */
@Composable
fun TimelineCard(rows: List<TodayRow>) {
    val app = rememberApp()
    val pending = rows.filter { !it.done && it.stepCount > 0 }
    val ids = pending.map { it.recipeId }
    val fulls by remember(ids) {
        if (ids.isEmpty()) flowOf(emptyList<RecipeFull?>())
        else combine(ids.map { app.repository.observeFull(it) }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())
    if (pending.size < 2) return

    // 开饭时间，一天里的第几分钟；-1 表示没设，现在就开始
    var serveAt by rememberSaveable { mutableStateOf(-1) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var estimating by remember { mutableStateOf(false) }
    var estimateMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }

    val pendingFulls = pending.mapNotNull { row -> fulls.firstOrNull { it?.recipe?.id == row.recipeId }?.let { row to it } }
    val dishes = pendingFulls.map { (row, full) ->
        val times = StepTimes.fromJson(full.recipe.stepTimes)
        CookTimeline.dish(
            row.recipeId,
            row.title,
            full.steps.map { CookTimeline.Step(it.text, it.timerSeconds, times?.of(it.text)) },
            fromStep = row.currentStep.coerceAtMost(full.steps.size),
        )
    }
    if (dishes.size < 2) return
    // 还有步骤没让 AI 估过时间的菜（剩下的步骤里没写计时的）
    val unestimated = pendingFulls.filter { (row, full) ->
        val left = full.steps.drop(row.currentStep).filter { it.timerSeconds == null }.map { it.text }
        StepTimes.fromJson(full.recipe.stepTimes)?.missing(left) ?: left.any { it.isNotBlank() }
    }.map { it.second }
    val plan = CookTimeline.plan(dishes)
    val serve = if (serveAt >= 0) LocalTime.of(serveAt / 60, serveAt % 60) else null
    val start = serve?.minusMinutes(plan.totalMinutes.toLong()) ?: now
    // 设的开饭时间赶不上：现在开始也要晚多少分钟
    val lateBy = serve?.let { (now.toSecondOfDay() / 60 + plan.totalMinutes) - (it.toSecondOfDay() / 60) }?.takeIf { it > 0 }

    SoftCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("多菜时间线", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("一共约 ${plan.totalMinutes} 分钟，同时出锅", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = { picking = true }) { Text(serve?.let { "${it.format(clock)} 开饭" } ?: "设开饭时间") }
            }
            Text(CookTimeline.summary(plan), Modifier.padding(end = 10.dp), style = MaterialTheme.typography.bodyMedium)
            if (serve != null && lateBy != null) {
                Text(
                    "来不及了：要 ${serve.format(clock)} 开饭得 ${start.format(clock)} 就开始，现在开始也要晚 $lateBy 分钟",
                    Modifier.padding(end = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column(Modifier.padding(top = 4.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CookTimeline.events(plan).forEach { event ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            start.plusMinutes(event.minute.toLong()).format(clock),
                            fontFamily = SerifFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.width(52.dp),
                        )
                        val dot = when (event.kind) {
                            CookTimeline.Kind.START -> MaterialTheme.colorScheme.secondary
                            CookTimeline.Kind.FIRE -> MaterialTheme.colorScheme.primary
                            CookTimeline.Kind.DONE -> MaterialTheme.colorScheme.outline
                            CookTimeline.Kind.SERVE -> MaterialTheme.colorScheme.tertiary
                        }
                        Box(Modifier.size(8.dp).background(dot, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            event.text,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (event.kind == CookTimeline.Kind.SERVE) FontWeight.Bold else null,
                        )
                        Text(
                            if (event.minute == 0) "开始" else "+${event.minute} 分",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (unestimated.isEmpty()) "按步骤计时和 AI 估的时间排，洗切、炒这些要动手的错开做，炖着腌着的时候去忙别的；做到一半的菜只算剩下的步骤。"
                    else "按步骤计时排，没写时间的步骤按文字猜或按 2～3 分钟算，要动手的错开做；让 AI 估一下会更准。",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (serve != null) TextButton(onClick = { serveAt = -1 }) { Text("现在开始") }
            }
            if (unestimated.isNotEmpty() && app.aiSettings.hasKey) {
                TextButton(
                    onClick = {
                        estimating = true
                        estimateMessage = null
                        scope.launch {
                            val failed = mutableListOf<String>()
                            unestimated.forEach { full ->
                                try {
                                    app.repository.setStepTimes(full.recipe.id, app.stepTimer.estimate(full))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    failed += "「${full.recipe.title}」${(e as? ImportException)?.message ?: e.message ?: e.javaClass.simpleName}"
                                }
                            }
                            estimating = false
                            estimateMessage = failed.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "没估成：\n")
                        }
                    },
                    enabled = !estimating,
                ) { Text(if (estimating) "AI 正在估每一步要多久…" else "让 AI 估每一步要多久") }
            }
            estimateMessage?.let {
                Text(it, Modifier.padding(end = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (picking) {
        ServeTimeDialog(
            initial = serve ?: now.plusMinutes(plan.totalMinutes.toLong()),
            onDismiss = { picking = false },
            onPick = {
                picking = false
                serveAt = it.hour * 60 + it.minute
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServeTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("几点开饭") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("好") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
