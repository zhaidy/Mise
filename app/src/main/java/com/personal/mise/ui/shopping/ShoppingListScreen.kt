package com.personal.mise.ui.shopping

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.RecipeFull
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.personal.mise.ui.common.CheckRow
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.MiseHeader
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.ShoppingList
import com.personal.mise.util.UnitText
import com.personal.mise.util.DayText
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.personal.mise.importer.ImportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import com.personal.mise.MiseApp
import com.personal.mise.data.ExtraItem
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.core.content.FileProvider
import java.io.File
import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/** [onBack] 为 null 时是底部「购物清单」那一格，没有返回键 */
fun ShoppingListScreen(planFrom: String?, planDays: Int, onBack: (() -> Unit)?) {
    val app = rememberApp()
    val context = LocalContext.current
    val todayRows by app.repository.observeToday().collectAsStateWithLifecycle(emptyList())
    val plans by app.repository.observePlan().collectAsStateWithLifecycle(emptyList())
    var days by rememberSaveable { mutableIntStateOf(planDays) }
    // 有起始日期时是计划里这几天的菜；同一道菜排了几天就买几份，明细里写上哪天
    val rows = if (planFrom == null) {
        todayRows.map { BuyFor(it.recipeId, it.title) }
    } else {
        val dates = DayText.days(planFrom, days)
        plans.filter { it.date in dates && it.recipeId != null }
            .map { BuyFor(it.recipeId!!, if (days > 1) "${it.title}·${DayText.weekday(it.date)}" else it.title) }
    }
    val ids = rows.map { it.recipeId }.distinct()
    val fulls by remember(ids) {
        if (ids.isEmpty()) flowOf(emptyList<RecipeFull?>())
        else combine(ids.map { app.repository.observeFull(it) }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())
    val have by app.pantry.have.collectAsStateWithLifecycle()
    val extras by app.extraShopping.items.collectAsStateWithLifecycle()
    val extrasToBuy = extras.filter { !it.bought }

    val sources = rows.flatMap { row ->
        val full = fulls.firstOrNull { it?.recipe?.id == row.recipeId } ?: return@flatMap emptyList()
        val factor = app.scaleOf(row.recipeId)
        full.ingredients.sortedBy { it.order }.map {
            ShoppingList.Source(row.label, it.name, UnitText.display(AmountScaler.scale(it.amountText, factor)), it.kind)
        }
    }
    val built = ShoppingList.build(sources)
    // AI 整理过这份清单（同样几样东西）就按它合并、分区；用量变了照样套得上
    val cacheKey = built.map { it.key }
    var aiView by rememberSaveable { mutableStateOf(true) }
    var organizing by remember { mutableStateOf(false) }
    var aiMessage by remember { mutableStateOf<String?>(null) }
    var cacheVersion by remember { mutableIntStateOf(0) }
    val sections = remember(built, cacheVersion, aiView) {
        app.shoppingGroups[cacheKey]?.takeIf { aiView }?.let { ShoppingList.regroup(built, it) }
    }
    val items = sections?.flatMap { it.items } ?: built
    val (owned, toBuy) = items.partition { it.ownedIn(have) }
    val scope = rememberCoroutineScope()

    fun toggle(item: ShoppingList.Item) {
        if (item.ownedIn(have)) app.pantry.clear(item.keys) else app.pantry.add(item.keys)
    }

    fun organize() {
        organizing = true
        aiMessage = null
        scope.launch {
            try {
                app.shoppingGroups[cacheKey] = app.shoppingOrganizer.organize(built)
                aiView = true
                cacheVersion++
                val after = ShoppingList.regroup(built, app.shoppingGroups.getValue(cacheKey)).sumOf { it.items.size }
                aiMessage = if (after < built.size) "AI 把 ${built.size} 样合成了 $after 样，按超市区域分好了" else "AI 按超市区域分好了，没有重复的"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aiMessage = (e as? ImportException)?.message ?: "AI 整理失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                organizing = false
            }
        }
    }

    Scaffold(
        topBar = {
            MiseHeader("购物清单", eyebrow = planFrom?.let { planLabel(it, days) }, onBack = onBack) {
                if (toBuy.isNotEmpty() || extrasToBuy.isNotEmpty()) {
                    CircleIconButton(
                        MiseIcons.Share,
                        "发送要买的",
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, ShoppingList.shareText(toBuy, sections, extrasToBuy.map { it.name }))
                            context.startActivity(Intent.createChooser(send, "发送购物清单"))
                        },
                    )
                }
            }
        },
    ) { padding ->
        // 顶上留出标题的位置再放列表，往上滚时不会从标题底下透出来
        LazyColumn(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (planFrom != null) {
                item(key = "days") { DaysSwitch(days, { days = it }) }
            }
            item(key = "hint") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        when {
                            rows.isNotEmpty() && items.isEmpty() -> "这些菜还没有填用料。别的要买的在下面自己加。"
                            items.isEmpty() && planFrom != null -> "这几天还没排菜。别的要买的在下面自己加。"
                            items.isEmpty() -> "今日菜单是空的。别的要买的在下面自己加。"
                            toBuy.isEmpty() -> "菜里要用的都有了，不用买"
                            else -> "菜里要买 ${toBuy.size} 样。家里有的勾上，会挪到下面，下次也记得。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (items.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            sections != null -> TextButton(onClick = { aiView = false; aiMessage = null }) { Text("看原来的清单") }
                            app.shoppingGroups[cacheKey] != null -> TextButton(onClick = { aiView = true }) { Text("看 AI 整理的") }
                            built.size >= 2 && app.aiSettings.hasKey -> TextButton(onClick = { organize() }, enabled = !organizing) {
                                Text(if (organizing) "AI 正在合并同样的东西、按区域分…" else "✦ 让 AI 合并整理")
                            }
                        }
                        if (organizing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    aiMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "extras") {
                ExtraGroup(
                    extras,
                    onAdd = { app.extraShopping.add(it) },
                    readPhoto = if (app.aiSettings.hasKey) { uri -> readShoppingPhoto(app, uri) } else null,
                    onToggle = { app.extraShopping.toggle(it.id) },
                    onRemove = { list -> app.extraShopping.remove(list.map { it.id }) },
                )
            }
            if (toBuy.isNotEmpty()) {
                // AI 整理过就按超市区域一组一组列，没整理过就是一张「要买」
                val buyGroups = sections?.mapNotNull { g -> g.items.filter { !it.ownedIn(have) }.takeIf { it.isNotEmpty() }?.let { g.section to it } }
                    ?: listOf("要买" to toBuy)
                buyGroups.forEach { (title, list) ->
                    item(key = "buy-$title") {
                        ShoppingGroup(title, MaterialTheme.colorScheme.primary) {
                            list.forEach { item ->
                                key(item.key) { ShoppingRow(item, owned = false, accent = MaterialTheme.colorScheme.primary) { toggle(item) } }
                            }
                        }
                    }
                }
            }
            if (owned.isNotEmpty()) {
                item(key = "have") {
                    ShoppingGroup(
                        "家里有",
                        MaterialTheme.colorScheme.secondary,
                        action = { TextButton(onClick = { app.pantry.clear(owned.flatMap { it.keys }) }) { Text("全部取消") } },
                    ) {
                        owned.forEach { item ->
                            key(item.key) { ShoppingRow(item, owned = true, accent = MaterialTheme.colorScheme.secondary) { toggle(item) } }
                        }
                    }
                }
            }
        }
    }
}

/** 自己加的要买的东西：顶上一个输入框（可以一次贴一串），勾上是买到了，长按删掉 */
@Composable
private fun ExtraGroup(
    items: List<ExtraItem>,
    onAdd: (List<String>) -> Unit,
    onToggle: (ExtraItem) -> Unit,
    onRemove: (List<ExtraItem>) -> Unit,
    /** 让 AI 认照片上的清单；没填 AI Key 时为 null，不显示拍照按钮 */
    readPhoto: (suspend (Uri) -> List<String>)?,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun submit() {
        val names = ShoppingList.parseExtras(text)
        if (names.isEmpty()) return
        onAdd(names)
        text = ""
        photoMessage = null
    }
    // 认出来的先填进输入框，看过、改过再点「加上」
    fun read(uri: Uri) {
        val reader = readPhoto ?: return
        reading = true
        photoMessage = null
        scope.launch {
            try {
                val names = reader(uri)
                text = (text.lines().filter { it.isNotBlank() } + names).joinToString("\n")
                photoMessage = "认出 ${names.size} 样，看看对不对，再点「加上」"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                photoMessage = (e as? ImportException)?.message ?: "AI 识别失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                reading = false
            }
        }
    }
    val bought = items.filter { it.bought }
    ShoppingGroup(
        "另外要买",
        MaterialTheme.colorScheme.primary,
        action = { if (bought.isNotEmpty()) TextButton(onClick = { onRemove(bought) }) { Text("清掉买好的") } },
    ) {
        Row(Modifier.padding(end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text,
                { text = it },
                Modifier.weight(1f),
                // 提示只占一行，字大时也不把输入框撑高
                placeholder = { Text("纸巾、牛奶…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                maxLines = 5,
                shape = FieldShape,
                colors = miseFieldColors(),
                // 软键盘上的完成键直接加上、键盘不收起，好接着加下一样；贴进来的换行照样拆成几样
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            if (readPhoto != null) PhotoButton(enabled = !reading, onPicked = ::read)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { submit() }, enabled = text.isNotBlank()) { Text("加上") }
        }
        if (reading || photoMessage != null) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (reading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (reading) "AI 正在看照片上的清单…" else photoMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items.forEach { item ->
            key(item.id) {
                CheckRow(
                    title = item.name,
                    detail = "",
                    checked = item.bought,
                    onToggle = { onToggle(item) },
                    accent = MaterialTheme.colorScheme.primary,
                    onLongClick = { onRemove(listOf(item)) },
                )
            }
        }
        Text(
            when {
                items.isNotEmpty() -> "勾上是买到了，长按删掉"
                readPhoto != null -> "可以一次贴一串，也可以拍照认手写的清单"
                else -> "可以一次贴一串，按换行或顿号分开"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/** 拍一张或从相册选一张购物清单的照片 */
@Composable
private fun PhotoButton(enabled: Boolean, onPicked: (Uri) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    // 相机拍的照片先存在 cache/camera/，通过 FileProvider 交给相机 App
    val photoUri = remember {
        val file = File(context.cacheDir, "camera/shopping.jpg").apply { parentFile?.mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) onPicked(photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    Box {
        IconButton(onClick = { menu = true }, enabled = enabled) { Icon(MiseIcons.Camera, "拍照识别购物清单") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("拍一张清单") }, onClick = { menu = false; camera.launch(photoUri) })
            DropdownMenuItem(
                text = { Text("从相册选") },
                onClick = {
                    menu = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

/** 照片缩小后交给 AI 认，认完就删掉，不留在图片目录里 */
private suspend fun readShoppingPhoto(app: MiseApp, uri: Uri): List<String> {
    val path = app.images.importUri(uri) ?: throw ImportException("照片打不开，换一张再试")
    return try {
        app.shoppingReader.read(app.images.file(path).readBytes())
    } finally {
        app.images.delete(path)
    }
}

/** 要买菜的一道菜；label 是明细里写的菜名，计划跨几天时带上星期 */
private data class BuyFor(val recipeId: Long, val label: String)

/** 「明天」「10月11日起 7 天」 */
private fun planLabel(from: String, days: Int): String =
    if (days == 1) "${DayText.ahead(from)}的计划" else "${DayText.ahead(from)}起 $days 天的计划"

/** 计划的买菜清单可以切换看几天 */
@Composable
private fun DaysSwitch(days: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 3, 7).forEach { n ->
            FilterChip(
                selected = days == n,
                onClick = { onChange(n) },
                label = { Text(if (n == 1) "这一天" else "$n 天") },
            )
        }
    }
}

/** 一组清单：卡片里一个小标题，下面是勾选行 */
@Composable
private fun ShoppingGroup(
    title: String,
    accent: Color,
    action: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                action()
            }
            content()
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingList.Item, owned: Boolean, accent: Color, onToggle: () -> Unit) {
    CheckRow(
        title = item.name + if (item.seasoning) "（调料）" else "",
        detail = item.detail,
        checked = owned,
        onToggle = onToggle,
        accent = accent,
    )
}
