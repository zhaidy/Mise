package com.personal.mise.ui.bowl

import com.personal.mise.ui.common.FieldShape
import com.personal.mise.ui.common.miseFieldColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.mise.data.Ingredient
import com.personal.mise.data.IngredientKind
import com.personal.mise.ui.common.CircleIconButton
import com.personal.mise.ui.common.MiseHeader
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.PillButton
import com.personal.mise.ui.common.rememberApp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.personal.mise.ui.edit.AiKeyDialog
import com.personal.mise.util.StepPhase
import com.personal.mise.util.UnitText
import kotlin.math.roundToInt

/** 拖动中的一项：来自某个碗（item）或者来自用料（ingredient） */
private data class Dragging(val label: String, val itemKey: Long?, val ingredient: Ingredient?, val position: Offset)

private const val POOL_KEY = -1L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BowlEditScreen(recipeId: Long, onBack: () -> Unit) {
    val app = rememberApp()
    val vm: BowlEditViewModel = viewModel(key = "bowls-$recipeId") { BowlEditViewModel(app, recipeId) }

    val targets = remember { mutableStateMapOf<Long, Rect>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf<Dragging?>(null) }
    var editingItem by remember { mutableStateOf<EditItem?>(null) }
    var addingIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var askingKey by remember { mutableStateOf(false) }
    val cookStart = remember(vm.steps) { StepPhase.cookStart(vm.steps.map { it.text }) }

    fun targetAt(position: Offset): Long? = targets.entries.firstOrNull { (key, rect) ->
        (key == POOL_KEY || vm.bowls.any { it.key == key }) && rect.contains(position)
    }?.key

    fun drop(d: Dragging) {
        val target = targetAt(d.position) ?: return
        if (d.itemKey != null) {
            vm.moveItem(d.itemKey, if (target == POOL_KEY) null else target)
        } else if (d.ingredient != null && target != POOL_KEY) {
            vm.addFromPool(d.ingredient, target)
        }
    }

    /** 长按后拖动；松手时落在哪张卡片上就放进哪个碗 */
    fun Modifier.dragSource(id: Any, coordinates: () -> LayoutCoordinates?, start: (Offset) -> Dragging): Modifier =
        pointerInput(id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val origin = coordinates()?.positionInRoot() ?: Offset.Zero
                    dragging = start(origin + offset)
                },
                onDrag = { change, amount ->
                    change.consume()
                    dragging = dragging?.let { it.copy(position = it.position + amount) }
                },
                onDragEnd = {
                    dragging?.let { drop(it) }
                    dragging = null
                },
                onDragCancel = { dragging = null },
            )
        }

    BackHandler { if (vm.dirty) confirmLeave = true else onBack() }

    Scaffold(
        topBar = {
            MiseHeader("调料碗", onBack = { if (vm.dirty) confirmLeave = true else onBack() }) {
                CircleIconButton(Icons.Filled.Refresh, "重新自动生成", { confirmRegenerate = true })
                PillButton("保存", onClick = { vm.save(onBack) }, enabled = vm.loaded, height = 44.dp)
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .onGloballyPositioned { rootOrigin = it.positionInRoot() },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = dragging == null).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "长按调料拖到另一个碗里；点一下可以改用量或移动。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = vm.loaded && !vm.aiBusy,
                        onClick = { if (vm.hasAiKey) vm.aiOrganize() else askingKey = true },
                        shape = CircleShape,
                    ) { Text("AI 按下锅时机分碗") }
                    Spacer(Modifier.weight(1f))
                    if (vm.aiBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                vm.aiMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                vm.bowls.forEachIndexed { index, bowl ->
                    val highlighted = dragging?.let { targetAt(it.position) == bowl.key } == true
                    Card(
                        Modifier.fillMaxWidth().onGloballyPositioned { targets[bowl.key] = it.boundsInRoot() },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                        border = if (highlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(MiseIcons.Bowl, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(vm.label(index), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                StepPicker(
                                    stepLabel = vm.steps.indexOfFirst { it.id == bowl.stepId }.takeIf { it >= 0 }
                                        ?.let { StepPhase.bowlStepLabel(it, cookStart, vm.steps[it].text) } ?: "选择下锅步骤",
                                    stepCount = vm.steps.size,
                                    onPick = { i -> vm.setStep(bowl.key, i?.let { vm.steps[it].id }) },
                                )
                                IconButton(onClick = { vm.removeBowl(bowl.key) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除这个碗")
                                }
                            }
                            if (bowl.items.isEmpty()) {
                                Text("空碗：把调料拖进来", style = MaterialTheme.typography.bodySmall)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                bowl.items.forEach { item ->
                                    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                    AssistChip(
                                        onClick = { editingItem = item },
                                        label = { Text("${item.name} ${UnitText.display(item.amount)}") },
                                        modifier = Modifier
                                            .onGloballyPositioned { coords = it }
                                            .dragSource(item.key, { coords }) { pos ->
                                                Dragging("${item.name} ${item.amount}", item.key, null, pos)
                                            },
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(onClick = vm::addBowl, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" 新建碗")
                }

                val poolHighlighted = dragging?.itemKey != null && dragging?.let { targetAt(it.position) } == POOL_KEY
                Card(
                    Modifier.fillMaxWidth().onGloballyPositioned { targets[POOL_KEY] = it.boundsInRoot() },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = BorderStroke(
                        if (poolHighlighted) 3.dp else 1.dp,
                        if (poolHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("用料", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "拖进碗里或点一下选择放进哪个碗；把碗里的调料拖回这里就是拿出来",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.pool.forEach { ingredient ->
                                var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                val used = vm.usageCount(ingredient.id)
                                FilterChip(
                                    selected = used > 0,
                                    onClick = { addingIngredient = ingredient },
                                    label = {
                                        val kind = if (ingredient.kind == IngredientKind.SEASONING) "" else "（${ingredient.kind}）"
                                        Text("${ingredient.name}$kind${if (used > 0) " ×$used" else ""}")
                                    },
                                    modifier = Modifier
                                        .onGloballyPositioned { coords = it }
                                        .dragSource("pool-${ingredient.id}", { coords }) { pos ->
                                            Dragging(ingredient.name, null, ingredient, pos)
                                        },
                                )
                            }
                        }
                    }
                }
            }

            dragging?.let { d ->
                Surface(
                    modifier = Modifier.offset {
                        IntOffset(
                            (d.position.x - rootOrigin.x - 40.dp.toPx()).roundToInt(),
                            (d.position.y - rootOrigin.y - 48.dp.toPx()).roundToInt(),
                        )
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                ) {
                    Text(d.label, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    editingItem?.let { item ->
        var amount by remember(item.key) { mutableStateOf(item.amount) }
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text(item.name) },
            text = {
                Column {
                    OutlinedTextField(shape = FieldShape, colors = miseFieldColors(), value = amount, onValueChange = { amount = it }, label = { Text("这碗的用量") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text("移到：", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.bowls.forEachIndexed { index, bowl ->
                            if (bowl.items.none { it.key == item.key }) {
                                AssistChip(
                                    onClick = {
                                        vm.setAmount(item.key, amount)
                                        vm.moveItem(item.key, bowl.key)
                                        editingItem = null
                                    },
                                    label = { Text(vm.label(index)) },
                                )
                            }
                        }
                        AssistChip(
                            onClick = { vm.moveItem(item.key, null); editingItem = null },
                            label = { Text("拿出调料碗") },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setAmount(item.key, amount); editingItem = null }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("取消") } },
        )
    }

    addingIngredient?.let { ingredient ->
        AlertDialog(
            onDismissRequest = { addingIngredient = null },
            title = { Text("把「${ingredient.name}」放进") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.bowls.forEachIndexed { index, bowl ->
                        AssistChip(
                            onClick = { vm.addFromPool(ingredient, bowl.key); addingIngredient = null },
                            label = { Text(vm.label(index)) },
                        )
                    }
                    if (vm.bowls.isEmpty()) Text("还没有碗，先点「新建碗」")
                }
            },
            confirmButton = { TextButton(onClick = { addingIngredient = null }) { Text("取消") } },
        )
    }

    if (askingKey) {
        AiKeyDialog(
            confirmLabel = "保存并分碗",
            onDismiss = { askingKey = false },
            onConfirm = {
                askingKey = false
                vm.aiOrganize()
            },
        )
    }

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text("重新自动生成") },
            text = { Text("按规则重新生成调料碗，会覆盖现在的手动调整。") },
            confirmButton = { TextButton(onClick = { confirmRegenerate = false; vm.regenerate() }) { Text("重新生成") } },
            dismissButton = { TextButton(onClick = { confirmRegenerate = false }) { Text("取消") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("还没保存") },
            text = { Text("放弃这次对调料碗的调整吗？") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("放弃") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("继续编辑") } },
        )
    }
}

@Composable
private fun StepPicker(stepLabel: String, stepCount: Int, onPick: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stepLabel) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("不指定") }, onClick = { open = false; onPick(null) })
            repeat(stepCount) { i ->
                DropdownMenuItem(text = { Text("第 ${i + 1} 步") }, onClick = { open = false; onPick(i) })
            }
        }
    }
}
