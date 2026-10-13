package com.personal.mise.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.mise.chat.CardStatus
import com.personal.mise.chat.ChatCard
import com.personal.mise.ui.common.RecipeReviewCard
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.ui.theme.SerifFamily
import com.personal.mise.ui.theme.coverFor
import com.personal.mise.util.DayText

@Composable
fun ChatCardView(
    card: ChatCard,
    busy: Boolean,
    canUndo: Boolean,
    recipeChat: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onImport: (String, Boolean) -> Unit,
    onFix: () -> Unit,
) {
    when (card) {
        is ChatCard.Proposal -> ProposalCard(card, busy, canUndo, onApply, onDismiss, onUndo)
        is ChatCard.Review -> RecipeReviewCard(
            card.review,
            onFix = if (recipeChat && !busy) onFix else null,
        )
        is ChatCard.Recipes -> RecipesCard(card, onOpenRecipe)
        is ChatCard.Plan -> PlanCard(card, busy, canUndo, onApply, onDismiss, onUndo)
        is ChatCard.Import -> ImportCard(card, onImport)
        is ChatCard.Error -> ErrorCard(card, busy, onRetry)
    }
}

/** 卡片的外框：浅底细边，标题一行 */
@Composable
private fun CardFrame(title: String, tag: String? = null, content: @Composable () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                tag?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** 方案下面的按钮，或者处理过以后的一行状态 */
@Composable
private fun Decision(
    status: CardStatus,
    applyLabel: String,
    doneLabel: String,
    busy: Boolean,
    canUndo: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
) {
    when (status) {
        CardStatus.PENDING -> Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f), shape = CircleShape) { Text("不用了") }
            Button(
                onClick = onApply,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(applyLabel) }
        }
        CardStatus.APPLIED -> Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "✓ $doneLabel",
                Modifier.weight(1f).padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (canUndo) TextButton(onClick = onUndo, enabled = !busy) { Text("撤销") }
        }
        CardStatus.DISMISSED -> StatusLine("没有用这个方案")
        CardStatus.UNDONE -> StatusLine("已撤销")
        CardStatus.EXPIRED -> StatusLine("这个方案已失效，需要的话再让 AI 做一次")
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProposalCard(
    card: ChatCard.Proposal,
    busy: Boolean,
    canUndo: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val tag = if (card.changes.size > 1) "${card.changes.size} 处" else null
    CardFrame(card.title, tag) {
        if (card.summary.isNotBlank()) Text(card.summary, style = MaterialTheme.typography.bodyLarge)
        val shown = if (expanded) card.changes else card.changes.take(COLLAPSED_CHANGES)
        shown.forEach { line ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (card.changes.size > COLLAPSED_CHANGES) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "还有 ${card.changes.size - COLLAPSED_CHANGES} 处，展开")
            }
        }
        Decision(card.status, card.kind.applyLabel, card.kind.doneLabel, busy, canUndo, onApply, onDismiss, onUndo)
    }
}

@Composable
private fun PlanCard(
    card: ChatCard.Plan,
    busy: Boolean,
    canUndo: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
) {
    CardFrame("排进 ${DayText.label(card.date)}", "${card.titles.size} 道菜") {
        card.titles.forEach { RecipeLine(it) }
        Decision(card.status, "排进去", "已排进计划", busy, canUndo, onApply, onDismiss, onUndo)
    }
}

@Composable
private fun RecipesCard(card: ChatCard.Recipes, onOpenRecipe: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        card.ids.zip(card.titles).forEach { (id, title) ->
            SoftCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), onClick = { onOpenRecipe(id) }) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RecipeLine(title, Modifier.weight(1f))
                    Text("看看 ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                }
            }
        }
    }
}

/** 一道菜：菜名首字色块加菜名 */
@Composable
private fun RecipeLine(title: String, modifier: Modifier = Modifier) {
    val colors = LocalMiseColors.current.coverFor(title)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(colors.background, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Text(title.take(1), color = colors.content, fontFamily = SerifFamily, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontFamily = SerifFamily, maxLines = 1)
    }
}

@Composable
private fun ImportCard(card: ChatCard.Import, onImport: (String, Boolean) -> Unit) {
    CardFrame("导入菜谱", if (card.link) "链接" else "笔记") {
        Text(
            card.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(80),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Text(
            if (card.link) "打开导入页，从链接抓下菜谱，用料和步骤可以先看看再存。" else "打开导入页，让 AI 整理成菜名、用料和步骤，看过再存。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onImport(card.text, !card.link) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
            Text(if (card.status == CardStatus.APPLIED) "再打开一次" else "去导入")
        }
    }
}

@Composable
private fun ErrorCard(card: ChatCard.Error, busy: Boolean, onRetry: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(card.message, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            if (card.retry != null || card.action != null) TextButton(onClick = onRetry, enabled = !busy) { Text("重试") }
        }
    }
}

/** AI 回复的文字：「- 」开头的行换成圆点，**粗体** 加粗，# 标题当粗体 */
@Composable
fun ChatText(text: String) {
    Text(chatAnnotated(text), style = MaterialTheme.typography.bodyLarge)
}

fun chatAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        var line = raw
        val heading = Regex("^\\s*#{1,6}\\s+").find(line)
        if (heading != null) line = "**" + line.substring(heading.range.last + 1) + "**"
        val bullet = Regex("^(\\s*)[-*•]\\s+").find(line)
        if (bullet != null) {
            append(bullet.groupValues[1])
            append("• ")
            line = line.substring(bullet.range.last + 1)
        }
        var rest = line
        while (true) {
            val start = rest.indexOf("**")
            val end = if (start >= 0) rest.indexOf("**", start + 2) else -1
            if (start < 0 || end < 0) {
                append(rest)
                break
            }
            append(rest.substring(0, start))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(rest.substring(start + 2, end)) }
            rest = rest.substring(end + 2)
        }
    }
}

private const val COLLAPSED_CHANGES = 6
