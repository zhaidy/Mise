package com.personal.mise.util

/** 用量统一保存原文，只在显示时把英文缩写换成中文单位。 */
object UnitText {
    private val rules = listOf(
        Regex("(?i)(?<![A-Za-z])(tablespoons?|tbsps?|tbs)\\.?(?![A-Za-z])") to "汤匙",
        Regex("(?i)(?<![A-Za-z])(teaspoons?|tsps?)\\.?(?![A-Za-z])") to "茶匙",
        Regex("(?i)(?<=[\\d.½¼¾])\\s*(kg|kgs)(?![A-Za-z])") to "千克",
        Regex("(?i)(?<=[\\d.½¼¾])\\s*(g|gs|grams?)(?![A-Za-z])") to "克",
        Regex("(?i)(?<=[\\d.½¼¾])\\s*(ml|mls)(?![A-Za-z])") to "毫升",
    )

    fun display(amount: String): String =
        rules.fold(amount) { text, (regex, replacement) -> regex.replace(text, replacement) }
}

/** 从步骤文字里猜计时时长，如「焖 15 分钟」→ 900 秒 */
object TimeText {
    private val minutes = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:分钟|min)")
    private val seconds = Regex("(\\d+)\\s*秒")
    private val hours = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:个)?小时")

    fun guessSeconds(text: String): Int? {
        hours.find(text)?.let { return (it.groupValues[1].toDouble() * 3600).toInt() }
        minutes.find(text)?.let { return (it.groupValues[1].toDouble() * 60).toInt() }
        seconds.find(text)?.let { return it.groupValues[1].toInt() }
        return null
    }

    fun format(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    fun describe(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return when {
            m == 0 -> "${s} 秒"
            s == 0 -> "${m} 分钟"
            else -> "${m} 分 ${s} 秒"
        }
    }
}
