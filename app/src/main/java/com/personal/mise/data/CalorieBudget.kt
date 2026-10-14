package com.personal.mise.data

import android.content.Context
import com.personal.mise.util.CalorieText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** 一起吃饭的一个人：一天大约消耗多少千卡就是他的上限；[eating] 是今天吃不吃 */
data class Diner(
    val name: String,
    val perDay: Int,
    val eating: Boolean = true,
    val body: BodyProfile? = null,
)

/** 今天这个人平分到多少千卡；[diff] 正的是多吃了，负的是少吃了 */
data class DinerShare(val diner: Diner, val eaten: Int) {
    val diff: Int get() = eaten - diner.perDay
}

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
    private val _diners = MutableStateFlow(load())

    /** 一起吃饭的人；只存在本机，换菜单也留着 */
    val diners: StateFlow<List<Diner>> = _diners.asStateFlow()

    fun save(next: List<Diner>) {
        val clean = clean(next)
        _diners.value = clean
        prefs.edit().putString(DINERS_JSON, toJson(clean)).apply()
    }

    private fun load(): List<Diner> {
        prefs.getString(DINERS_JSON, null)?.let { json ->
            runCatching { fromJson(json) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return clean(it) }
        }
        // 以前只存了「几个人 × 每人上限」和一份身体情况，换成一个个人
        val count = prefs.getInt(OLD_DINERS, DEFAULT_DINERS).coerceIn(1, MAX_DINERS)
        val perDay = prefs.getInt(OLD_PER_PERSON, DEFAULT_PER_DAY)
        val body = oldBody()
        return List(count) { Diner(defaultName(it), perDay, body = body.takeIf { _ -> it == 0 }) }
    }

    private fun oldBody(): BodyProfile? {
        if (!prefs.contains(OLD_BODY_AGE)) return null
        return BodyProfile(
            male = prefs.getBoolean(OLD_BODY_MALE, true),
            age = prefs.getInt(OLD_BODY_AGE, 0),
            heightCm = prefs.getInt(OLD_BODY_HEIGHT, 0),
            weightJin = prefs.getInt(OLD_BODY_WEIGHT, 0),
            activity = ActivityLevel.entries.firstOrNull { it.name == prefs.getString(OLD_BODY_ACTIVITY, null) } ?: ActivityLevel.SEDENTARY,
        )
    }

    companion object {
        const val DEFAULT_DINERS = 2
        /** 成年人一天大约的推荐摄入量 */
        const val DEFAULT_PER_DAY = 2000
        const val MAX_DINERS = 20
        const val MAX_PER_DAY = 10000
        private const val DINERS_JSON = "diners_json"
        private const val OLD_DINERS = "diners"
        private const val OLD_PER_PERSON = "per_person"
        private const val OLD_BODY_MALE = "body_male"
        private const val OLD_BODY_AGE = "body_age"
        private const val OLD_BODY_HEIGHT = "body_height"
        private const val OLD_BODY_WEIGHT = "body_weight_jin"
        private const val OLD_BODY_ACTIVITY = "body_activity"

        fun defaultName(index: Int) = "${index + 1} 号"

        /** 最多 [MAX_DINERS] 个人；没写名字的按位置叫「N 号」，上限夹在合理范围里 */
        fun clean(diners: List<Diner>): List<Diner> = diners.take(MAX_DINERS).mapIndexed { i, d ->
            d.copy(name = d.name.trim().ifEmpty { defaultName(i) }, perDay = d.perDay.coerceIn(1, MAX_PER_DAY))
        }

        /** 今天吃的人平分整份菜单 */
        fun split(total: Int, diners: List<Diner>): List<DinerShare> {
            val eating = diners.filter { it.eating }
            if (eating.isEmpty()) return emptyList()
            val each = CalorieText.perPerson(total, eating.size)
            return eating.map { DinerShare(it, each) }
        }

        fun toJson(diners: List<Diner>): String = JSONArray().apply {
            diners.forEach { d ->
                put(
                    JSONObject()
                        .put("name", d.name)
                        .put("perDay", d.perDay)
                        .put("eating", d.eating)
                        .apply {
                            d.body?.let { b ->
                                put(
                                    "body",
                                    JSONObject()
                                        .put("male", b.male)
                                        .put("age", b.age)
                                        .put("heightCm", b.heightCm)
                                        .put("weightJin", b.weightJin)
                                        .put("activity", b.activity.name),
                                )
                            }
                        },
                )
            }
        }.toString()

        fun fromJson(json: String): List<Diner> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Diner(
                    name = o.optString("name"),
                    perDay = o.optInt("perDay", DEFAULT_PER_DAY),
                    eating = o.optBoolean("eating", true),
                    body = o.optJSONObject("body")?.let { b ->
                        BodyProfile(
                            male = b.optBoolean("male", true),
                            age = b.optInt("age"),
                            heightCm = b.optInt("heightCm"),
                            weightJin = b.optInt("weightJin"),
                            activity = ActivityLevel.entries.firstOrNull { it.name == b.optString("activity") } ?: ActivityLevel.SEDENTARY,
                        )
                    },
                )
            }
        }
    }
}
