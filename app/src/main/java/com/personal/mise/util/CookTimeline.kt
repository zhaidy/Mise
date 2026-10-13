package com.personal.mise.util

import com.personal.mise.ai.StepTime
import kotlin.math.ceil

/**
 * 多菜时间线：从开饭那一刻往前倒推每道菜每一步什么时候做，让几道菜同时出锅。
 * 一个人做饭，同一时间只能盯一样：洗切、炒这类要一直动手的步骤彼此错开，
 * 炖着、腌着这类能走开的步骤可以和别的事同时进行。
 */
object CookTimeline {

    /** 没写时间的步骤按这么久算：备料（洗切腌）3 分钟，开火后 2 分钟 */
    const val DEFAULT_PREP_STEP_SECONDS = 180
    const val DEFAULT_COOK_STEP_SECONDS = 120

    /** [estimate] 是 AI 估的时长和能不能走开，没估过为空 */
    data class Step(val text: String, val timerSeconds: Int?, val estimate: StepTime? = null)

    /** 一步要多久、能不能走开、是不是开火前的备料 */
    data class Task(val seconds: Int, val handsOff: Boolean, val prep: Boolean)

    /** 一道菜剩下的步骤 */
    data class Dish(val id: Long, val title: String, val tasks: List<Task>) {
        /** 开火前的备料要多久，按整分钟算 */
        val prepMinutes: Int get() = minutes(tasks.filter { it.prep }.sumOf { it.seconds })
        val cookMinutes: Int get() = minutes(tasks.filter { !it.prep }.sumOf { it.seconds })
        val totalMinutes: Int get() = prepMinutes + cookMinutes
    }

    /** 都是从第一道菜开始算起的第几分钟：开始、开火（没有备料段时就是开始）、做好 */
    data class Slot(val dish: Dish, val startMinute: Int, val fireMinute: Int, val doneMinute: Int)

    data class Plan(val totalMinutes: Int, val slots: List<Slot>)

    enum class Kind { START, FIRE, DONE, SERVE }

    /** 时间线上的一个点；dish 为空的是最后一起出锅 */
    data class Event(val minute: Int, val kind: Kind, val dish: Dish?) {
        val text: String
            get() = when (kind) {
                Kind.START -> if (dish!!.prepMinutes > 0) "开始备「${dish.title}」" else "开始做「${dish.title}」"
                Kind.FIRE -> "「${dish!!.title}」开火"
                Kind.DONE -> "「${dish!!.title}」先做好，放着保温"
                Kind.SERVE -> "一起出锅"
            }
    }

    /**
     * 一道菜从第 [fromStep] 步做到最后的每一步。已经做到一半的菜只算剩下的步骤。
     * 时长：步骤的计时优先，其次 AI 估的，再从文字里找「焖 15 分钟」，再没有按默认时长。
     * 能不能走开：AI 估过的照它说的，没估过的按文字猜。
     */
    fun dish(id: Long, title: String, steps: List<Step>, fromStep: Int = 0): Dish {
        val cookStart = StepPhase.cookStart(steps.map { it.text })
        val tasks = steps.mapIndexedNotNull { index, step ->
            if (index < fromStep) return@mapIndexedNotNull null
            val prep = StepPhase.isPrep(index, cookStart)
            val seconds = step.timerSeconds ?: step.estimate?.seconds ?: TimeText.guessSeconds(step.text)
                ?: if (prep) DEFAULT_PREP_STEP_SECONDS else DEFAULT_COOK_STEP_SECONDS
            Task(seconds, step.estimate?.handsOff ?: StepPhase.isHandsOff(step.text, seconds), prep)
        }
        return Dish(id, title, tasks)
    }

    private fun minutes(seconds: Int): Int = ceil(seconds / 60.0).toInt()

    /**
     * 从开饭时刻（0）往前倒推：每次挑「下一步最晚要结束」的那道菜，把它的最后一步排上。
     * 能走开的步骤紧挨着排；要动手的步骤不和已经排好的动手步骤重叠，重叠就再往前挪，
     * 这道菜在两步之间等一会儿（切好的菜放着、炒好的先盛出来）。
     */
    fun plan(dishes: List<Dish>): Plan {
        if (dishes.isEmpty()) return Plan(0, emptyList())
        // 都是相对开饭时刻的秒数，往前是负数
        val busy = mutableListOf<IntRange>()
        val end = IntArray(dishes.size)
        val next = IntArray(dishes.size) { dishes[it].tasks.lastIndex }
        val start = IntArray(dishes.size)
        val fire = IntArray(dishes.size)
        val done = IntArray(dishes.size) { Int.MIN_VALUE }
        while (true) {
            // 一样晚的，剩下的活少的排在后面：快手菜最后做，炖菜早点好也能放着
            val d = dishes.indices.filter { next[it] >= 0 }
                .maxWithOrNull(compareBy<Int> { end[it] }.thenByDescending { i -> (0..next[i]).sumOf { dishes[i].tasks[it].seconds } })
                ?: break
            val task = dishes[d].tasks[next[d]]
            val taskEnd = if (task.handsOff) end[d] else latestFree(busy, end[d], task.seconds)
            val taskStart = taskEnd - task.seconds
            if (!task.handsOff) busy += taskStart until taskEnd
            if (done[d] == Int.MIN_VALUE) done[d] = taskEnd
            if (!task.prep) fire[d] = taskStart
            start[d] = taskStart
            end[d] = taskStart
            next[d]--
        }
        val slotsAt = dishes.indices.map { d ->
            val tasks = dishes[d].tasks
            if (tasks.isEmpty()) Triple(0, 0, 0)
            else Triple(start[d], if (tasks.any { !it.prep } && tasks.any { it.prep }) fire[d] else start[d], done[d])
        }
        val origin = slotsAt.minOf { it.first }
        val total = minutes(-origin)
        fun at(seconds: Int) = minutes(seconds - origin)
        val slots = dishes.indices.map { d ->
            val (s, f, e) = slotsAt[d]
            Slot(dishes[d], at(s).coerceAtMost(total), at(f).coerceAtMost(total), at(e).coerceAtMost(total))
        }.sortedWith(compareBy<Slot> { it.startMinute }.thenByDescending { it.dish.prepMinutes })
        return Plan(total, slots)
    }

    /** 不晚于 [before]、长 [length] 秒、和 [busy] 都不重叠的一段，返回它的结束时刻 */
    private fun latestFree(busy: List<IntRange>, before: Int, length: Int): Int {
        var candidate = before
        while (true) {
            val clash = busy.filter { it.first < candidate && candidate - length < it.last + 1 }
            if (clash.isEmpty()) return candidate
            candidate = clash.minOf { it.first }
        }
    }

    /** 按时间排好的每个点：各道菜开始、开火、提前做好的，最后一起出锅 */
    fun events(plan: Plan): List<Event> {
        val events = plan.slots.flatMap { slot ->
            listOfNotNull(
                Event(slot.startMinute, Kind.START, slot.dish),
                Event(slot.fireMinute, Kind.FIRE, slot.dish).takeIf { slot.dish.prepMinutes > 0 && slot.dish.cookMinutes > 0 },
                Event(slot.doneMinute, Kind.DONE, slot.dish).takeIf { slot.doneMinute <= plan.totalMinutes - EARLY_MINUTES },
            )
        }.sortedBy { it.minute }
        return if (plan.slots.isEmpty()) events else events + Event(plan.totalMinutes, Kind.SERVE, null)
    }

    /** 早做好这么多分钟以上才单独提 */
    private const val EARLY_MINUTES = 2

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
