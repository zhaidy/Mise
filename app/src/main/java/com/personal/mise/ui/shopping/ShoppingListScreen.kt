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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.RecipeFull
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.ShoppingList
import com.personal.mise.util.UnitText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(onBack: () -> Unit) {
    val app = rememberApp()
    val context = LocalContext.current
    val rows by app.repository.observeToday().collectAsStateWithLifecycle(emptyList())
    val ids = rows.map { it.recipeId }
    val fulls by remember(ids) {
        if (ids.isEmpty()) flowOf(emptyList<RecipeFull?>())
        else combine(ids.map { app.repository.observeFull(it) }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())
    val have by app.pantry.have.collectAsStateWithLifecycle()

    val sources = rows.flatMap { row ->
        val full = fulls.firstOrNull { it?.recipe?.id == row.recipeId } ?: return@flatMap emptyList()
        val factor = app.scaleOf(row.recipeId)
        full.ingredients.sortedBy { it.order }.map {
            ShoppingList.Source(row.title, it.name, UnitText.display(AmountScaler.scale(it.amountText, factor)), it.kind)
        }
    }
    val items = ShoppingList.build(sources)
    val (owned, toBuy) = items.partition { it.key in have }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("购物清单") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (toBuy.isNotEmpty()) {
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, ShoppingList.shareText(toBuy))
                            context.startActivity(Intent.createChooser(send, "发送购物清单"))
                        }) { Icon(Icons.Filled.Share, contentDescription = "发送要买的") }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (rows.isEmpty()) "今日菜单是空的" else "今日的菜还没有填用料",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item(key = "hint") {
                Text(
                    if (toBuy.isEmpty()) "都有了，不用买" else "要买 ${toBuy.size} 样。家里有的勾上，会挪到下面，下次也记得。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (toBuy.isNotEmpty()) {
                item(key = "head-buy") { SectionHeader("要买") }
                items(toBuy, key = { "buy-${it.key}" }) { item ->
                    ShoppingRow(item, owned = false) { app.pantry.toggle(item.key) }
                }
            }
            if (owned.isNotEmpty()) {
                item(key = "head-have") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionHeader("家里有") }
                        TextButton(
                            onClick = { app.pantry.clear(owned.map { it.key }) },
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("全部取消") }
                    }
                }
                items(owned, key = { "have-${it.key}" }) { item ->
                    ShoppingRow(item, owned = true) { app.pantry.toggle(item.key) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ShoppingRow(item: ShoppingList.Item, owned: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = owned, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                item.name + if (item.seasoning) "（调料）" else "",
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (owned) TextDecoration.LineThrough else null,
                color = if (owned) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (item.detail.isNotBlank()) {
                Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 56.dp))
}
