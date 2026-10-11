package com.personal.mise.ui.edit

import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personal.mise.ai.AiModel
import com.personal.mise.ai.AiProvider
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.rememberApp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableColumn

/**
 * 填各家 AI 的 API Key，选用哪些模型、按什么顺序；已经填过的 Key 会带出来。
 * 确认后保存，再由调用方继续之前的 AI 操作。
 */
@Composable
fun AiKeyDialog(confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val settings = rememberApp().aiSettings
    // 正在填哪家的 Key，先显示会先用的那家
    var provider by rememberSaveable { mutableStateOf(settings.firstUsable?.provider ?: settings.models.first().provider) }
    var keys by remember { mutableStateOf(AiProvider.entries.associateWith { settings.key(it) }) }
    val key = keys.getValue(provider)
    var cellularFallback by rememberSaveable { mutableStateOf(settings.cellularFallback) }
    // 勾选的模型按保存的顺序排在前面，没勾的排在后面；存的是 AiModel.key
    var enabled by rememberSaveable { mutableStateOf(settings.models.map { it.key }) }
    var order by rememberSaveable { mutableStateOf(enabled + AiModel.ALL.map { it.key }.filter { it !in enabled }) }
    val usable = order.any { it in enabled && AiModel.fromKey(it)?.let { m -> keys.getValue(m.provider).isNotBlank() } == true }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AiKeyFields(provider, { provider = it }, key, { keys = keys + (provider to it) })
                if (provider == AiProvider.DEEPSEEK && key.isNotBlank()) DeepSeekBalance(key.trim())
                Spacer(Modifier.height(12.dp))
                ModelOrderFields(
                    order = order,
                    enabled = enabled,
                    hasKey = { keys.getValue(it).isNotBlank() },
                    onOrderChange = { order = it },
                    onEnabledChange = { enabled = it },
                )
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
                // 至少有一个勾选的模型填了 Key
                enabled = usable,
                onClick = {
                    // 切换服务商时填过的 Key 都保存，比如在 DeepSeek 下填了 Key 再切回 Gemini
                    keys.forEach { (p, k) -> settings.saveKey(p, k) }
                    settings.cellularFallback = cellularFallback
                    settings.models = order.filter { it in enabled }.mapNotNull(AiModel::fromKey)
                    onConfirm()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 用哪些模型、什么顺序：三家的模型放在一张表里，从上往下试 */
@Composable
private fun ModelOrderFields(
    order: List<String>,
    enabled: List<String>,
    hasKey: (AiProvider) -> Boolean,
    onOrderChange: (List<String>) -> Unit,
    onEnabledChange: (List<String>) -> Unit,
) {
    val usage = rememberApp().geminiUsage
    val now = remember { System.currentTimeMillis() }
    Text("AI 模型", style = MaterialTheme.typography.bodyMedium)
    Text(
        "勾选的模型从上往下试，额度用完、余额不足或连不上就换下一个；没填 Key 的会跳过。按住左侧把手拖动调整顺序。" +
            "Google 查不到 Gemini 的剩余额度，次数是这台手机今天用的",
        style = MaterialTheme.typography.bodySmall,
    )
    val models = order.mapNotNull(AiModel::fromKey)
    ReorderableColumn(
        list = models,
        onSettle = { from, to -> onOrderChange(models.map { it.key }.toMutableList().apply { add(to, removeAt(from)) }) },
    ) { _, model, isDragging ->
        key(model.key) {
            val id = model.key
            val checked = id in enabled
            val note = when {
                !hasKey(model.provider) -> "还没填 ${model.provider.label} Key，会跳过"
                model.provider == AiProvider.GEMINI -> geminiUsageText(usage.exhaustedUntil(model.id, now), usage.count(model.id, now))
                else -> null
            }
            Row(
                Modifier.background(
                    if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    MaterialTheme.shapes.small,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}, modifier = Modifier.draggableHandle().size(width = 32.dp, height = 48.dp)) {
                    Icon(MiseIcons.DragDots, contentDescription = "拖动排序", tint = MaterialTheme.colorScheme.outline)
                }
                Checkbox(
                    checked = checked,
                    // 至少留一个
                    enabled = !checked || enabled.size > 1,
                    onCheckedChange = { onEnabledChange(if (it) enabled + id else enabled - id) },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        model.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasKey(model.provider)) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 模型今天的用量：额度用完了就说几点重置，否则说用了几次；还没用过返回 null */
private fun geminiUsageText(exhaustedUntil: Long?, count: Int): String? = when {
    exhaustedUntil != null -> {
        val reset = Instant.ofEpochMilli(exhaustedUntil).atZone(ZoneId.systemDefault())
        "今天额度用完了，${reset.format(DateTimeFormatter.ofPattern("HH:mm"))} 重置"
    }
    count > 0 -> "今天用了 $count 次"
    else -> null
}

/** DeepSeek 账户余额，打开设置或改了 Key 时查一次 */
@Composable
private fun DeepSeekBalance(key: String) {
    val app = rememberApp()
    var text by remember(key) { mutableStateOf("正在查余额…") }
    LaunchedEffect(key) {
        // 正在输入 Key 时等停下来再查
        delay(600)
        text = try {
            app.deepSeek.balance(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "查不到余额：${e.message ?: e.javaClass.simpleName}"
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            "${provider.label} API Key：${provider.keyHelp}，保存在这台手机上；加入了共享厨房的话，厨房里的人共用。",
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
