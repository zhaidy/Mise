package com.personal.mise.util

/**
 * 把步骤分成「备料」和「开火」两段：第一个要用到锅、火的步骤之前都是备料（洗、切、泡、腌），
 * 从它开始是开火烹饪。
 */
object StepPhase {

    /** 出现这些字就算动火了 */
    private val heatWords = listOf(
        "锅", "火", "油热", "热油", "烧热", "烧开", "水开", "煮", "蒸", "烤", "炒", "煎", "炸", "焯", "炖", "焖", "煸", "爆香", "微波",
    )

    /** 带这些字的用料名不算动火，先去掉再找 */
    private val notHeat = listOf("火腿", "火龙果", "烤鸭", "烤肠", "炸酱", "锅巴", "蒸鱼豉油")

    fun isHeatStep(text: String): Boolean {
        val cleaned = notHeat.fold(text) { acc, w -> acc.replace(w, "") }
        return heatWords.any { cleaned.contains(it) }
    }

    /**
     * 开火那一步的下标。第一步就开火、或者一直没开火时返回 null，这时不分段。
     */
    fun cookStart(steps: List<String>): Int? =
        steps.indexOfFirst { isHeatStep(it) }.takeIf { it > 0 }

    /** [index] 这一步是不是开火前的备料 */
    fun isPrep(index: Int, cookStart: Int?): Boolean = cookStart != null && index < cookStart

    /** 这些步骤大部分时间可以走开 */
    private val handsOffWords = listOf("炖", "焖", "煲", "蒸", "烤", "腌", "泡", "静置", "醒", "冷藏", "冷冻", "卤", "煮", "发酵", "晾")

    /** 带这些字的要一直动手或盯着 */
    private val busyWords = listOf("炒", "煎", "炸", "翻", "搅", "不停", "收汁")

    /** 至少这么久才算能走开，几十秒的「蒸一下」不算 */
    private const val HANDS_OFF_MIN_SECONDS = 300

    /** 没让 AI 估过时，按文字猜这一步能不能走开：炖、焖、腌这类够久的，又不用翻炒的 */
    fun isHandsOff(text: String, seconds: Int): Boolean {
        if (seconds < HANDS_OFF_MIN_SECONDS) return false
        val cleaned = notHeat.fold(text) { acc, w -> acc.replace(w, "") }
        return handsOffWords.any { cleaned.contains(it) } && busyWords.none { cleaned.contains(it) }
    }

    /** 不动火、要腌一会儿的步骤 */
    fun isMarinateStep(text: String): Boolean = text.contains("腌") && !isHeatStep(text)

    /**
     * 调料碗用在哪一步：腌制的说「第 N 步腌制」，其他备料时用的说「第 N 步备料时用」，开火后的说「第 N 步下锅」。
     * [stepText] 是那一步的内容，用来认出腌制。
     */
    fun bowlStepLabel(index: Int, cookStart: Int?, stepText: String = ""): String = when {
        isMarinateStep(stepText) -> "第 ${index + 1} 步腌制"
        isPrep(index, cookStart) -> "第 ${index + 1} 步备料时用"
        else -> "第 ${index + 1} 步下锅"
    }
}
