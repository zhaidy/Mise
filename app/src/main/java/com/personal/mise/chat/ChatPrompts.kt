package com.personal.mise.chat

import com.personal.mise.ai.AiAmountEstimator
import com.personal.mise.ai.AiRecipeTranslator
import com.personal.mise.ai.RecipeReview
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeFull
import com.personal.mise.data.RecipeText
import com.personal.mise.util.AmountScaler
import com.personal.mise.util.DayText
import java.time.LocalDate

/** 全局对话里给 AI 看的一道菜 */
data class ChatRecipeInfo(
    val id: Long,
    val title: String,
    val category: String?,
    val tags: List<String>,
    val ingredients: List<String>,
    val calories: Int?,
    /** 最后一次做的日子，没做过为空 */
    val lastCooked: String?,
)

/** 全局对话里给 AI 看的一条计划 */
data class ChatPlanInfo(val date: String, val title: String, val done: Boolean)

/** 这道菜现在能用的 AI 功能，工具盘和提示词共用 */
data class RecipeTools(
    val review: Boolean,
    val fix: Boolean,
    val calories: Boolean,
    val amounts: Boolean,
    val bowls: Boolean,
    val split: Boolean,
    val translate: Boolean,
) {
    companion object {
        /** 有一步超过这么多字，就像是好几件事挤在一步里，值得拆一拆 */
        private const val LONG_STEP = 80

        fun of(full: RecipeFull): RecipeTools {
            val ingredients = full.ingredients.filter { it.name.isNotBlank() }
            val steps = full.steps.filter { it.text.isNotBlank() }
            val review = RecipeReview.fromJson(full.recipe.review)
            return RecipeTools(
                review = ingredients.isNotEmpty() || steps.isNotEmpty(),
                fix = review != null && review.concerns.isNotEmpty(),
                calories = ingredients.isNotEmpty(),
                amounts = AiAmountEstimator.targets(ingredients.map { it.name to it.amountText }).isNotEmpty(),
                bowls = ingredients.isNotEmpty() && steps.isNotEmpty(),
                split = steps.any { it.text.length > LONG_STEP },
                translate = AiRecipeTranslator.isEnglish(
                    RecipeText(full.recipe.title, full.recipe.servings, ingredients.map { it.name to it.amountText }, steps.map { it.text }),
                ),
            )
        }
    }
}

/** 「问问 AI」的系统提示：说话的规矩、这次能看到的内容、能让 App 做的事 */
object ChatPrompts {

    private const val STYLE = "你是做菜 App「Mise」里的做菜助手，像一个懂行、说话直接的朋友。用简体中文回答，简洁口语化，先说结论。\n" +
        "回复是纯文字：可以用「- 」开头列点，用 **两个星号** 标出重点；不要用标题、表格、代码块和表情符号。\n"

    private const val MARKER_RULES = "你还可以让 App 帮用户做下面这些事。需要时在回复的最后另起一行写操作标记，一行一个，可以写几个。" +
        "App 会去执行，把结果做成卡片给用户看；会改菜谱的都要用户点确认才会生效。" +
        "标记不会显示给用户，回复里不要提到标记本身，也不要说已经改好了，可以说「我把方案列在下面了」。" +
        "只是聊天、回答问题时不要写标记。\n"

    private fun todayLine(today: LocalDate) = "今天是 $today（${DayText.weekday(today.toString())}）。\n"

    fun recipe(full: RecipeFull, factor: Double, today: LocalDate = LocalDate.now()): String = buildString {
        append(STYLE)
        append(todayLine(today))
        append("用户正在看下面这道菜谱，问题默认都是关于它的。\n\n")
        append(describe(full, factor))
        append('\n')
        append(MARKER_RULES)
        val tools = RecipeTools.of(full)
        append("<<edit: 一句话写清楚要怎么改>> 修改这份菜谱：换份量、换食材、改做法、调口味、把你说的建议写进步骤等。用户明确想改菜谱时才用。\n")
        if (tools.review) append("<<review>> 从口味上给这道菜打分评价，结果会存到菜谱上。\n")
        if (tools.fix) append("<<fix>> 按菜谱上已有评价里要注意的地方改菜谱。\n")
        if (tools.calories) append("<<calories>> 估算整道菜的热量。\n")
        if (tools.amounts) append("<<amounts>> 给写着「适量」或没写用量的用料估一个具体用量，并给不是按克写的用量补上大概克重。\n")
        if (tools.bowls) append("<<bowls>> 按下锅时机重新分调料碗。\n")
        if (tools.split) append("<<split>> 把挤在一起的步骤拆成一步一步。\n")
        if (tools.translate) append("<<translate>> 把这份英文菜谱翻译成中文。\n")
        append("<<note: 要记的话>> 把一句话加进这道菜的批注，比如用户说「记一下下次少放盐」。\n")
        append("<<plan: 日期>> 把这道菜排进某天的计划，日期写成 ${today.plusDays(1)} 这样。\n")
    }

    fun global(
        recipes: List<ChatRecipeInfo>,
        plan: List<ChatPlanInfo>,
        today: LocalDate = LocalDate.now(),
    ): String = buildString {
        append(STYLE)
        append(todayLine(today))
        append("用户在 App 的首页和你聊，可能问做什么菜、怎么做、排菜单，也可能想存一份新菜谱。")
        append("推荐菜时优先从用户自己的菜谱里挑，挑的时候考虑最近做过什么、荤素搭配；用户的菜谱里没有合适的再说别的。\n\n")
        if (recipes.isEmpty()) {
            append("用户还没有存菜谱。\n")
        } else {
            append("用户的菜谱（# 后面是编号）：\n")
            recipes.forEach { append(recipeLine(it, today)).append('\n') }
        }
        val upcoming = plan.filter { it.date >= today.toString() }.sortedBy { it.date }
        if (upcoming.isNotEmpty()) {
            append("\n已经排好的计划：\n")
            upcoming.groupBy { it.date }.forEach { (date, rows) ->
                append("$date（${DayText.weekday(date)}）：")
                append(rows.joinToString("、") { if (it.done) "${it.title}（做完了）" else it.title })
                append('\n')
            }
        }
        append('\n')
        append(MARKER_RULES)
        append("<<recipes: 编号,编号>> 推荐或提到用户菜谱里的菜时用，App 会列出来让用户点进去看，比如 <<recipes: 12,34>>。\n")
        append("<<plan: 日期 编号,编号>> 把几道菜排进某天的计划，比如 <<plan: ${today.plusDays(1)} 12,34>>。用户想排菜单时才用。\n")
        append("<<import>> 用户发来了菜谱链接或一段菜谱文字、想存起来时用，App 会把用户刚发的那条交给导入功能整理。\n")
        append("<<generate: 要求>> 用户想让你写一份新菜谱、存进 App 时用（比如「帮我写个番茄牛腩的菜谱」「用土豆和鸡蛋想一道菜存起来」），" +
            "冒号后面写清要求：菜名或想法、几人份、口味和忌口，比如 <<generate: 番茄牛腩 3人份 少油>>。" +
            "App 会让 AI 写好菜谱给用户检查后保存，你不用在回复里写出完整菜谱，一两句话说说这道菜就好。用户只是问怎么做、没想存时不用。\n")
    }

    /** 全局对话里一道菜的一行：编号、菜名、分类标签、主要用料、热量、上次做的时间 */
    fun recipeLine(info: ChatRecipeInfo, today: LocalDate = LocalDate.now()): String = buildString {
        append("#${info.id} ${info.title}")
        info.category?.takeIf { it.isNotBlank() }?.let { append("｜").append(it) }
        if (info.tags.isNotEmpty()) append("｜").append(info.tags.joinToString(" ") { "#$it" })
        if (info.ingredients.isNotEmpty()) append("｜").append(info.ingredients.take(MAX_INGREDIENTS).joinToString("、"))
        info.calories?.let { append("｜约${it}千卡") }
        info.lastCooked?.let { append("｜上次做：").append(DayText.ago(it, today)) }
    }

    /** 菜谱的全部内容，写成 AI 好读的文字 */
    fun describe(full: RecipeFull, factor: Double): String = buildString {
        val recipe = full.recipe
        append("菜名：${recipe.title}\n")
        recipe.servings?.takeIf { it.isNotBlank() }?.let { append("份量：$it\n") }
        if (factor != 1.0) append("用户现在把用量换算成了原来的 ${AmountScaler.label(factor)} 在看，下面的用量已经按这个换算过。\n")
        listOfNotNull(recipe.category, recipe.subcategory).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
            ?.let { append("分类：${it.joinToString(" › ")}\n") }
        recipe.tags.takeIf { it.isNotBlank() }?.let { append("标签：$it\n") }
        recipe.calories?.let { append("热量：整道菜（按原文份量）约 $it 千卡\n") }
        recipe.note?.takeIf { it.isNotBlank() }?.let { append("用户的批注：$it\n") }
        if (recipe.original != null) append("这份菜谱是从英文翻译过来的。\n")

        fun amount(text: String) = if (factor == 1.0) text else AmountScaler.scale(text, factor)
        val mains = full.ingredients.filter { it.kind != IngredientKind.SEASONING && it.name.isNotBlank() }
        val seasonings = full.ingredients.filter { it.kind == IngredientKind.SEASONING && it.name.isNotBlank() }
        if (mains.isNotEmpty()) {
            append("\n主料：\n")
            mains.forEach { append("- ${it.name}").append(amount(it.amountText).takeIf { a -> a.isNotBlank() }?.let { a -> "：$a" }.orEmpty()).append('\n') }
        }
        if (seasonings.isNotEmpty()) {
            append("\n调料：\n")
            seasonings.forEach { append("- ${it.name}").append(amount(it.amountText).takeIf { a -> a.isNotBlank() }?.let { a -> "：$a" }.orEmpty()).append('\n') }
        }
        if (full.bowls.isNotEmpty()) {
            append("\n调料碗（做菜前按下锅时机分装好的碗）：\n")
            full.bowls.forEach { b ->
                val step = full.steps.indexOfFirst { it.id == b.bowl.stepId }.takeIf { it >= 0 }?.let { "，第 ${it + 1} 步下锅" }.orEmpty()
                append("- ${b.bowl.label}$step：")
                append(b.items.joinToString("、") { item -> item.name + amount(item.amountText).takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty() })
                append('\n')
            }
        }
        val steps = full.steps.filter { it.text.isNotBlank() }
        if (steps.isNotEmpty()) {
            append("\n步骤：\n")
            steps.forEachIndexed { i, step ->
                append("${i + 1}. ${step.text}")
                step.timerSeconds?.let { append("（计时 ${it / 60} 分钟${if (it % 60 != 0) " ${it % 60} 秒" else ""}）") }
                append('\n')
            }
        }
        RecipeReview.fromJson(recipe.review)?.let { review ->
            append("\nApp 之前的口味评价：${review.score}/10，${review.summary}\n")
            review.highlights.forEach { append("- 亮点：$it\n") }
            review.concerns.forEach { append("- 要注意：$it\n") }
        }
    }

    private const val MAX_INGREDIENTS = 6
}
