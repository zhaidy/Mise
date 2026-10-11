package com.personal.mise.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.personal.mise.MiseApp
import com.personal.mise.ui.theme.LocalMiseColors
import com.personal.mise.ui.theme.SerifFamily
import com.personal.mise.ui.theme.coverFor
import androidx.compose.ui.text.font.FontWeight

@Composable
fun rememberApp(): MiseApp {
    val context = LocalContext.current
    return remember(context) { context.applicationContext as MiseApp }
}

/** 图片文件在本机吗？别的手机同步来的菜谱可能只有路径，还没有文件 */
fun MiseApp.hasImage(path: String?): Boolean {
    images.version.intValue // 在界面里调用时，同步来了新图片会触发重绘
    return !path.isNullOrBlank() && images.file(path).exists()
}

/** 显示存在 files/ 下的图片；没有图片时显示占位 */
@Composable
fun LocalImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: String = "暂无图片",
) {
    val app = rememberApp()
    if (!app.hasImage(path)) {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        val file = remember(path) { app.images.file(path!!) }
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

/** 菜谱封面：有图用图；没图用一块按菜名固定配色的纯色 + 衬线首字 */
@Composable
fun RecipeCover(
    title: String,
    imagePath: String?,
    modifier: Modifier = Modifier,
    initialSize: TextUnit = 48.sp,
) {
    val app = rememberApp()
    if (app.hasImage(imagePath)) {
        LocalImage(imagePath, modifier)
    } else {
        val colors = LocalMiseColors.current.coverFor(title)
        Box(modifier.background(colors.background), contentAlignment = Alignment.Center) {
            Text(
                title.trim().take(1).ifEmpty { "菜" },
                color = colors.content,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Black,
                fontSize = initialSize,
                lineHeight = initialSize,
            )
        }
    }
}

/** 圆形图标按钮：浅色底，或浮在封面上时用半透明白底 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 页面头部：可选返回按钮、小字眉题、衬线大标题，右边放操作。
 * 自己处理状态栏留白，放在 Scaffold 的 topBar 里。
 */
@Composable
fun MiseHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backDescription: String = "返回",
    large: Boolean = onBack == null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = if (onBack != null) 16.dp else 20.dp, end = 16.dp, top = if (large) 20.dp else 12.dp, bottom = 8.dp),
        verticalAlignment = if (large) Alignment.Bottom else Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleIconButton(backIcon, backDescription, onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            eyebrow?.let { Eyebrow(it) }
            Text(
                title,
                style = if (large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** 标题上方的小字，例如日期 */
@Composable
fun Eyebrow(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

/** 分段标题：左边一小段色条 + 衬线字，右边可放操作 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    hint: String? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 18.dp, height = 3.dp).background(accent, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 2.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = action)
    }
}

/** 小胶囊标签，例如「4 人份」 */
@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier.heightIn(min = 30.dp).background(container, CircleShape).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = content)
    }
}

/** 浅底、细描边、不带阴影的卡片 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    shadow: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border, shadowElevation = shadow, content = content)
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border, shadowElevation = shadow, content = content)
    }
}

/** 主操作：陶土色大胶囊 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = if (height >= 60.dp) 19.sp else 16.sp))
        if (icon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** 次要操作：深色描边胶囊 */
@Composable
fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = if (height >= 60.dp) 19.sp else 16.sp))
    }
}

/** 页面底部的操作区：从透明渐变到底色，按钮浮在内容上方 */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, navigationBars: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.35f to bg))
            .then(if (navigationBars) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 空状态：一个大号衬线字当插画，加标题和说明 */
@Composable
fun EmptyState(glyph: String, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, fontFamily = SerifFamily, fontWeight = FontWeight.Black, fontSize = 44.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 输入框统一样式：圆角、浅底、描边淡 */
val FieldShape = RoundedCornerShape(16.dp)

@Composable
fun miseFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)

/** core 图标里没有的几个线条图标 */
object MiseIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 2f): ImageVector {
        val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            builder.addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val Book: ImageVector by lazy {
        stroke("Book", "M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5z", "M4 5.5v16")
    }

    /** 冒着热气的碗：今日菜单 */
    val Pot: ImageVector by lazy {
        stroke("Pot", "M3 11h18", "M5 11a7 7 0 0 0 14 0", "M9 7c0-1.5 1-2 1-3.5", "M14 7c0-1.5 1-2 1-3.5")
    }

    /** 调料碗 */
    val Bowl: ImageVector by lazy {
        stroke("Bowl", "M3 11h18a9 9 0 0 1-18 0z", "M8 7c0-1 0.6-1.6 0.6-2.6", "M12 7c0-1 0.6-1.6 0.6-2.6")
    }

    val Timer: ImageVector by lazy {
        stroke("Timer", "M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M12 9v4l2.5 2", "M10 2h4")
    }

    val ChevronRight: ImageVector by lazy { stroke("ChevronRight", "M9 6l6 6-6 6", width = 2.4f) }

    /** 拖动把手：六个点 */
    val DragDots: ImageVector by lazy {
        val builder = ImageVector.Builder("DragDots", 24.dp, 24.dp, 24f, 24f)
        listOf(9f, 15f).forEach { x ->
            listOf(6f, 12f, 18f).forEach { y ->
                builder.addPath(
                    pathData = addPathNodes("M${x - 1.6f} ${y}a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z"),
                    fill = SolidColor(Color.Black),
                )
            }
        }
        builder.build()
    }
}
