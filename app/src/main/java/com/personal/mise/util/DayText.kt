package com.personal.mise.util

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 做菜记录用的日期：存成 2026-10-11 这样的本地日期字符串 */
object DayText {
    private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun today(): String = LocalDate.now().toString()

    fun parse(date: String): LocalDate? = try {
        LocalDate.parse(date)
    } catch (e: DateTimeParseException) {
        null
    }

    /** 「今天」「昨天」，其余如「10月3日 周五」，不是今年的带上年份 */
    fun label(date: String, today: LocalDate = LocalDate.now()): String {
        val d = parse(date) ?: return date
        return when (d) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> {
                val year = if (d.year == today.year) "" else "${d.year}年"
                "$year${d.monthValue}月${d.dayOfMonth}日 ${weekdays[d.dayOfWeek.value - 1]}"
            }
        }
    }

    /** 详情页用的简短写法：「今天」「昨天」「3 天前」，再早就是日期 */
    fun ago(date: String, today: LocalDate = LocalDate.now()): String {
        val d = parse(date) ?: return date
        val days = today.toEpochDay() - d.toEpochDay()
        return when {
            days == 0L -> "今天"
            days == 1L -> "昨天"
            days in 2..30 -> "$days 天前"
            d.year == today.year -> "${d.monthValue}月${d.dayOfMonth}日"
            else -> "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
        }
    }

    /** 月份标题，如「2026年10月」 */
    fun month(date: String): String = parse(date)?.let { "${it.year}年${it.monthValue}月" } ?: date
}
