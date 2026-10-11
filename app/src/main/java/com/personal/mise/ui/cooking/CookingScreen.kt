package com.personal.mise.ui.cooking

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.hasImage
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.util.TimeText
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.UnitText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BodySize = 24.sp
private val ButtonHeight = 60.dp

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("做菜") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        val list = rows
        if (list == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (list.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("今日菜单是空的，先加几道菜吧。", fontSize = BodySize)
            }
            return@Scaffold
        }
        val selectedIndex = list.indexOfFirst { it.recipeId == selectedId }.coerceAtLeast(0)
        val selected = list[selectedIndex]

        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 8.dp) {
                list.forEachIndexed { index, row ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { selectedId = row.recipeId },
                        modifier = Modifier.heightIn(min = 64.dp),
                        text = { TabLabel(row, timers.filter { it.recipeId == row.recipeId }, now) },
                    )
                }
            }
            key(selected.recipeId) {
                RecipePager(
                    row = selected,
                    timers = timers.filter { it.recipeId == selected.recipeId },
                    now = now,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabLabel(row: TodayRow, timers: List<CookingTimer>, now: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val progress = if (row.stepCount == 0) "" else " ${(row.currentStep + 1).coerceAtMost(row.stepCount)}/${row.stepCount}"
        Text("${row.title}$progress", fontSize = 18.sp, maxLines = 1)
        timers.minByOrNull { it.endAtElapsed }?.let {
            Text(
                "⏱ ${TimeText.format(it.remainingSeconds(now))}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.tertiary,
            )
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
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("这道菜还没有步骤", fontSize = BodySize)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = row.currentStep.coerceIn(0, steps.lastIndex)) { steps.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { app.repository.setCurrentStep(row.recipeId, it) }
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
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = pagerState.currentPage > 0,
                modifier = Modifier.weight(1f).height(ButtonHeight),
            ) { Text("上一步", fontSize = 20.sp) }
            Button(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                enabled = pagerState.currentPage < steps.lastIndex,
                modifier = Modifier.weight(1f).height(ButtonHeight),
            ) { Text(if (pagerState.currentPage < steps.lastIndex) "下一步" else "最后一步", fontSize = 20.sp) }
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "第 ${index + 1} 步 / 共 $total 步",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )

        bowls.forEach { bowl ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("倒入${bowl.bowl.label}", fontSize = BodySize, fontWeight = FontWeight.Bold)
                    bowl.items.forEach { item ->
                        Text("${item.name}  ${UnitText.display(AmountScaler.scale(item.amountText, factor))}", fontSize = 20.sp)
                    }
                }
            }
        }

        Text(step.text, fontSize = BodySize, lineHeight = 36.sp)

        if (app.hasImage(step.image)) {
            LocalImage(step.image, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)))
        }

        timers.forEach { timer ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        TimeText.format(timer.remainingSeconds(now)),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { onCancelTimer(timer.id) }, modifier = Modifier.height(ButtonHeight)) {
                        Text("取消计时", fontSize = 18.sp)
                    }
                }
            }
        }

        if (suggested != null) {
            Button(
                onClick = { onStartTimer(suggested) },
                modifier = Modifier.fillMaxWidth().height(ButtonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) { Text("开始计时 ${TimeText.describe(suggested)}", fontSize = 20.sp) }
        }
        OutlinedButton(
            onClick = { customOpen = true },
            modifier = Modifier.fillMaxWidth().height(ButtonHeight),
        ) { Text(if (suggested == null) "计时" else "自定义计时", fontSize = 20.sp) }

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
