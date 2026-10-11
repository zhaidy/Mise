package com.personal.mise.ui.edit

import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.mise.ai.AiProvider
import com.personal.mise.ai.GeminiClient
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
    var cellularFallback by rememberSaveable { mutableStateOf(settings.cellularFallback) }
    // 勾选的模型按保存的顺序排在前面，没勾的排在后面
    var geminiEnabled by rememberSaveable { mutableStateOf(settings.geminiModels) }
    var geminiOrder by rememberSaveable { mutableStateOf(geminiEnabled + GeminiClient.MODELS.map { it.first }.filter { it !in geminiEnabled }) }
    var deepSeekFallback by rememberSaveable { mutableStateOf(settings.deepSeekFallback) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AiKeyFields(provider, { provider = it }, key, { keys = keys + (provider to it) })
                if (provider == AiProvider.GEMINI) {
                    Spacer(Modifier.height(12.dp))
                    GeminiFallbackFields(
                        order = geminiOrder,
                        enabled = geminiEnabled,
                        onOrderChange = { geminiOrder = it },
                        onEnabledChange = { geminiEnabled = it },
                        deepSeekFallback = deepSeekFallback,
                        onDeepSeekFallbackChange = { deepSeekFallback = it },
                        hasDeepSeekKey = keys.getValue(AiProvider.DEEPSEEK).isNotBlank(),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wi-Fi 连不上时改用移动数据", style = MaterialTheme.typography.bodyMedium)
                        Text("会用掉一些流量，漫游时注意费用", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = cellularFallback, onCheckedChange = { cellularFallback = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank(),
                onClick = {
                    // 切换服务商时填过的 Key 都保存，比如在 DeepSeek 下填了 Key 再切回 Gemini
                    keys.forEach { (p, k) -> settings.saveKey(p, k) }
                    settings.provider = provider
                    settings.cellularFallback = cellularFallback
                    settings.geminiModels = geminiOrder.filter { it in geminiEnabled }
                    settings.deepSeekFallback = deepSeekFallback
                    onConfirm()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** Gemini 依次尝试哪些模型、什么顺序，都用完后要不要改用 DeepSeek */
@Composable
private fun GeminiFallbackFields(
    order: List<String>,
    enabled: List<String>,
    onOrderChange: (List<String>) -> Unit,
    onEnabledChange: (List<String>) -> Unit,
    deepSeekFallback: Boolean,
    onDeepSeekFallbackChange: (Boolean) -> Unit,
    hasDeepSeekKey: Boolean,
) {
    val labels = GeminiClient.MODELS.toMap()
    Text("免费额度用完时依次换用", style = MaterialTheme.typography.bodyMedium)
    Text("每个模型的免费额度分开算，从上往下试；点箭头调整顺序", style = MaterialTheme.typography.bodySmall)
    order.forEachIndexed { i, model ->
        val checked = model in enabled
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                // 至少留一个
                enabled = !checked || enabled.size > 1,
                onCheckedChange = { onEnabledChange(if (it) enabled + model else enabled - model) },
            )
            Text(labels[model] ?: model, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(
                enabled = i > 0,
                onClick = { onOrderChange(order.toMutableList().apply { add(i - 1, removeAt(i)) }) },
            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移") }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("都用完后改用 DeepSeek", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (hasDeepSeekKey) "扣 DeepSeek 的预付余额，不会自动扣费" else "还没填 DeepSeek Key，点上面的 DeepSeek 填写",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = deepSeekFallback, onCheckedChange = onDeepSeekFallbackChange)
    }
}

/** 选 AI 服务商 + 填 Key 的输入框，AI 设置和「AI 整理笔记」共用 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiKeyFields(
    provider: AiProvider,
    onProviderChange: (AiProvider) -> Unit,
    key: String,
    onKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
