package com.personal.mise.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.mise.ai.RecipeReview

/**
 * AI 对菜谱的评价：分数、一句话总评、亮点和要注意的地方。
 * [review] 为空时只显示 [busy] 的进度或 [message]；[onRefresh] 为空时没有「重新评价」按钮。
 * [onFix] 不为空、评价里有要注意的地方时，显示「照评价修改」；[fixing] 时 AI 正在改。
 */
@Composable
fun RecipeReviewCard(
    review: RecipeReview?,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    message: String? = null,
    onRefresh: (() -> Unit)? = null,
    fixing: Boolean = false,
    onFix: (() -> Unit)? = null,
) {
    SoftCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨ AI 口味评价", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (review != null) {
                    Text(
                        "  ${review.score}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(" / 10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else if (onRefresh != null) {
                        TextButton(onClick = onRefresh) { Text(if (review == null) "AI 评价" else "重新评价") }
                    }
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    review != null -> {
                        Text(review.summary, style = MaterialTheme.typography.bodyLarge)
                        review.highlights.forEach { ReviewLine("👍", it) }
                        review.concerns.forEach { ReviewLine("⚠️", it) }
                    }
                    busy -> Text("正在让 AI 尝尝这份菜谱的味道…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text(
                        message ?: "还没评价",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (review != null && review.concerns.isNotEmpty() && onFix != null) {
                    if (fixing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("AI 正在照评价修改…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onFix, enabled = !busy) { Text("✨ 照评价修改") }
                            review.improvedScore?.let {
                                Text(
                                    "改完约 $it 分（+${it - review.score}）",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                if (review != null && message != null && !busy) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReviewLine(mark: String, text: String) {
    Row {
        Text(mark, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
