package com.personal.mise.data

import android.content.Context
import com.personal.mise.util.ShoppingList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 购物清单里自己加的一样东西；删了的也留着，共享厨房同步时才知道哪边改得新 */
data class ExtraItem(
    val id: String,
    val name: String,
    val bought: Boolean,
    val addedAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/**
 * 购物清单里自己加的东西：纸巾、牛奶这类不是哪道菜要用的。勾上是买到了，清掉才从清单上拿走；
 * 加入了共享厨房的话厨房里的人共用。
 */
class ExtraShopping(context: Context) {
    private val prefs = context.getSharedPreferences("extra_shopping", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    private val _items = MutableStateFlow(visible(_entries.value.values))

    /** 还在清单上的：没买的在前，各自先加的在前 */
    val items: StateFlow<List<ExtraItem>> = _items.asStateFlow()

    /** 加一样；清单上已经有同名的就不再加，买过的重新算没买 */
    fun add(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val same = _items.value.firstOrNull { ShoppingList.keyOf(it.name) == ShoppingList.keyOf(clean) }
        when {
            same == null -> write(listOf(ExtraItem(UUID.randomUUID().toString(), clean, false, now, now)))
            same.bought -> write(listOf(same.copy(bought = false, updatedAt = now)))
        }
    }

    fun toggle(id: String) {
        val item = _entries.value[id]?.takeIf { !it.deleted } ?: return
        write(listOf(item.copy(bought = !item.bought, updatedAt = System.currentTimeMillis())))
    }

    fun remove(ids: Collection<String>) {
        val now = System.currentTimeMillis()
        val changed = ids.mapNotNull { _entries.value[it]?.takeIf { e -> !e.deleted } }.map { it.copy(deleted = true, updatedAt = now) }
        if (changed.isNotEmpty()) write(changed)
    }

    /** 共享厨房里别人加了、勾了或删了，同步到本机 */
    fun applyRemote(item: ExtraItem) = write(listOf(item))

    /** 给共享厨房上传用 */
    fun entry(id: String): ExtraItem? = _entries.value[id]

    /** 每一项的 id 和修改时间，给共享厨房同步用 */
    fun observeStamps(): Flow<List<RecipeStamp>> = _entries.map { entries ->
        entries.map { (id, e) -> RecipeStamp(id, e.updatedAt) }
    }

    private fun write(changed: List<ExtraItem>) {
        val next = _entries.value + changed.associateBy { it.id }
        prefs.edit().putString(ENTRIES, toJson(next.values)).apply()
        _entries.value = next
        _items.value = visible(next.values)
    }

    private fun load(): Map<String, ExtraItem> =
        prefs.getString(ENTRIES, null)
            ?.let { runCatching { fromJson(it) }.getOrNull() }
            .orEmpty()
            .associateBy { it.id }

    companion object {
        private const val ENTRIES = "entries"

        fun visible(entries: Collection<ExtraItem>): List<ExtraItem> =
            entries.filter { !it.deleted }.sortedWith(compareBy({ it.bought }, { it.addedAt }))

        fun toJson(entries: Collection<ExtraItem>): String = JSONArray().apply {
            entries.forEach {
                put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("bought", it.bought)
                        .put("addedAt", it.addedAt)
                        .put("updatedAt", it.updatedAt)
                        .put("deleted", it.deleted)
                )
            }
        }.toString()

        fun fromJson(json: String): List<ExtraItem> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ExtraItem(
                    id = o.optString("id").ifEmpty { return@mapNotNull null },
                    name = o.optString("name"),
                    bought = o.optBoolean("bought"),
                    addedAt = o.optLong("addedAt"),
                    updatedAt = o.optLong("updatedAt"),
                    deleted = o.optBoolean("deleted"),
                )
            }
        }
    }
}
