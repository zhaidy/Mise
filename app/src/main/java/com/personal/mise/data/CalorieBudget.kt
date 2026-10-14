package com.personal.mise.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** 今天几个人吃、每人热量上限多少；只存在本机，换菜单也留着 */
data class CalorieLimit(val diners: Int, val perPerson: Int)

enum class ActivityLevel(val label: String, val factor: Double) {
    SEDENTARY("很少动", 1.2),
    LIGHT("每周运动 1–3 次", 1.375),
    MODERATE("每周运动 3–5 次", 1.55),
    ACTIVE("几乎天天运动", 1.725),
}

/** 用来估一天大约消耗多少千卡的身体情况，体重按斤 */
data class BodyProfile(
    val male: Boolean,
    val age: Int,
    val heightCm: Int,
    val weightJin: Int,
    val activity: ActivityLevel,
) {
    /** Mifflin-St Jeor 公式算基础代谢，再乘活动量 */
    fun dailyBurn(): Int {
        val bmr = 10 * (weightJin / 2.0) + 6.25 * heightCm - 5 * age + if (male) 5 else -161
        return (bmr * activity.factor).roundToInt()
    }

    companion object {
        val AGES = 15..100
        val HEIGHTS = 100..230
        val WEIGHTS = 60..400

        /** 输入框里的字转成身体情况，没填全或不在合理范围里返回 null */
        fun parse(male: Boolean?, age: String, heightCm: String, weightJin: String, activity: ActivityLevel): BodyProfile? {
            return BodyProfile(
                male = male ?: return null,
                age = age.toIntOrNull()?.takeIf { it in AGES } ?: return null,
                heightCm = heightCm.toIntOrNull()?.takeIf { it in HEIGHTS } ?: return null,
                weightJin = weightJin.toIntOrNull()?.takeIf { it in WEIGHTS } ?: return null,
                activity = activity,
            )
        }
    }
}

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

    /** 上次「帮我算」填的身体情况，下次打开不用重填 */
    val body: BodyProfile?
        get() {
            if (!prefs.contains(BODY_AGE)) return null
            return BodyProfile(
                male = prefs.getBoolean(BODY_MALE, true),
                age = prefs.getInt(BODY_AGE, 0),
                heightCm = prefs.getInt(BODY_HEIGHT, 0),
                weightJin = prefs.getInt(BODY_WEIGHT, 0),
                activity = ActivityLevel.entries.firstOrNull { it.name == prefs.getString(BODY_ACTIVITY, null) } ?: ActivityLevel.SEDENTARY,
            )
        }

    fun saveBody(body: BodyProfile) {
        prefs.edit()
            .putBoolean(BODY_MALE, body.male)
            .putInt(BODY_AGE, body.age)
            .putInt(BODY_HEIGHT, body.heightCm)
            .putInt(BODY_WEIGHT, body.weightJin)
            .putString(BODY_ACTIVITY, body.activity.name)
            .apply()
    }

    companion object {
        const val DEFAULT_DINERS = 2
        /** 成年人一天大约的推荐摄入量 */
        const val DEFAULT_PER_PERSON = 2000
        const val MAX_DINERS = 20
        const val MAX_PER_PERSON = 10000
        private const val DINERS = "diners"
        private const val PER_PERSON = "per_person"
        private const val BODY_MALE = "body_male"
        private const val BODY_AGE = "body_age"
        private const val BODY_HEIGHT = "body_height"
        private const val BODY_WEIGHT = "body_weight_jin"
        private const val BODY_ACTIVITY = "body_activity"
    }
}
