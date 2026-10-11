package com.personal.mise.ui.common

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.mise.share.ShareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private sealed interface Rendered {
    data object Loading : Rendered
    /** full 用来保存、分享；preview 是缩小的，长图整张放进界面会超出显卡纹理上限 */
    class Done(val full: Bitmap, val preview: Bitmap) : Rendered
    class Failed(val message: String) : Rendered
}

/**
 * 导出图片前先预览，再选存相册还是分享。
 * key 变了就重画（比如切换菜单图要不要写日期），options 放这类开关。
 */
@Composable
fun ShareImageDialog(
    title: String,
    name: String,
    key: Any?,
    render: suspend () -> Bitmap,
    onDismiss: () -> Unit,
    options: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val state by produceState<Rendered>(Rendered.Loading, key) {
        value = Rendered.Loading
        value = try {
            val full = render()
            Rendered.Done(full, withContext(Dispatchers.Default) { previewOf(full) })
        } catch (e: OutOfMemoryError) {
            Rendered.Failed("内容太多，图片太大，生成不了")
        } catch (e: Exception) {
            Rendered.Failed("生成图片失败：${e.message ?: e.javaClass.simpleName}")
        }
    }
    val done = state as? Rendered.Done

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(Modifier.padding(top = 20.dp, start = 20.dp, end = 12.dp, bottom = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 8.dp))
                options()
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp, end = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val s = state) {
                        Rendered.Loading -> CircularProgressIndicator(Modifier.size(32.dp))
                        is Rendered.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                        is Rendered.Done -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            Image(
                                s.preview.asImageBitmap(),
                                contentDescription = "图片预览",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(s.preview.width.toFloat() / s.preview.height)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    Spacer(Modifier.weight(1f))
                    // Android 10 以下存相册要申请存储权限，那就只给分享（分享面板里一般也能存图）
                    if (Build.VERSION.SDK_INT >= 29) {
                        TextButton(
                            enabled = done != null && !busy,
                            onClick = {
                                val bitmap = done?.full ?: return@TextButton
                                busy = true
                                scope.launch {
                                    val ok = ShareImage.saveToGallery(context, bitmap, name)
                                    busy = false
                                    Toast.makeText(context, if (ok) "已保存到相册（Pictures/Mise）" else "保存失败", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) { Text("保存到相册") }
                    }
                    TextButton(
                        enabled = done != null && !busy,
                        onClick = {
                            val bitmap = done?.full ?: return@TextButton
                            busy = true
                            scope.launch {
                                try {
                                    ShareImage.share(context, bitmap, name)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                busy = false
                            }
                        },
                    ) { Text("分享") }
                }
            }
        }
    }
}

private fun previewOf(full: Bitmap): Bitmap {
    val scale = min(720f / full.width, 6000f / full.height).coerceAtMost(1f)
    if (scale >= 1f) return full
    return Bitmap.createScaledBitmap(full, (full.width * scale).toInt().coerceAtLeast(1), (full.height * scale).toInt().coerceAtLeast(1), true)
}
