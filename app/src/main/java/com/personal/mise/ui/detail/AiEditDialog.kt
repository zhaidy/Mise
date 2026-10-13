package com.personal.mise.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors

/** 写一句话让 AI 改菜谱；[busy] 时等 AI 回复，[error] 是上次没改成的原因 */
@Composable
fun AiEditDialog(busy: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("✨ 让 AI 改菜谱") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("比如：改成4人份、不要辣、换成空气炸锅做法") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    maxLines = 4,
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("AI 正在修改…", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        error ?: "改好后直接保存，不满意可以马上撤销",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && text.isNotBlank(), onClick = { onSubmit(text.trim()) }) { Text("让 AI 改") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") }
        },
    )
}

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
