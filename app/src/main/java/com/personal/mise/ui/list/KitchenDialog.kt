package com.personal.mise.ui.list

import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.ui.common.rememberApp
import kotlinx.coroutines.launch

/** 新建、加入、退出共享厨房 */
@Composable
fun KitchenDialog(onDismiss: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val code by app.kitchen.kitchenCode.collectAsStateWithLifecycle()
    val syncError by app.kitchen.error.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    fun run(action: suspend () -> Unit) {
        busy = true
        message = null
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                message = e.message ?: "出错了"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("共享厨房") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val current = code
                if (current == null) {
                    Text("加入同一个厨房的手机共用菜谱，一边改了另一边自动更新。本机已有的菜谱也会合并进去。")
                    OutlinedTextField(
                        shape = FieldShape,
                        colors = miseFieldColors(),
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("对方给的邀请码") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy && input.isNotBlank(), onClick = { run { app.kitchen.join(input) } }) {
                            Text("加入")
                        }
                        OutlinedButton(enabled = !busy, onClick = { run { app.kitchen.create() } }) {
                            Text("新建一个厨房")
                        }
                    }
                } else {
                    Text("已加入共享厨房。让对方在这里输入邀请码：")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(current, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("邀请码", current))
                            message = "已复制"
                        }) { Text("复制") }
                    }
                    syncError?.let { Text("同步出错：$it", color = MaterialTheme.colorScheme.error) }
                    TextButton(enabled = !busy, onClick = { confirmLeave = true }) { Text("退出共享") }
                }
                if (busy) Text("请稍候…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("关闭") } },
    )

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("退出共享厨房？") },
            text = { Text("本机的菜谱都会留着，只是之后不再和对方同步。") },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; app.kitchen.leave() }) { Text("退出") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("取消") } },
        )
    }
}
