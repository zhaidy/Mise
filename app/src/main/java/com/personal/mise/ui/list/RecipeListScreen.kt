package com.personal.mise.ui.list

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.MainActivity
import com.personal.mise.data.Recipe
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.hasImage
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.update.UpdateInfo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onImport: (text: String, aiNote: Boolean) -> Unit,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    val recipes by remember(query) { app.repository.search(query) }.collectAsStateWithLifecycle(emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var kitchenOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            try {
                val count = app.backup.export(uri)
                snackbar.showSnackbar("已导出 $count 个菜谱")
            } catch (e: Exception) {
                snackbar.showSnackbar("导出失败：${e.message}")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    // manual=true 时才提示“已是最新 / 检查失败”，启动时的静默检查只在有新版本时弹窗
    suspend fun checkUpdate(manual: Boolean) {
        try {
            val info = app.updater.check()
            if (info != null) update = info else if (manual) snackbar.showSnackbar("已是最新版本")
        } catch (e: Exception) {
            if (manual) snackbar.showSnackbar("检查更新失败：${e.message}")
        }
    }
    LaunchedEffect(Unit) { checkUpdate(manual = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("菜谱") },
                actions = {
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("共享厨房") },
                                onClick = { moreOpen = false; kitchenOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text("检查更新") },
                                onClick = { moreOpen = false; scope.launch { checkUpdate(manual = true) } },
                            )
                            DropdownMenuItem(
                                text = { Text("导出备份") },
                                onClick = {
                                    moreOpen = false
                                    val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                                    exportLauncher.launch("mise-backup-$date.zip")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("从备份恢复") },
                                onClick = { moreOpen = false; restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("手动新建") }, onClick = { menuOpen = false; onCreate() })
                    DropdownMenuItem(text = { Text("粘贴链接导入") }, onClick = { menuOpen = false; pasteOpen = true })
                    DropdownMenuItem(text = { Text("AI 整理笔记") }, onClick = { menuOpen = false; noteOpen = true })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索菜谱") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "清空") }
                    }
                },
                singleLine = true,
            )
            if (recipes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isEmpty()) "还没有菜谱。点右下角的加号，手动新建、粘贴下厨房链接，或者让 AI 整理笔记里的菜谱。" else "没有找到「$query」",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recipes, key = { it.id }) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            onClick = { onOpen(recipe.id) },
                            onLongClick = {
                                scope.launch {
                                    val added = app.repository.addToToday(recipe.id)
                                    snackbar.showSnackbar(if (added) "已加入今日菜单：${recipe.title}" else "「${recipe.title}」已经在今日菜单里")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!updating) update = null },
            title = { Text("发现新版本 build ${info.build}") },
            text = { Text(if (updating) "正在下载…" else "下载后会弹出系统安装界面，数据会保留。") },
            confirmButton = {
                TextButton(
                    enabled = !updating,
                    onClick = {
                        updating = true
                        scope.launch {
                            try {
                                val installing = app.updater.install(app.updater.download(info))
                                if (!installing) snackbar.showSnackbar("请先允许安装未知应用，再点一次更新")
                                update = null
                            } catch (e: Exception) {
                                snackbar.showSnackbar("下载失败：${e.message}")
                            } finally {
                                updating = false
                            }
                        }
                    },
                ) { Text("更新") }
            },
            dismissButton = { TextButton(enabled = !updating, onClick = { update = null }) { Text("以后再说") } },
        )
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!restoring) restoreUri = null },
            title = { Text("从备份恢复") },
            text = { Text(if (restoring) "正在恢复…" else "会用备份替换现在所有的菜谱和图片，无法撤销。恢复完 App 会自动重启。") },
            confirmButton = {
                TextButton(
                    enabled = !restoring,
                    onClick = {
                        restoring = true
                        scope.launch {
                            try {
                                app.backup.restore(uri)
                                MainActivity.restartApp(context)
                            } catch (e: Exception) {
                                // 已经关库的话 App 用不了了，也得重启
                                if (!app.database.isOpen) MainActivity.restartApp(context)
                                restoreUri = null
                                snackbar.showSnackbar("恢复失败：${e.message}")
                            } finally {
                                restoring = false
                            }
                        }
                    },
                ) { Text("恢复") }
            },
            dismissButton = { TextButton(enabled = !restoring, onClick = { restoreUri = null }) { Text("取消") } },
        )
    }

    if (kitchenOpen) {
        KitchenDialog(onDismiss = { kitchenOpen = false })
    }

    if (pasteOpen) {
        PasteLinkDialog(
            onDismiss = { pasteOpen = false },
            onConfirm = { pasteOpen = false; onImport(it, false) },
        )
    }

    if (noteOpen) {
        AiNoteDialog(
            needsKey = !app.aiSettings.hasKey,
            onDismiss = { noteOpen = false },
            onConfirm = { note, key ->
                if (key.isNotBlank()) app.aiSettings.geminiKey = key
                noteOpen = false
                onImport(note, true)
            },
        )
    }
}

/** 紧凑的一行：大多数菜谱没有图，只有有图时才在左边放个小缩略图 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit, onLongClick: () -> Unit) {
    val app = rememberApp()
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app.hasImage(recipe.coverImage)) {
                LocalImage(recipe.coverImage, Modifier.size(48.dp).clip(MaterialTheme.shapes.small))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                recipe.servings?.takeIf { it.isNotBlank() }?.let {
                    Text("份量：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
        title = { Text("粘贴下厨房链接") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("https://www.xiachufang.com/recipe/…") },
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 粘贴手机笔记里的菜谱文字，交给 AI 整理；还没填 Gemini Key 时一起填 */
@Composable
private fun AiNoteDialog(needsKey: Boolean, onDismiss: () -> Unit, onConfirm: (note: String, key: String) -> Unit) {
    val context = LocalContext.current
    var text by remember {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        mutableStateOf(if (UrlExtractor.isLinkShare(pasted)) "" else pasted)
    }
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
                )
                if (needsKey) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "需要 Gemini API Key：在 aistudio.google.com 免费申请，只保存在这台手机上。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text, key) },
                enabled = text.isNotBlank() && (!needsKey || key.isNotBlank()),
            ) { Text("整理") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
