package com.personal.mise.sync

/** 计划里的一道菜在云端的样子，文档 id 是计划的 uid（菜谱 uid_日期） */
data class SyncedPlanEntry(
    val uid: String,
    val recipeUid: String,
    val title: String,
    val date: String,
    /** 提前备料已勾选的项目，按下标写，如「i0」「b1」 */
    val checked: List<String>,
    val updatedAt: Long,
    val deleted: Boolean,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "recipeUid" to recipeUid,
        "title" to title,
        "date" to date,
        "checked" to checked,
        "updatedAt" to updatedAt,
        "deleted" to deleted,
    )

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
            )
        }.getOrNull()
    }
}
