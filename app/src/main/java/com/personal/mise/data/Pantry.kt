package com.personal.mise.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** 一样用料家里有没有；取消了的也留着，共享厨房同步时才知道哪边改得新 */
data class PantryEntry(val key: String, val have: Boolean, val updatedAt: Long)

/**
 * 购物清单里勾成「家里有」的用料，按 ShoppingList.keyOf 记名字。
 * 换菜单也留着：盐、酱油这类常备的不用每次再勾；加入了共享厨房的话厨房里的人共用。
 */
class Pantry(context: Context) {
    private val prefs = context.getSharedPreferences("pantry", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    private val _have = MutableStateFlow(haveOf(_entries.value))
    val have: StateFlow<Set<String>> = _have.asStateFlow()

    fun toggle(key: String) = mark(listOf(key), key !in _have.value)

    fun add(key: String) = mark(listOf(key), true)

    /** 只把这些项重新标成没有，别的菜里勾过的不动 */
    fun clear(keys: Collection<String>) = mark(keys, false)

    private fun mark(keys: Collection<String>, have: Boolean) {
        val now = System.currentTimeMillis()
        val changed = keys.toSet().filter { (it in _have.value) != have }.map { PantryEntry(it, have, now) }
        if (changed.isNotEmpty()) write(changed)
    }

    /** 共享厨房里别人勾了或取消了，同步到本机 */
    fun applyRemote(entry: PantryEntry) = write(listOf(entry))

    /** 文档 id 对应的那一项，给共享厨房上传用 */
    fun entry(docId: String): PantryEntry? = _entries.value[docId]

    /** 每一项的文档 id 和修改时间，给共享厨房同步用 */
    fun observeStamps(): Flow<List<RecipeStamp>> = _entries.map { entries ->
        entries.map { (id, e) -> RecipeStamp(id, e.updatedAt) }
    }

    private fun write(changed: List<PantryEntry>) {
        val next = _entries.value + changed.associateBy { docId(it.key) }
        prefs.edit().putString(ENTRIES, toJson(next.values)).apply()
        _entries.value = next
        _have.value = haveOf(next)
    }

    private fun load(): Map<String, PantryEntry> {
        val json = prefs.getString(ENTRIES, null)
        val entries = if (json != null) {
            runCatching { fromJson(json) }.getOrDefault(emptyList())
        } else {
            // 以前只存了勾上的名字；当成很久以前勾的，云端有更新的就听云端的
            prefs.getStringSet(OLD_HAVE, emptySet()).orEmpty().map { PantryEntry(it, true, OLD_UPDATED_AT) }
        }
        return entries.associateBy { docId(it.key) }
    }

    companion object {
        private const val ENTRIES = "entries"
        private const val OLD_HAVE = "have"
        private const val OLD_UPDATED_AT = 1L

        private fun haveOf(entries: Map<String, PantryEntry>): Set<String> =
            entries.values.filter { it.have }.map { it.key }.toSet()

        /** 用料名里可能有「/」这类云端文档 id 不能用的字，取 SHA-1 当 id */
        fun docId(key: String): String =
            MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

        fun toJson(entries: Collection<PantryEntry>): String = JSONArray().apply {
            entries.forEach { put(JSONObject().put("key", it.key).put("have", it.have).put("updatedAt", it.updatedAt)) }
        }.toString()

        fun fromJson(json: String): List<PantryEntry> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PantryEntry(o.optString("key").ifEmpty { return@mapNotNull null }, o.optBoolean("have"), o.optLong("updatedAt"))
            }
        }
    }
}
