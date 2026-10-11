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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.EmptyState
import com.personal.mise.ui.common.Eyebrow
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.RecipeCover
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.common.miseFieldColors
import java.util.Locale
import com.personal.mise.util.CalorieText
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
import com.personal.mise.ai.AiProvider
import com.personal.mise.data.Recipe
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.hasImage
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.edit.AiKeyDialog
import com.personal.mise.ui.edit.AiKeyFields
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
    val found by remember(query) { app.repository.search(query) }.collectAsStateWithLifecycle(emptyList())
    // 还没读出来时是 null，免得把转屏前选的分类当成不存在
    val loadedCategories by remember { app.repository.observeCategories() }.collectAsStateWithLifecycle(null)
    val categories = loadedCategories.orEmpty()
    // null 是全部，UNCATEGORIZED 是没分类的
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    // 选中的分类被改名或删光了，就回到全部
    LaunchedEffect(loadedCategories) {
        val known = loadedCategories ?: return@LaunchedEffect
        if (category != null && category != UNCATEGORIZED && category !in known) category = null
    }
    val recipes = when (category) {
        null -> found
        UNCATEGORIZED -> found.filter { it.category.isNullOrBlank() }
        else -> found.filter { it.category == category }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var pasteOpen by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var aiSettingsOpen by remember { mutableStateOf(false) }
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

    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)) }
    val full = GridItemSpan(2)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { menuOpen = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("新菜谱", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("手动新建") }, onClick = { menuOpen = false; onCreate() })
                    DropdownMenuItem(text = { Text("粘贴链接导入") }, onClick = { menuOpen = false; pasteOpen = true })
                    DropdownMenuItem(text = { Text("AI 整理笔记") }, onClick = { menuOpen = false; noteOpen = true })
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header", span = { full }) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Eyebrow(today)
                            Text("今天做点什么？", style = MaterialTheme.typography.headlineLarge)
                        }
                        Box {
                            CircleIconButton(
                                Icons.Filled.MoreVert,
                                "更多",
                                onClick = { moreOpen = true },
                                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                            )
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("共享厨房") },
                                    onClick = { moreOpen = false; kitchenOpen = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("AI 设置") },
                                    onClick = { moreOpen = false; aiSettingsOpen = true },
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
                    }
                    SearchField(query, { query = it })
                    if (categories.isNotEmpty()) {
                        CategoryChips(categories, category, { category = it })
                    }
                    if (recipes.isNotEmpty()) {
                        val heading = when {
                            query.isNotEmpty() -> "搜索结果"
                            category == UNCATEGORIZED -> "未分类"
                            category != null -> category!!
                            else -> "我的菜谱"
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(heading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Text(
                                "共 ${recipes.size} 道 · 长按加入今日",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (recipes.isEmpty()) {
                item(key = "empty", span = { full }) {
                    if (found.isNotEmpty()) {
                        EmptyState("类", "这个分类下没有菜谱", "换个分类看看，或者点「全部」", Modifier.padding(top = 24.dp))
                    } else if (query.isEmpty()) {
                        EmptyState(
                            "谱",
                            "还没有菜谱",
                            "点右下角的「新菜谱」，手动新建、粘贴下厨房或小红书链接，或者让 AI 整理笔记里的菜谱。",
                            Modifier.padding(top = 24.dp),
                        )
                    } else {
                        EmptyState("寻", "没有找到", "没有和「$query」相关的菜谱", Modifier.padding(top = 24.dp))
                    }
                }
            } else {
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
            initialProvider = app.aiSettings.provider,
            onDismiss = { noteOpen = false },
            onConfirm = { note, provider, key ->
                if (key.isNotBlank()) {
                    app.aiSettings.saveKey(provider, key)
                    app.aiSettings.provider = provider
                }
                noteOpen = false
                onImport(note, true)
            },
        )
    }

    if (aiSettingsOpen) {
        AiKeyDialog(
            confirmLabel = "保存",
            onDismiss = { aiSettingsOpen = false },
            onConfirm = {
                aiSettingsOpen = false
                scope.launch { snackbar.showSnackbar("已改用 ${app.aiSettings.provider.label}") }
            },
        )
    }
}

/** 网格卡片：上面是封面（没图就用首字色块），下面是菜名和份量 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    SoftCard(
        Modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
    ) {
        Column {
            RecipeCover(recipe.title, recipe.coverImage, Modifier.fillMaxWidth().height(118.dp), initialSize = 52.sp)
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val info = listOfNotNull(
                    recipe.servings?.takeIf { it.isNotBlank() },
                    recipe.calories?.let { CalorieText.format(it) },
                ).joinToString(" · ")
                Text(
                    info.ifEmpty { "　" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val UNCATEGORIZED = "\u0000未分类"

/** 按分类筛选：全部、各个分类、未分类，横着滑 */
@Composable
private fun CategoryChips(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") { CategoryChip("全部", selected == null) { onSelect(null) } }
        items(categories, key = { it }) { name -> CategoryChip(name, selected == name) { onSelect(name) } }
        item(key = "none") { CategoryChip("未分类", selected == UNCATEGORIZED) { onSelect(UNCATEGORIZED) } }
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = CircleShape,
    )
}

/** 胶囊形搜索框 */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜索菜谱") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "清空") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
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
