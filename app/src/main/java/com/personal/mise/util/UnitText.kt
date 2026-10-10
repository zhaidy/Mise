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

/** 把用量原文里的数字按倍率换算，如「150克」×2 →「300克」；「适量」「少许」这类不动。 */
object AmountScaler {
    private val fractions = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)
    private val chineseDigits = mapOf(
        '半' to 0.5, '一' to 1.0, '两' to 2.0, '二' to 2.0, '三' to 3.0, '四' to 4.0, '五' to 5.0,
        '六' to 6.0, '七' to 7.0, '八' to 8.0, '九' to 9.0, '十' to 10.0,
    )

    // 「1 1/2」「1/2」「1½」「½」「1.5」
    private val number = Regex(
        "(?:(\\d+)\\s+)?(\\d+)/(\\d+)|(\\d+(?:\\.\\d+)?)?\\s*([½¼¾⅓⅔])|(\\d+(?:\\.\\d+)?)",
    )

    // 开头的中文数量词：「半个」「两勺」「一小把」；「一点」「一些」是少许，不换算
    private val chineseLead = Regex("^([半一两二三四五六七八九十])(?![点些一二三四五六七八九十])")

    fun scale(amount: String, factor: Double): String {
        if (factor == 1.0 || amount.isBlank()) return amount
        if (!amount.any { it.isDigit() || it in fractions }) {
            val m = chineseLead.find(amount) ?: return amount
            return format(chineseDigits.getValue(m.value[0]) * factor) + amount.substring(1)
        }
        return number.replace(amount) { m ->
            val g = m.groupValues
            val value = when {
                g[3].isNotEmpty() -> {
                    val den = g[3].toDouble()
                    if (den == 0.0) return@replace m.value
                    (g[1].toDoubleOrNull() ?: 0.0) + g[2].toDouble() / den
                }
                g[5].isNotEmpty() -> (g[4].toDoubleOrNull() ?: 0.0) + fractions.getValue(g[5][0])
                else -> g[6].toDouble()
            }
            format(value * factor)
        }
    }

    /** 整数就写整数；10 以上取整；小数最多两位 */
    fun format(value: Double): String {
        val rounded = Math.round(value)
        if (kotlin.math.abs(value - rounded) < 0.01 || value >= 10) return rounded.toString()
        return "%.2f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }

    fun label(factor: Double): String = "×" + format(factor)
}
