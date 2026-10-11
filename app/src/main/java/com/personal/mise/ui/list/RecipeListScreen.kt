package com.personal.mise.ui.list

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import com.personal.mise.ui.common.ClassifyManageDialog
import com.personal.mise.ui.common.ClassifyDialog
import androidx.compose.material.icons.filled.Edit
import com.personal.mise.data.Categories
import com.personal.mise.data.RecipeFilter
import com.personal.mise.data.RecipeMenu
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.HorizontalDivider
import com.personal.mise.data.CategoryNode
import com.personal.mise.data.tagList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Switch
import com.personal.mise.share.ShareImage
import com.personal.mise.ui.common.ShareImageDialog
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
import androidx.compose.runtime.collectAsState
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
import com.personal.mise.ai.AiRecipeOrganizer
import com.personal.mise.data.Recipe
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.hasImage
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.common.rememberSavedView
import com.personal.mise.ui.plan.PlanDateDialog
import com.personal.mise.util.DayText
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
    onImportImages: (List<Uri>) -> Unit,
) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    val found by remember(query) { app.repository.search(query) }.collectAsStateWithLifecycle(emptyList())
    // 还没读出来时是 null，免得把转屏前选的分类、标签当成不存在
    val loadedCategories by remember { app.repository.observeCategories() }.collectAsStateWithLifecycle(null)
    val loadedTags by remember { app.repository.observeTags() }.collectAsStateWithLifecycle(null)
    val categories = loadedCategories.orEmpty()
    val allTags = loadedTags.orEmpty()
    // 筛选条件拆成几个能存进 Bundle 的值，转屏后还在；标签用换行连起来
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var subcategory by rememberSaveable { mutableStateOf<String?>(null) }
    var tagsSaved by rememberSaveable { mutableStateOf("") }
    val filter = RecipeFilter(category, subcategory, tagsSaved.split('\n').filter { it.isNotEmpty() }.toSet())
    fun applyFilter(f: RecipeFilter) {
        category = f.category
        subcategory = f.subcategory
        tagsSaved = f.tags.joinToString("\n")
    }
    // 选中的分类、标签被改名或删光了，就去掉
    LaunchedEffect(loadedCategories, loadedTags) {
        applyFilter(filter.prune(loadedCategories ?: return@LaunchedEffect, loadedTags ?: return@LaunchedEffect))
    }
    val hits = found.filter { filter.matches(it.recipe) }
    var menuView by rememberSavedView("recipes_menu")
    val lastCooked by remember { app.repository.observeLastCooked() }.collectAsStateWithLifecycle(emptyMap())
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
    var classifying by remember { mutableStateOf<Recipe?>(null) }
    var planning by remember { mutableStateOf<Recipe?>(null) }
    var manageOpen by remember { mutableStateOf(false) }
    var menuShareOpen by remember { mutableStateOf(false) }
    var menuShareDates by rememberSaveable { mutableStateOf(true) }
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
    val imagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AiRecipeOrganizer.MAX_IMAGES),
    ) { uris ->
        if (uris.isNotEmpty()) onImportImages(uris)
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

    // 卡片和菜单行长按弹出的菜单：加入今日菜单、排进计划、设分类和标签
    @Composable
    fun LongPressMenu(recipe: Recipe, content: @Composable (onLongClick: () -> Unit) -> Unit) {
        var open by remember { mutableStateOf(false) }
        Box {
            content { open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("加入今日菜单") },
                    onClick = {
                        open = false
                        scope.launch {
                            val added = app.repository.addToToday(recipe.id)
                            snackbar.showSnackbar(if (added) "已加入今日菜单：${recipe.title}" else "「${recipe.title}」已经在今日菜单里")
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("排进计划…") },
                    onClick = { open = false; planning = recipe },
                )
                DropdownMenuItem(
                    text = { Text("分类和标签") },
                    onClick = { open = false; classifying = recipe },
                )
            }
        }
    }

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
                    DropdownMenuItem(
                        text = { Text("从图片导入") },
                        onClick = {
                            menuOpen = false
                            imagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 菜单视图自己有刊头，这里就不放日期了
                    Box(Modifier.weight(1f)) {
                        if (!menuView) Eyebrow(today)
                    }
                    ViewSwitch(menuView, onChange = { menuView = it })
                    if (menuView) {
                        CircleIconButton(
                            Icons.Filled.Share,
                            "导出菜单图片",
                            onClick = { menuShareOpen = true },
                            container = MaterialTheme.colorScheme.surfaceContainerLowest,
                        )
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
                                text = { Text("管理分类和标签") },
                                onClick = { moreOpen = false; manageOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text("导出菜单图片") },
                                onClick = { moreOpen = false; menuShareOpen = true },
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
            }
            if (menuView) {
                // 菜单本身按分类分区，左栏的分类、标签筛选不用，只按搜索词过滤
                val sections = remember(found, lastCooked) { RecipeMenu.sections(found.map { it.recipe }, lastCooked) }
                MenuList(
                    sections = sections,
                    modifier = Modifier.fillMaxSize(),
                    onOpen = onOpen,
                    menuItem = { recipe, content -> LongPressMenu(recipe, content) },
                    empty = {
                        item(key = "empty") {
                            if (query.isEmpty()) {
                                EmptyState("谱", "还没有菜谱", "点右下角的「新菜谱」加几道菜，菜单就有内容了。", Modifier.padding(top = 24.dp))
                            } else {
                                EmptyState("寻", "没有找到", "没有和「$query」相关的菜谱", Modifier.padding(top = 24.dp))
                            }
                        }
                    },
                )
            } else {
                // 没有分类和标签时不要左栏，卡片占满整宽
                val showRail = categories.isNotEmpty() || allTags.isNotEmpty()
                Row(Modifier.fillMaxSize()) {
                    if (showRail) {
                        CategoryRail(
                            categories = categories,
                            tags = allTags,
                            filter = filter,
                            found = found.map { it.recipe },
                            onFilter = ::applyFilter,
                            onManage = { manageOpen = true },
                        )
                    }
                    val side = if (showRail) 12.dp else 20.dp
                    val gap = if (showRail) 10.dp else 14.dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(start = side, end = if (showRail) 16.dp else 20.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        if (hits.isNotEmpty()) {
                            item(key = "heading", span = { full }) {
                                val heading = when {
                                    query.isNotEmpty() -> "搜索结果"
                                    category == Categories.NONE -> "未分类"
                                    category != null -> listOfNotNull(category, subcategory).joinToString(" › ")
                                    filter.tags.isNotEmpty() -> filter.tags.joinToString(" ") { "#$it" }
                                    else -> "我的菜谱"
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        heading,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        if (showRail) "共 ${hits.size} 道" else "共 ${hits.size} 道 · 长按加入今日、设分类",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (hits.isEmpty()) {
                            item(key = "empty", span = { full }) {
                                if (found.isNotEmpty()) {
                                    EmptyState("类", "没有符合条件的菜谱", "换个分类或者少选几个标签看看", Modifier.padding(top = 24.dp))
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
                            items(hits, key = { it.recipe.id }) { hit ->
                                val recipe = hit.recipe
                                LongPressMenu(recipe) { onLongClick ->
                                    RecipeCard(
                                        recipe = recipe,
                                        matchedIngredients = hit.matchedIngredients,
                                        compact = showRail,
                                        onClick = { onOpen(recipe.id) },
                                        onLongClick = onLongClick,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (menuShareOpen) {
        // 和菜单视图一样：按搜索词过滤，不管左栏的分类、标签筛选
        val sections = remember(found, lastCooked) { RecipeMenu.sections(found.map { it.recipe }, lastCooked) }
        ShareImageDialog(
            title = "导出菜单图片",
            name = "家宴菜单",
            key = Triple(sections, menuShareDates, query),
            render = { ShareImage.menu(sections, app.images, withDates = menuShareDates) },
            onDismiss = { menuShareOpen = false },
            options = {
                if (query.isNotEmpty()) {
                    Text(
                        "只有搜索「$query」找到的菜",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("写上次下厨的时间", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = menuShareDates, onCheckedChange = { menuShareDates = it })
                }
            },
        )
    }

    update?.let { info ->
        val timersRunning = app.timers.timers.collectAsState().value.isNotEmpty()
        AlertDialog(
            onDismissRequest = { if (!updating) update = null },
            title = { Text("发现新版本 build ${info.build}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (info.changes.isNotEmpty()) {
                        Text("更新内容", style = MaterialTheme.typography.titleSmall)
                        info.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        Spacer(Modifier.height(12.dp))
                    }
                    Text(
                        when {
                            updating -> "正在下载安装…装好后应用会自动重新打开。"
                            timersRunning -> "数据会保留。注意：更新会中断正在进行的计时。"
                            else -> "装好后应用会自动重新打开，数据会保留。"
                        }
                    )
                }
            },
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

    classifying?.let { recipe ->
        ClassifyDialog(recipe, onDismiss = { classifying = null })
    }

    planning?.let { recipe ->
        PlanDateDialog(
            onDismiss = { planning = null },
            onPick = { date ->
                planning = null
                scope.launch {
                    val added = app.repository.addToPlan(recipe.id, date)
                    snackbar.showSnackbar(if (added) "已排进计划：${DayText.ahead(date)}做${recipe.title}" else "${DayText.ahead(date)}已经排了「${recipe.title}」")
                }
            },
        )
    }

    if (manageOpen) {
        ClassifyManageDialog(onDismiss = { manageOpen = false })
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
            initialProvider = app.aiSettings.models.first().provider,
            onDismiss = { noteOpen = false },
            onConfirm = { note, provider, key ->
                if (key.isNotBlank()) {
                    app.aiSettings.saveKey(provider, key)
                    app.aiSettings.enableProvider(provider)
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
                val first = app.aiSettings.firstUsable?.label
                scope.launch { snackbar.showSnackbar(if (first != null) "已保存，先用 $first" else "已保存") }
            },
        )
    }
}

/** 网格卡片：上面是封面（没图就用首字色块），下面是菜名和份量 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeCard(
    recipe: Recipe,
    matchedIngredients: List<String>,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    SoftCard(
        Modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
    ) {
        Column {
            // 有左栏时卡片窄，封面也矮一点
            RecipeCover(
                recipe.title,
                recipe.coverImage,
                Modifier.fillMaxWidth().height(if (compact) 72.dp else 100.dp),
                initialSize = if (compact) 30.sp else 44.sp,
            )
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // 菜名给两行，长名字也能看全；固定两行高，卡片才对得齐
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 靠用料搜到的，这一行换成对上的用料
                val info = if (matchedIngredients.isNotEmpty()) {
                    "用料：" + matchedIngredients.joinToString("、")
                } else {
                    listOfNotNull(
                        recipe.servings?.takeIf { it.isNotBlank() },
                        recipe.calories?.let { CalorieText.format(it) },
                    ).joinToString(" · ")
                }
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

/** 左侧常驻的分类栏：全部、各大类（选中的展开小类）、未分类，下面是可多选的标签和管理入口 */
@Composable
private fun CategoryRail(
    categories: List<CategoryNode>,
    tags: List<String>,
    filter: RecipeFilter,
    found: List<Recipe>,
    onFilter: (RecipeFilter) -> Unit,
    onManage: () -> Unit,
) {
    // 数字跟着搜索结果走
    val byCategory = remember(found) { found.groupingBy { it.category?.takeIf { c -> c.isNotBlank() } ?: Categories.NONE }.eachCount() }
    val bySub = remember(found) { found.groupingBy { it.category to it.subcategory }.eachCount() }
    val byTag = remember(found) { found.flatMap { it.tagList }.groupingBy { it }.eachCount() }
    LazyColumn(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topEnd = 22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        item(key = "all") {
            RailItem("全部", found.size, filter.category == null) { onFilter(filter.copy(category = null, subcategory = null)) }
        }
        categories.forEach { node ->
            val selected = filter.category == node.name
            item(key = "c:${node.name}") {
                RailItem(node.name, byCategory[node.name] ?: 0, selected) {
                    onFilter(filter.copy(category = node.name, subcategory = null))
                }
            }
            if (selected && node.subcategories.isNotEmpty()) {
                item(key = "s-all:${node.name}") {
                    RailSubItem("全部", filter.subcategory == null) { onFilter(filter.copy(subcategory = null)) }
                }
                items(node.subcategories, key = { "s:${node.name}:$it" }) { sub ->
                    RailSubItem(sub, filter.subcategory == sub, bySub[node.name to sub] ?: 0) {
                        onFilter(filter.copy(subcategory = sub))
                    }
                }
            }
        }
        if (categories.isNotEmpty()) {
            item(key = "none") {
                RailItem("未分类", byCategory[Categories.NONE] ?: 0, filter.category == Categories.NONE) {
                    onFilter(filter.copy(category = Categories.NONE, subcategory = null))
                }
            }
        }
        if (tags.isNotEmpty()) {
            item(key = "tags-divider") {
                HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
            // 标签可以多选，要全都带上才显示
            items(tags, key = { "t:$it" }) { tag ->
                val on = tag in filter.tags
                RailItem("#$tag", byTag[tag] ?: 0, on) {
                    onFilter(filter.copy(tags = if (on) filter.tags - tag else filter.tags + tag))
                }
            }
        }
        item(key = "manage") {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onManage).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("管理", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 左栏的一项：选中时左边一道竖条、底色变深 */
@Composable
private fun RailItem(text: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(if (selected) colors.surfaceContainerLowest else Color.Transparent)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                .background(if (selected) colors.primary else Color.Transparent),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        )
        Text(
            if (count > 0) "$count" else "",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, end = 10.dp),
        )
    }
}

/** 选中大类下面缩进的小类 */
@Composable
private fun RailSubItem(text: String, selected: Boolean, count: Int = 0, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .background(colors.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        if (count > 0) Text("$count", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}

/** 胶囊形搜索框 */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    // 自己画的矮胶囊：默认 TextField 至少 56dp 高，占地方
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Box(Modifier.weight(1f).padding(start = 10.dp)) {
                    if (query.isEmpty()) {
                        Text(
                            "搜菜名或用料，空格隔开",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Clear, contentDescription = "清空", modifier = Modifier.size(18.dp))
                    }
                } else {
                    Spacer(Modifier.width(10.dp))
                }
            }
        },
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
