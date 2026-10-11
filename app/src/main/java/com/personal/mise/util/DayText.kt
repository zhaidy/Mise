package com.personal.mise.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** 做菜记录用的日期：存成 2026-10-11 这样的本地日期字符串 */
object DayText {
    private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun today(): String = LocalDate.now().toString()

    /** 今天的日期，过了半夜换成新的一天；每分钟看一次 */
    fun observeToday(): Flow<String> = flow {
        while (true) {
            emit(today())
            delay(60_000)
        }
    }.distinctUntilChanged()

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

    /** 计划用的写法：「今天」「明天」「后天」，其余和 label 一样 */
    fun ahead(date: String, today: LocalDate = LocalDate.now()): String {
        val d = parse(date) ?: return date
        return when (d) {
            today.plusDays(1) -> "明天"
            today.plusDays(2) -> "后天"
            else -> label(date, today)
        }
    }

    /** 星期几，如「周二」 */
    fun weekday(date: String): String = parse(date)?.let { weekdays[it.dayOfWeek.value - 1] } ?: date

    /** 从 from 开始连续 count 天 */
    fun days(from: String, count: Int): List<String> {
        val start = parse(from) ?: return emptyList()
        return (0 until count).map { start.plusDays(it.toLong()).toString() }
    }

    /** 几天后的日期，按日期字符串算 */
    fun plus(date: String, days: Long): String = parse(date)?.plusDays(days)?.toString() ?: date

    /** 月份标题，如「2026年10月」 */
    fun month(date: String): String = parse(date)?.let { "${it.year}年${it.monthValue}月" } ?: date
}
