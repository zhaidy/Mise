package com.personal.mise.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.mise.ai.AiRecipeOrganizer
import com.personal.mise.chat.ChatAction
import com.personal.mise.chat.ChatItem
import com.personal.mise.chat.ChatSession
import com.personal.mise.chat.ChatSuggestions
import com.personal.mise.chat.SuggestionGroup
import com.personal.mise.chat.RecipeTools
import com.personal.mise.ui.common.miseFieldColors
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.edit.AiKeyDialog
import com.personal.mise.ui.list.NewRecipeWay
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.ui.theme.SerifFamily
import java.time.LocalDateTime

/** 工具盘上的一格：点了直接做 [action]，或者把 [prefill] 填进输入框让用户接着写，或者发 [send] */
private data class Tool(
    val glyph: String,
    val label: String,
    val action: String? = null,
    val prefill: String? = null,
    val send: String? = null,
    val pickImages: Boolean = false,
    /** 手动新建菜谱 */
    val create: Boolean = false,
    /** 搜下厨房、粘贴链接、整理笔记 */
    val way: NewRecipeWay? = null,
)

/**
 * 「问问 AI」聊天框：从底部升起，对话、AI 给的卡片、工具盘和输入框。
 * [tools] 是这道菜能用的 AI 功能，为空时是首页的全局对话。
 * 卡片上点开菜谱、去导入时调 [onOpenRecipe]、[onImport]（文字, 是不是交给 AI 整理笔记）、[onImportImages]，由调用方关掉聊天框再跳转。
 * [onCreate] 不为空时（首页）可以在这里新建菜谱：手动新建、从图片导入，搜下厨房、粘贴链接、整理笔记交给 [onNewRecipe]。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiChatSheet(
    session: ChatSession,
    title: String,
    subtitle: String,
    tools: RecipeTools?,
    onDismiss: () -> Unit,
    onOpenRecipe: (Long) -> Unit = {},
    onImport: (text: String, aiNote: Boolean) -> Unit = { _, _ -> },
    onImportImages: (List<Uri>) -> Unit = {},
    onCreate: (() -> Unit)? = null,
    onNewRecipe: (NewRecipeWay) -> Unit = {},
) {
    val app = rememberApp()
    // 每次打开按现在的菜谱、计划和时间换一批问法
    val suggestions by produceState(emptyList<SuggestionGroup>(), session) {
        value = when (val id = session.recipeId) {
            null -> ChatSuggestions.global(app.repository.loadChatIndex(), app.repository.loadPlanForChat(), LocalDateTime.now())
            else -> app.repository.loadFull(id)?.let { ChatSuggestions.recipe(it) }.orEmpty()
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by rememberSaveable { mutableStateOf("") }
    var trayOpen by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    /** 没填 Key 时先记下要做的事，填完接着做 */
    var afterKey by remember { mutableStateOf<(() -> Unit)?>(null) }
    val focus = remember { FocusRequester() }
    val recipeChat = tools != null

    fun withKey(block: () -> Unit) {
        if (app.aiSettings.hasKey) block() else afterKey = block
    }

    fun send(text: String) {
        if (text.isBlank() || session.busy) return
        withKey {
            session.send(text)
            input = ""
            trayOpen = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AiRecipeOrganizer.MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) {
            session.logImageImport(uris.size)
            onImportImages(uris)
        }
    }

    fun use(tool: Tool) {
        trayOpen = false
        when {
            tool.create -> onCreate?.invoke()
            tool.way != null -> onNewRecipe(tool.way)
            tool.pickImages -> imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            tool.action != null -> withKey { session.runTool(ChatAction(tool.action), "✦ ${tool.label}") }
            tool.send != null -> send(tool.send)
            tool.prefill != null -> {
                input = tool.prefill
                runCatching { focus.requestFocus() }
            }
        }
    }

    val toolList = remember(tools) { if (tools == null) globalTools(canCreate = onCreate != null) else recipeTools(tools) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
        ) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (session.items.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空对话", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (session.loaded && session.items.isEmpty() && session.streaming == null && session.working == null) {
                    EmptyChat(
                        recipeChat = recipeChat,
                        groups = suggestions,
                        newRecipe = if (onCreate != null) newRecipeTools() else emptyList(),
                        onPick = { send(it) },
                        onUse = { use(it) },
                    )
                } else {
                    Messages(session, recipeChat, onOpenRecipe, onImport, onFix = { withKey { session.runTool(ChatAction(ChatAction.FIX), "✦ 照评价修改") } })
                }
            }

            AnimatedVisibility(trayOpen) {
                ToolTray(toolList, recipeChat, onUse = { use(it) })
            }

            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = { trayOpen = !trayOpen },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (trayOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("✦", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = {
                        Text(if (recipeChat) "问问题，或说说怎么改" else "问问题、排菜单、存菜谱", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = miseFieldColors(),
                    maxLines = 5,
                )
                val busy = session.busy
                Surface(
                    onClick = { if (busy) session.stop() else send(input) },
                    enabled = busy || input.isNotBlank(),
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (busy || input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (busy || input.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) {
                            Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(3.dp)))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空对话") },
            text = { Text("清空后从头开始聊。已经改好的菜谱不会变，但就不能再撤销了。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    session.clear()
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }

    afterKey?.let { pending ->
        AiKeyDialog(
            confirmLabel = "保存",
            onDismiss = { afterKey = null },
            onConfirm = {
                afterKey = null
                pending()
            },
        )
    }
}

@Composable
private fun Messages(
    session: ChatSession,
    recipeChat: Boolean,
    onOpenRecipe: (Long) -> Unit,
    onImport: (String, Boolean) -> Unit,
    onFix: () -> Unit,
) {
    val listState = rememberLazyListState()
    val items = session.items
    val streaming = session.streaming
    val working = session.working
    // 最后一格是个空白，滚到它就是滚到底
    val endIndex = items.size + (if (streaming != null) 1 else 0) + (if (working != null) 1 else 0)
    LaunchedEffect(items.size, working, streaming == null) {
        if (endIndex > 0) listState.scrollToItem(endIndex)
    }
    LaunchedEffect(streaming) {
        // AI 边说边往下滚；用户往上翻着看时不打扰
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (streaming != null && last >= endIndex - 1) listState.scrollToItem(endIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            ChatRow(session, item, recipeChat, onOpenRecipe, onImport, onFix)
        }
        if (streaming != null) {
            item(key = "streaming") {
                if (streaming.isEmpty()) TypingDots() else AiText(streaming, model = null)
            }
        }
        if (working != null) {
            item(key = "working") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(working, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(1.dp)) }
    }
}

@Composable
private fun ChatRow(
    session: ChatSession,
    item: ChatItem,
    recipeChat: Boolean,
    onOpenRecipe: (Long) -> Unit,
    onImport: (String, Boolean) -> Unit,
    onFix: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.fromUser) {
            UserBubble(item.text)
        } else if (item.text.isNotBlank()) {
            AiText(item.text, item.model)
        }
        item.card?.let { card ->
            ChatCardView(
                card = card,
                busy = session.busy,
                canUndo = session.undoable == item.id,
                recipeChat = recipeChat,
                onApply = { session.apply(item) },
                onDismiss = { session.dismiss(item) },
                onUndo = { session.undo(item) },
                onRetry = { session.retry(item) },
                onOpenRecipe = onOpenRecipe,
                onImport = { text, aiNote ->
                    session.markImported(item)
                    onImport(text, aiNote)
                },
                onFix = onFix,
            )
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val colors = LocalMiseColors.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(colors.ink, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = colors.onInk,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AiText(text: String, model: String?) {
    Column(Modifier.fillMaxWidth().padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        ChatText(text)
        model?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TypingDots() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
    }
}

/** 还没聊过时：一句招呼、按现在的情况出的两组问法；首页还能直接新建菜谱 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(
    recipeChat: Boolean,
    groups: List<SuggestionGroup>,
    newRecipe: List<Tool>,
    onPick: (String) -> Unit,
    onUse: (Tool) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (recipeChat) "关于这道菜，想问点什么，或者想怎么改？" else "想吃什么、怎么做、排菜单，都可以问我。",
            style = MaterialTheme.typography.titleMedium,
        )
        if (newRecipe.isNotEmpty()) {
            ChipGroup("新菜谱", newRecipe.map { "${it.glyph}  ${it.label}" }) { onUse(newRecipe[it]) }
        }
        groups.forEach { group -> ChipGroup(group.label, group.asks) { onPick(group.asks[it]) } }
        Text(
            "点左下角的 ✦ 还能直接用评价、估热量、分调料碗这些功能。".takeIf { recipeChat }
                ?: "点左下角的 ✦ 还有排一周菜单、用冰箱里的东西做菜这些功能。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(label: String, chips: List<String>, onClick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEachIndexed { index, chip ->
                Surface(
                    onClick = { onClick(index) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(chip, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ToolTray(tools: List<Tool>, recipeChat: Boolean, onUse: (Tool) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (recipeChat) "这道菜可以让 AI 做" else "AI 可以帮你",
            Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tools.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { tool ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onUse(tool) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(tool.glyph, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SerifFamily, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(tool.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}

/** 这道菜用得上的工具：评价、改菜谱、估热量总在；补用量、拆步骤、翻译、照评价修改只在用得上时出现 */
private fun recipeTools(tools: RecipeTools): List<Tool> = listOfNotNull(
    Tool("★", "口味评价", action = ChatAction.REVIEW).takeIf { tools.review },
    Tool("✎", "改菜谱", prefill = "帮我把这道菜改成："),
    Tool("⇄", "照评价修改", action = ChatAction.FIX).takeIf { tools.fix },
    Tool("⚖", "补全用量", action = ChatAction.AMOUNTS).takeIf { tools.amounts },
    Tool("◔", "估算热量", action = ChatAction.CALORIES).takeIf { tools.calories },
    Tool("◍", "分调料碗", action = ChatAction.BOWLS).takeIf { tools.bowls },
    Tool("☰", "拆分步骤", action = ChatAction.SPLIT).takeIf { tools.split },
    Tool("文", "翻译成中文", action = ChatAction.TRANSLATE).takeIf { tools.translate },
    Tool("▦", "排进计划", prefill = "帮我排到明天"),
    Tool("✐", "记批注", prefill = "帮我记一下："),
)

private fun globalTools(canCreate: Boolean): List<Tool> = listOf(
    Tool("◷", "今晚吃什么", send = "今晚吃什么？"),
    Tool("▦", "排一周菜单", send = "帮我排一下接下来一周的菜单"),
    Tool("❄", "用冰箱里的", prefill = "冰箱里有："),
) + if (canCreate) newRecipeTools() else listOf(
    Tool("▣", "从图片导入", pickImages = true),
    Tool("⇣", "存链接笔记", prefill = "帮我存这个菜谱："),
)

/** 新建菜谱的几种办法，原来首页右下角「新菜谱」里的 */
private fun newRecipeTools(): List<Tool> = listOf(
    Tool("✚", "手动新建", create = true),
    Tool("⌕", "搜下厨房", way = NewRecipeWay.XIACHUFANG),
    Tool("⇣", "粘贴链接", way = NewRecipeWay.LINK),
    Tool("✎", "整理笔记", way = NewRecipeWay.NOTE),
    Tool("▣", "从图片导入", pickImages = true),
)

/** 菜谱、今日菜单、日历页和菜谱详情页右下角浮着的「问问 AI」按钮 */
@Composable
fun AskAiButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMiseColors.current
    Surface(onClick = onClick, modifier = modifier, shape = CircleShape, color = colors.ink, contentColor = colors.onInk, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text("✦", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text("问问 AI", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
