package com.personal.mise.util

import kotlin.math.roundToInt

/** 热量的显示：整道菜存一个千卡数，换算倍率和每人份都在显示时算 */
object CalorieText {
    private val digits = mapOf(
        '一' to 1, '两' to 2, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
    )

    // 「2 人」「3人份」「两人份」「2-3 人」，范围按小的算
    private val people = Regex("(\\d+|[一两二三四五六七八九十])\\s*(?:[-~～到至]\\s*(?:\\d+|[一两二三四五六七八九十])\\s*)?(?:人|份)")

    /** 份量里写的人数，看不出来时返回 null */
    fun servingsCount(servings: String?): Int? {
        val m = people.find(servings ?: return null) ?: return null
        val n = m.groupValues[1]
        return (n.toIntOrNull() ?: digits[n[0]])?.takeIf { it in 1..100 }
    }

    fun scaled(calories: Int, factor: Double): Int = (calories * factor).roundToInt()

    /** 「约 860 千卡」 */
    fun format(calories: Int): String = "约 $calories 千卡"

    /** 「约 860 千卡 · 每人约 430」；按倍率换算过的不再分人，原文的人数已经对不上了 */
    fun describe(calories: Int, servings: String?, factor: Double = 1.0): String {
        if (factor != 1.0) return "${format(scaled(calories, factor))}（${AmountScaler.label(factor)}）"
        val n = servingsCount(servings)
        return if (n == null || n == 1) format(calories) else "${format(calories)} · 每人约 ${(calories.toDouble() / n).roundToInt()}"
    }
}
