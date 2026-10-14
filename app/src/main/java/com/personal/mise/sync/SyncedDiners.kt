package com.personal.mise.sync

import com.personal.mise.data.CalorieBudget
import com.personal.mise.data.Diner

/** 今日热量里谁来吃在云端的样子：kitchens/{邀请码}/settings/diners；diners 是 [CalorieBudget.toJson] 的 JSON */
data class SyncedDiners(
    val diners: List<Diner>,
    val updatedAt: Long,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "diners" to CalorieBudget.toJson(diners),
        "updatedAt" to updatedAt,
    )

    companion object {
        /** 不是谁来吃的文档、格式不对或一个人都没有时返回 null */
        fun fromMap(id: String, data: Map<String, Any?>): SyncedDiners? = runCatching {
            if (id != CalorieBudget.SYNC_ID) return null
            SyncedDiners(
                diners = CalorieBudget.fromJson(data["diners"] as String).ifEmpty { return null },
                updatedAt = (data["updatedAt"] as Number).toLong(),
            )
        }.getOrNull()

        fun local(budget: CalorieBudget, id: String): SyncedDiners? {
            if (id != CalorieBudget.SYNC_ID || budget.updatedAt == 0L) return null
            return SyncedDiners(budget.diners.value, budget.updatedAt)
        }
    }
}
