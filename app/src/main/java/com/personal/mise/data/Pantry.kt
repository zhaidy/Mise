package com.personal.mise.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 购物清单里勾成「家里有」的用料，按 ShoppingList.keyOf 记名字。
 * 只存在本机，换菜单也留着：盐、酱油这类常备的不用每次再勾。
 */
class Pantry(context: Context) {
    private val prefs = context.getSharedPreferences("pantry", Context.MODE_PRIVATE)
    private val _have = MutableStateFlow(prefs.getStringSet(HAVE, emptySet()).orEmpty().toSet())
    val have: StateFlow<Set<String>> = _have.asStateFlow()

    fun toggle(key: String) = save(if (key in _have.value) _have.value - key else _have.value + key)

    /** 只把这些项重新标成没有，别的菜里勾过的不动 */
    fun clear(keys: Collection<String>) = save(_have.value - keys.toSet())

    private fun save(next: Set<String>) {
        _have.value = next
        prefs.edit().putStringSet(HAVE, next).apply()
    }

    private companion object {
        const val HAVE = "have"
    }
}
