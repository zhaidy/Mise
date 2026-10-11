package com.personal.mise.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.mise.data.Recipe
import com.personal.mise.data.RecipeTags
import com.personal.mise.data.tagList
import kotlinx.coroutines.launch

/** 一道菜正在选的大类、小类和标签；编辑页和快捷设置共用 */
class ClassifyState(category: String? = null, subcategory: String? = null, tags: List<String> = emptyList()) {
    /** 大类，空表示未分类 */
    var category by mutableStateOf(category.orEmpty())
    /** 大类下的小类 */
    var subcategory by mutableStateOf(subcategory.orEmpty())
    val tags = mutableStateListOf<String>().apply { addAll(tags) }
    /** 正在输入、还没加进去的标签 */
    var tagInput by mutableStateOf("")
        private set

    fun fill(category: String?, subcategory: String?, tags: List<String>) {
        this.category = category.orEmpty()
        this.subcategory = subcategory.orEmpty()
        this.tags.clear()
        this.tags += tags
        tagInput = ""
    }

    fun selectCategory(name: String) {
        if (name != category.trim()) subcategory = ""
        category = name
    }

    /** 输入里出现逗号、空格或 # 时，前面的部分就算一个标签 */
    fun updateTagInput(text: String) {
        val parts = text.split(Regex("[,，、#＃\\s]"))
        if (parts.size > 1) {
            parts.dropLast(1).forEach { addTag(it) }
            tagInput = parts.last()
        } else {
            tagInput = text
        }
    }

    fun commitTagInput() {
        addTag(tagInput)
        tagInput = ""
    }

    fun addTag(text: String) {
        RecipeTags.parse(text).forEach { if (it !in tags) tags += it }
    }

    fun removeTag(tag: String) {
        tags.remove(tag)
    }

    /** 输了还没按「添加」的标签也算上 */
    fun allTags(): List<String> = (tags + RecipeTags.parse(tagInput)).distinct()
}

/**
 * 分类和标签：大类、小类可以直接输入新的，也可以点下面已有的；再点一次选中的就清空。
 * 标签输入时打逗号、空格或 # 就算一个，下面列出别的菜谱用过的标签。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassifyFields(state: ClassifyState) {
    val app = rememberApp()
    val tree by remember { app.repository.observeCategories() }.collectAsStateWithLifecycle(emptyList())
    val allTags by remember { app.repository.observeTags() }.collectAsStateWithLifecycle(emptyList())
    Column {
        OutlinedTextField(
            shape = FieldShape,
            colors = miseFieldColors(),
            value = state.category,
            onValueChange = { state.category = it },
            label = { OneLine("大类（可不填）") },
            placeholder = { OneLine("如 家常菜、汤、甜点") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        PickChips(tree.map { it.name }, state.category.trim()) { state.selectCategory(it) }

        if (state.category.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                shape = FieldShape,
                colors = miseFieldColors(),
                value = state.subcategory,
                onValueChange = { state.subcategory = it },
                label = { OneLine("小类（可不填）") },
                placeholder = { OneLine("如 炒菜、炖菜") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            val subs = tree.find { it.name == state.category.trim() }?.subcategories.orEmpty()
            PickChips(subs, state.subcategory.trim()) { state.subcategory = it }
        }

        Spacer(Modifier.height(8.dp))
        if (state.tags.isNotEmpty()) {
            FlowRow(
                Modifier.padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { state.removeTag(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "去掉", Modifier.size(16.dp)) },
                    )
                }
            }
        }
        OutlinedTextField(
            shape = FieldShape,
            colors = miseFieldColors(),
            value = state.tagInput,
            onValueChange = state::updateTagInput,
            label = { OneLine("加标签（可不填）") },
            placeholder = { OneLine("如 荤菜、烘焙、好吃") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { state.commitTagInput() }),
            trailingIcon = {
                if (state.tagInput.isNotBlank()) {
                    IconButton(onClick = state::commitTagInput) { Icon(Icons.Filled.Add, contentDescription = "添加标签") }
                }
            },
        )
        val suggestions = allTags.filter { it !in state.tags }
        if (suggestions.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { tag ->
                    FilterChip(selected = false, onClick = { state.addTag(tag) }, label = { Text("#$tag") })
                }
            }
        }
    }
}

/** 输入框的提示只占一行，放不下就省略，免得输入框被撑高 */
@Composable
private fun OneLine(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 已有的名字，点一下选中，再点一下清空 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickChips(names: List<String>, current: String, onPick: (String) -> Unit) {
    if (names.isEmpty()) return
    FlowRow(
        Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        names.forEach { name ->
            val selected = current == name
            FilterChip(
                selected = selected,
                onClick = { onPick(if (selected) "" else name) },
                label = { Text(name) },
            )
        }
    }
}

/** 不进编辑页，直接改一道菜的分类和标签；保存后会同步 */
@Composable
fun ClassifyDialog(recipe: Recipe, onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    val state = remember(recipe.id) { ClassifyState(recipe.category, recipe.subcategory, recipe.tagList) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类和标签") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    recipe.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ClassifyFields(state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    app.repository.setClassification(recipe.id, state.category, state.subcategory, state.allTags())
                    onSaved()
                    onDismiss()
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 要改名或删除的是哪一个：大类、某个大类下的小类，或标签 */
private sealed interface ClassifyTarget {
    val name: String
    data class Category(override val name: String) : ClassifyTarget
    data class Subcategory(val category: String, override val name: String) : ClassifyTarget
    data class Tag(override val name: String) : ClassifyTarget
}

/** 管理所有大类、小类和标签：点一下可以改名或删除，所有用到它的菜谱一起改 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClassifyManageDialog(onDismiss: () -> Unit) {
    val app = rememberApp()
    val tree by remember { app.repository.observeCategories() }.collectAsStateWithLifecycle(emptyList())
    val allTags by remember { app.repository.observeTags() }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<ClassifyTarget?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理分类和标签") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tree.isEmpty() && allTags.isEmpty()) {
                    Text(
                        "还没有分类和标签。在菜谱详情页点「分类和标签」，或编辑菜谱时就能加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tree.isNotEmpty()) {
                    Text("大类和小类", style = MaterialTheme.typography.titleSmall)
                    tree.forEach { node ->
                        Column {
                            ManageRow(node.name) { editing = ClassifyTarget.Category(node.name) }
                            if (node.subcategories.isNotEmpty()) {
                                FlowRow(
                                    Modifier.padding(start = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    node.subcategories.forEach { sub ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { editing = ClassifyTarget.Subcategory(node.name, sub) },
                                            label = { Text(sub) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (allTags.isNotEmpty()) {
                    Text("标签", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTags.forEach { tag ->
                            FilterChip(selected = false, onClick = { editing = ClassifyTarget.Tag(tag) }, label = { Text("#$tag") })
                        }
                    }
                }
                Text(
                    "点一下可以改名或删除，用到它的菜谱会一起改。改成已有的名字就是合并。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )

    editing?.let { target -> RenameDialog(target, onDismiss = { editing = null }) }
}

@Composable
private fun ManageRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Edit, contentDescription = "改名", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RenameDialog(target: ClassifyTarget, onDismiss: () -> Unit) {
    val app = rememberApp()
    val scope = rememberCoroutineScope()
    var text by remember(target) { mutableStateOf(target.name) }
    var confirmDelete by remember(target) { mutableStateOf(false) }
    val kind = when (target) {
        is ClassifyTarget.Category -> "大类"
        is ClassifyTarget.Subcategory -> "小类"
        is ClassifyTarget.Tag -> "标签"
    }

    fun apply(newName: String) {
        scope.launch {
            val repo = app.repository
            when (target) {
                is ClassifyTarget.Category -> repo.renameCategory(target.name, newName)
                is ClassifyTarget.Subcategory -> repo.renameSubcategory(target.category, target.name, newName)
                is ClassifyTarget.Tag -> repo.renameTag(target.name, newName)
            }
            onDismiss()
        }
    }

    if (confirmDelete) {
        val effect = when (target) {
            is ClassifyTarget.Category -> "用到它的菜谱会变成未分类，小类也一起去掉。"
            is ClassifyTarget.Subcategory -> "用到它的菜谱还留在「${target.category}」里。"
            is ClassifyTarget.Tag -> "所有菜谱都会去掉这个标签。"
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("删除$kind「${target.name}」？") },
            text = { Text("菜谱本身不会删。$effect") },
            confirmButton = { TextButton(onClick = { apply("") }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$kind「${target.name}」") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("新名字") },
                singleLine = true,
                shape = FieldShape,
                colors = miseFieldColors(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && text.trim() != target.name,
                onClick = { apply(text) },
            ) { Text("改名") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
