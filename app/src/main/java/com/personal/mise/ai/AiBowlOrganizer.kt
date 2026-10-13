package com.personal.mise.ai

import com.personal.mise.bowl.SauceBowlGenerator
import com.personal.mise.data.IngredientKind
import com.personal.mise.importer.ImportException
import com.personal.mise.util.StepPhase
import com.personal.mise.util.UnitText
import org.json.JSONObject

/** AI 分好的一个碗：[stepIndex] 是下锅的步骤（从 0 开始），items 是 用料序号 to 这碗的用量 */
data class AiBowl(val stepIndex: Int?, val items: List<Pair<Int, String>>)

/**
 * 把用料和步骤交给 AI，按「同一时刻一起下锅」分调料碗。
 * 本地规则只能看步骤里提到了哪些用料，分不清腌料、碗汁和爆香料，这里让 AI 按烹饪常识来分。
 */
class AiBowlOrganizer(private val ai: AiClient) {

    /** [ingredients] 是 名称 to 用量；[kinds] 与之一一对应，用来认出只有一份的主料、配料 */
    suspend fun organize(
        ingredients: List<Pair<String, String>>,
        steps: List<String>,
        kinds: List<String> = emptyList(),
    ): List<AiBowl> {
        if (ingredients.isEmpty()) throw ImportException("还没有用料，没法分调料碗")
        if (steps.none { it.isNotBlank() }) throw ImportException("还没有步骤，没法分调料碗")
        val bowls = parseResponse(ai.generate(prompt(ingredients, steps), schema), ingredients.size, steps.size)
        val single = ingredients.mapIndexed { i, (name, _) ->
            !SauceBowlGenerator.isSplittable(name, kinds.getOrElse(i) { IngredientKind.MAIN })
        }
        val deduped = keepSingleOnce(bowls, single, steps)
        return unifySplitAmounts(deduped, ingredients.map { UnitText.normalize(it.second) })
    }

    companion object {
        private const val PROMPT = "下面是一道中文菜的用料和做法。做菜前我会把用料按下锅时机提前分装进几个碗（备料碗），" +
            "做菜时按步骤一碗一碗倒进锅里。请帮我分碗。\n" +
            "要求：\n" +
            "1. 同一时刻一起下锅（或一起拌匀）的用料放一个碗；不同时机下锅的必须分开。" +
            "例如：腌肉的腌料和肉一碗，腌好后整碗下锅，下锅那一步不要再为肉单独分一碗；预先调好、最后淋入的碗汁（糖、醋、酱油、水淀粉等）单独一碗；" +
            "下锅爆香的花椒、干辣椒、葱姜蒜一碗；出锅前撒的葱花、香油单独一碗。" +
            "要先下锅炒一会儿（煸炒出油、煎至金黄、炒至变色）再放后面用料的，前后必须分碗，" +
            "例如五花肉先下锅煸出油，再下干辣椒、姜蒜：五花肉一碗，干辣椒、姜蒜一碗。\n" +
            "2. 炒菜用的油不进碗。主料、配料只有一份，只能进一个碗。调料分几次用时，可以出现在多个碗里，每个碗的 amount 都写「分次加入，共 X」，X 是用料的总用量。" +
            "只出现一次的 amount 直接照抄用料的用量，没有用量留空字符串。\n" +
            "3. 每个碗的 step 填这碗在第几步下锅（步骤序号，从 1 开始）。\n" +
            "4. ingredient 填用料序号（从 1 开始），只用下面列出的用料，不要编造。\n" +
            "5. 按下锅的先后顺序排列碗。\n" +
            "6. 标了「备料」的步骤还没开火。只是清洗、切配的用料不算在那一步下锅，要按它真正下锅的那一步分碗；" +
            "备料步骤里只有腌制、泡、调汁这类要提前把用料混在一起的，才在那一步配碗。\n\n"

        fun prompt(ingredients: List<Pair<String, String>>, steps: List<String>): String = buildString {
            append(PROMPT)
            append("用料：\n")
            ingredients.forEachIndexed { i, (name, amount) ->
                append("${i + 1}. $name")
                if (amount.isNotBlank()) append("：$amount")
                append('\n')
            }
            append("\n做法：\n")
            val cookStart = StepPhase.cookStart(steps)
            steps.forEachIndexed { i, text ->
                append("${i + 1}. ")
                if (StepPhase.isPrep(i, cookStart)) append("（备料）")
                append("${text.trim()}\n")
            }
        }

        private val schema
            get() = AiSchema.obj(
                "bowls" to AiSchema.array(
                    AiSchema.obj(
                        "step" to AiSchema.number(nullable = true),
                        "items" to AiSchema.array(
                            AiSchema.obj(
                                "ingredient" to AiSchema.number(),
                                "amount" to AiSchema.string(),
                                required = listOf("ingredient", "amount"),
                            ),
                        ),
                        required = listOf("items"),
                    ),
                ),
                required = listOf("bowls"),
            )

        /**
         * 主料、配料只有一份，AI 有时腌的时候放一碗、下锅时又单独放一碗。
         * 腌过的留在腌料碗（腌好整碗倒进锅），没腌过的留最后一碗；这样空出来的碗不留。
         */
        fun keepSingleOnce(bowls: List<AiBowl>, single: List<Boolean>, steps: List<String>): List<AiBowl> {
            val marinating = bowls.map { bowl -> bowl.stepIndex?.let { SauceBowlGenerator.isMarinating(steps[it]) } == true }
            val home = mutableMapOf<Int, Int>()
            bowls.forEachIndexed { b, bowl ->
                bowl.items.forEach { (index, _) ->
                    if (single.getOrElse(index) { false } && home[index]?.let { marinating[it] } != true) home[index] = b
                }
            }
            return bowls.mapIndexed { b, bowl ->
                bowl.copy(items = bowl.items.filter { (index, _) -> home[index]?.let { it == b } ?: true })
            }.filter { it.items.isNotEmpty() }
        }

        /**
         * 同一样用料进了好几个碗时，每个碗都统一写「分次加入，共 总用量」。
         * AI 有时一个碗照抄总用量、另一个碗才写分次加入，看着像要放两份。
         */
        fun unifySplitAmounts(bowls: List<AiBowl>, amounts: List<String>): List<AiBowl> {
            val appearances = bowls.flatMap { bowl -> bowl.items.map { it.first } }.groupingBy { it }.eachCount()
            return bowls.map { bowl ->
                bowl.copy(
                    items = bowl.items.map { (index, amount) ->
                        if ((appearances[index] ?: 0) > 1) {
                            index to SauceBowlGenerator.splitAmountText(amounts.getOrElse(index) { "" })
                        } else {
                            index to amount
                        }
                    },
                )
            }
        }

        /** 序号越界的用料丢掉，同一碗里重复的只留一次，空碗不留 */
        fun parseResponse(reply: JSONObject, ingredientCount: Int, stepCount: Int): List<AiBowl> {
            val arr = reply.optJSONArray("bowls")
                ?: throw ImportException("AI 返回的格式不对，请重试")
            val bowls = (0 until arr.length()).mapNotNull { i ->
                val bowl = arr.optJSONObject(i) ?: return@mapNotNull null
                val step = bowl.optDouble("step", Double.NaN).takeIf { !it.isNaN() }?.toInt()?.minus(1)
                    ?.takeIf { it in 0 until stepCount }
                val itemsJson = bowl.optJSONArray("items") ?: return@mapNotNull null
                val items = (0 until itemsJson.length()).mapNotNull { j ->
                    val item = itemsJson.optJSONObject(j) ?: return@mapNotNull null
                    val index = item.optDouble("ingredient", Double.NaN).takeIf { !it.isNaN() }?.toInt()?.minus(1)
                        ?.takeIf { it in 0 until ingredientCount } ?: return@mapNotNull null
                    index to item.optString("amount").trim().let { if (it == "null") "" else UnitText.normalize(it) }
                }.distinctBy { it.first }
                if (items.isEmpty()) null else AiBowl(step, items)
            }
            return bowls.ifEmpty { throw ImportException("AI 没有分出调料碗，请重试") }
        }
    }
}
