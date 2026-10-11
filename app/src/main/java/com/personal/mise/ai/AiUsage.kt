package com.personal.mise.ai

import android.content.Context

/**
 * 这台手机上每个模型今天用了几次、哪些 Gemini 模型当天额度用完了；模型按 API 名记，几家的名字不重。
 * Google 和 Agnes 都不提供查剩余额度的接口，只能自己记；「今天」按太平洋时间算，和 Gemini 免费额度重置的时间一致。
 * 在别的手机或别的地方用同一个 Key 的次数记不到。
 */
class AiUsage(context: Context) {
    // 以前只记 Gemini，沿用原来的文件名，升级后今天的次数还在
    private val prefs = context.getSharedPreferences("gemini_usage", Context.MODE_PRIVATE)

    /** 今天成功调用的次数 */
    fun count(model: String, now: Long = System.currentTimeMillis()): Int {
        if (prefs.getString(DAY, null) != GeminiClient.quotaDay(now)) return 0
        return prefs.getInt(COUNT_PREFIX + model, 0)
    }

    fun recordSuccess(model: String, now: Long = System.currentTimeMillis()) {
        val day = GeminiClient.quotaDay(now)
        val edit = prefs.edit()
        // 换了一天，清掉昨天的次数
        if (prefs.getString(DAY, null) != day) {
            prefs.all.keys.filter { it.startsWith(COUNT_PREFIX) }.forEach { edit.remove(it) }
            edit.putString(DAY, day)
        }
        edit.putInt(COUNT_PREFIX + model, count(model, now) + 1).apply()
    }

    /** 当天额度用完的模型，返回额度重置的时间；没用完返回 null */
    fun exhaustedUntil(model: String, now: Long = System.currentTimeMillis()): Long? =
        prefs.getLong(EXHAUSTED_PREFIX + model, 0).takeIf { it > now }

    fun markExhausted(model: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(EXHAUSTED_PREFIX + model, GeminiClient.nextQuotaReset(now)).apply()
    }

    private companion object {
        const val DAY = "day"
        const val COUNT_PREFIX = "count_"
        const val EXHAUSTED_PREFIX = "exhausted_"
    }
}
