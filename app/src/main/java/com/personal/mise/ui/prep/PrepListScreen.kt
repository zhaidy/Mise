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
import com.personal.mise.ui.cooking.ModeTabs
import com.personal.mise.ui.cooking.recipeOverviewItems
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.CutPlan
import com.personal.mise.util.DayText
import com.personal.mise.util.StepPhase
import com.personal.mise.util.UnitText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class PrepItem(val key: String, val title: String, val detail: String)

private data class PrepSection(val name: String, val items: List<PrepItem>)

/**
 * 备料清单里的一道菜：某天计划的菜，今日菜单就是今天的计划。
 * key 是计划的 uid；checked 是已勾选的项目，写法见 MealPlan.checked。
 */
private data class PrepSource(val key: String, val recipeId: Long, val title: String, val checked: String)

private data class PrepRecipe(val row: PrepSource, val sections: List<PrepSection>, val cut: CutPlan.Recipe) {
    val allKeys: List<String> = sections.flatMap { s -> s.items.map { it.key } }

    /**
     * 切配汇总里勾的那一项：备料清单里有这样用料就勾同一项，两边一起勾上；
     * 进了调料碗的用料（如蒜末）在清单里没有自己的一项，单记一个「c」，不算进进度
     */
    fun cutKey(ingredientId: Long): String = "i$ingredientId".takeIf { it in allKeys } ?: "c$ingredientId"

    /** 勾选里认得的项目，菜谱改过以后已经没有的项丢掉 */
    fun known(checked: Set<String>): Set<String> =
        checked.filter { it in allKeys || (it.startsWith("c") && cut.ingredients.any { g -> "c${g.id}" == it }) }.toSet()
}

/** 一道菜的备料项：主料、调料碗、没进调料碗的其他调料；用料后面写上步骤里认出来的切法 */
private fun buildPrep(row: PrepSource, full: RecipeFull, factor: Double): PrepRecipe {
    fun amount(text: String) = UnitText.display(AmountScaler.scale(text, factor))
    val cut = CutPlan.Recipe(
        row.key,
        row.title,
        full.ingredients.map { CutPlan.Ingredient(it.id, it.name, amount(it.amountText)) },
        full.steps.map { it.text },
    )
    val methods = CutPlan.methodsById(cut)
    fun detail(id: Long, text: String) = listOfNotNull(amount(text).takeIf { it.isNotBlank() }, methods[id]).joinToString(" · ")
    val inBowls = full.bowls.flatMap { b -> b.items.map { it.ingredientId } }.toSet()
    val mains = full.ingredients.filter { it.kind != IngredientKind.SEASONING && it.id !in inBowls }
        .map { PrepItem("i${it.id}", it.name, detail(it.id, it.amountText)) }
    val cookStart = StepPhase.cookStart(full.steps.map { it.text })
    val bowls = full.bowls.map { b ->
        val stepIndex = full.steps.indexOfFirst { it.id == b.bowl.stepId }
        val detail = b.items.joinToString("、") { "${it.name} ${amount(it.amountText)}".trim() }
        PrepItem("b${b.bowl.id}", b.bowl.label + if (stepIndex >= 0) "（${StepPhase.bowlStepLabel(stepIndex, cookStart, full.steps[stepIndex].text)}）" else "", detail)
    }
    val others = full.ingredients.filter { it.kind == IngredientKind.SEASONING && it.id !in inBowls }
        .map { PrepItem("i${it.id}", it.name, detail(it.id, it.amountText)) }
    return PrepRecipe(
        row,
        listOf(PrepSection("备菜", mains), PrepSection("调料碗", bowls), PrepSection("其他调料", others))
            .filter { it.items.isNotEmpty() },
        cut,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepListScreen(planDate: String?, onBack: () -> Unit, onCook: () -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val plans by app.repository.observePlan().collectAsStateWithLifecycle(emptyList())
    // 没给日期就是今日菜单；有日期时是那天计划的菜，可以提前备，到了那天接着勾
    val date = planDate ?: DayText.today()
    val rows = plans.filter { it.date == date && it.recipeId != null }.map { PrepSource(it.uid, it.recipeId!!, it.title, it.checked) }
    val ids = rows.map { it.recipeId }
    val fulls by remember(ids) {
        if (ids.isEmpty()) flowOf(emptyList<RecipeFull?>())
        else combine(ids.map { app.repository.observeFull(it) }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())
    var showDone by remember { mutableStateOf(false) }
    // 0 按菜备料；1 切配汇总，几道菜要切的合在一起；2 整份菜谱，对照原始用料和步骤，看调料碗有没有配错
    var mode by rememberSaveable { mutableStateOf(0) }

    val recipes = rows.mapNotNull { row ->
        fulls.firstOrNull { it?.recipe?.id == row.recipeId }?.let { buildPrep(row, it, app.scaleOf(row.recipeId)) }
    }
    val total = recipes.sumOf { it.allKeys.size }
    val done = recipes.sumOf { r -> parseChecked(r.row.checked).count { it in r.allKeys } }
    // 计划里的菜只有当天才能开始做
    val canCook = date == DayText.today()

    fun save(recipe: PrepRecipe, next: Set<String>) {
        scope.launch { app.repository.setPlanChecked(recipe.row.key, next) }
    }

    fun toggle(recipe: PrepRecipe, key: String) {
        val current = recipe.known(parseChecked(recipe.row.checked))
        val next = if (key in current) current - key else current + key
        val willBeDone = done - current.count { it in recipe.allKeys } + next.count { it in recipe.allKeys }
        save(recipe, next)
        if (willBeDone == total && total > 0 && key !in current) showDone = true
    }

    val cutItems = remember(recipes.map { it.cut }) { CutPlan.build(recipes.map { it.cut }) }

    fun cutChecked(item: CutPlan.Item): Boolean = item.cuts.all { c ->
        val recipe = recipes.first { it.row.key == c.recipeKey }
        recipe.cutKey(c.ingredientId) in parseChecked(recipe.row.checked)
    }

    /** 切配汇总里勾一样，几道菜里的这样用料一起勾上或取消 */
    fun toggleCut(item: CutPlan.Item) {
        val on = !cutChecked(item)
        var willBeDone = done
        item.cuts.groupBy { it.recipeKey }.forEach { (key, cuts) ->
            val recipe = recipes.first { it.row.key == key }
            val current = recipe.known(parseChecked(recipe.row.checked))
            val keys = cuts.map { recipe.cutKey(it.ingredientId) }.toSet()
            val next = if (on) current + keys else current - keys
            willBeDone += next.count { it in recipe.allKeys } - current.count { it in recipe.allKeys }
            save(recipe, next)
        }
        if (on && willBeDone == total && total > 0) showDone = true
    }

    Scaffold(
        topBar = { MiseHeader("备料清单", eyebrow = planDate?.let { "${DayText.ahead(it)}的计划" }, onBack = onBack) },
        bottomBar = {
            if (canCook) BottomActionBar { PillButton("开始做菜", onCook, Modifier.weight(1f)) }
        },
    ) { padding ->
        if (recipes.isEmpty()) {
            if (planDate == null) {
                EmptyState("备", "今日菜单是空的", "先在菜谱里挑几道菜加入今日菜单。", Modifier.padding(padding))
            } else {
                EmptyState("备", "这天还没排菜", "在「日历」里给这天加几道菜。", Modifier.padding(padding))
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            ModeTabs(listOf("按菜", "切配", "菜谱"), mode, { mode = it })
            if (mode == 2) {
                RecipesOverview(recipes, fulls, Modifier.weight(1f), bottom = padding.calculateBottomPadding())
                return@Column
            }
            if (mode == 1) {
                CutList(cutItems, ::cutChecked, ::toggleCut, Modifier.weight(1f), bottom = padding.calculateBottomPadding())
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
                if (canCook) TextButton(onClick = { showDone = false; onCook() }) { Text("开始做菜") }
                else TextButton(onClick = { showDone = false }) { Text("好的") }
            },
            dismissButton = { if (canCook) TextButton(onClick = { showDone = false }) { Text("再看看") } },
        )
    }
}

/** 切配汇总：几道菜要切的用料合在一起，如「葱：切段 2根、切末 1根」，一次切完 */
@Composable
private fun CutList(
    items: List<CutPlan.Item>,
    checked: (CutPlan.Item) -> Boolean,
    onToggle: (CutPlan.Item) -> Unit,
    modifier: Modifier,
    bottom: Dp,
) {
    if (items.isEmpty()) {
        EmptyState("刀", "没认出要切的", "步骤里写明「切段」「切丝」「剁碎」这类切法，这里就会把几道菜要切的合在一起。", modifier)
        return
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottom + 16.dp),
    ) {
        item(key = "cut-hint") {
            Text(
                "从步骤里认出的切法，几道菜的同一样用料合在一起，一次切完。勾上的在「按菜」里也会勾上。",
                Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(key = "cut-card") {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    items.forEach { item ->
                        // 只有一道菜用时，菜名写在后面就够；几道菜用时先写合计，再分菜列
                        val detail = if (item.cuts.size == 1) item.detail[0] else item.summary + "\n" + item.detail.joinToString("、")
                        CheckRow(
                            title = item.name,
                            detail = detail,
                            checked = checked(item),
                            onToggle = { onToggle(item) },
                            accent = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
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

