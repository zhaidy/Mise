package com.personal.mise.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.mise.timer.CookingTimer
import com.personal.mise.ui.bowl.BowlEditScreen
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.cooking.CookingScreen
import com.personal.mise.ui.detail.RecipeDetailScreen
import com.personal.mise.ui.edit.ImportScreen
import com.personal.mise.ui.edit.RecipeEditScreen
import com.personal.mise.ui.history.HistoryScreen
import com.personal.mise.ui.list.RecipeListScreen
import com.personal.mise.ui.prep.PrepListScreen
import com.personal.mise.ui.shopping.ShoppingListScreen
import com.personal.mise.ui.today.TodayScreen
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface NavRequest {
    /** aiNote = true 时交给 AI 整理笔记文字，否则按下厨房或小红书链接导入 */
    data class Import(val text: String, val aiNote: Boolean = false) : NavRequest
    data class Cooking(val recipeId: Long) : NavRequest
}

object Routes {
    const val RECIPES = "recipes"
    const val TODAY = "today"
    const val PREP = "prep"
    const val SHOPPING = "shopping"
    const val HISTORY = "history"
    fun detail(id: Long) = "detail/$id"
    fun edit(id: Long) = "edit/$id"
    fun bowls(id: Long) = "bowls/$id"
    fun import(text: String, aiNote: Boolean = false) = "import?text=${Uri.encode(text)}&ai=$aiNote"
    fun cooking(recipeId: Long = -1L) = "cooking?recipeId=$recipeId"
}

@Composable
fun MiseRoot(navRequests: MutableStateFlow<NavRequest?>) {
    val app = rememberApp()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val request by navRequests.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        when (val r = request) {
            is NavRequest.Import -> nav.navigate(Routes.import(r.text, r.aiNote))
            is NavRequest.Cooking -> nav.navigate(Routes.cooking(r.recipeId)) { launchSingleTop = true }
            null -> Unit
        }
        if (request != null) navRequests.value = null
    }

    var finishedTimer by remember { mutableStateOf<CookingTimer?>(null) }
    LaunchedEffect(Unit) {
        app.timers.finished.collect { finishedTimer = it }
    }
    finishedTimer?.let { timer ->
        AlertDialog(
            onDismissRequest = { finishedTimer = null },
            title = { Text("计时结束") },
            text = { Text("${timer.recipeTitle} · 第 ${timer.stepIndex + 1} 步的时间到了") },
            confirmButton = {
                TextButton(onClick = {
                    finishedTimer = null
                    nav.navigate(Routes.cooking(timer.recipeId)) { launchSingleTop = true }
                }) { Text("去看看") }
            },
            dismissButton = { TextButton(onClick = { finishedTimer = null }) { Text("知道了") } },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (route == Routes.RECIPES || route == Routes.TODAY || route == Routes.HISTORY) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.RECIPES,
                        onClick = { nav.switchTab(Routes.RECIPES) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("菜谱") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.TODAY,
                        onClick = { nav.switchTab(Routes.TODAY) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("今日菜单") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.HISTORY,
                        onClick = { nav.switchTab(Routes.HISTORY) },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text("做过") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.RECIPES,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.RECIPES) {
                RecipeListScreen(
                    onOpen = { nav.navigate(Routes.detail(it)) },
                    onCreate = { nav.navigate(Routes.edit(0)) },
                    onImport = { text, aiNote -> nav.navigate(Routes.import(text, aiNote)) },
                )
            }
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenRecipe = { nav.navigate(Routes.detail(it)) },
                    onPrep = { nav.navigate(Routes.PREP) },
                    onShopping = { nav.navigate(Routes.SHOPPING) },
                    onCook = { nav.navigate(Routes.cooking()) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenRecipe = { nav.navigate(Routes.detail(it)) })
            }
            composable(
                "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                RecipeDetailScreen(
                    recipeId = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.edit(id)) },
                    onEditBowls = { nav.navigate(Routes.bowls(id)) },
                    onCook = { nav.navigate(Routes.cooking(id)) },
                )
            }
            composable(
                "edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                RecipeEditScreen(
                    recipeId = id,
                    onDone = { savedId ->
                        nav.popBackStack()
                        if (id == 0L && savedId != null) nav.navigate(Routes.detail(savedId))
                    },
                )
            }
            composable(
                "import?text={text}&ai={ai}",
                arguments = listOf(
                    navArgument("text") { type = NavType.StringType; defaultValue = "" },
                    navArgument("ai") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                ImportScreen(
                    sharedText = entry.arguments?.getString("text").orEmpty(),
                    aiNote = entry.arguments?.getBoolean("ai") ?: false,
                    onDone = { savedId ->
                        nav.popBackStack()
                        if (savedId != null) nav.navigate(Routes.detail(savedId))
                    },
                )
            }
            composable(
                "bowls/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                BowlEditScreen(
                    recipeId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SHOPPING) {
                ShoppingListScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.PREP) {
                PrepListScreen(
                    onBack = { nav.popBackStack() },
                    onCook = { nav.navigate(Routes.cooking()) },
                )
            }
            composable(
                "cooking?recipeId={recipeId}",
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                CookingScreen(
                    initialRecipeId = entry.arguments?.getLong("recipeId") ?: -1L,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
