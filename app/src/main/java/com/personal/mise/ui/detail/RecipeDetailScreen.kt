package com.personal.mise.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.tagList
import com.personal.mise.ui.common.ClassifyDialog
import androidx.compose.foundation.clickable
import com.personal.mise.data.BowlFull
import com.personal.mise.data.Ingredient
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeText
import com.personal.mise.ui.common.BottomActionBar
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.InfoPill
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.OutlinePillButton
import com.personal.mise.ui.common.PillButton
import com.personal.mise.ui.common.RecipeCover
import com.personal.mise.ui.common.SectionHeader
import com.personal.mise.ui.common.ShareImageDialog
import com.personal.mise.share.ShareImage
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.importer.ImportException
import com.personal.mise.ui.edit.AiKeyDialog
import com.personal.mise.util.CalorieText
import com.personal.mise.util.DayText
import java.time.LocalDate
import com.personal.mise.ui.common.hasImage
import com.personal.mise.ui.common.miseFieldColors
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.theme.SerifFamily
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.StepPhase
import com.personal.mise.util.TimeText
import com.personal.mise.util.UnitText
import kotlinx.coroutines.launch

/** 封面横幅高度；下面的内容面板往上盖住一截 */
private val CoverHeight = 230.dp
private val PanelOverlap = 30.dp

@OptIn(ExperimentalLayoutApi::class)
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
    var classifyOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    /** 翻译过的菜谱切到原文看 */
    var showOriginal by remember { mutableStateOf(false) }
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
        // 封面延伸到状态栏下面，按钮自己避开状态栏
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomActionBar {
                OutlinePillButton(
                    "加入今日菜单",
                    onClick = {
                        scope.launch {
                            val added = app.repository.addToToday(recipeId)
                            snackbar.showSnackbar(if (added) "已加入今日菜单" else "已经在今日菜单里了")
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    "开始做",
                    onClick = {
                        scope.launch {
                            app.repository.addToToday(recipeId)
                            onCook()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = MiseIcons.ChevronRight,
                )
            }
        },
    ) { padding ->
        val data = full ?: return@Scaffold
        val cookStart = remember(data.steps) { StepPhase.cookStart(data.steps.map { it.text }) }
        val mains = data.ingredients.filter { it.kind != IngredientKind.SEASONING }
        val seasonings = data.ingredients.filter { it.kind == IngredientKind.SEASONING }
        val original = remember(data.recipe.original) { RecipeText.fromJson(data.recipe.original) }
        val originalShown = original?.takeIf { showOriginal }

        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "hero") {
                Box {
                    RecipeCover(
                        data.recipe.title,
                        data.recipe.coverImage,
                        Modifier.fillMaxWidth().height(CoverHeight + statusTop),
                        initialSize = 120.sp,
                    )
                    val glass = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f)
                    Row(
                        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack, container = glass)
                        Spacer(Modifier.weight(1f))
                        CircleIconButton(Icons.Filled.Share, "导出图片", { shareOpen = true }, container = glass)
                        CircleIconButton(Icons.Filled.Edit, "编辑", onEdit, container = glass)
                        CircleIconButton(Icons.Filled.Delete, "删除", { confirmDelete = true }, container = glass)
                    }
                    Column(
                        Modifier
                            .padding(top = CoverHeight + statusTop - PanelOverlap)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                            .padding(start = 20.dp, end = 20.dp, top = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(originalShown?.title?.takeIf { it.isNotBlank() } ?: data.recipe.title, style = MaterialTheme.typography.headlineMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 点分类或标签直接改，不用进编辑页
                            val classify = Modifier.clip(CircleShape).clickable { classifyOpen = true }
                            data.recipe.category?.takeIf { it.isNotBlank() }?.let { cat ->
                                val text = data.recipe.subcategory?.takeIf { it.isNotBlank() }?.let { "$cat › $it" } ?: cat
                                InfoPill(text, classify, container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            data.recipe.tagList.forEach { InfoPill("#$it", classify, content = MaterialTheme.colorScheme.primary) }
                            if (data.recipe.category.isNullOrBlank() && data.recipe.tagList.isEmpty()) {
                                InfoPill("＋ 分类/标签", classify, content = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            data.recipe.servings?.takeIf { it.isNotBlank() }?.let { InfoPill(it) }
                            if (data.steps.isNotEmpty()) InfoPill("${data.steps.size} 步")
                            Surface(
                                onClick = { scaleOpen = true },
                                shape = CircleShape,
                                color = if (factor == 1.0) Color.Transparent else MaterialTheme.colorScheme.primary,
                                contentColor = if (factor == 1.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            ) {
                                Text(
                                    if (factor == 1.0) "换算用量" else "换算 ${AmountScaler.label(factor)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                            if (factor != 1.0) {
                                TextButton(onClick = { app.scales.remove(recipeId) }) { Text("恢复原量") }
                            }
                            if (original != null) {
                                Surface(
                                    onClick = { showOriginal = !showOriginal },
                                    shape = CircleShape,
                                    color = if (showOriginal) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                    contentColor = if (showOriginal) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary,
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                                ) {
                                    Text(
                                        if (showOriginal) "看中文" else "看原文",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                        SoftCard(Modifier.fillMaxWidth().padding(top = 4.dp), shape = RoundedCornerShape(20.dp)) {
                            Column {
                                val kcal = data.recipe.calories
                                StatRow(
                                    label = "热量",
                                    value = if (kcal == null) "还没算" else CalorieText.describe(kcal, data.recipe.servings, factor),
                                    muted = kcal == null,
                                ) {
                                    if (estimatingCalories) {
                                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                                    } else if (data.ingredients.isNotEmpty()) {
                                        TextButton(onClick = { if (app.aiSettings.hasKey) estimateCalories() else askingKey = true }) {
                                            Text(if (kcal == null) "AI 估算" else "重新估算")
                                        }
                                    }
                                }
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                StatRow(
                                    label = "做过",
                                    value = if (cookDates.isEmpty()) "还没做过" else "${cookDates.size} 次 · 上次${DayText.ago(cookDates.first())}",
                                    muted = cookDates.isEmpty(),
                                ) {
                                    TextButton(onClick = { logOpen = true }) { Text("记一笔") }
                                }
                            }
                        }
                    }
                }
            }

            if (originalShown != null) {
                originalItems(originalShown)
            } else {
                item(key = "mains-title") { SectionHeader("主料", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)) }
                item(key = "mains") { IngredientTable(mains, factor) }
                item(key = "seasonings-title") {
                    SectionHeader(
                        "调料",
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
                item(key = "seasonings") { IngredientTable(seasonings, factor) }

                item(key = "bowls-title") {
                    SectionHeader(
                        "调料碗",
                        Modifier.padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
                        accent = MaterialTheme.colorScheme.tertiary,
                    ) {
                        TextButton(onClick = onEditBowls) { Text("调整") }
                    }
                }
                if (data.bowls.isEmpty()) {
                    item(key = "bowls-empty") {
                        Text(
                            "没有需要提前配的调料碗",
                            Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    data.bowls.forEach { bowl ->
                        item(key = "bowl-${bowl.bowl.id}") {
                            val stepIndex = data.steps.indexOfFirst { it.id == bowl.bowl.stepId }
                            BowlCard(bowl, if (stepIndex >= 0) StepPhase.bowlStepLabel(stepIndex, cookStart, data.steps[stepIndex].text) else null, factor)
                        }
                    }
                }

                item(key = "steps-title") {
                    SectionHeader(
                        "步骤",
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                        accent = MaterialTheme.colorScheme.secondary,
                    )
                }
                itemsIndexed(data.steps, key = { _, s -> "step-${s.id}" }) { index, step ->
                    Column {
                        if (cookStart != null && (index == 0 || index == cookStart)) {
                            PhaseLabel(if (index == 0) "备料 · 开火前" else "开火", prep = index == 0)
                        }
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 20.dp)) {
                            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(32.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color = MaterialTheme.colorScheme.background,
                                        fontFamily = SerifFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                    )
                                }
                                // 备料最后一步和开火第一步之间断开
                                if (index < data.steps.lastIndex && index + 1 != cookStart) {
                                    Box(
                                        Modifier.padding(vertical = 4.dp).width(2.dp).weight(1f)
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            data.recipe.sourceUrl?.let { url ->
                item(key = "source") { SourceLink(url) }
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

    if (scaleOpen) {
        ScaleDialog(
            current = factor,
            mains = full?.ingredients.orEmpty().filter { it.kind != IngredientKind.SEASONING },
            onDismiss = { scaleOpen = false },
            onPick = {
                if (it == 1.0) app.scales.remove(recipeId) else app.scales[recipeId] = it
                scaleOpen = false
            },
        )
    }

    if (shareOpen) {
        full?.let { data ->
            ShareImageDialog(
                title = "导出菜谱图片",
                name = data.recipe.title,
                key = data to factor,
                render = { ShareImage.recipe(data, factor, app.images) },
                onDismiss = { shareOpen = false },
                options = {
                    if (factor != 1.0) {
                        Text(
                            "用量按 ${AmountScaler.label(factor)} 换算",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }

    if (classifyOpen) {
        full?.let { ClassifyDialog(it.recipe, onDismiss = { classifyOpen = false }) }
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

/** 翻译前的原文：用料和步骤都只有文字，没有调料碗、计时和图片 */
private fun LazyListScope.originalItems(original: RecipeText) {
    original.servings?.takeIf { it.isNotBlank() }?.let { servings ->
        item(key = "original-servings") {
            Text(servings, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item(key = "original-ingredients-title") { SectionHeader("Ingredients", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp)) }
    item(key = "original-ingredients") {
        IngredientTable(
            original.ingredients.mapIndexed { i, (name, amount) ->
                Ingredient(id = -1L - i, recipeId = 0, name = name, amountText = amount, kind = IngredientKind.MAIN, order = i)
            },
            1.0,
        )
    }
    item(key = "original-steps-title") {
        SectionHeader(
            "Steps",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
            accent = MaterialTheme.colorScheme.secondary,
        )
    }
    itemsIndexed(original.steps, key = { i, _ -> "original-step-$i" }) { index, text ->
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Box(
                Modifier.size(32.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    color = MaterialTheme.colorScheme.background,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(text, Modifier.weight(1f).padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 菜谱来源，网址可以直接点开 */
@Composable
internal fun SourceLink(url: String) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(url, linkColor) {
        buildAnnotatedString {
            append("来源：")
            if (url.startsWith("http://") || url.startsWith("https://")) {
                val link = LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                ) { runCatching { uriHandler.openUri(url) } }
                withLink(link) { append(url) }
            } else {
                append(url)
            }
        }
    }
    Text(
        text,
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 步骤分段的小标题：备料用浅色，开火用主色 */
@Composable
internal fun PhaseLabel(text: String, prep: Boolean) {
    Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
        InfoPill(
            text,
            container = if (prep) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
            content = if (prep) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** 步骤下面列出这一步倒入的调料碗：碗名和碗里每样调料的用量 */
@Composable
internal fun StepBowl(bowl: BowlFull, factor: Double) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MiseIcons.Bowl, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("倒入${bowl.bowl.label}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            bowl.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(start = 24.dp)) {
                    Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(UnitText.display(AmountScaler.scale(item.amountText, factor)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 详情卡里的一行：左边小标签，中间数值，右边操作 */
@Composable
private fun StatRow(label: String, value: String, muted: Boolean, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

/** 用料表：名称 ······ 用量 */
@Composable
internal fun IngredientTable(items: List<Ingredient>, factor: Double) {
    if (items.isEmpty()) {
        Text("无", Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val dots = MaterialTheme.colorScheme.outline
    Column(Modifier.padding(horizontal = 20.dp)) {
        items.forEach { ing ->
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ing.name, style = MaterialTheme.typography.bodyLarge)
                Box(
                    Modifier.weight(1f).height(12.dp).padding(horizontal = 10.dp).drawBehind {
                        drawLine(
                            color = dots,
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())),
                        )
                    },
                )
                Text(
                    UnitText.display(AmountScaler.scale(ing.amountText, factor)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 调料碗卡片：芥末色浅底 */
@Composable
fun BowlCard(bowl: BowlFull, subtitle: String?, factor: Double = 1.0, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MiseIcons.Bowl, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(bowl.bowl.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            }
            bowl.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 2.dp)) {
                    Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(UnitText.display(AmountScaler.scale(item.amountText, factor)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val presetFactors = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0)

/** 选倍率：常用倍数直接点，也能自己输，如 1.25；或者按手上主料的量反推，如菜谱牛肉 250克、我有 500克 → ×2 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleDialog(current: Double, mains: List<Ingredient>, onDismiss: () -> Unit, onPick: (Double) -> Unit) {
    var custom by remember { mutableStateOf(if (current in presetFactors) "" else AmountScaler.format(current)) }
    val customValue = custom.trim().toDoubleOrNull()?.takeIf { it > 0 && it <= 100 }

    val bases = remember(mains) { mains.filter { (AmountScaler.quantity(it.amountText) ?: 0.0) > 0 } }
    var base by remember(bases) { mutableStateOf(bases.firstOrNull()) }
    var have by remember { mutableStateOf("") }
    val baseQuantity = base?.let { AmountScaler.quantity(it.amountText) }
    val haveFactor = have.trim().toDoubleOrNull()?.takeIf { it > 0 }
        ?.let { h -> baseQuantity?.let { h / it } }?.takeIf { it <= 100 }
    val picked = customValue ?: haveFactor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("换算用量") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                    onValueChange = { custom = it; if (it.isNotBlank()) have = "" },
                    label = { Text("自定义倍率") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = custom.isNotBlank() && customValue == null,
                )
                if (base != null) {
                    Text("按主料换算", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (bases.size > 1) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            bases.forEach { ing ->
                                FilterChip(
                                    selected = ing == base,
                                    onClick = { base = ing },
                                    label = { Text(ing.name) },
                                )
                            }
                        }
                    }
                    val b = base!!
                    val unit = UnitText.display(AmountScaler.unit(b.amountText))
                    OutlinedTextField(
                        value = have,
                        onValueChange = { have = it; if (it.isNotBlank()) custom = "" },
                        label = { Text("我有多少${b.name}") },
                        suffix = if (unit.isNotEmpty()) { { Text(unit) } } else null,
                        supportingText = {
                            Text(
                                "菜谱用 ${UnitText.display(b.amountText)}" +
                                    (haveFactor?.let { "，其他用料按 ${AmountScaler.label(it)} 换算" } ?: ""),
                            )
                        },
                        singleLine = true,
                        shape = FieldShape,
                        colors = miseFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = have.isNotBlank() && haveFactor == null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null) { Text("确定") }
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
