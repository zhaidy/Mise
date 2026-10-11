package com.personal.mise.util

import kotlin.math.ceil

/**
 * 多菜时间线：按每道菜还要多久倒推开始时间，让几道菜同时出锅。
 * 最费时的菜最先开始，其余的晚开始「最长时长 − 自己的时长」，如「先炖汤，20 分钟后开始备炒青菜」。
 */
object CookTimeline {

    /** 没写时间的步骤按这么久算：备料（洗切腌）3 分钟，开火后 2 分钟 */
    const val DEFAULT_PREP_STEP_SECONDS = 180
    const val DEFAULT_COOK_STEP_SECONDS = 120

    data class Step(val text: String, val timerSeconds: Int?)

    /** 一道菜还要多久，分成开火前的备料和开火后两段，都按整分钟算 */
    data class Dish(val id: Long, val title: String, val prepMinutes: Int, val cookMinutes: Int) {
        val totalMinutes: Int get() = prepMinutes + cookMinutes
    }

    /** startMinute：从第一道菜开始算起，第几分钟开始这道菜 */
    data class Slot(val dish: Dish, val startMinute: Int) {
        /** 开火的时候；没有备料段时就是开始的时候 */
        val fireMinute: Int get() = startMinute + dish.prepMinutes
    }

    data class Plan(val totalMinutes: Int, val slots: List<Slot>)

    enum class Kind { START, FIRE, SERVE }

    /** 时间线上的一个点；dish 为空的是最后一起出锅 */
    data class Event(val minute: Int, val kind: Kind, val dish: Dish?) {
        val text: String
            get() = when (kind) {
                Kind.START -> if (dish!!.prepMinutes > 0) "开始备「${dish.title}」" else "开始做「${dish.title}」"
                Kind.FIRE -> "「${dish!!.title}」开火"
                Kind.SERVE -> "一起出锅"
            }
    }

    /**
     * 一道菜从第 [fromStep] 步做到最后要多久。已经做到一半的菜只算剩下的步骤；
     * 步骤的计时优先，没设计时就从文字里找「焖 15 分钟」，再没有按默认时长。
     */
    fun dish(id: Long, title: String, steps: List<Step>, fromStep: Int = 0): Dish {
        val cookStart = StepPhase.cookStart(steps.map { it.text })
        var prep = 0
        var cook = 0
        steps.forEachIndexed { index, step ->
            if (index < fromStep) return@forEachIndexed
            val isPrep = StepPhase.isPrep(index, cookStart)
            val seconds = step.timerSeconds ?: TimeText.guessSeconds(step.text)
                ?: if (isPrep) DEFAULT_PREP_STEP_SECONDS else DEFAULT_COOK_STEP_SECONDS
            if (isPrep) prep += seconds else cook += seconds
        }
        return Dish(id, title, minutes(prep), minutes(cook))
    }

    private fun minutes(seconds: Int): Int = ceil(seconds / 60.0).toInt()

    /** 最长的那道菜第 0 分钟开始，其余的往后推，让大家同一分钟做完；开始得早的在前 */
    fun plan(dishes: List<Dish>): Plan {
        val total = dishes.maxOfOrNull { it.totalMinutes } ?: 0
        val slots = dishes.map { Slot(it, total - it.totalMinutes) }
            .sortedWith(compareBy<Slot> { it.startMinute }.thenByDescending { it.dish.prepMinutes })
        return Plan(total, slots)
    }

    /** 按时间排好的每个点：各道菜开始、开火，最后一起出锅 */
    fun events(plan: Plan): List<Event> {
        val events = plan.slots.flatMap { slot ->
            listOfNotNull(
                Event(slot.startMinute, Kind.START, slot.dish),
                Event(slot.fireMinute, Kind.FIRE, slot.dish).takeIf { slot.dish.prepMinutes > 0 && slot.dish.cookMinutes > 0 },
            )
        }.sortedBy { it.minute }
        return if (plan.slots.isEmpty()) events else events + Event(plan.totalMinutes, Kind.SERVE, null)
    }

    /** 一句话说清先后：「先做「炖汤」，20 分钟后开始备「炒青菜」」 */
    fun summary(plan: Plan): String {
        if (plan.slots.isEmpty()) return ""
        val first = plan.slots.first()
        val rest = plan.slots.drop(1)
        if (rest.all { it.startMinute == first.startMinute }) return "几道菜一起开始，约 ${plan.totalMinutes} 分钟后一起出锅"
        return buildString {
            append("先做「${first.dish.title}」")
            rest.forEach { slot ->
                append("，")
                append(if (slot.startMinute == 0) "同时" else "${slot.startMinute} 分钟后")
                append(if (slot.dish.prepMinutes > 0) "开始备「${slot.dish.title}」" else "开始做「${slot.dish.title}」")
            }
        }
    }
}
