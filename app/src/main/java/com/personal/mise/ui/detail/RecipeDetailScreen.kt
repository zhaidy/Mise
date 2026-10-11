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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.BowlFull
import com.personal.mise.data.Ingredient
import com.personal.mise.data.IngredientKind
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
import com.personal.mise.ui.common.hasImage
import com.personal.mise.ui.common.miseFieldColors
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.theme.SerifFamily
import com.personal.mise.util.AmountScaler
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
    val factor = app.scaleOf(recipeId)

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
        val mains = data.ingredients.filter { it.kind != IngredientKind.SEASONING }
        val seasonings = data.ingredients.filter { it.kind == IngredientKind.SEASONING }

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
                        Text(data.recipe.title, style = MaterialTheme.typography.headlineMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
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
                        }
                    }
                }
            }

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
                        BowlCard(bowl, if (stepIndex >= 0) "第 ${stepIndex + 1} 步下锅" else null, factor)
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
                        if (index < data.steps.lastIndex) {
                            Box(
                                Modifier.padding(vertical = 4.dp).width(2.dp).weight(1f)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(step.text, style = MaterialTheme.typography.bodyLarge)
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
            data.recipe.sourceUrl?.let { url ->
                item(key = "source") {
                    Text(
                        "来源：$url",
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (scaleOpen) {
        ScaleDialog(
            current = factor,
            onDismiss = { scaleOpen = false },
            onPick = {
                if (it == 1.0) app.scales.remove(recipeId) else app.scales[recipeId] = it
                scaleOpen = false
            },
        )
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

/** 用料表：名称 ······ 用量 */
@Composable
private fun IngredientTable(items: List<Ingredient>, factor: Double) {
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

/** 选倍率：常用倍数直接点，也能自己输，如 1.25 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScaleDialog(current: Double, onDismiss: () -> Unit, onPick: (Double) -> Unit) {
    var custom by remember { mutableStateOf(if (current in presetFactors) "" else AmountScaler.format(current)) }
    val customValue = custom.trim().toDoubleOrNull()?.takeIf { it > 0 && it <= 100 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("换算用量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    onValueChange = { custom = it },
                    label = { Text("自定义倍率") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = custom.isNotBlank() && customValue == null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { customValue?.let(onPick) }, enabled = customValue != null) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
