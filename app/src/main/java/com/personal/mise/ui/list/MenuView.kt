package com.personal.mise.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.mise.data.Freshness
import com.personal.mise.data.MenuItem
import com.personal.mise.data.MenuSection
import com.personal.mise.data.Recipe
import com.personal.mise.data.RecipeMenu
import com.personal.mise.data.tagList
import com.personal.mise.ui.common.Eyebrow
import com.personal.mise.ui.common.RecipeCover
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.ui.theme.SerifFamily
import com.personal.mise.util.CalorieText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 两种视图的切换，放在头部右上角；菜谱页是「卡片 / 菜单」，做菜记录是「列表 / 日历」 */
@Composable
internal fun ViewSwitch(second: Boolean, onChange: (Boolean) -> Unit, labels: Pair<String, String> = "卡片" to "菜单") {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(3.dp)) {
            ViewOption(labels.first, selected = !second, onClick = { onChange(false) })
            ViewOption(labels.second, selected = second, onClick = { onChange(true) })
        }
    }
}

@Composable
private fun ViewOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val ink = LocalMiseColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) ink.ink else Color.Transparent,
        contentColor = if (selected) ink.onInk else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(36.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/**
 * 餐厅菜单式的菜谱列表：按分类分区，菜名后面拉一条点线，
 * 原本写价格的地方写上次做的时间，按多久没做着色。
 * menuItem 是每一行外面包的长按菜单，和卡片共用。
 */
@Composable
internal fun MenuList(
    sections: List<MenuSection>,
    modifier: Modifier = Modifier,
    onOpen: (Long) -> Unit,
    menuItem: @Composable (recipe: Recipe, content: @Composable (onLongClick: () -> Unit) -> Unit) -> Unit,
    empty: LazyListScope.() -> Unit,
) {
    val today = remember { LocalDate.now() }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 96.dp)) {
        item(key = "masthead") { Masthead(today) }
        if (sections.isEmpty()) empty()
        sections.forEachIndexed { index, section ->
            item(key = "section-$index") { SectionHeading(section) }
            section.groups.forEachIndexed { g, group ->
                if (group.sub != null) {
                    item(key = "sub-$index-$g") {
                        Text(
                            "—  ${group.sub}  —",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                }
                group.items.forEach { dish ->
                    item(key = dish.recipe.id) {
                        menuItem(dish.recipe) { onLongClick ->
                            DishRow(dish, today, onClick = { onOpen(dish.recipe.id) }, onLongClick = onLongClick)
                        }
                    }
                }
            }
        }
        if (sections.isNotEmpty()) {
            item(key = "footer") {
                Text(
                    "价格栏写的是 · 上次下厨",
                    fontFamily = SerifFamily,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun Masthead(today: LocalDate) {
    val date = remember(today) { today.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)) }
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Eyebrow("M E N U")
        Text(
            "家宴菜单",
            style = MaterialTheme.typography.headlineLarge,
            letterSpacing = 8.sp,
            modifier = Modifier.padding(top = 4.dp, start = 8.dp),
        )
        Text(date, fontFamily = SerifFamily, fontSize = 13.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            Text("◆", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(Freshness.FRESH, "一周内做过")
            LegendDot(Freshness.STALE, "一个多月没做")
            LegendDot(Freshness.NEVER, "还没做过")
        }
    }
}

@Composable
private fun LegendDot(freshness: Freshness, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).background(freshnessColor(freshness), CircleShape))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeading(section: MenuSection) {
    Row(Modifier.padding(top = 26.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(section.name, style = MaterialTheme.typography.titleLarge, letterSpacing = 3.sp)
        Text(
            "${section.count} 道",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DishRow(item: MenuItem, today: LocalDate, onClick: () -> Unit, onLongClick: () -> Unit) {
    val recipe = item.recipe
    val leader = MaterialTheme.colorScheme.outline
    val meta = (recipe.tagList + listOfNotNull(recipe.calories?.let(CalorieText::format))).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左边一张小缩略图，没图就是和卡片一样的纯色首字
        RecipeCover(
            recipe.title,
            recipe.coverImage,
            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
            initialSize = 20.sp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                // 点线铺满菜名这一格，菜名带底色盖住前半截，长菜名就省略号截断
                Box(
                    Modifier.weight(1f).drawBehind {
                        val y = size.height - 7.dp.toPx()
                        drawLine(
                            leader,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx())),
                        )
                    },
                ) {
                    Text(
                        recipe.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(end = 8.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                val never = item.freshness == Freshness.NEVER
                Text(
                    RecipeMenu.whenText(item, today),
                    fontFamily = SerifFamily,
                    fontSize = if (never) 14.sp else 15.sp,
                    fontWeight = if (never) FontWeight.Medium else FontWeight.Bold,
                    fontStyle = if (never) FontStyle.Italic else FontStyle.Normal,
                    color = freshnessColor(item.freshness),
                    maxLines = 1,
                )
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun freshnessColor(freshness: Freshness): Color = when (freshness) {
    Freshness.FRESH -> MaterialTheme.colorScheme.secondary
    Freshness.NORMAL -> MaterialTheme.colorScheme.onSurface
    Freshness.STALE -> MaterialTheme.colorScheme.tertiary
    Freshness.NEVER -> MaterialTheme.colorScheme.onSurfaceVariant
}
