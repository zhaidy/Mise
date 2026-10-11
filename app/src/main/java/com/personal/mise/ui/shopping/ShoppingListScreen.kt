package com.personal.mise.ui.shopping

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.RecipeFull
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.personal.mise.ui.common.CheckRow
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.EmptyState
import com.personal.mise.ui.common.MiseHeader
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.ShoppingList
import com.personal.mise.util.UnitText
import com.personal.mise.util.DayText
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(planFrom: String?, planDays: Int, onBack: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val todayRows by app.repository.observeToday().collectAsStateWithLifecycle(emptyList())
    val plans by app.repository.observePlan().collectAsStateWithLifecycle(emptyList())
    var days by rememberSaveable { mutableIntStateOf(planDays) }
    // 有起始日期时是计划里这几天的菜；同一道菜排了几天就买几份，明细里写上哪天
    val rows = if (planFrom == null) {
        todayRows.map { BuyFor(it.recipeId, it.title) }
    } else {
        val dates = DayText.days(planFrom, days)
        plans.filter { it.date in dates && it.recipeId != null }
            .map { BuyFor(it.recipeId!!, if (days > 1) "${it.title}·${DayText.weekday(it.date)}" else it.title) }
    }
    val ids = rows.map { it.recipeId }.distinct()
    val fulls by remember(ids) {
        if (ids.isEmpty()) flowOf(emptyList<RecipeFull?>())
        else combine(ids.map { app.repository.observeFull(it) }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())
    val have by app.pantry.have.collectAsStateWithLifecycle()

    val sources = rows.flatMap { row ->
        val full = fulls.firstOrNull { it?.recipe?.id == row.recipeId } ?: return@flatMap emptyList()
        val factor = app.scaleOf(row.recipeId)
        full.ingredients.sortedBy { it.order }.map {
            ShoppingList.Source(row.label, it.name, UnitText.display(AmountScaler.scale(it.amountText, factor)), it.kind)
        }
    }
    val items = ShoppingList.build(sources)
    val (owned, toBuy) = items.partition { it.key in have }

    Scaffold(
        topBar = {
            MiseHeader("购物清单", eyebrow = planFrom?.let { planLabel(it, days) }, onBack = onBack) {
                if (toBuy.isNotEmpty()) {
                    CircleIconButton(
                        MiseIcons.Share,
                        "发送要买的",
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, ShoppingList.shareText(toBuy))
                            context.startActivity(Intent.createChooser(send, "发送购物清单"))
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(Modifier.padding(padding)) {
                if (planFrom != null) DaysSwitch(days, { days = it }, Modifier.padding(horizontal = 20.dp))
                EmptyState(
                    "购",
                    when {
                        rows.isNotEmpty() -> "还没有用料"
                        planFrom != null -> "这几天还没排菜"
                        else -> "今日菜单是空的"
                    },
                    when {
                        rows.isNotEmpty() -> "这些菜还没有填用料。"
                        planFrom != null -> "在「计划」里给这几天加几道菜。"
                        else -> "先在菜谱里挑几道菜加入今日菜单。"
                    },
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (planFrom != null) {
                item(key = "days") { DaysSwitch(days, { days = it }) }
            }
            item(key = "hint") {
                Text(
                    if (toBuy.isEmpty()) "都有了，不用买" else "要买 ${toBuy.size} 样。家里有的勾上，会挪到下面，下次也记得。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (toBuy.isNotEmpty()) {
                item(key = "buy") {
                    ShoppingGroup("要买", MaterialTheme.colorScheme.primary) {
                        toBuy.forEach { item ->
                            key(item.key) { ShoppingRow(item, owned = false, accent = MaterialTheme.colorScheme.primary) { app.pantry.toggle(item.key) } }
                        }
                    }
                }
            }
            if (owned.isNotEmpty()) {
                item(key = "have") {
                    ShoppingGroup(
                        "家里有",
                        MaterialTheme.colorScheme.secondary,
                        action = { TextButton(onClick = { app.pantry.clear(owned.map { it.key }) }) { Text("全部取消") } },
                    ) {
                        owned.forEach { item ->
                            key(item.key) { ShoppingRow(item, owned = true, accent = MaterialTheme.colorScheme.secondary) { app.pantry.toggle(item.key) } }
                        }
                    }
                }
            }
        }
    }
}

/** 要买菜的一道菜；label 是明细里写的菜名，计划跨几天时带上星期 */
private data class BuyFor(val recipeId: Long, val label: String)

/** 「明天」「10月11日起 7 天」 */
private fun planLabel(from: String, days: Int): String =
    if (days == 1) "${DayText.ahead(from)}的计划" else "${DayText.ahead(from)}起 $days 天的计划"

/** 计划的买菜清单可以切换看几天 */
@Composable
private fun DaysSwitch(days: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 3, 7).forEach { n ->
            FilterChip(
                selected = days == n,
                onClick = { onChange(n) },
                label = { Text(if (n == 1) "这一天" else "$n 天") },
            )
        }
    }
}

/** 一组清单：卡片里一个小标题，下面是勾选行 */
@Composable
private fun ShoppingGroup(
    title: String,
    accent: Color,
    action: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                action()
            }
            content()
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingList.Item, owned: Boolean, accent: Color, onToggle: () -> Unit) {
    CheckRow(
        title = item.name + if (item.seasoning) "（调料）" else "",
        detail = item.detail,
        checked = owned,
        onToggle = onToggle,
        accent = accent,
    )
}
