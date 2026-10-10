package com.personal.mise.ai

import android.content.Context

/** Gemini API Key 只存在本机，不会跟菜谱一起导出 */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)

    var geminiKey: String
        get() = prefs.getString(KEY, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY, value.trim()).apply()
        }

    val hasKey: Boolean get() = geminiKey.isNotBlank()

    private companion object {
        const val KEY = "gemini_key"
    }
}
