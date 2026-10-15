package com.personal.mise.ui.cooking

import com.personal.mise.util.NoteText
import com.personal.mise.ui.common.CookFeedbackDialog
import com.personal.mise.data.Ingredient
import com.personal.mise.ui.common.SubstituteAsk
import com.personal.mise.ui.common.SubstituteDialog
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.BowlFull
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeFull
import com.personal.mise.data.Step
import com.personal.mise.data.TodayRow
import com.personal.mise.data.tagList
import com.personal.mise.timer.CookingTimer
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.EmptyState
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.InfoPill
import com.personal.mise.ui.common.NoteBanner
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.OutlinePillButton
import com.personal.mise.ui.common.PillButton
import com.personal.mise.ui.common.SectionHeader
import com.personal.mise.ui.common.hasImage
import com.personal.mise.ui.common.miseFieldColors
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.common.rememberSavedView
import com.personal.mise.ui.detail.BowlCard
import com.personal.mise.ui.detail.IngredientTable
import com.personal.mise.ui.detail.PhaseLabel
import com.personal.mise.ui.detail.SourceLink
import com.personal.mise.ui.detail.StepBowl
import com.personal.mise.util.CalorieText
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.ui.theme.SerifFamily
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.StepPhase
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
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableLongStateOf(initialRecipeId) }
    // 做菜时随时切到整份菜谱看看用料和全部步骤，切回来还在原来那一步
    var showRecipe by rememberSaveable { mutableStateOf(false) }
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
        snackbarHost = { SnackbarHost(snackbar) },
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
                snackbar = snackbar,
                showRecipe = showRecipe,
                onShowRecipe = { showRecipe = it },
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
        color = if (selected) ink.selected else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) ink.onSelected else MaterialTheme.colorScheme.onSurface,
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
            } else if (row.done) {
                Text(
                    "做完了",
                    fontSize = 12.sp,
                    color = if (selected) ink.onInkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (row.stepCount > 0) {
                Text(
                    "${(row.currentStep + 1).coerceAtMost(row.stepCount)} / ${row.stepCount}",
                    fontSize = 12.sp,
                    color = if (selected) ink.onSelectedMuted else MaterialTheme.colorScheme.onSurfaceVariant,
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
    snackbar: SnackbarHostState,
    showRecipe: Boolean,
    onShowRecipe: (Boolean) -> Unit,
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
    val factor = app.scaleOf(row.recipeId)
    val cookStart = remember(steps) { StepPhase.cookStart(steps.map { it.text }) }
    if (steps.isEmpty()) {
        Column(modifier.fillMaxSize()) {
            ModeSwitch(showRecipe, onShowRecipe)
            if (showRecipe) {
                RecipeOverview(data, factor, cookStart, currentStep = -1, onJump = {}, modifier = Modifier.weight(1f))
            } else {
                EmptyState("步", "这道菜还没有步骤", "去菜谱详情里编辑，加上步骤再来。", Modifier.weight(1f))
            }
        }
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

    // 在菜谱里点了某一步，切回做菜后翻到那一步
    var jumpTo by remember { mutableStateOf<Int?>(null) }
    // 点「做好了」后问一句做得怎么样
    var feedbackOpen by remember { mutableStateOf(false) }
    if (feedbackOpen) {
        CookFeedbackDialog(
            recipeId = row.recipeId,
            title = row.title,
            onDismiss = { feedbackOpen = false },
            onSaved = { scope.launch { snackbar.showSnackbar(it) } },
        )
    }
    // 计时到点是只响铃，还是不响铃、自动翻到下一步接着计时；记在本机，下次还是这个
    var autoNext by rememberSavedView("timer_auto_next")

    Column(modifier.fillMaxSize()) {
        ModeSwitch(showRecipe, onShowRecipe)
        if (showRecipe) {
            RecipeOverview(
                data = data,
                factor = factor,
                cookStart = cookStart,
                currentStep = pagerState.currentPage,
                onJump = { page ->
                    jumpTo = page
                    onShowRecipe(false)
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LaunchedEffect(jumpTo) {
                jumpTo?.let {
                    pagerState.scrollToPage(it)
                    jumpTo = null
                }
            }
            data.recipe.note?.let { NoteBanner(it, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val step = steps[page]
                StepPage(
                    step = step,
                    index = page,
                    total = steps.size,
                    phase = when {
                        cookStart == null -> null
                        page < cookStart -> "备料 · 还没开火"
                        page == cookStart -> "开火"
                        else -> null
                    },
                    bowls = data.bowlsForStep(step.id),
                    reminders = NoteText.forStep(data.recipe.note, page),
                    factor = factor,
                    timers = timers.filter { it.stepIndex == page },
                    now = now,
                    autoNext = autoNext,
                    onAutoNextChange = { autoNext = it },
                    onStartTimer = { seconds ->
                        app.timers.start(row.recipeId, row.title, page, seconds, autoNext = autoNext && page < steps.lastIndex)
                    },
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
                    if (last) "做好了" else "下一步",
                    onClick = {
                        scope.launch {
                            if (!last) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            } else {
                                // 最后一步点「做好了」：今日菜单里标成做完，记录页也记一笔
                                if (row.done) {
                                    snackbar.showSnackbar("${row.title}已经标成做完了")
                                } else {
                                    app.repository.setTodayDone(row.recipeId, true)
                                    feedbackOpen = true
                                    snackbar.showSnackbar("${row.title}做完了，已记下")
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                    height = ButtonHeight,
                    icon = if (last) Icons.Filled.Check else MiseIcons.ChevronRight,
                    container = if (last) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    content = if (last) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** 「做菜 / 菜谱」切换：做菜是一步一页，菜谱是整份用料和全部步骤；备料清单也用它切到菜谱 */
@Composable
internal fun ModeSwitch(showRecipe: Boolean, onChange: (Boolean) -> Unit, mainLabel: String = "做菜") {
    ModeTabs(listOf(mainLabel, "菜谱"), if (showRecipe) 1 else 0, { onChange(it == 1) })
}

/** 几个选项平分一条胶囊，选中的那格深色；备料清单用它切「按菜 / 切配 / 菜谱」 */
@Composable
internal fun ModeTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().then(modifier),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                ModeOption(label, selected = index == selected, onClick = { onSelect(index) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val ink = LocalMiseColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) ink.selected else Color.Transparent,
        contentColor = if (selected) ink.onSelected else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/** 整份菜谱：主料、调料、调料碗、全部步骤；正在做的那步高亮，点哪步就切回做菜翻到哪步 */
@Composable
private fun RecipeOverview(
    data: RecipeFull,
    factor: Double,
    cookStart: Int?,
    currentStep: Int,
    onJump: (Int) -> Unit,
    modifier: Modifier,
) {
    var substitute by remember { mutableStateOf<SubstituteAsk?>(null) }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        recipeOverviewItems(data, factor, cookStart, currentStep, onJump, onAsk = { ing, amount -> substitute = SubstituteAsk(data.recipe.id, ing.name, amount) })
    }
    substitute?.let { SubstituteDialog(it, onDismiss = { substitute = null }) }
}

/**
 * 整份菜谱的列表项，做菜页和备料清单共用。
 * [onJump] 为 null 时步骤不能点；[keyPrefix] 让一个列表里放多道菜时 key 不重复。
 */
internal fun LazyListScope.recipeOverviewItems(
    data: RecipeFull,
    factor: Double,
    cookStart: Int?,
    currentStep: Int = -1,
    onJump: ((Int) -> Unit)? = null,
    keyPrefix: String = "",
    onAsk: ((Ingredient, String) -> Unit)? = null,
) {
    val mains = data.ingredients.filter { it.kind != IngredientKind.SEASONING }
    val seasonings = data.ingredients.filter { it.kind == IngredientKind.SEASONING }
    item(key = keyPrefix + "info") { RecipeInfo(data, factor) }
    item(key = keyPrefix + "mains-title") { SectionHeader("主料", Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) }
    item(key = keyPrefix + "mains") { IngredientTable(mains, factor, onAsk) }
    item(key = keyPrefix + "seasonings-title") {
        SectionHeader(
            "调料",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            accent = MaterialTheme.colorScheme.secondary,
        )
    }
    item(key = keyPrefix + "seasonings") { IngredientTable(seasonings, factor, onAsk) }
    item(key = keyPrefix + "bowls-title") {
        SectionHeader(
            "调料碗",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            accent = MaterialTheme.colorScheme.tertiary,
        )
    }
    if (data.bowls.isEmpty()) {
        item(key = keyPrefix + "bowls-empty") {
            Text("没有需要提前配的调料碗", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        items(data.bowls, key = { keyPrefix + "bowl-${it.bowl.id}" }) { bowl ->
            val stepIndex = data.steps.indexOfFirst { it.id == bowl.bowl.stepId }
            BowlCard(bowl, if (stepIndex >= 0) StepPhase.bowlStepLabel(stepIndex, cookStart, data.steps[stepIndex].text) else null, factor)
        }
    }
    if (data.steps.isNotEmpty()) {
        item(key = keyPrefix + "steps-title") {
            SectionHeader(
                "步骤",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                accent = MaterialTheme.colorScheme.secondary,
                hint = if (onJump != null) "点一步，回到做菜从这步接着做" else null,
            )
        }
        itemsIndexed(data.steps, key = { _, s -> keyPrefix + "step-${s.id}" }) { index, step ->
            val app = rememberApp()
            val current = index == currentStep
            Column {
                if (cookStart != null && (index == 0 || index == cookStart)) {
                    PhaseLabel(if (index == 0) "备料 · 开火前" else "开火", prep = index == 0)
                }
                Surface(
                    onClick = { onJump?.invoke(index) },
                    enabled = onJump != null,
                    shape = RoundedCornerShape(20.dp),
                    color = if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Box(
                            Modifier.size(32.dp).background(
                                if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${index + 1}",
                                color = if (current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.background,
                                fontFamily = SerifFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (current) Text("正在做", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(step.text, style = MaterialTheme.typography.bodyLarge)
                            data.bowlsForStep(step.id).forEach { StepBowl(it, factor) }
                            step.timerSeconds?.let {
                                InfoPill(
                                    "计时 ${TimeText.describe(it)}",
                                    container = MaterialTheme.colorScheme.tertiaryContainer,
                                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            if (app.hasImage(step.image)) {
                                LocalImage(step.image, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)))
                            }
                        }
                    }
                }
            }
        }
    }
    data.recipe.sourceUrl?.let { url ->
        item(key = keyPrefix + "source") { SourceLink(url) }
    }
}

/** 菜谱开头的分类、份量、步数、热量，和详情页一样 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeInfo(data: RecipeFull, factor: Double) {
    val pills = buildList {
        data.recipe.category?.takeIf { it.isNotBlank() }?.let { cat ->
            add(data.recipe.subcategory?.takeIf { it.isNotBlank() }?.let { "$cat › $it" } ?: cat)
        }
        data.recipe.tagList.forEach { add("#$it") }
        data.recipe.servings?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (data.steps.isNotEmpty()) add("${data.steps.size} 步")
        data.recipe.calories?.let { add(CalorieText.describe(it, data.recipe.servings, factor)) }
    }
    Column {
        data.recipe.note?.let { NoteBanner(it, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) }
        if (pills.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pills.forEach { InfoPill(it) }
            }
        }
    }
}

@Composable
private fun StepPage(
    step: Step,
    index: Int,
    total: Int,
    /** 备料阶段、开火那一步给个提示，其余不显示 */
    phase: String?,
    bowls: List<BowlFull>,
    /** 批注里写给这一步的提醒，如「鸡腿多炖 5 分钟」 */
    reminders: List<String>,
    factor: Double,
    timers: List<CookingTimer>,
    now: Long,
    autoNext: Boolean,
    onAutoNextChange: (Boolean) -> Unit,
    onStartTimer: (Int) -> Unit,
    onCancelTimer: (Int) -> Unit,
) {
    val last = index >= total - 1
    val app = rememberApp()
    var customOpen by remember { mutableStateOf(false) }
    val suggested = step.timerSeconds ?: TimeText.guessSeconds(step.text)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
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
                Spacer(Modifier.weight(1f))
                phase?.let {
                    val firing = it == "开火"
                    InfoPill(
                        it,
                        modifier = Modifier.padding(bottom = 8.dp),
                        container = if (firing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = if (firing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

        if (reminders.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("我的批注", style = MaterialTheme.typography.labelMedium)
                    reminders.forEach { Text(it, fontSize = 19.sp, fontWeight = FontWeight.Medium) }
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            TimeText.format(remaining),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            if (timer.autoNext) "到点自动下一步" else "到点响铃",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { onCancelTimer(timer.id) }, modifier = Modifier.height(48.dp), shape = CircleShape) {
                        Text("取消", fontSize = 16.sp)
                    }
                }
            }
        }

        // 最后一步没有下一步可跳，只能响铃
        if (!last) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeTabs(
                    labels = listOf("闹钟", "自动下一步"),
                    selected = if (autoNext) 1 else 0,
                    onSelect = { onAutoNextChange(it == 1) },
                    modifier = Modifier,
                )
                Text(
                    if (autoNext) "到点不响铃，直接翻到下一步，下一步有时间就接着计时" else "到点只响铃，停在这一步",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
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
