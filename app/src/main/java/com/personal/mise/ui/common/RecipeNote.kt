package com.personal.mise.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

/** 自己的批注：黄底便签，放在菜谱、做菜页最上面；[onEdit] 为空时只看不改 */
@Composable
fun NoteBanner(note: String, modifier: Modifier = Modifier, onEdit: (() -> Unit)? = null) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = if (onEdit != null) 4.dp else 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("我的批注", style = MaterialTheme.typography.labelMedium)
                Text(note, style = MaterialTheme.typography.bodyLarge)
            }
            if (onEdit != null) TextButton(onClick = onEdit) { Text("改") }
        }
    }
}

/** 写批注；清空保存就是删掉 */
@Composable
fun NoteDialog(current: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的批注") },
        text = {
            Column {
                Text(
                    "记下下次要注意的，比如「盐少放一半」「火开大点」。打开这道菜时会显示在最上面。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_NOTE) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("盐少放一半") },
                    shape = FieldShape,
                    colors = miseFieldColors(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(if (text.isBlank() && !current.isNullOrBlank()) "删掉" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private const val MAX_NOTE = 500
