package com.personal.mise.ui.detail

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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.personal.mise.ui.common.NoteBanner
import com.personal.mise.ui.common.NoteDialog
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.OutlinePillButton
import com.personal.mise.ui.common.PillButton
import com.personal.mise.ui.common.RecipeCover
import com.personal.mise.ui.common.RecipeReviewCard
import com.personal.mise.ui.plan.PlanDateDialog
import com.personal.mise.ui.common.SectionHeader
import com.personal.mise.ui.common.ShareImageDialog
import com.personal.mise.share.ShareImage
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ai.AiRecipeEditor
import com.personal.mise.ai.AiRecipeReviewer
import com.personal.mise.ai.RecipeReview
import com.personal.mise.util.RecipeChanges
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var reviewing by remember { mutableStateOf(false) }
    var askingKeyForReview by remember { mutableStateOf(false) }
    var fixing by remember { mutableStateOf(false) }
    var askingKeyForFix by remember { mutableStateOf(false) }
    /** 照评价改好、等用户看完决定留着还是撤销 */
    var fixResult by remember { mutableStateOf<FixResult?>(null) }
    var askingKey by remember { mutableStateOf(false) }
    var aiEditOpen by remember { mutableStateOf(false) }
    var aiEditing by remember { mutableStateOf(false) }
    var aiEditError by remember { mutableStateOf<String?>(null) }
    /** 没填 Key 时先记下要求，填完 Key 接着改 */
    var aiEditPending by remember { mutableStateOf<String?>(null) }
    var logOpen by remember { mutableStateOf(false) }
    var planOpen by remember { mutableStateOf(false) }
    var classifyOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    /** 翻译过的菜谱切到原文看 */
    var showOriginal by remember { mutableStateOf(false) }
    val cookDates by remember(recipeId) { app.repository.observeCookDates(recipeId) }.collectAsStateWithLifecycle(emptyList())
    val planDates by remember(recipeId) { app.repository.observePlanDates(recipeId) }.collectAsStateWithLifecycle(emptyList())
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

    fun aiReview() {
        val current = full ?: return
        if (reviewing) return
        reviewing = true
        scope.launch {
            val message = try {
                app.repository.setReview(recipeId, app.recipeReviewer.review(current))
                null
            } catch (e: ImportException) {
                e.message ?: "AI 评价失败"
            } catch (e: Exception) {
                "AI 评价失败：${e.message ?: e.javaClass.simpleName}"
            }
            reviewing = false
            message?.let { snackbar.showSnackbar(it) }
        }
    }

    /**
     * 照评价里要注意的地方让 AI 改菜谱，改完直接保存，再列出改了什么，可以撤销。
     * 留着改好的就按新菜谱重新评价；去掉的步骤图等确定不撤销了再删。
     */
    fun fixByReview() {
        val review = RecipeReview.fromJson(full?.recipe?.review) ?: return
        if (fixing) return
        fixing = true
        scope.launch {
            val before = app.repository.snapshot(recipeId)
            var message: String? = null
            val applied = try {
                if (before == null) throw ImportException("菜谱不存在")
                val edit = app.recipeEditor.edit(AiRecipeEditor.editable(before.draft), AiRecipeReviewer.fixInstruction(review))
                val after = AiRecipeEditor.apply(before.draft, edit.recipe)
                val changes = RecipeChanges.describe(before.draft, after)
                if (changes.isEmpty()) {
                    message = edit.summary.ifBlank { "AI 看了一遍，觉得不用改" }
                    null
                } else {
                    val dropped = AiRecipeEditor.droppedImages(before.draft, after)
                    app.repository.save(after, keepImages = dropped)
                    val ingredientsChanged = before.draft.ingredients.map { it.name to it.amountText } != after.ingredients.map { it.name to it.amountText }
                    val notes = listOfNotNull(
                        "有 ${dropped.size} 张步骤图没地方放，去掉了".takeIf { dropped.isNotEmpty() },
                        "用料变了，热量可以重新估算".takeIf { ingredientsChanged && before.draft.calories != null },
                    )
                    Triple(edit.summary, changes + notes, dropped)
                }
            } catch (e: ImportException) {
                message = e.message ?: "AI 修改失败"
                null
            } catch (e: Exception) {
                message = "AI 修改失败：${e.message ?: e.javaClass.simpleName}"
                null
            } finally {
                fixing = false
            }
            message?.let { snackbar.showSnackbar(it) }
            if (before == null || applied == null) return@launch
            val (summary, changes, dropped) = applied
            val decision = CompletableDeferred<Boolean>()
            var undone = false
            try {
                fixResult = FixResult(summary, changes, decision)
                undone = decision.await()
                fixResult = null
                if (undone) {
                    app.repository.restore(before)
                    snackbar.showSnackbar("已撤销 AI 的修改")
                } else {
                    aiReview()
                }
            } finally {
                if (!undone && dropped.isNotEmpty()) withContext(NonCancellable) { app.repository.deleteUnusedImages(recipeId, dropped) }
            }
        }
    }

    /**
     * 按一句话让 AI 改菜谱，改完直接保存；提示条上可以撤销。
     * 去掉的步骤图等提示条消失、确定不撤销了再删。
     */
    fun aiEdit(instruction: String) {
        if (aiEditing) return
        aiEditing = true
        aiEditError = null
        scope.launch {
            val before = app.repository.snapshot(recipeId)
            val result = try {
                if (before == null) throw ImportException("菜谱不存在")
                val edit = app.recipeEditor.edit(AiRecipeEditor.editable(before.draft), instruction)
                val after = AiRecipeEditor.apply(before.draft, edit.recipe)
                val dropped = AiRecipeEditor.droppedImages(before.draft, after)
                app.repository.save(after, keepImages = dropped)
                Triple(edit.summary, after, dropped)
            } catch (e: ImportException) {
                aiEditError = e.message ?: "AI 修改失败"
                null
            } catch (e: Exception) {
                aiEditError = "AI 修改失败：${e.message ?: e.javaClass.simpleName}"
                null
            } finally {
                aiEditing = false
            }
            if (before == null || result == null) return@launch
            val (summary, after, dropped) = result
            aiEditOpen = false
            val ingredientsChanged = before.draft.ingredients.map { it.name to it.amountText } != after.ingredients.map { it.name to it.amountText }
            val message = listOfNotNull(
                summary.ifBlank { "AI 改好了" },
                "有 ${dropped.size} 张步骤图没地方放，去掉了".takeIf { dropped.isNotEmpty() },
                "用料变了，热量可以重新估算".takeIf { ingredientsChanged && before.draft.calories != null },
            ).joinToString("；")
            var undone = false
            try {
                undone = snackbar.showSnackbar(message, actionLabel = "撤销", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed
                if (undone) {
                    app.repository.restore(before)
                    snackbar.showSnackbar("已撤销 AI 的修改")
                }
            } finally {
                if (!undone && dropped.isNotEmpty()) withContext(NonCancellable) { app.repository.deleteUnusedImages(recipeId, dropped) }
            }
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
        val review = remember(data.recipe.review) { RecipeReview.fromJson(data.recipe.review) }

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
                        data.recipe.note?.let { NoteBanner(it, onEdit = { noteOpen = true }) }
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
                            if (data.recipe.note == null) {
                                InfoPill("＋ 批注", Modifier.clip(CircleShape).clickable { noteOpen = true }, content = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (review == null && (data.ingredients.isNotEmpty() || data.steps.isNotEmpty())) {
                                InfoPill(
                                    if (reviewing) "AI 评价中…" else "✨ AI 评价",
                                    Modifier.clip(CircleShape).clickable(enabled = !reviewing) {
                                        if (app.aiSettings.hasKey) aiReview() else askingKeyForReview = true
                                    },
                                    content = MaterialTheme.colorScheme.primary,
                                )
                            }
                            InfoPill(
                                "✨ 让 AI 改",
                                Modifier.clip(CircleShape).clickable {
                                    aiEditError = null
                                    aiEditOpen = true
                                },
                                content = MaterialTheme.colorScheme.primary,
                            )
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
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                val upcoming = planDates.filter { it >= DayText.today() }
                                StatRow(
                                    label = "计划",
                                    value = if (upcoming.isEmpty()) "还没排" else upcoming.take(3).joinToString("、") { DayText.ahead(it) },
                                    muted = upcoming.isEmpty(),
                                ) {
                                    TextButton(onClick = { planOpen = true }) { Text("排一天") }
                                }
                            }
                        }
                        review?.let {
                            RecipeReviewCard(
                                it,
                                busy = reviewing,
                                onRefresh = { if (app.aiSettings.hasKey) aiReview() else askingKeyForReview = true },
                                fixing = fixing,
                                onFix = { if (app.aiSettings.hasKey) fixByReview() else askingKeyForFix = true },
                            )
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
                        // 备料最后一步和开火第一步之间断开；竖线画在背景上，不用 IntrinsicSize 量高度（FlowRow 量出来偏矮，会把下面的内容截掉）
                        val connector = index < data.steps.lastIndex && index + 1 != cookStart
                        val lineColor = MaterialTheme.colorScheme.outlineVariant
                        Row(
                            Modifier.fillMaxWidth()
                                .drawBehind {
                                    if (connector) {
                                        val x = 20.dp.toPx() + 16.dp.toPx()
                                        drawLine(
                                            color = lineColor,
                                            start = Offset(x, 36.dp.toPx()),
                                            end = Offset(x, size.height - 4.dp.toPx()),
                                            strokeWidth = 2.dp.toPx(),
                                        )
                                    }
                                }
                                .padding(horizontal = 20.dp),
                        ) {
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

    if (aiEditOpen) {
        AiEditDialog(
            busy = aiEditing,
            error = aiEditError,
            onDismiss = { aiEditOpen = false },
            onSubmit = { if (app.aiSettings.hasKey) aiEdit(it) else aiEditPending = it },
        )
    }

    aiEditPending?.let { instruction ->
        AiKeyDialog(
            confirmLabel = "保存并修改",
            onDismiss = { aiEditPending = null },
            onConfirm = {
                aiEditPending = null
                aiEdit(instruction)
            },
        )
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

    if (askingKeyForReview) {
        AiKeyDialog(
            confirmLabel = "保存并评价",
            onDismiss = { askingKeyForReview = false },
            onConfirm = {
                askingKeyForReview = false
                aiReview()
            },
        )
    }

    if (askingKeyForFix) {
        AiKeyDialog(
            confirmLabel = "保存并修改",
            onDismiss = { askingKeyForFix = false },
            onConfirm = {
                askingKeyForFix = false
                fixByReview()
            },
        )
    }

    fixResult?.let { result ->
        AiChangesDialog(
            summary = result.summary,
            changes = result.changes,
            onUndo = { result.decision.complete(true) },
            onKeep = { result.decision.complete(false) },
        )
    }

    if (noteOpen) {
        NoteDialog(
            current = full?.recipe?.note,
            onDismiss = { noteOpen = false },
            onSave = {
                noteOpen = false
                scope.launch { app.repository.setNote(recipeId, it) }
            },
        )
    }

    if (planOpen) {
        PlanDateDialog(
            onDismiss = { planOpen = false },
            onPick = { date ->
                planOpen = false
                scope.launch {
                    val added = app.repository.addToPlan(recipeId, date)
                    snackbar.showSnackbar(if (added) "已排进计划：${DayText.ahead(date)}" else "${DayText.ahead(date)}已经排了这道菜")
                }
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
                BowlItemRow(item.name, UnitText.display(AmountScaler.scale(item.amountText, factor)), Modifier.padding(start = 24.dp))
            }
        }
    }
}

/** 调料碗里的一样调料：名称靠左、用量靠右；用量太长放不下就换到下一行，不挤压名称 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BowlItemRow(name: String, amount: String, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
        Text(amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
                BowlItemRow(item.name, UnitText.display(AmountScaler.scale(item.amountText, factor)), Modifier.padding(start = 28.dp, top = 2.dp))
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

/** 照评价改好的结果：AI 的说明、改了哪些地方，[decision] 等用户选撤销（true）还是留着（false） */
private class FixResult(val summary: String, val changes: List<String>, val decision: CompletableDeferred<Boolean>)
