package com.personal.mise.ui.prep

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import com.personal.mise.ui.common.BottomActionBar
import com.personal.mise.ui.common.CheckRow
import com.personal.mise.ui.common.EmptyState
import com.personal.mise.ui.common.MiseHeader
import com.personal.mise.ui.common.PillButton
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.ui.theme.SerifFamily
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeFull
import com.personal.mise.data.parseChecked
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.cooking.ModeSwitch
import com.personal.mise.ui.cooking.recipeOverviewItems
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.DayText
import com.personal.mise.util.StepPhase
import com.personal.mise.util.UnitText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class PrepItem(val key: String, val title: String, val detail: String)

private data class PrepSection(val name: String, val items: List<PrepItem>)

/**
 * 备料清单里的一道菜：来自今日菜单，或者某天的计划。
 * key 在清单里区分各道菜；checked 是已勾选的项目，写法见 TodayMenu.bowlsChecked。
 */
private data class PrepSource(val key: String, val recipeId: Long, val title: String, val checked: String)

private data class PrepRecipe(val row: PrepSource, val sections: List<PrepSection>) {
    val allKeys: List<String> get() = sections.flatMap { s -> s.items.map { it.key } }
}

/** 一道菜的备料项：主料、调料碗、没进调料碗的其他调料 */
private fun buildPrep(row: PrepSource, full: RecipeFull, factor: Double): PrepRecipe {
    fun amount(text: String) = UnitText.display(AmountScaler.scale(text, factor))
    val inBowls = full.bowls.flatMap { b -> b.items.map { it.ingredientId } }.toSet()
    val mains = full.ingredients.filter { it.kind != IngredientKind.SEASONING && it.id !in inBowls }
        .map { PrepItem("i${it.id}", it.name, amount(it.amountText)) }
    val cookStart = StepPhase.cookStart(full.steps.map { it.text })
    val bowls = full.bowls.map { b ->
        val stepIndex = full.steps.indexOfFirst { it.id == b.bowl.stepId }
        val detail = b.items.joinToString("、") { "${it.name} ${amount(it.amountText)}".trim() }
        PrepItem("b${b.bowl.id}", b.bowl.label + if (stepIndex >= 0) "（${StepPhase.bowlStepLabel(stepIndex, cookStart, full.steps[stepIndex].text)}）" else "", detail)
    }
    val others = full.ingredients.filter { it.kind == IngredientKind.SEASONING && it.id !in inBowls }
        .map { PrepItem("i${it.id}", it.name, amount(it.amountText)) }
    return PrepRecipe(
        row,
        listOf(PrepSection("备菜", mains), PrepSection("调料碗", bowls), PrepSection("其他调料", others))
            .filter { it.items.isNotEmpty() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepListScreen(planDate: String?, onBack: () -> Unit, onCook: () -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val todayRows by app.repository.observeToday().collectAsStateWithLifecycle(emptyList())
    val plans by app.repository.observePlan().collectAsStateWithLifecycle(emptyList())
    // 有日期时是那天计划的菜，可以提前备；勾选记在计划里，放进今日菜单时一起带过去
    val rows = if (planDate == null) {
        todayRows.map { PrepSource("t${it.recipeId}", it.recipeId, it.title, it.bowlsChecked) }
    } else {
        plans.filter { it.date == planDate && it.recipeId != null }.map { PrepSource(it.uid, it.recipeId!!, it.title, it.checked) }
    }
    val ids = rows.map { it.recipeId }
    val fulls by remember(ids) {
        if (ids.isEmpty()) flowOf(emptyList<RecipeFull?>())
        else combine(ids.map { app.repository.observeFull(it) }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())
    var showDone by remember { mutableStateOf(false) }
    // 切到整份菜谱，对照原始用料和步骤，看调料碗有没有配错
    var showRecipe by rememberSaveable { mutableStateOf(false) }

    val recipes = rows.mapNotNull { row ->
        fulls.firstOrNull { it?.recipe?.id == row.recipeId }?.let { buildPrep(row, it, app.scaleOf(row.recipeId)) }
    }
    val total = recipes.sumOf { it.allKeys.size }
    val done = recipes.sumOf { r -> parseChecked(r.row.checked).count { it in r.allKeys } }
    // 计划里的菜只有当天才能直接开始做：先放进今日菜单
    val canCook = planDate == null || planDate == DayText.today()
    val cook: () -> Unit = {
        if (planDate == null) onCook() else scope.launch { app.repository.planToToday(planDate); onCook() }
    }

    fun toggle(recipe: PrepRecipe, key: String) {
        val current = parseChecked(recipe.row.checked).filter { it in recipe.allKeys }.toSet()
        val next = if (key in current) current - key else current + key
        val willBeDone = done - current.size + next.size
        scope.launch {
            if (planDate == null) app.repository.setChecked(recipe.row.recipeId, next)
            else app.repository.setPlanChecked(recipe.row.key, next)
        }
        if (willBeDone == total && total > 0 && key !in current) showDone = true
    }

    Scaffold(
        topBar = { MiseHeader("备料清单", eyebrow = planDate?.let { "${DayText.ahead(it)}的计划" }, onBack = onBack) },
        bottomBar = {
            if (canCook) BottomActionBar { PillButton("开始做菜", cook, Modifier.weight(1f)) }
        },
    ) { padding ->
        if (recipes.isEmpty()) {
            if (planDate == null) {
                EmptyState("备", "今日菜单是空的", "先在菜谱里挑几道菜加入今日菜单。", Modifier.padding(padding))
            } else {
                EmptyState("备", "这天还没排菜", "在「计划」里给这天加几道菜。", Modifier.padding(padding))
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            ModeSwitch(showRecipe, { showRecipe = it }, mainLabel = "备料")
            if (showRecipe) {
                RecipesOverview(recipes, fulls, Modifier.weight(1f), bottom = padding.calculateBottomPadding())
                return@Column
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "progress") { ProgressCard(done, total) }
                recipes.forEach { recipe ->
                    val checked = parseChecked(recipe.row.checked)
                    item(key = "recipe-${recipe.row.key}") {
                        SoftCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(recipe.row.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
                                    app.scaleOf(recipe.row.recipeId).takeIf { it != 1.0 }?.let {
                                        Text(
                                            " ${AmountScaler.label(it)}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${checked.count { it in recipe.allKeys }} / ${recipe.allKeys.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                recipe.sections.forEach { section ->
                                    val accent = when (section.name) {
                                        "调料碗" -> MaterialTheme.colorScheme.tertiary
                                        "备菜" -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    Text(
                                        section.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = accent,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                    )
                                    section.items.forEach { prep ->
                                        CheckRow(
                                            title = prep.title,
                                            detail = prep.detail,
                                            checked = prep.key in checked,
                                            onToggle = { toggle(recipe, prep.key) },
                                            accent = accent,
                                            bold = section.name == "调料碗",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDone) {
        AlertDialog(
            onDismissRequest = { showDone = false },
            title = { Text("都备好了") },
            text = { Text(if (canCook) "所有用料和调料碗都准备好了，开始做菜吧！" else "所有用料和调料碗都准备好了，到那天直接开火。") },
            confirmButton = {
                if (canCook) TextButton(onClick = { showDone = false; cook() }) { Text("开始做菜") }
                else TextButton(onClick = { showDone = false }) { Text("好的") }
            },
            dismissButton = { if (canCook) TextButton(onClick = { showDone = false }) { Text("再看看") } },
        )
    }
}

/** 今日菜单里每道菜的完整菜谱：用料、调料碗、步骤，用量按换算倍率显示 */
@Composable
private fun RecipesOverview(recipes: List<PrepRecipe>, fulls: List<RecipeFull?>, modifier: Modifier, bottom: Dp) {
    val app = rememberApp()
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(top = 8.dp, bottom = bottom + 24.dp)) {
        recipes.forEachIndexed { index, recipe ->
            val full = fulls.firstOrNull { it?.recipe?.id == recipe.row.recipeId } ?: return@forEachIndexed
            val factor = app.scaleOf(recipe.row.recipeId)
            item(key = "title-${recipe.row.key}") {
                Column {
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                        Text(recipe.row.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
                        factor.takeIf { it != 1.0 }?.let {
                            Text(
                                " ${AmountScaler.label(it)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            recipeOverviewItems(
                data = full,
                factor = factor,
                cookStart = StepPhase.cookStart(full.steps.map { it.text }),
                keyPrefix = "r${recipe.row.key}-",
            )
        }
    }
}

/** 顶部的深色进度卡：圆环 + 已备好 / 总数 */
@Composable
private fun ProgressCard(done: Int, total: Int) {
    val colors = LocalMiseColors.current
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, label = "prep")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = colors.ink, contentColor = colors.onInk) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = LocalMiseColors.current.covers[2].background,
                    trackColor = colors.onInkMuted.copy(alpha = 0.3f),
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$done", fontFamily = SerifFamily, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Text("/$total", fontFamily = SerifFamily, fontSize = 15.sp, color = colors.onInkMuted, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (done >= total) "都备好了" else "还差 ${total - done} 样",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "切好的菜、配好的调料碗，都勾上就可以开火了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onInkMuted,
                )
            }
        }
    }
}

