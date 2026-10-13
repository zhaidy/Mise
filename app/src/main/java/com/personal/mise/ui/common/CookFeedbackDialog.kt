package com.personal.mise.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personal.mise.ai.NoteUpdate
import com.personal.mise.importer.ImportException
import com.personal.mise.ui.edit.AiKeyDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 点一下就能写进去的常见感受 */
private val quickFeelings = listOf("偏咸", "偏淡", "太甜", "太油", "火候过了", "没熟透", "很成功")

/**
 * 做完一道菜写一句感受，AI 整理进批注：先给看整理好的批注，确定了才存。
 * 跟某一步有关的写成「第 N 步：…」，下次做到那一步时会提醒。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CookFeedbackDialog(recipeId: Long, title: String, onDismiss: () -> Unit, onSaved: (String) -> Unit = {}) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<NoteUpdate?>(null) }
    var needsKey by remember { mutableStateOf(false) }

    fun organize() {
        if (!app.aiSettings.hasKey) {
            needsKey = true
            return
        }
        working = true
        error = null
        scope.launch {
            try {
                val full = app.repository.loadFull(recipeId) ?: throw ImportException("这道菜已经删掉了")
                val result = app.cookFeedback.merge(full, text)
                if (result.note.trim() == full.recipe.note.orEmpty().trim()) {
                    error = result.summary.ifBlank { "AI 看了，觉得批注不用改" }
                } else {
                    update = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e as? ImportException)?.message ?: "AI 整理失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                working = false
            }
        }
    }

    if (needsKey) {
        AiKeyDialog(confirmLabel = "保存并整理", onDismiss = { needsKey = false }, onConfirm = { needsKey = false; organize() })
        return
    }

    val proposed = update
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (proposed == null) "「$title」做得怎么样？" else "整理好的批注") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (proposed == null) {
                    Text(
                        "写一句感受，AI 会整理成下次要注意的，记进批注；跟哪一步有关的，下次做到那一步会提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(200) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                        placeholder = { Text("偏咸，鸡腿多炖了 5 分钟才熟") },
                        shape = FieldShape,
                        colors = miseFieldColors(),
                        enabled = !working,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickFeelings.forEach { feeling ->
                            FilterChip(
                                selected = feeling in text,
                                onClick = { if (feeling !in text) text = listOf(text.trim(), feeling).filter { it.isNotEmpty() }.joinToString("，") },
                                label = { Text(feeling) },
                                enabled = !working,
                            )
                        }
                    }
                    if (working) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("AI 正在整理…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                } else {
                    if (proposed.summary.isNotBlank()) {
                        Text(proposed.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (proposed.note.isBlank()) Text("（批注会清空）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else NoteBanner(proposed.note)
                }
            }
        },
        confirmButton = {
            if (proposed == null) {
                TextButton(onClick = { organize() }, enabled = text.isNotBlank() && !working) { Text("整理进批注") }
            } else {
                TextButton(onClick = {
                    scope.launch {
                        app.repository.setNote(recipeId, proposed.note)
                        onSaved(proposed.summary.ifBlank { "批注已更新" })
                        onDismiss()
                    }
                }) { Text("存进批注") }
            }
        },
        dismissButton = {
            if (proposed == null) TextButton(onClick = onDismiss) { Text("跳过") }
            else TextButton(onClick = { update = null }) { Text("再改改") }
        },
    )
}
