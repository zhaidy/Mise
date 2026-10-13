package com.personal.mise.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.mise.ai.Substitute
import com.personal.mise.importer.ImportException
import com.personal.mise.ui.edit.AiKeyDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 点（备料页长按）某样用料要问的：哪道菜、什么用料、按现在份量的用量 */
data class SubstituteAsk(val recipeId: Long, val name: String, val amount: String)

/**
 * 家里没有某样用料时，问 AI 用什么代替、用多少；选一个「这次就这样做」会记进这道菜的批注，
 * 下次打开就能看到。
 */
@Composable
fun SubstituteDialog(ask: SubstituteAsk, onDismiss: () -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<List<Substitute>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsKey by remember { mutableStateOf(!app.aiSettings.hasKey) }
    var attempt by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt, needsKey) {
        if (needsKey) return@LaunchedEffect
        options = null
        error = null
        try {
            val full = app.repository.loadFull(ask.recipeId) ?: throw ImportException("这道菜已经删掉了")
            options = app.substituter.suggest(full, ask.name, ask.amount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? ImportException)?.message ?: "AI 没答上来：${e.message ?: e.javaClass.simpleName}"
        }
    }

    if (needsKey) {
        AiKeyDialog(confirmLabel = "保存并问", onDismiss = onDismiss, onConfirm = { needsKey = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("没有「${ask.name}」怎么办") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ask.amount.isNotBlank()) {
                    Text("菜谱里要 ${ask.amount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val list = options
                when {
                    saved != null -> Text("已记进批注：$saved", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    list == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("AI 正在想用什么代替…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> list.forEach { option ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp)) {
                                Text(
                                    listOfNotNull(option.name, option.amount.takeIf { it.isNotBlank() }).joinToString("  "),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (option.note.isNotBlank()) {
                                    Text(option.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val line = option.noteLine(ask.name)
                                            val old = app.repository.loadFull(ask.recipeId)?.recipe?.note.orEmpty()
                                            app.repository.setNote(ask.recipeId, listOf(old.trim(), line).filter { it.isNotEmpty() }.joinToString("\n"))
                                            saved = line
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.End),
                                ) { Text("这次就这样做，记进批注") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (saved != null) "好" else "关闭") } },
        dismissButton = {
            if (error != null) TextButton(onClick = { attempt++ }) { Text("重试") }
        },
    )
}
