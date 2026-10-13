package com.personal.mise.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * AI 改完以后列出改了什么：[summary] 是 AI 的一句话说明，[changes] 是对比前后列出的每一处改动。
 * [onUndo] 撤回到改之前，[onKeep] 留着改好的。
 */
@Composable
fun AiChangesDialog(summary: String, changes: List<String>, onUndo: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("AI 改了这些") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyLarge)
                changes.forEach { line ->
                    Row {
                        Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 6.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("已经改好了；不满意可以撤销，回到改之前的样子。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onKeep) { Text("就这样") } },
        dismissButton = { TextButton(onClick = onUndo) { Text("撤销") } },
    )
}
