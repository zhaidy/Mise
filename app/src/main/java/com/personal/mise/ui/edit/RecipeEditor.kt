package com.personal.mise.ui.edit

import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personal.mise.data.IngredientKind
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.LocalImage
import com.personal.mise.ui.common.SectionHeader
import com.personal.mise.ui.common.SoftCard
import com.personal.mise.ui.theme.SerifFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight

/** 菜谱编辑表单：编辑页和导入预览页共用 */
@Composable
fun RecipeEditor(
    vm: EditorViewModel,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    var pickingStepKey by rememberSaveable { mutableStateOf<Long?>(null) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.pickCover(it) }
    }
    val pickStep = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = pickingStepKey
        if (uri != null && key != null) vm.pickStepImage(key, uri)
        pickingStepKey = null
    }
    // 填完 Key 后要接着做的 AI 操作：split 拆分步骤、estimate 估算用量和克重、calories 估算热量
    var askingKeyFor by rememberSaveable { mutableStateOf<String?>(null) }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    askingKeyFor?.let { action ->
        AiKeyDialog(
            confirmLabel = if (action == "split") "保存并拆分" else "保存并估算",
            onDismiss = { askingKeyFor = null },
            onConfirm = {
                askingKeyFor = null
                when (action) {
                    "estimate" -> vm.aiEstimateAmounts()
                    "calories" -> vm.aiEstimateCalories()
                    else -> vm.aiSplitSteps()
                }
            },
        )
    }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 32.dp)) {
        item { header() }
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { pickCover.launch(imageOnly) },
            ) {
                LocalImage(vm.cover, Modifier.fillMaxWidth().aspectRatio(16f / 9f), placeholder = "点这里选封面图")
                if (vm.cover != null) {
                    CircleIconButton(
                        Icons.Filled.Close,
                        "去掉封面",
                        vm::removeCover,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        container = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    value = vm.title,
                    onValueChange = { vm.title = it },
                    label = { Text("菜名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    value = vm.servings,
                    onValueChange = { vm.servings = it },
                    label = { Text("份量（可不填）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                CategoryField(vm)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        shape = FieldShape,
                        colors = miseFieldColors(),
                        value = vm.calories,
                        onValueChange = vm::updateCalories,
                        label = { Text("整道菜热量（千卡，可不填）") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (vm.estimatingCalories) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            enabled = vm.canEstimateCalories,
                            onClick = { if (vm.hasAiKey) vm.aiEstimateCalories() else askingKeyFor = "calories" },
                        ) { Text(if (vm.calories.isBlank()) "AI 估算" else "重新估算") }
                    }
                }
                val caloriesHint = if (vm.caloriesStale && !vm.estimatingCalories) "用料改过了，热量可能要重新估算" else vm.caloriesMessage
                caloriesHint?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vm.caloriesStale) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        item { EditorSection("用料", "点「主料／调料」可以切换分类") }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !vm.estimating && vm.canEstimate,
                        onClick = { if (vm.hasAiKey) vm.aiEstimateAmounts() else askingKeyFor = "estimate" },
                    ) { Text("AI 估算用量和克重") }
                    if (vm.estimating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                vm.estimateMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        itemsIndexed(vm.ingredients, key = { _, row -> "ing-${row.key}" }) { index, row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    value = row.name,
                    onValueChange = { vm.updateIngredientName(index, it) },
                    placeholder = { Text("名称") },
                    modifier = Modifier.weight(1.3f),
                    singleLine = true,
                )
                OutlinedTextField(
                    shape = FieldShape,
                    colors = miseFieldColors(),
                    value = row.amount,
                    onValueChange = { vm.updateIngredientAmount(index, it) },
                    placeholder = { Text("用量") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                FilterChip(
                    selected = row.kind == IngredientKind.SEASONING,
                    onClick = { vm.toggleIngredientKind(index) },
                    label = { Text(row.kind) },
                )
                IconButton(onClick = { vm.removeIngredient(index) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "删除用料")
                }
            }
        }
        item {
            TextButton(onClick = vm::addIngredient, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加用料")
            }
        }

        item { EditorSection("步骤", "计时填分钟数，做菜时可以一键开始计时") }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !vm.aiBusy && vm.steps.any { it.text.isNotBlank() },
                        onClick = { if (vm.hasAiKey) vm.aiSplitSteps() else askingKeyFor = "split" },
                    ) { Text(if (vm.stepsAiSplit) "AI 重新拆分" else "AI 拆分步骤") }
                    if (vm.aiBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    TextButton(onClick = { askingKeyFor = "split" }) { Text("设置 Key") }
                }
                vm.aiMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (vm.stepsAiSplit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        itemsIndexed(vm.steps, key = { _, row -> "step-${row.key}" }) { index, row ->
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(28.dp).background(MaterialTheme.colorScheme.onSurface, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.background, fontFamily = SerifFamily, fontWeight = FontWeight.Bold)
                        }
                        Text("第 ${index + 1} 步", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        IconButton(onClick = { vm.moveStep(index, -1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(onClick = { vm.moveStep(index, 1) }, enabled = index < vm.steps.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                        }
                        IconButton(onClick = { vm.removeStep(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "删除步骤")
                        }
                    }
                    OutlinedTextField(
                        shape = FieldShape,
                        colors = miseFieldColors(),
                        value = row.text,
                        onValueChange = { vm.updateStepText(index, it) },
                        placeholder = { Text("这一步做什么") },
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            shape = FieldShape,
                            colors = miseFieldColors(),
                            value = row.timerMinutes,
                            onValueChange = { vm.updateStepTimer(index, it) },
                            label = { Text("计时（分钟）") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            pickingStepKey = row.key
                            pickStep.launch(imageOnly)
                        }) { Text(if (row.image == null) "加图片" else "换图片") }
                    }
                    if (row.image != null) {
                        Box {
                            LocalImage(
                                row.image,
                                Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)),
                            )
                            IconButton(onClick = { vm.removeStepImage(index) }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Filled.Close, contentDescription = "去掉图片")
                            }
                        }
                    }
                }
            }
        }
        item {
            TextButton(onClick = vm::addStep, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加步骤")
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, hint: String) {
    SectionHeader(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp), hint = hint)
}

/**
 * 分类和标签：大类、小类可以直接输入新的，也可以点下面已有的；再点一次选中的就清空。
 * 标签输入时打逗号、空格或 # 就算一个，下面列出别的菜谱用过的标签。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryField(vm: EditorViewModel) {
    val tree by vm.categories.collectAsStateWithLifecycle(emptyList())
    val allTags by vm.allTags.collectAsStateWithLifecycle(emptyList())
    OutlinedTextField(
        shape = FieldShape,
        colors = miseFieldColors(),
        value = vm.category,
        onValueChange = { vm.category = it },
        label = { Text("大类（可不填，如 家常菜、汤、甜点）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    PickChips(tree.map { it.name }, vm.category.trim()) { vm.selectCategory(it) }

    if (vm.category.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            shape = FieldShape,
            colors = miseFieldColors(),
            value = vm.subcategory,
            onValueChange = { vm.subcategory = it },
            label = { Text("小类（可不填，如 炒菜、炖菜）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        val subs = tree.find { it.name == vm.category.trim() }?.subcategories.orEmpty()
        PickChips(subs, vm.subcategory.trim()) { vm.subcategory = it }
    }

    Spacer(Modifier.height(8.dp))
    if (vm.tags.isNotEmpty()) {
        FlowRow(
            Modifier.padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { vm.removeTag(tag) },
                    label = { Text("#$tag") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "去掉", Modifier.size(16.dp)) },
                )
            }
        }
    }
    OutlinedTextField(
        shape = FieldShape,
        colors = miseFieldColors(),
        value = vm.tagInput,
        onValueChange = vm::updateTagInput,
        label = { Text("加标签（如 荤菜、烘焙、好吃）") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.commitTagInput() }),
        trailingIcon = {
            if (vm.tagInput.isNotBlank()) {
                IconButton(onClick = vm::commitTagInput) { Icon(Icons.Filled.Add, contentDescription = "添加标签") }
            }
        },
    )
    val suggestions = allTags.filter { it !in vm.tags }
    if (suggestions.isNotEmpty()) {
        FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { tag ->
                FilterChip(selected = false, onClick = { vm.addTag(tag) }, label = { Text("#$tag") })
            }
        }
    }
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
