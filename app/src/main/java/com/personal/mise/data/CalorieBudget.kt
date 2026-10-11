package com.personal.mise.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 今天几个人吃、每人热量上限多少；只存在本机，换菜单也留着 */
data class CalorieLimit(val diners: Int, val perPerson: Int)

class CalorieBudget(context: Context) {
    private val prefs = context.getSharedPreferences("calorie_budget", Context.MODE_PRIVATE)
    private val _limit = MutableStateFlow(
        CalorieLimit(
            diners = prefs.getInt(DINERS, DEFAULT_DINERS),
            perPerson = prefs.getInt(PER_PERSON, DEFAULT_PER_PERSON),
        ),
    )
    val limit: StateFlow<CalorieLimit> = _limit.asStateFlow()

    fun save(next: CalorieLimit) {
        val clean = CalorieLimit(next.diners.coerceIn(1, MAX_DINERS), next.perPerson.coerceIn(1, MAX_PER_PERSON))
        _limit.value = clean
        prefs.edit().putInt(DINERS, clean.diners).putInt(PER_PERSON, clean.perPerson).apply()
    }

    companion object {
        const val DEFAULT_DINERS = 2
        /** 成年人一天大约的推荐摄入量 */
        const val DEFAULT_PER_PERSON = 2000
        const val MAX_DINERS = 20
        const val MAX_PER_PERSON = 10000
        private const val DINERS = "diners"
        private const val PER_PERSON = "per_person"
    }
}
