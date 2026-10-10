package com.personal.mise.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 填写 Gemini API Key；确认后由调用方保存并继续之前的 AI 操作 */
@Composable
fun GeminiKeyDialog(confirmLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var keyInput by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API Key") },
        text = {
            Column {
                Text(
                    "在 aistudio.google.com 免费申请，只保存在这台手机上。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = keyInput, onValueChange = { keyInput = it }, singleLine = true, label = { Text("API Key") })
            }
        },
        confirmButton = {
            TextButton(enabled = keyInput.isNotBlank(), onClick = { onConfirm(keyInput) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
