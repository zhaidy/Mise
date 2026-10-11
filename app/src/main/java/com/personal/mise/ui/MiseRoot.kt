package com.personal.mise.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.personal.mise.ui.common.MiseIcons
import com.personal.mise.ui.common.rememberApp
import com.personal.mise.ui.cooking.CookingScreen
import com.personal.mise.ui.detail.RecipeDetailScreen
import com.personal.mise.ui.edit.ImportScreen
import com.personal.mise.ui.edit.RecipeEditScreen
import com.personal.mise.ui.history.HistoryScreen
import com.personal.mise.ui.list.RecipeListScreen
import com.personal.mise.ui.prep.PrepListScreen
import com.personal.mise.ui.shopping.ShoppingListScreen
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.ui.today.TodayScreen
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface NavRequest {
    /** aiNote = true 时交给 AI 整理笔记文字，否则按下厨房或小红书链接导入 */
    data class Import(val text: String, val aiNote: Boolean = false) : NavRequest
    /** 分享来的图片，交给 AI 看图整理 */
    data class ImportImages(val uris: List<Uri>) : NavRequest
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
    fun importImages(uris: List<Uri>) = "import?images=${Uri.encode(uris.joinToString("\n"))}"
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
            is NavRequest.ImportImages -> nav.navigate(Routes.importImages(r.uris))
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
                val todayCount by remember { app.repository.observeToday() }.collectAsStateWithLifecycle(emptyList())
                FloatingTabBar {
                    TabPill(
                        selected = route == Routes.RECIPES,
                        icon = MiseIcons.Book,
                        label = "菜谱",
                        onClick = { nav.switchTab(Routes.RECIPES) },
                    )
                    TabPill(
                        selected = route == Routes.TODAY,
                        icon = MiseIcons.Pot,
                        label = "今日菜单",
                        badge = todayCount.size.takeIf { it > 0 },
                        onClick = { nav.switchTab(Routes.TODAY) },
                    )
                    TabPill(
                        selected = route == Routes.HISTORY,
                        icon = MiseIcons.Calendar,
                        label = "做过",
                        onClick = { nav.switchTab(Routes.HISTORY) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.RECIPES,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Routes.RECIPES) {
                RecipeListScreen(
                    onOpen = { nav.navigate(Routes.detail(it)) },
                    onCreate = { nav.navigate(Routes.edit(0)) },
                    onImport = { text, aiNote -> nav.navigate(Routes.import(text, aiNote)) },
                    onImportImages = { nav.navigate(Routes.importImages(it)) },
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
                "import?text={text}&ai={ai}&images={images}",
                arguments = listOf(
                    navArgument("text") { type = NavType.StringType; defaultValue = "" },
                    navArgument("ai") { type = NavType.BoolType; defaultValue = false },
                    navArgument("images") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                ImportScreen(
                    sharedText = entry.arguments?.getString("text").orEmpty(),
                    aiNote = entry.arguments?.getBoolean("ai") ?: false,
                    imageUris = entry.arguments?.getString("images").orEmpty().lines().filter { it.isNotBlank() }.map { Uri.parse(it) },
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

/** 悬浮在页面底部的深色胶囊导航 */
@Composable
private fun FloatingTabBar(content: @Composable RowScope.() -> Unit) {
    val colors = LocalMiseColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = CircleShape,
            color = colors.ink,
            shadowElevation = 10.dp,
        ) {
            Row(
                Modifier.fillMaxHeight().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** 选中的那一格展开显示文字，其余只显示图标 */
@Composable
private fun RowScope.TabPill(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    val colors = LocalMiseColors.current
    val weight by animateFloatAsState(if (selected) 2f else 1f, label = "tab")
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(weight).height(52.dp).semantics { contentDescription = label },
        shape = CircleShape,
        color = if (selected) colors.onInk else colors.ink,
        contentColor = if (selected) colors.ink else colors.onInkMuted,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (badge != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$badge", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiary)
                }
            }
        }
    }
}
