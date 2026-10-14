package com.personal.mise.sync

import com.personal.mise.data.Pantry
import com.personal.mise.data.PantryEntry

/** 购物清单里一样用料「家里有没有」在云端的样子：kitchens/{邀请码}/pantry/{用料名的 SHA-1} */
object SyncedPantryEntry {
    fun toMap(entry: PantryEntry): Map<String, Any?> = mapOf(
        "key" to entry.key,
        "have" to entry.have,
        "updatedAt" to entry.updatedAt,
    )

    /** 格式不对或文档 id 和用料名对不上时返回 null */
    fun fromMap(id: String, data: Map<String, Any?>): PantryEntry? = runCatching {
        val key = data["key"] as String
        if (key.isEmpty() || Pantry.docId(key) != id) return null
        PantryEntry(key, data["have"] as Boolean, (data["updatedAt"] as Number).toLong())
    }.getOrNull()
}
