package com.personal.mise.sync

/**
 * 计划里的一道菜在云端的样子，文档 id 是计划的 uid（菜谱 uid_日期）。
 * 今日菜单就是今天的计划，顺序、做到第几步、做没做完也在这里；旧版本传的没有这几样，读出来是 null
 */
data class SyncedPlanEntry(
    val uid: String,
    val recipeUid: String,
    val title: String,
    val date: String,
    /** 备料已勾选的项目，按下标写，如「i0」「b1」 */
    val checked: List<String>,
    val updatedAt: Long,
    val deleted: Boolean,
    val order: Int? = null,
    val currentStep: Int? = null,
    val done: Boolean? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "recipeUid" to recipeUid,
        "title" to title,
        "date" to date,
        "checked" to checked,
        "updatedAt" to updatedAt,
        "deleted" to deleted,
        "order" to order,
        "currentStep" to currentStep,
        "done" to done,
    ).filterValues { it != null }

    companion object {
        /** Firestore 读回来的数字都是 Long；格式不对时返回 null */
        fun fromMap(uid: String, data: Map<String, Any?>): SyncedPlanEntry? = runCatching {
            SyncedPlanEntry(
                uid = uid,
                recipeUid = data["recipeUid"] as String,
                title = data["title"] as String,
                date = data["date"] as String,
                checked = (data["checked"] as? List<*>).orEmpty().map { it as String },
                updatedAt = (data["updatedAt"] as Number).toLong(),
                deleted = data["deleted"] as Boolean,
                order = (data["order"] as? Number)?.toInt(),
                currentStep = (data["currentStep"] as? Number)?.toInt(),
                done = data["done"] as? Boolean,
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
