package com.personal.mise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFB5532E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF77574B),
    secondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFF6B5E2F),
    tertiaryContainer = Color(0xFFF5E2A7),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5C1A00),
    primaryContainer = Color(0xFF8F3B18),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE7BDAF),
    tertiary = Color(0xFFD8C68D),
    tertiaryContainer = Color(0xFF52461A),
)

@Composable
fun MiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
