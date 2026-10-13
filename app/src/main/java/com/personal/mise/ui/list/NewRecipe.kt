package com.personal.mise.ui.list

import com.personal.mise.ui.edit.AiKeyDialog
import com.personal.mise.ai.AiRecipeOrganizer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.personal.mise.ai.AiProvider
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.edit.AiKeyFields

/** 新建菜谱的几种办法；放在「问问 AI」里，原来是首页右下角的「新菜谱」菜单 */
enum class NewRecipeWay { GENERATE, XIACHUFANG, LINK, NOTE }

/**
 * 让 AI 写菜谱、搜下厨房、粘贴链接、AI 整理笔记的对话框；
 * 选好后调 [onImport]（文字, 是不是交给 AI 整理笔记），让 AI 写的调 [onGenerate]（要求）
 */
@Composable
fun NewRecipeDialog(
    way: NewRecipeWay,
    onDismiss: () -> Unit,
    onImport: (text: String, aiNote: Boolean) -> Unit,
    onGenerate: (request: String) -> Unit,
) {
    val app = rememberApp()
    when (way) {
        NewRecipeWay.GENERATE -> GenerateRecipeDialog(onDismiss = onDismiss, onConfirm = onGenerate)
        NewRecipeWay.XIACHUFANG -> XcfSearchDialog(onDismiss = onDismiss, onPick = { onImport(it, false) })
        NewRecipeWay.LINK -> PasteLinkDialog(onDismiss = onDismiss, onConfirm = { onImport(it, false) })
        NewRecipeWay.NOTE -> AiNoteDialog(
            needsKey = !app.aiSettings.hasKey,
            initialProvider = app.aiSettings.models.first().provider,
            onDismiss = onDismiss,
            onConfirm = { note, provider, key ->
                if (key.isNotBlank()) {
                    app.aiSettings.saveKey(provider, key)
                    app.aiSettings.enableProvider(provider)
                }
                onImport(note, true)
            },
        )
    }
}

@Composable
private fun PasteLinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        mutableStateOf(if (pasted.contains("http")) pasted else "")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴下厨房或小红书链接") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("https://www.xiachufang.com/recipe/…") },
                minLines = 2,
                shape = FieldShape,
                colors = miseFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 粘贴手机笔记里的菜谱文字，交给 AI 整理；还没填 Key 时一起选 AI、填 Key */
@Composable
private fun AiNoteDialog(
    needsKey: Boolean,
    initialProvider: AiProvider,
    onDismiss: () -> Unit,
    onConfirm: (note: String, provider: AiProvider, key: String) -> Unit,
) {
    val context = LocalContext.current
    var text by remember {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        mutableStateOf(if (UrlExtractor.isLinkShare(pasted)) "" else pasted)
    }
    var provider by remember { mutableStateOf(initialProvider) }
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 整理笔记") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "把笔记里的菜谱整段粘贴进来，格式乱也没关系，AI 会整理出菜名、用料和步骤。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("例如：红烧肉 五花肉一斤切块，冷水下锅焯水，冰糖炒糖色…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                    shape = FieldShape,
                    colors = miseFieldColors(),
                )
                if (needsKey) {
                    Spacer(Modifier.height(12.dp))
                    AiKeyFields(provider, { provider = it }, key, { key = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text, provider, key) },
                enabled = text.isNotBlank() && (!needsKey || key.isNotBlank()),
            ) { Text("整理") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 想做什么菜点一下就能填进去的例子 */
private val generateExamples = listOf("番茄牛腩 3人份", "鸡胸肉低脂快手菜", "土豆和鸡蛋能做的下饭菜", "不辣的家常豆腐", "20 分钟搞定的早餐")

/** 让 AI 写一份新菜谱：说想做什么，几人份、口味、忌口都可以一起写 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenerateRecipeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val app = rememberApp()
    var text by rememberSaveable { mutableStateOf("") }
    var needsKey by remember { mutableStateOf(false) }
    if (needsKey) {
        AiKeyDialog(confirmLabel = "保存并生成", onDismiss = { needsKey = false }, onConfirm = { needsKey = false; onConfirm(text.trim()) })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("让 AI 写菜谱") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "说说想做什么：菜名、手边的用料、几人份、口味和忌口都行。AI 写好后先给你看，检查过再存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(AiRecipeOrganizer.MAX_REQUEST) },
                    placeholder = { Text("例如：番茄牛腩，3 人份，少油") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = FieldShape,
                    colors = miseFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    generateExamples.forEach { example ->
                        FilterChip(selected = text == example, onClick = { text = example }, label = { Text(example) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (app.aiSettings.hasKey) onConfirm(text.trim()) else needsKey = true },
                enabled = text.isNotBlank(),
            ) { Text("生成") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
