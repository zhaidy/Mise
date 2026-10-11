package com.personal.mise.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.CookLogRow
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.sp
import com.personal.mise.ui.common.EmptyState
import com.personal.mise.ui.common.MiseHeader
import com.personal.mise.ui.common.RecipeCover
import com.personal.mise.ui.common.SectionHeader
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.util.DayText
import kotlinx.coroutines.launch

/** 做菜记录：哪天做过哪道菜，按月份、日期分组，新的在前 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenRecipe: (Long) -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val logs by app.repository.observeCookLog().collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf<CookLogRow?>(null) }

    Scaffold(
        topBar = {
            MiseHeader("做菜记录", eyebrow = if (logs.isEmpty()) "还没有记录" else "一共做了 ${logs.size} 次")
        },
    ) { padding ->
        if (logs.isEmpty()) {
            EmptyState(
                "记",
                "还没有记录",
                "做菜时翻到最后一步点「做好了」，或者在菜谱详情里点「记一笔」。",
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        val months = logs.groupBy { DayText.month(it.date) }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        ) {
            months.forEach { (month, monthLogs) ->
                item(key = "month-$month") {
                    SectionHeader(
                        month,
                        Modifier.padding(top = 20.dp, bottom = 4.dp),
                    ) {
                        Text(
                            "做了 ${monthLogs.size} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                monthLogs.groupBy { it.date }.forEach { (date, dayLogs) ->
                    item(key = "day-$date") {
                        Text(
                            DayText.label(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 6.dp),
                        )
                    }
                    items(dayLogs, key = { it.uid }) { log ->
                        LogRow(
                            log = log,
                            onClick = log.recipeId?.let { id -> { onOpenRecipe(id) } },
                            onDelete = { confirmDelete = log },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除记录") },
            text = { Text("删除「${DayText.label(log.date)}做了${log.title}」这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { app.repository.deleteCookLog(log.uid) }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}

/** 菜谱已经删掉的记录点不开，菜名旁边注明 */
@Composable
private fun LogRow(log: CookLogRow, onClick: (() -> Unit)?, onDelete: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Row(
            Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecipeCover(log.title, log.coverImage, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), initialSize = 22.sp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    log.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onClick == null) {
                    Text("菜谱已删除", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, contentDescription = "删除记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
