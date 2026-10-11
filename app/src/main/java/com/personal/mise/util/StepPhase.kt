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

    /** 调料碗用在哪一步：备料时用的说「第 N 步备料时用」，开火后的说「第 N 步下锅」 */
    fun bowlStepLabel(index: Int, cookStart: Int?): String =
        if (isPrep(index, cookStart)) "第 ${index + 1} 步备料时用" else "第 ${index + 1} 步下锅"
}
