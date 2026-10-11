package com.personal.mise.ui.cooking

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.BowlFull
import com.personal.mise.data.Step
import com.personal.mise.data.TodayRow
import com.personal.mise.timer.CookingTimer
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.EmptyState
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.OutlinePillButton
import com.personal.mise.ui.common.PillButton
import com.personal.mise.ui.common.hasImage
import com.personal.mise.ui.common.miseFieldColors
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.ui.theme.SerifFamily
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.TimeText
import com.personal.mise.util.UnitText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BodySize = 24.sp
private val ButtonHeight = 64.dp

/** 别的手机翻了步骤后，等这么久再跟着翻 */
private const val FOLLOW_DELAY_MS = 600L

/** 步骤数不超过这么多时画分段进度条，再多就用一条连续的 */
private const val MAX_SEGMENTS = 16

@Composable
fun CookingScreen(initialRecipeId: Long, onBack: () -> Unit) {
    val app = rememberApp()

    // 做菜模式下屏幕常亮
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val rows by app.repository.observeToday().collectAsStateWithLifecycle(null)
    val timers by app.timers.timers.collectAsStateWithLifecycle()
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    var selectedId by rememberSaveable { mutableLongStateOf(initialRecipeId) }
    LaunchedEffect(initialRecipeId) { if (initialRecipeId > 0) selectedId = initialRecipeId }

    val list = rows
    val selectedIndex = list?.indexOfFirst { it.recipeId == selectedId }?.coerceAtLeast(0) ?: 0

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                if (!list.isNullOrEmpty()) {
                    val tabsState = rememberLazyListState()
                    LaunchedEffect(selectedIndex) { tabsState.animateScrollToItem(selectedIndex) }
                    LazyRow(
                        state = tabsState,
                        contentPadding = PaddingValues(start = 10.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(list, key = { _, row -> row.recipeId }) { index, row ->
                            RecipeTab(
                                row = row,
                                selected = index == selectedIndex,
                                timers = timers.filter { it.recipeId == row.recipeId },
                                now = now,
                                onClick = { selectedId = row.recipeId },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                    Text("做菜", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
    ) { padding ->
        if (list == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (list.isEmpty()) {
            EmptyState("厨", "今日菜单是空的", "先加几道菜吧。", Modifier.padding(padding))
            return@Scaffold
        }
        val selected = list[selectedIndex]
        key(selected.recipeId) {
            RecipePager(
                row = selected,
                timers = timers.filter { it.recipeId == selected.recipeId },
                now = now,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** 顶部的菜名胶囊：选中的是深色；下面一行小字是进度或倒计时 */
@Composable
private fun RecipeTab(row: TodayRow, selected: Boolean, timers: List<CookingTimer>, now: Long, onClick: () -> Unit) {
    val ink = LocalMiseColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) ink.ink else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) ink.onInk else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.heightIn(min = 52.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
            Text(row.title, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            val timer = timers.minByOrNull { it.endAtElapsed }
            if (timer != null) {
                Text(
                    "计时 ${TimeText.format(timer.remainingSeconds(now))}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiary,
                )
            } else if (row.stepCount > 0) {
                Text(
                    "${(row.currentStep + 1).coerceAtMost(row.stepCount)} / ${row.stepCount}",
                    fontSize = 12.sp,
                    color = if (selected) ink.onInkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecipePager(
    row: TodayRow,
    timers: List<CookingTimer>,
    now: Long,
    modifier: Modifier,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val full by remember(row.recipeId) { app.repository.observeFull(row.recipeId) }.collectAsStateWithLifecycle(null)
    val data = full
    if (data == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val steps = data.steps
    if (steps.isEmpty()) {
        EmptyState("步", "这道菜还没有步骤", "去菜谱详情里编辑，加上步骤再来。", modifier)
        return
    }

    val pagerState = rememberPagerState(initialPage = row.currentStep.coerceIn(0, steps.lastIndex)) { steps.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { app.repository.setCurrentStep(row.recipeId, it) }
    }
    // 对方手机翻了步骤，这边跟着翻。等一会儿再看，自己快速翻页时数据库里滞后的值不会把页面拽回去
    LaunchedEffect(row.currentStep) {
        delay(FOLLOW_DELAY_MS)
        val target = row.currentStep.coerceIn(0, steps.lastIndex)
        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    Column(modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            val step = steps[page]
            StepPage(
                step = step,
                index = page,
                total = steps.size,
                bowls = data.bowlsForStep(step.id),
                factor = app.scaleOf(row.recipeId),
                timers = timers.filter { it.stepIndex == page },
                now = now,
                onStartTimer = { seconds -> app.timers.start(row.recipeId, row.title, page, seconds) },
                onCancelTimer = { app.timers.cancel(it) },
            )
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinePillButton(
                "上一步",
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = pagerState.currentPage > 0,
                modifier = Modifier.weight(1f),
                height = ButtonHeight,
            )
            val last = pagerState.currentPage >= steps.lastIndex
            PillButton(
                if (last) "最后一步" else "下一步",
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                enabled = !last,
                modifier = Modifier.weight(1.4f),
                height = ButtonHeight,
                icon = if (last) null else MiseIcons.ChevronRight,
            )
        }
    }
}

@Composable
private fun StepPage(
    step: Step,
    index: Int,
    total: Int,
    bowls: List<BowlFull>,
    factor: Double,
    timers: List<CookingTimer>,
    now: Long,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: (Int) -> Unit,
) {
    val app = rememberApp()
    var customOpen by remember { mutableStateOf(false) }
    val suggested = step.timerSeconds ?: TimeText.guessSeconds(step.text)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    " / ${total.toString().padStart(2, '0')}",
                    fontFamily = SerifFamily,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            StepProgress(index, total)
        }

        bowls.forEach { bowl ->
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MiseIcons.Bowl, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("倒入${bowl.bowl.label}", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                    bowl.items.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(item.name, fontSize = 19.sp, modifier = Modifier.weight(1f))
                            Text(UnitText.display(AmountScaler.scale(item.amountText, factor)), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Text(step.text, fontSize = BodySize, lineHeight = 38.sp, fontWeight = FontWeight.Medium)

        if (app.hasImage(step.image)) {
            LocalImage(step.image, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp)))
        }

        timers.forEach { timer ->
            val remaining = timer.remainingSeconds(now)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { if (timer.totalSeconds <= 0) 0f else (remaining.toFloat() / timer.totalSeconds).coerceIn(0f, 1f) },
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        TimeText.format(remaining),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { onCancelTimer(timer.id) }, modifier = Modifier.height(48.dp), shape = CircleShape) {
                        Text("取消", fontSize = 16.sp)
                    }
                }
            }
        }

        if (suggested != null) {
            PillButton(
                "开始计时 ${TimeText.describe(suggested)}",
                onClick = { onStartTimer(suggested) },
                modifier = Modifier.fillMaxWidth(),
                height = 60.dp,
                icon = MiseIcons.Timer,
                container = MaterialTheme.colorScheme.tertiary,
                content = MaterialTheme.colorScheme.onTertiary,
            )
        }
        OutlinePillButton(
            if (suggested == null) "计时" else "自定义计时",
            onClick = { customOpen = true },
            modifier = Modifier.fillMaxWidth(),
            height = 60.dp,
        )

        Spacer(Modifier.height(24.dp))
    }

    if (customOpen) {
        var minutes by remember { mutableStateOf(suggested?.let { (it / 60).coerceAtLeast(1).toString() } ?: "5") }
        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text("计时多久") },
            text = {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { v -> minutes = v.filter { it.isDigit() || it == '.' }.take(5) },
                    label = { Text("分钟") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val seconds = minutes.toDoubleOrNull()?.let { (it * 60).toInt() } ?: 0
                    if (seconds > 0) onStartTimer(seconds)
                    customOpen = false
                }) { Text("开始") }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text("取消") } },
        )
    }
}

/** 步骤进度：步数不多时一段一格，多了用一条连续的 */
@Composable
private fun StepProgress(index: Int, total: Int) {
    if (total <= MAX_SEGMENTS) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier.weight(1f).height(5.dp).background(
                        if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
                )
            }
        }
    } else {
        LinearProgressIndicator(
            progress = { (index + 1f) / total },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}
