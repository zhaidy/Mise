package com.personal.mise.sync

import com.personal.mise.data.ExtraItem

/** 购物清单里自己加的一样东西在云端的样子：kitchens/{邀请码}/shopping/{id} */
object SyncedExtraItem {
    fun toMap(item: ExtraItem): Map<String, Any?> = mapOf(
        "name" to item.name,
        "bought" to item.bought,
        "addedAt" to item.addedAt,
        "updatedAt" to item.updatedAt,
        "deleted" to item.deleted,
    )

    /** 格式不对时返回 null */
    fun fromMap(id: String, data: Map<String, Any?>): ExtraItem? = runCatching {
        ExtraItem(
            id = id,
            name = data["name"] as String,
            bought = data["bought"] as Boolean,
            addedAt = (data["addedAt"] as Number).toLong(),
            updatedAt = (data["updatedAt"] as Number).toLong(),
            deleted = data["deleted"] as Boolean,
        )
    }.getOrNull()
}
