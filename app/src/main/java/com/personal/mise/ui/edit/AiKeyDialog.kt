package com.personal.mise.ui.edit

import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.mise.ai.AiProvider
import com.personal.mise.ui.common.rememberApp

/**
 * 选用哪家的 AI 并填写它的 API Key；已经填过的 Key 会带出来，可以直接切换。
 * 确认后保存，再由调用方继续之前的 AI 操作。
 */
@Composable
fun AiKeyDialog(confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val settings = rememberApp().aiSettings
    var provider by rememberSaveable { mutableStateOf(settings.provider) }
    var keys by remember { mutableStateOf(AiProvider.entries.associateWith { settings.key(it) }) }
    val key = keys.getValue(provider)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 设置") },
        text = {
            AiKeyFields(provider, { provider = it }, key, { keys = keys + (provider to it) })
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank(),
                onClick = {
                    settings.saveKey(provider, key)
                    settings.provider = provider
                    onConfirm()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 选 AI 服务商 + 填 Key 的输入框，AI 设置和「AI 整理笔记」共用 */
@Composable
fun AiKeyFields(
    provider: AiProvider,
    onProviderChange: (AiProvider) -> Unit,
    key: String,
    onKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach {
                FilterChip(selected = it == provider, onClick = { onProviderChange(it) }, label = { Text(it.label) })
            }
        }
        Text(
            "${provider.label} API Key：${provider.keyHelp}，只保存在这台手机上。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            shape = FieldShape,
            colors = miseFieldColors(),
            value = key,
            onValueChange = onKeyChange,
            singleLine = true,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
