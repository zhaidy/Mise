package com.personal.mise.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** 做菜记录的日历视图：排月历格子，算每天吃了多少千卡 */
object CookCalendar {
    /** 一个月的格子，周一开头；前后不属于这个月的位置是 null，凑成整周 */
    fun grid(month: YearMonth): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val cells = List(lead) { null } + days
        val trail = (7 - cells.size % 7) % 7
        return cells + List(trail) { null }
    }

    /** 几道菜的热量合计，不知道热量的跳过；一道都不知道时返回 null */
    fun total(calories: List<Int?>): Int? {
        val known = calories.filterNotNull()
        return if (known.isEmpty()) null else known.sum()
    }

    /** 日历格子里的简写：860、1.2k */
    fun short(calories: Int): String =
        if (calories < 1000) "$calories" else String.format(Locale.ROOT, "%.1fk", calories / 1000.0).replace(".0k", "k")
}
