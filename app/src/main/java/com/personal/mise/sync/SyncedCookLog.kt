package com.personal.mise.sync

import com.personal.mise.data.CookLog

/** 一条做菜记录在云端的样子，文档 id 是记录的 uid（菜谱 uid_日期） */
object SyncedCookLog {
    fun toMap(log: CookLog): Map<String, Any?> = mapOf(
        "recipeUid" to log.recipeUid,
        "title" to log.title,
        "date" to log.date,
        "updatedAt" to log.updatedAt,
        "deleted" to log.deleted,
    )

    /** Firestore 读回来的数字都是 Long；格式不对时返回 null */
    fun fromMap(uid: String, data: Map<String, Any?>): CookLog? = runCatching {
        CookLog(
            uid = uid,
            recipeUid = data["recipeUid"] as String,
            title = data["title"] as String,
            date = data["date"] as String,
            updatedAt = (data["updatedAt"] as Number).toLong(),
            deleted = data["deleted"] as Boolean,
        )
    }.getOrNull()
}
