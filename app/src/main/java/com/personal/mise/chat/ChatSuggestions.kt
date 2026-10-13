package com.personal.mise.chat

import com.personal.mise.ai.RecipeReview
import com.personal.mise.data.IngredientKind
import com.personal.mise.data.RecipeFull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** 聊天框里一组可以直接点的问法 */
data class SuggestionGroup(val label: String, val asks: List<String>)

/**
 * 「问问 AI」还没聊过时给的问法：按现在几点、今天排了什么、哪道菜好久没做、这道菜缺什么来出，
 * 每次打开换一批，不是写死的几句。
 */
object ChatSuggestions {

    /** 每组最多几句 */
    private const val PER_GROUP = 3

    /** 首页那段对话：吃什么、排菜单两组 */
    fun global(
        recipes: List<ChatRecipeInfo>,
        plan: List<ChatPlanInfo>,
        now: LocalDateTime,
        random: Random = Random.Default,
    ): List<SuggestionGroup> {
        val today = now.toLocalDate()
        val todayText = today.toString()
        val meal = mealOf(now.hour)
        val todayPlan = plan.filter { it.date == todayText }
        val pending = todayPlan.filter { !it.done }

        val eat = mutableListOf<String>()
        if (todayPlan.isEmpty()) eat += "${meal}吃什么？"
        if (pending.size >= 2) eat += "今天的${pending.take(2).joinToString("和") { "「${it.title}」" }}，先做哪个？"
        else if (pending.size == 1) eat += "今天做「${pending.single().title}」，再配个什么菜？"
        longestNotCooked(recipes, today, random)?.let { eat += "好久没做「${it.title}」了，现在做要注意什么？" }
        recipes.filter { it.ingredients.isNotEmpty() }.randomOrNull(random)?.let { r ->
            val main = r.ingredients.take(2)
            eat += "家里有${main.joinToString("和")}，除了「${r.title}」还能做什么？"
        }
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) eat += "周末有空，挑一道费点功夫的硬菜"
        else eat += "工作日晚上，来道半小时能做好的"
        seasonAsk(today)?.let { eat += it }
        recipes.filter { (it.calories ?: 0) in 1..700 }.takeIf { it.size >= 2 }?.let { eat += "想吃清淡点，挑两道热量低的" }

        val arrange = mutableListOf<String>()
        val week = (0L..6L).map { today.plusDays(it).toString() }
        val plannedDays = plan.filter { it.date in week }.map { it.date }.distinct().size
        when {
            plannedDays == 0 -> arrange += "帮我排一下这周的菜单"
            plannedDays < 7 -> arrange += "这周还有 ${7 - plannedDays} 天没排菜，帮我补上"
            else -> arrange += "看看这周排的菜荤素搭配合不合理"
        }
        val tomorrow = today.plusDays(1).toString()
        if (plan.none { it.date == tomorrow }) arrange += listOf("明天想吃清淡点，排两道菜", "明天排一荤一素", "明天排个汤和一道快手菜").random(random)
        recipes.filter { it.lastCooked == null }.randomOrNull(random)?.let { arrange += "把还没做过的「${it.title}」排进这周" }
        if (recipes.size >= 4) arrange += "这周别重样，每天排两道"

        return listOf(
            SuggestionGroup("吃什么", pick(eat, random)),
            SuggestionGroup("排菜单", pick(arrange, random)),
        ).filter { it.asks.isNotEmpty() }
    }

    /** 某道菜的对话：问问题、改菜谱两组，按这道菜的用料、评价、批注来出 */
    fun recipe(full: RecipeFull, random: Random = Random.Default): List<SuggestionGroup> {
        val ingredients = full.ingredients.filter { it.name.isNotBlank() }
        val seasonings = ingredients.filter { it.kind == IngredientKind.SEASONING }
        val mains = ingredients.filter { it.kind != IngredientKind.SEASONING }
        val review = RecipeReview.fromJson(full.recipe.review)

        val ask = mutableListOf<String>()
        seasonings.randomOrNull(random)?.let { ask += "没有${it.name}，用什么代替？" }
        mains.randomOrNull(random)?.let { ask += "${it.name}怎么挑、怎么处理更好吃？" }
        if (full.steps.size >= 4) ask += "哪几步可以提前做好？"
        ask += "怎么做才不容易失败？"
        if (full.recipe.calories == null && ingredients.isNotEmpty()) ask += "这道菜大概多少热量？"
        ask += "配什么菜一起吃合适？"

        val change = mutableListOf<String>()
        review?.concerns?.firstOrNull()?.let { change += "照评价改：${it.take(24)}" }
        full.recipe.note?.takeIf { it.isNotBlank() }?.let { change += "按我的批注改菜谱：${it.lines().first().take(20)}" }
        servingsAsk(full.recipe.servings)?.let { change += it }
        change += listOf("少油少盐，清淡一点", "改得下饭一点", "换个更省事的做法", "改成不辣的版本").shuffled(random).take(2)
        mains.randomOrNull(random)?.let { change += "把${it.name}换成别的，怎么改？" }

        return listOf(
            SuggestionGroup("问问题", pick(ask, random)),
            SuggestionGroup("改菜谱", pick(change, random)),
        ).filter { it.asks.isNotEmpty() }
    }

    /** 去重后随机挑几句，保留出现的先后（越前面的越贴近现在的情况） */
    private fun pick(asks: List<String>, random: Random): List<String> {
        val unique = asks.distinct()
        if (unique.size <= PER_GROUP) return unique
        // 第一句总是最贴近现在的，其余随机
        val first = unique.first()
        val rest = unique.drop(1).shuffled(random).take(PER_GROUP - 1).toSet()
        return listOf(first) + unique.filter { it in rest }
    }

    fun mealOf(hour: Int): String = when (hour) {
        in 5..9 -> "早饭"
        in 10..13 -> "午饭"
        in 14..16 -> "下午茶"
        in 17..20 -> "晚饭"
        else -> "夜宵"
    }

    /** 做过、但最久没做的那几道里挑一道 */
    private fun longestNotCooked(recipes: List<ChatRecipeInfo>, today: LocalDate, random: Random): ChatRecipeInfo? {
        val cooked = recipes.mapNotNull { r ->
            val date = r.lastCooked?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            r to ChronoUnit.DAYS.between(date, today)
        }.filter { it.second >= 14 }
        return cooked.sortedByDescending { it.second }.take(3).randomOrNull(random)?.first
    }

    private fun seasonAsk(today: LocalDate): String? = when (today.monthValue) {
        3, 4, 5 -> "春天有什么时令菜可以做？"
        6, 7, 8 -> "天热没胃口，来点开胃的"
        9, 10, 11 -> "秋天适合吃点什么？"
        else -> "天冷了，来点暖和的炖菜"
    }

    /** 份量写着几人份的，给个改人数的问法 */
    private fun servingsAsk(servings: String?): String? {
        val n = servings?.let { Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: return "改成 2 人份"
        return if (n <= 2) "改成 4 人份" else "改成 2 人份"
    }
}
