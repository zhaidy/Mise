package com.personal.mise.util

/** 批注里「第 3 步：鸡腿多炖 5 分钟」这样跟某一步有关的提醒 */
object NoteText {
    private val stepLine = Regex("^第\\s*(\\d+)\\s*步\\s*[：:，,]?\\s*(.+)$")

    /** 第 [index] 步（从 0 开始）的提醒 */
    fun forStep(note: String?, index: Int): List<String> =
        note.orEmpty().lines().mapNotNull { line ->
            val m = stepLine.find(line.trim()) ?: return@mapNotNull null
            m.groupValues[2].trim().takeIf { m.groupValues[1].toIntOrNull() == index + 1 && it.isNotEmpty() }
        }
}
