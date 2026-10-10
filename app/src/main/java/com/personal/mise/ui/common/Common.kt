package com.personal.mise.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.personal.mise.MiseApp

@Composable
fun rememberApp(): MiseApp {
    val context = LocalContext.current
    return remember(context) { context.applicationContext as MiseApp }
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
    if (path.isNullOrBlank()) {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        val file = remember(path) { app.images.file(path) }
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
