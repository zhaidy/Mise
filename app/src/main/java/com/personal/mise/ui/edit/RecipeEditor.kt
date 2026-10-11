package com.personal.mise.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personal.mise.data.IngredientKind
import com.personal.mise.ui.common.LocalImage

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
    // 填完 Key 后要接着做的 AI 操作：split 拆分步骤、estimate 估算用量和克重
    var askingKeyFor by rememberSaveable { mutableStateOf<String?>(null) }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    askingKeyFor?.let { action ->
        AiKeyDialog(
            confirmLabel = if (action == "estimate") "保存并估算" else "保存并拆分",
            onDismiss = { askingKeyFor = null },
            onConfirm = {
                askingKeyFor = null
                if (action == "estimate") vm.aiEstimateAmounts() else vm.aiSplitSteps()
            },
        )
    }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 32.dp)) {
        item { header() }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clickable { pickCover.launch(imageOnly) },
            ) {
                LocalImage(vm.cover, Modifier.fillMaxWidth().aspectRatio(16f / 9f), placeholder = "点这里选封面图")
                if (vm.cover != null) {
                    IconButton(onClick = vm::removeCover, modifier = Modifier.align(Alignment.TopEnd)) {
                        Icon(Icons.Filled.Close, contentDescription = "去掉封面")
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = vm.title,
                    onValueChange = { vm.title = it },
                    label = { Text("菜名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.servings,
                    onValueChange = { vm.servings = it },
                    label = { Text("份量（可不填）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        item { EditorSection("用料", "点「主料／调料」可以切换分类") }
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
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
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { vm.updateIngredientName(index, it) },
                    placeholder = { Text("名称") },
                    modifier = Modifier.weight(1.3f),
                    singleLine = true,
                )
                OutlinedTextField(
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
            TextButton(onClick = vm::addIngredient, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加用料")
            }
        }

        item { EditorSection("步骤", "计时填分钟数，做菜时可以一键开始计时") }
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
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
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${index + 1} 步", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
                        value = row.text,
                        onValueChange = { vm.updateStepText(index, it) },
                        placeholder = { Text("这一步做什么") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
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
                                Modifier.fillMaxWidth().aspectRatio(4f / 3f).padding(top = 8.dp).clip(RoundedCornerShape(8.dp)),
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
            TextButton(onClick = vm::addStep, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加步骤")
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, hint: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
