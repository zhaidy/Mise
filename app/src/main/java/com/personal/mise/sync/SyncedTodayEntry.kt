package com.personal.mise.sync

/** 今日菜单里的一道菜在云端的样子，文档 id 是菜谱的 uid */
data class SyncedTodayEntry(
    val uid: String,
    val order: Int,
    val currentStep: Int,
    /** 已勾选的备料，按下标写，如「i0」「b1」 */
    val checked: List<String>,
    val updatedAt: Long,
    val removed: Boolean,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "order" to order,
        "currentStep" to currentStep,
        "checked" to checked,
        "updatedAt" to updatedAt,
        "removed" to removed,
    )

    companion object {
        /** Firestore 读回来的数字都是 Long；格式不对时返回 null */
        fun fromMap(uid: String, data: Map<String, Any?>): SyncedTodayEntry? = runCatching {
            SyncedTodayEntry(
                uid = uid,
                order = (data["order"] as Number).toInt(),
                currentStep = (data["currentStep"] as Number).toInt(),
                checked = (data["checked"] as List<*>).map { it as String },
                updatedAt = (data["updatedAt"] as Number).toLong(),
                removed = data["removed"] as Boolean,
            )
        }.getOrNull()
    }
}

/**
 * 备料清单的勾选项本地写成「i<用料 id>」「b<调料碗 id>」，id 只在本机有意义；
 * 同步时换成在这道菜里的下标「i<第几个用料>」「b<第几个调料碗>」。
 */
object TodayKeys {
    fun toPortable(key: String, ingredientIds: List<Long>, bowlIds: List<Long>): String? {
        val (prefix, ids) = split(key, ingredientIds, bowlIds) ?: return null
        val id = key.drop(1).toLongOrNull() ?: return null
        return ids.indexOf(id).takeIf { it >= 0 }?.let { "$prefix$it" }
    }

    fun fromPortable(key: String, ingredientIds: List<Long>, bowlIds: List<Long>): String? {
        val (prefix, ids) = split(key, ingredientIds, bowlIds) ?: return null
        val index = key.drop(1).toIntOrNull() ?: return null
        return ids.getOrNull(index)?.let { "$prefix$it" }
    }

    private fun split(key: String, ingredientIds: List<Long>, bowlIds: List<Long>): Pair<Char, List<Long>>? =
        when (key.firstOrNull()) {
            'i' -> 'i' to ingredientIds
            'b' -> 'b' to bowlIds
            else -> null
        }
}
