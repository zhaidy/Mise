package com.personal.mise.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.personal.mise.MiseApp
import com.personal.mise.ai.AiRecipeEditor
import com.personal.mise.ai.AiRecipeReviewer
import com.personal.mise.ai.AiStepSplitter
import com.personal.mise.ai.ChatTurn
import com.personal.mise.ai.RecipeReview
import com.personal.mise.bowl.SauceBowlGenerator
import com.personal.mise.data.BowlEdit
import com.personal.mise.data.ChatMessageEntity
import com.personal.mise.data.RecipeDraft
import com.personal.mise.data.RecipeSnapshot
import com.personal.mise.data.RecipeText
import com.personal.mise.data.StepDraft
import com.personal.mise.importer.ImportException
import com.personal.mise.importer.UrlExtractor
import com.personal.mise.util.CalorieText
import com.personal.mise.util.DayText
import com.personal.mise.util.RecipeChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 对话里显示的一句话 */
data class ChatItem(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val card: ChatCard? = null,
    /** AI 说的话是哪个模型说的 */
    val model: String? = null,
)

/** 所有「问问 AI」对话；跟着进程走，关掉聊天框后 AI 还在回的话接着收完、存下来 */
class ChatSessions(private val app: MiseApp) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions = mutableMapOf<String, ChatSession>()

    /** 某道菜的对话，按菜谱 uid 存 */
    fun recipe(recipeId: Long, recipeUid: String): ChatSession =
        sessions.getOrPut(recipeUid) { ChatSession(app, scope, recipeUid, recipeId) }

    /** 不针对某道菜的对话 */
    fun global(): ChatSession = sessions.getOrPut(ChatMessageEntity.GLOBAL) { ChatSession(app, scope, ChatMessageEntity.GLOBAL, null) }
}

/**
 * 一段对话：发消息、边收边显示 AI 的回复，回复里带的操作（改菜谱、评价、排计划…）接着做，结果做成卡片。
 * 会改菜谱的结果先是方案，[apply] 了才保存；最近一次应用的可以 [undo]。
 * [recipeId] 为空时是首页的全局对话。
 */
class ChatSession internal constructor(
    private val app: MiseApp,
    private val scope: CoroutineScope,
    private val thread: String,
    val recipeId: Long?,
) {
    private val dao = app.database.chatDao()
    private val repo get() = app.repository

    val items = mutableStateListOf<ChatItem>()
    var loaded by mutableStateOf(false)
        private set
    /** 正在回复或正在做操作 */
    var busy by mutableStateOf(false)
        private set
    /** AI 正在说的话，还没说完；null 表示没在说 */
    var streaming by mutableStateOf<String?>(null)
        private set
    /** 正在做的操作，比如「AI 正在改菜谱…」 */
    var working by mutableStateOf<String?>(null)
        private set
    /** 最近一次应用的方案，还能撤销 */
    var undoable by mutableStateOf<Long?>(null)
        private set

    /** 还没决定的方案怎么应用；只在这次运行里有，App 重开后方案就失效了 */
    private val pending = mutableMapOf<Long, suspend () -> Applied>()
    private var lastApplied: Applied? = null
    private var job: Job? = null
    private val loadJob = scope.launch { load() }

    /** 应用以后怎么撤销；[finish] 是确定不撤销了以后的收尾，比如删掉用不上的步骤图 */
    private class Applied(val undo: suspend () -> Unit, val finish: suspend () -> Unit = {})

    private suspend fun load() {
        val rows = dao.thread(thread)
        items.clear()
        rows.forEach { row ->
            var card = ChatCard.fromJson(row.card)
            // 上次运行时没决定的方案，应用的办法没存下来，标成失效
            if (card is ChatCard.Proposal && card.status == CardStatus.PENDING) {
                card = card.copy(status = CardStatus.EXPIRED)
                dao.setCard(row.id, card.toJson().toString())
            }
            items += ChatItem(row.id, row.fromUser, row.text, card, row.model)
        }
        loaded = true
    }

    // 对外的操作

    fun send(text: String) {
        val ask = text.trim()
        if (ask.isEmpty()) return
        launch {
            add(fromUser = true, text = ask)
            reply(ask)
        }
    }

    /** 工具盘上点的：记一句用户点了什么，直接做，不用先问 AI */
    fun runTool(action: ChatAction, label: String) = launch {
        add(fromUser = true, text = label)
        perform(action)
    }

    /** 出错卡片上的重试：去掉出错的那句，重新回复或重新做 */
    fun retry(item: ChatItem) {
        val error = item.card as? ChatCard.Error ?: return
        launch {
            remove(item.id)
            when {
                error.action != null -> perform(ChatAction.decode(error.action))
                error.retry != null -> reply(error.retry)
            }
        }
    }

    /** 停下正在回复的 AI */
    fun stop() {
        job?.cancel()
    }

    fun apply(item: ChatItem) = launch { applyNow(item) }

    fun dismiss(item: ChatItem) = launch {
        pending.remove(item.id)
        setStatus(item.id, CardStatus.DISMISSED)
    }

    fun undo(item: ChatItem) = launch {
        val applied = lastApplied
        if (undoable != item.id || applied == null) return@launch
        try {
            applied.undo()
            lastApplied = null
            undoable = null
            setStatus(item.id, CardStatus.UNDONE)
        } catch (e: ImportException) {
            // 撤销不了，这次修改就留下了
            finishApplied()
            add(false, "", ChatCard.Error(e.message ?: "撤销失败"))
        }
    }

    /** 导入卡片点了「去导入」：记下来，导入页由界面打开 */
    fun markImported(item: ChatItem) = launch { setStatus(item.id, CardStatus.APPLIED) }

    /** 从工具盘选了图片导入：记一句，导入页由界面打开 */
    fun logImageImport(count: Int) = launch { add(fromUser = true, text = "📷 从 $count 张图片导入菜谱") }

    /** 清空对话；还能撤销的修改就此确定下来 */
    fun clear() {
        job?.cancel()
        scope.launch {
            job?.join()
            finishApplied()
            pending.clear()
            dao.clear(thread)
            items.clear()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        job = scope.launch {
            try {
                loadJob.join()
                block()
            } finally {
                busy = false
                streaming = null
                working = null
            }
        }
    }

    // 回复

    private suspend fun reply(ask: String) {
        val raw = StringBuilder()
        streaming = ""
        try {
            val system = systemPrompt()
            val model = app.aiChat.chat(system, turns()) { delta ->
                raw.append(delta)
                streaming = ChatActions.visible(raw.toString())
            }
            val parsed = ChatActions.parse(raw.toString())
            streaming = null
            if (parsed.text.isNotBlank() || parsed.actions.isEmpty()) add(false, parsed.text.ifBlank { "（AI 没有回复内容）" }, model = model)
            parsed.actions.forEach { perform(it) }
        } catch (e: CancellationException) {
            val partial = ChatActions.visible(raw.toString())
            streaming = null
            if (partial.isNotBlank()) withContext(NonCancellable) { add(false, "$partial\n\n（已停止）") }
            throw e
        } catch (e: Exception) {
            val partial = ChatActions.visible(raw.toString())
            streaming = null
            if (partial.isNotBlank()) add(false, partial)
            add(false, "", ChatCard.Error(errorText(e, "AI 回复失败"), retry = ask))
        }
    }

    private suspend fun systemPrompt(): String {
        val id = recipeId ?: return ChatPrompts.global(repo.loadChatIndex(), repo.loadPlanForChat())
        val full = repo.loadFull(id) ?: throw ImportException("这道菜已经删掉了")
        return ChatPrompts.recipe(full, app.scaleOf(id))
    }

    /** 发给 AI 的对话：出错的不发，卡片换成一句说明；太早的不发 */
    private fun turns(): List<ChatTurn> = items.takeLast(MAX_HISTORY).mapNotNull { item ->
        when {
            item.card is ChatCard.Error -> null
            item.fromUser -> ChatTurn(true, item.text)
            else -> ChatTurn(false, listOf(item.text, item.card?.describe().orEmpty()).filter { it.isNotBlank() }.joinToString("\n"))
        }
    }

    // 操作

    private suspend fun perform(action: ChatAction) {
        val id = recipeId
        try {
            when (action.name) {
                ChatAction.EDIT -> if (id != null) work("AI 正在改菜谱…") {
                    proposeEdit(id, action.arg.ifBlank { throw ImportException("没说清楚要怎么改") }, ProposalKind.EDIT)
                }
                ChatAction.FIX -> if (id != null) work("AI 正在照评价修改…") {
                    val review = RecipeReview.fromJson(repo.loadFull(id)?.recipe?.review)
                        ?: throw ImportException("这道菜还没有评价，先让 AI 评价一下")
                    proposeEdit(id, AiRecipeReviewer.fixInstruction(review), ProposalKind.FIX)
                }
                ChatAction.REVIEW -> if (id != null) work("AI 正在尝这道菜…") { review(id) }
                ChatAction.CALORIES -> if (id != null) work("AI 正在估算热量…") { calories(id) }
                ChatAction.AMOUNTS -> if (id != null) work("AI 正在估用量…") { amounts(id) }
                ChatAction.BOWLS -> if (id != null) work("AI 正在分调料碗…") { bowls(id) }
                ChatAction.SPLIT -> if (id != null) work("AI 正在拆分步骤…") { split(id) }
                ChatAction.TRANSLATE -> if (id != null) work("AI 正在翻译…") { translate(id) }
                ChatAction.NOTE -> if (id != null && action.arg.isNotBlank()) note(id, action.arg)
                ChatAction.PLAN -> plan(action.arg)
                ChatAction.RECIPES -> if (id == null) recipes(action.arg)
                ChatAction.IMPORT -> if (id == null) import()
                ChatAction.GENERATE -> if (id == null && action.arg.isNotBlank()) add(false, "", ChatCard.Generate(action.arg.trim()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            add(false, "", ChatCard.Error(errorText(e, "没做成"), action = action.encode()))
        }
    }

    private suspend fun work(label: String, block: suspend () -> Unit) {
        working = label
        try {
            block()
        } finally {
            working = null
        }
    }

    private suspend fun snapshotOf(id: Long): RecipeSnapshot = repo.snapshot(id) ?: throw ImportException("这道菜已经删掉了")

    private suspend fun proposeEdit(id: Long, instruction: String, kind: ProposalKind) {
        val before = snapshotOf(id)
        val edit = app.recipeEditor.edit(AiRecipeEditor.editable(before.draft), instruction)
        proposeDraft(id, kind, "修改方案", edit.summary, before, AiRecipeEditor.apply(before.draft, edit.recipe))
    }

    /**
     * 把改好的菜谱做成方案卡片。应用前先看菜谱有没有被别处改过，改过就不用这个方案；
     * 应用后去掉的步骤图等确定不撤销了再删。
     */
    private suspend fun proposeDraft(id: Long, kind: ProposalKind, title: String, summary: String, before: RecipeSnapshot, after: RecipeDraft) {
        val changes = RecipeChanges.describe(before.draft, after)
        if (changes.isEmpty()) {
            add(false, summary.ifBlank { "看了一遍，菜谱不用改。" })
            return
        }
        val dropped = AiRecipeEditor.droppedImages(before.draft, after)
        val ingredientsChanged = before.draft.ingredients.map { it.name to it.amountText } != after.ingredients.map { it.name to it.amountText }
        val notes = listOfNotNull(
            "有 ${dropped.size} 张步骤图没地方放，会去掉".takeIf { dropped.isNotEmpty() },
            "用料变了，热量可以重新估算".takeIf { ingredientsChanged && before.draft.calories != null },
        )
        val item = add(false, "", ChatCard.Proposal(kind, title, summary, changes + notes))
        pending[item.id] = {
            ensureUnchanged(id, before)
            repo.save(after, keepImages = dropped)
            val applied = snapshotOf(id)
            Applied(
                undo = {
                    ensureUnchanged(id, applied, UNDO_STALE)
                    repo.restore(before)
                },
                finish = { repo.deleteUnusedImages(id, dropped) },
            )
        }
    }

    private suspend fun ensureUnchanged(id: Long, expected: RecipeSnapshot, message: String = STALE) {
        val now = snapshotOf(id)
        if (now.draft != expected.draft || now.manualBowls != expected.manualBowls) throw ImportException(message)
    }

    private suspend fun review(id: Long) {
        val full = repo.loadFull(id) ?: throw ImportException("这道菜已经删掉了")
        val review = app.recipeReviewer.review(full)
        repo.setReview(id, review)
        add(false, "", ChatCard.Review(review))
    }

    private suspend fun calories(id: Long) {
        val full = repo.loadFull(id) ?: throw ImportException("这道菜已经删掉了")
        val kcal = app.calorieEstimator.estimate(full)
        val old = full.recipe.calories
        val basis = full.recipe.servings?.takeIf { it.isNotBlank() }?.let { "按菜谱原文的份量（$it）算" } ?: "按菜谱原文的用量算"
        val changes = listOfNotNull(basis, old?.let { "菜谱上原来记的是约 $it 千卡" })
        val item = add(false, "", ChatCard.Proposal(ProposalKind.CALORIES, "热量", CalorieText.describe(kcal, full.recipe.servings), changes))
        pending[item.id] = {
            repo.setCalories(id, kcal)
            Applied(undo = { repo.setCalories(id, old) })
        }
    }

    private suspend fun amounts(id: Long) {
        val before = snapshotOf(id)
        val draft = before.draft
        val rows = draft.ingredients.withIndex().filter { it.value.name.isNotBlank() }
        val result = app.amountEstimator.estimate(
            draft.title,
            draft.servings.orEmpty(),
            rows.map { it.value.name to it.value.amountText },
            draft.steps.map { it.text },
        )
        val byDraftIndex = result.mapKeys { (i, _) -> rows[i].index }
        val after = draft.copy(ingredients = draft.ingredients.mapIndexed { i, ing -> byDraftIndex[i]?.let { ing.copy(amountText = it) } ?: ing })
        proposeDraft(id, ProposalKind.AMOUNTS, "补全用量", "估的用量标了「估算」，补的克重写在「≈」后面", before, after)
    }

    private suspend fun split(id: Long) {
        val before = snapshotOf(id)
        val draft = before.draft
        val old = draft.steps.filter { it.text.isNotBlank() || it.image != null }
        val result = app.stepSplitter.split(old.map { it.text })
        val carry = AiStepSplitter.carryOver(old.map { it.image != null }, result)
            ?: throw ImportException("AI 拆分后对不上原来的步骤图，步骤没有改")
        val fromOld = carry.entries.associate { (i, j) -> j to old[i] }
        val steps = result.mapIndexed { j, s ->
            val from = fromOld[j]
            StepDraft(
                id = from?.id ?: 0,
                text = s.text,
                image = from?.image,
                timerSeconds = s.timerMinutes?.let { (it * 60).toInt() }?.takeIf { it > 0 } ?: from?.timerSeconds,
            )
        }
        proposeDraft(id, ProposalKind.SPLIT, "拆分步骤", "${old.size} 步拆成 ${steps.size} 步", before, draft.copy(steps = steps))
    }

    private suspend fun translate(id: Long) {
        val before = snapshotOf(id)
        val draft = before.draft
        val ingredients = draft.ingredients.filter { it.name.isNotBlank() }
        val steps = draft.steps.filter { it.text.isNotBlank() }
        val source = RecipeText(draft.title, draft.servings, ingredients.map { it.name to it.amountText }, steps.map { it.text })
        val translated = app.translator.translate(source)
        var ingredientAt = 0
        var stepAt = 0
        val after = draft.copy(
            title = translated.title,
            servings = translated.servings,
            ingredients = draft.ingredients.map { ing ->
                if (ing.name.isBlank()) ing
                else translated.ingredients[ingredientAt++].let { (name, amount) -> ing.copy(name = name, amountText = amount) }
            },
            steps = draft.steps.map { step -> if (step.text.isBlank()) step else step.copy(text = translated.steps[stepAt++]) },
            original = draft.original ?: source,
        )
        proposeDraft(id, ProposalKind.TRANSLATE, "翻译成中文", "原文留着，详情页可以切回去看", before, after)
    }

    private suspend fun note(id: Long, text: String) {
        val old = repo.loadFull(id)?.recipe?.note?.takeIf { it.isNotBlank() }
        val changes = listOfNotNull(old?.let { "接在原来的批注后面：$it" })
        val item = add(false, "", ChatCard.Proposal(ProposalKind.NOTE, "批注", text.trim(), changes))
        pending[item.id] = {
            repo.setNote(id, listOfNotNull(old, text.trim()).joinToString("\n"))
            Applied(undo = { repo.setNote(id, old.orEmpty()) })
        }
    }

    private suspend fun bowls(id: Long) {
        val before = snapshotOf(id)
        val full = repo.loadFull(id) ?: throw ImportException("这道菜已经删掉了")
        val result = app.bowlOrganizer.organize(
            full.ingredients.map { it.name to it.amountText },
            full.steps.map { it.text },
            full.ingredients.map { it.kind },
        )
        val edits = result.filter { it.items.isNotEmpty() }.mapIndexed { i, bowl ->
            BowlEdit(
                SauceBowlGenerator.label(i),
                bowl.stepIndex?.let { full.steps.getOrNull(it)?.id },
                bowl.items.map { (index, amount) -> full.ingredients[index].id to amount },
            )
        }
        if (edits.isEmpty()) throw ImportException("AI 没分出调料碗")
        val changes = edits.map { bowl ->
            val step = full.steps.indexOfFirst { it.id == bowl.stepId }.takeIf { it >= 0 }?.let { "（第 ${it + 1} 步下锅）" }.orEmpty()
            val names = bowl.items.joinToString("、") { (ingredientId, amount) ->
                val name = full.ingredients.first { it.id == ingredientId }.name
                if (amount.isBlank()) name else "$name $amount"
            }
            "${bowl.label}$step：$names"
        }
        val item = add(false, "", ChatCard.Proposal(ProposalKind.BOWLS, "调料碗", "按下锅时机分成 ${edits.size} 碗", changes))
        pending[item.id] = {
            ensureUnchanged(id, before)
            repo.saveBowls(id, edits)
            val applied = snapshotOf(id)
            Applied(undo = {
                ensureUnchanged(id, applied, UNDO_STALE)
                repo.restore(before)
                // 原来的碗是自动分的：恢复成自动分，以后改菜谱还会跟着重新分
                if (before.manualBowls == null) repo.regenerateBowls(id)
            })
        }
    }

    private suspend fun plan(arg: String) {
        val date = DATE.find(arg)?.value?.takeIf { DayText.parse(it) != null } ?: throw ImportException("没看懂要排在哪天")
        val ids = NUMBER.findAll(arg.replace(date, "")).mapNotNull { it.value.toLongOrNull() }.toList().ifEmpty { listOfNotNull(recipeId) }
        val found = ids.distinct().mapNotNull { id -> repo.loadFull(id)?.let { id to it.recipe.title } }
        if (found.isEmpty()) throw ImportException("没找到要排的菜")
        add(false, "", ChatCard.Plan(date, found.map { it.first }, found.map { it.second }))
    }

    private suspend fun recipes(arg: String) {
        val found = NUMBER.findAll(arg).mapNotNull { it.value.toLongOrNull() }.distinct().toList()
            .mapNotNull { id -> repo.loadFull(id)?.let { id to it.recipe.title } }
        if (found.isNotEmpty()) add(false, "", ChatCard.Recipes(found.map { it.first }, found.map { it.second }))
    }

    /** 把用户刚发的那条交给导入：像链接的按链接导入，否则当笔记让 AI 整理 */
    private suspend fun import() {
        val text = items.lastOrNull { it.fromUser }?.text?.trim().orEmpty()
        if (text.isEmpty()) return
        val link = UrlExtractor.isLinkShare(text)
        add(false, "", ChatCard.Import(if (link) UrlExtractor.firstUrl(text) ?: text else text, link))
    }

    // 应用和撤销

    private suspend fun applyNow(item: ChatItem) {
        val card = items.firstOrNull { it.id == item.id }?.card ?: return
        try {
            val applied = when (card) {
                is ChatCard.Proposal -> {
                    if (card.status != CardStatus.PENDING) return
                    val change = pending[item.id]
                    if (change == null) {
                        setStatus(item.id, CardStatus.EXPIRED)
                        return
                    }
                    change()
                }
                is ChatCard.Plan -> {
                    if (card.status != CardStatus.PENDING) return
                    val added = card.ids.filter { repo.addToPlan(it, card.date) }.mapNotNull { repo.planUid(it, card.date) }
                    Applied(undo = { added.forEach { repo.removeFromPlan(it) } })
                }
                else -> return
            }
            pending.remove(item.id)
            finishApplied()
            lastApplied = applied
            undoable = item.id
            setStatus(item.id, CardStatus.APPLIED)
            // 照评价改好了，按改好的菜谱重新评一次分
            if (recipeId != null && card is ChatCard.Proposal && card.kind == ProposalKind.FIX) perform(ChatAction(ChatAction.REVIEW))
        } catch (e: ImportException) {
            if (e.message == STALE) {
                pending.remove(item.id)
                setStatus(item.id, CardStatus.EXPIRED)
            }
            add(false, "", ChatCard.Error(e.message ?: "没用上"))
        }
    }

    /** 上一次应用的不再能撤销了，做收尾 */
    private suspend fun finishApplied() {
        lastApplied?.let { withContext(NonCancellable) { it.finish() } }
        lastApplied = null
        undoable = null
    }

    // 存取

    private suspend fun add(fromUser: Boolean, text: String, card: ChatCard? = null, model: String? = null): ChatItem {
        val id = dao.insert(ChatMessageEntity(thread = thread, fromUser = fromUser, text = text, card = card?.toJson()?.toString(), model = model))
        return ChatItem(id, fromUser, text, card, model).also { items += it }
    }

    private suspend fun remove(id: Long) {
        dao.delete(id)
        items.removeAll { it.id == id }
    }

    private suspend fun setStatus(id: Long, status: CardStatus) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val card = when (val c = items[index].card) {
            is ChatCard.Proposal -> c.copy(status = status)
            is ChatCard.Plan -> c.copy(status = status)
            is ChatCard.Import -> c.copy(status = status)
            is ChatCard.Generate -> c.copy(status = status)
            else -> return
        }
        items[index] = items[index].copy(card = card)
        dao.setCard(id, card.toJson().toString())
    }

    private fun errorText(e: Exception, prefix: String): String =
        if (e is ImportException) e.message ?: prefix else "$prefix：${e.message ?: e.javaClass.simpleName}"

    companion object {
        /** 发给 AI 的最近几句；再早的不发，免得越聊越慢越贵 */
        private const val MAX_HISTORY = 40
        const val STALE = "菜谱在这之后又改过了，这个方案对不上，需要的话再让 AI 改一次"
        private const val UNDO_STALE = "菜谱在这之后又改过了，没法撤销"
        private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val NUMBER = Regex("\\d+")
    }
}
