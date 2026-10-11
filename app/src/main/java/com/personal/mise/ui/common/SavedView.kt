package com.personal.mise.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** 卡片/菜单、列表/日历切到哪个记在本机，下次打开还是那个 */
@Composable
fun rememberSavedView(key: String): MutableState<Boolean> {
    val prefs = LocalContext.current.applicationContext.getSharedPreferences("view_prefs", Context.MODE_PRIVATE)
    val state = remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
    LaunchedEffect(key) {
        snapshotFlow { state.value }.collect { prefs.edit().putBoolean(key, it).apply() }
    }
    return state
}
