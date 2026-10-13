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
 * AI 想这样改：[summary] 是 AI 的一句话说明，[changes] 是对比前后列出的每一处改动。
 * 这时还没改，[onConfirm] 才改，[onCancel] 保持原样。
 */
@Composable
fun AiChangesDialog(summary: String, changes: List<String>, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("AI 想这样改") },
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
                Text("还没改，确定后才会改；取消就保持原样。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定修改") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}
