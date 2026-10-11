package com.personal.mise.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.mise.ui.common.rememberApp
import kotlinx.coroutines.launch

/** 手动新建（recipeId = 0）或编辑菜谱 */
@Composable
fun RecipeEditScreen(recipeId: Long, onDone: (savedId: Long?) -> Unit) {
    val app = rememberApp()
    val vm: EditorViewModel = viewModel(key = "edit-$recipeId") { EditorViewModel(app, recipeId, null) }
    EditorScaffold(
        vm = vm,
        title = if (recipeId == 0L) "新建菜谱" else "编辑菜谱",
        saveLabel = "保存",
        discardMessage = "放弃这次修改吗？",
        onDone = onDone,
    )
}

/** 导入预览：解析下厨房链接，或让 AI 整理笔记文字、小红书笔记，确认或修改后再保存 */
@Composable
fun ImportScreen(sharedText: String, aiNote: Boolean, onDone: (savedId: Long?) -> Unit) {
    val app = rememberApp()
    val vm: EditorViewModel = viewModel(key = "import-$aiNote-$sharedText") { EditorViewModel(app, 0L, sharedText, aiNote) }
    EditorScaffold(
        vm = vm,
        title = if (vm.aiImport) "AI 整理预览" else "导入预览",
        saveLabel = "保存",
        discardMessage = "放弃导入吗？已下载的图片会被删除。",
        onDone = onDone,
        header = {
            if (vm.warnings.isNotEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null)
                            Text(if (vm.aiImport) "  有些内容没整理出来" else "  有些内容没解析出来", style = MaterialTheme.typography.titleSmall)
                        }
                        vm.warnings.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            } else {
                Text(
                    if (vm.aiImport) "AI 已整理完成，请对照原文检查用料和步骤再保存" else "已解析完成，检查一下再保存",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(
    vm: EditorViewModel,
    title: String,
    saveLabel: String,
    discardMessage: String,
    onDone: (savedId: Long?) -> Unit,
    header: @Composable () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var askingKey by remember { mutableStateOf(false) }

    fun discard() {
        vm.discard()
        onDone(null)
    }

    BackHandler { if (vm.loadState == LoadState.Ready) confirmDiscard = true else discard() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { if (vm.loadState == LoadState.Ready) confirmDiscard = true else discard() }) {
                        Icon(Icons.Filled.Close, contentDescription = "放弃")
                    }
                },
                actions = {
                    if (vm.loadState == LoadState.Ready) {
                        TextButton(
                            enabled = !vm.saving,
                            onClick = {
                                vm.save(
                                    onSaved = { onDone(it) },
                                    onError = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                                )
                            },
                        ) { Text(saveLabel) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val state = vm.loadState) {
            LoadState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when {
                            vm.aiNote -> "AI 正在整理菜谱，大约需要十几秒…"
                            vm.isXhsLink -> "正在读取小红书笔记，AI 看图整理大约需要半分钟…"
                            vm.isImport -> "正在读取网页和下载图片…"
                            else -> "加载中…"
                        },
                    )
                }
            }
            is LoadState.Failed -> Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { discard() }) { Text("返回") }
                        if (state.needsKey) Button(onClick = { askingKey = true }) { Text("填写 Key") }
                        else Button(onClick = vm::load) { Text("重试") }
                    }
                }
            }
            LoadState.Ready -> RecipeEditor(vm, Modifier.padding(padding).fillMaxSize(), header)
        }
    }

    if (askingKey) {
        AiKeyDialog(
            confirmLabel = "保存并整理",
            onDismiss = { askingKey = false },
            onConfirm = {
                askingKey = false
                vm.load()
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃") },
            text = { Text(discardMessage) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; discard() }) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
        )
    }
}
