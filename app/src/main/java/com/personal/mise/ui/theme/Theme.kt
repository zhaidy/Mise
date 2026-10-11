package com.personal.mise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 温暖杂志风：米白纸感底色、陶土橙主色、橄榄绿和芥末黄点缀
private val Light = lightColorScheme(
    primary = Color(0xFFB5532E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF5E6B3A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8C8),
    onSecondaryContainer = Color(0xFF1C2108),
    tertiary = Color(0xFFC98A1B),
    onTertiary = Color(0xFF2A1D08),
    tertiaryContainer = Color(0xFFF6E7C4),
    onTertiaryContainer = Color(0xFF5A3F0C),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF2A211B),
    surface = Color(0xFFFBF6EE),
    onSurface = Color(0xFF2A211B),
    surfaceVariant = Color(0xFFF3EADD),
    onSurfaceVariant = Color(0xFF6B5C50),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFFFFDF9),
    surfaceContainer = Color(0xFFF7F0E5),
    surfaceContainerHigh = Color(0xFFF3EADD),
    surfaceContainerHighest = Color(0xFFEDE2D3),
    inverseSurface = Color(0xFF2A211B),
    inverseOnSurface = Color(0xFFFBF6EE),
    outline = Color(0xFFCDBDA9),
    outlineVariant = Color(0xFFEFE4D4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF0956B),
    onPrimary = Color(0xFF2A1308),
    primaryContainer = Color(0xFF7A3218),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFBFCB97),
    onSecondary = Color(0xFF242B0E),
    secondaryContainer = Color(0xFF3B4422),
    onSecondaryContainer = Color(0xFFE3E8C8),
    tertiary = Color(0xFFF2C66A),
    onTertiary = Color(0xFF2A1D08),
    tertiaryContainer = Color(0xFF3A2E14),
    onTertiaryContainer = Color(0xFFF2C66A),
    background = Color(0xFF1B1612),
    onBackground = Color(0xFFF3EADD),
    surface = Color(0xFF1B1612),
    onSurface = Color(0xFFF3EADD),
    surfaceVariant = Color(0xFF2C241E),
    onSurfaceVariant = Color(0xFFA8978A),
    surfaceContainerLowest = Color(0xFF16120F),
    surfaceContainerLow = Color(0xFF241D18),
    surfaceContainer = Color(0xFF28201B),
    surfaceContainerHigh = Color(0xFF2C241E),
    surfaceContainerHighest = Color(0xFF362D26),
    inverseSurface = Color(0xFFF3EADD),
    inverseOnSurface = Color(0xFF1B1612),
    outline = Color(0xFF5A4C40),
    outlineVariant = Color(0xFF332A23),
)

/** 标题用衬线（安卓上是思源宋体），正文用系统无衬线 */
val SerifFamily = FontFamily.Serif

private val base = Typography()

private val MiseTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Black),
    displayMedium = base.displayMedium.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Black),
    displaySmall = base.displaySmall.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(letterSpacing = 0.8.sp),
    labelSmall = base.labelSmall,
)

private val MiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/** 无图菜谱的首字色块：一组纯色底 + 字色 */
@Immutable
data class CoverColors(val background: Color, val content: Color)

@Immutable
data class MiseExtraColors(
    val covers: List<CoverColors>,
    /** 底部导航、备料进度卡这类深色块 */
    val ink: Color,
    val onInk: Color,
    val onInkMuted: Color,
)

private val LightExtra = MiseExtraColors(
    covers = listOf(
        CoverColors(Color(0xFFC4673F), Color(0xFFFFF6EE)),
        CoverColors(Color(0xFF6F7B45), Color(0xFFF7F3E6)),
        CoverColors(Color(0xFFE2B04A), Color(0xFF3A2A10)),
        CoverColors(Color(0xFFD9A08B), Color(0xFF3B1E14)),
        CoverColors(Color(0xFF3F4A5A), Color(0xFFF4EEE4)),
        CoverColors(Color(0xFFA9B79A), Color(0xFF2A3020)),
    ),
    ink = Color(0xFF2A211B),
    onInk = Color(0xFFFBF6EE),
    onInkMuted = Color(0xFFCBBBA8),
)

private val DarkExtra = MiseExtraColors(
    covers = listOf(
        CoverColors(Color(0xFF9E4A2A), Color(0xFFFFE9DC)),
        CoverColors(Color(0xFF55603A), Color(0xFFF1EDDC)),
        CoverColors(Color(0xFFB9892F), Color(0xFF2A1D08)),
        CoverColors(Color(0xFFA8705E), Color(0xFFFFF0E8)),
        CoverColors(Color(0xFF3A4556), Color(0xFFEDE6DA)),
        CoverColors(Color(0xFF7E8C70), Color(0xFF1E2418)),
    ),
    ink = Color(0xFFF3EADD),
    onInk = Color(0xFF1B1612),
    onInkMuted = Color(0xFF6B5C50),
)

val LocalMiseColors = staticCompositionLocalOf { LightExtra }

/** 按菜名选一组封面色，同一道菜每次都一样 */
fun MiseExtraColors.coverFor(title: String): CoverColors =
    covers[Math.floorMod(title.hashCode(), covers.size)]

@Composable
fun MiseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalMiseColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = MiseTypography,
            shapes = MiseShapes,
            content = content,
        )
    }
}
