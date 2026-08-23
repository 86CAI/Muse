package com.caipan.music.ui.components

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.caipan.music.R
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 裁剪交互状态：由画布同步几何信息，由手势/底部按钮驱动。
 * scale/offset 只在绘制阶段（Canvas lambda）被读取，写入只触发重绘不触发重组。
 */
private class CropController {
    var fitScale = 1f
    var minScale = 1f
    var maxScale = 6f
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    // 画布几何（由 CropCanvas 同步）
    var canvasW = 0f
    var canvasH = 0f
    var cropW = 0f
    var cropH = 0f
    var iw = 0f
    var ih = 0f
    var bitmap: Bitmap? = null

    fun zoom(factor: Float) {
        scale = (scale * factor).coerceIn(minScale, maxScale)
        clampOffset()
    }

    fun reset() {
        scale = fitScale
        offset = Offset.Zero
    }

    fun pan(delta: Offset) {
        offset = offset + delta
        clampOffset()
    }

    private fun clampOffset() {
        val dispW = iw * scale
        val dispH = ih * scale
        val maxX = max(0f, (dispW - cropW) / 2f)
        val maxY = max(0f, (dispH - cropH) / 2f)
        offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    /** 按当前变换裁剪出裁剪框对应的 Bitmap；几何未就绪时返回 null。 */
    fun confirm(): Bitmap? {
        val bmp = bitmap ?: return null
        if (cropW <= 0f || cropH <= 0f || iw <= 0f || ih <= 0f) return null
        val centerX = canvasW / 2f
        val centerY = canvasH / 2f
        val dispW = iw * scale
        val dispH = ih * scale
        val imgLeft = centerX - dispW / 2f + offset.x
        val imgTop = centerY - dispH / 2f + offset.y
        val cropLeft = centerX - cropW / 2f
        val cropTop = centerY - cropH / 2f
        val sx = ((cropLeft - imgLeft) / scale).roundToInt().coerceIn(0, bmp.width - 1)
        val sy = ((cropTop - imgTop) / scale).roundToInt().coerceIn(0, bmp.height - 1)
        val sw = (cropW / scale).roundToInt().coerceIn(1, bmp.width - sx)
        val sh = (cropH / scale).roundToInt().coerceIn(1, bmp.height - sy)
        return Bitmap.createBitmap(bmp, sx, sy, sw, sh)
    }
}

/**
 * 全屏图片裁剪对话框：拖动平移 + 双指缩放 + 固定比例裁剪框。
 * 确认后回调裁剪后的 Bitmap（JPEG 编码由调用方负责）。
 *
 * @param uri 待裁剪图片的 Uri（ContentResolver 可读）
 * @param aspectRatio 裁剪框宽高比（1f = 方形；壁纸传屏幕宽高比）
 */
@Composable
fun ImageCropDialog(
    uri: Uri,
    aspectRatio: Float,
    accentColor: Color,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember(uri) { mutableStateOf(false) }
    val controller = remember { CropController() }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxDim = max(info.size.width, info.size.height)
                    if (maxDim > 1600) decoder.setTargetSampleSize((maxDim / 1600).coerceAtLeast(2))
                    // SOFTWARE 分配器保证 createBitmap 子区域安全
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
        }
        if (bitmap == null) decodeFailed = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xF20A0A0E))) {
            Column(Modifier.fillMaxSize()) {
                // ── 顶部栏 ──
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MuseIconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_apple_x), "关闭", tint = Color.White)
                    }
                    Text(
                        "裁剪图片",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    Text(
                        "拖动 / 双指缩放",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                // ── 裁剪画布 ──
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        decodeFailed -> {
                            Text(
                                "无法读取这张图片",
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        bitmap != null -> CropCanvas(
                            bitmap = bitmap!!,
                            aspectRatio = aspectRatio,
                            controller = controller,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // ── 底部操作栏 ──
                if (bitmap != null) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MuseTextButton(onClick = onDismiss) { Text("取消") }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CropZoomButton(painterResource(R.drawable.ic_apple_minus), "缩小") { controller.zoom(0.8f) }
                            CropZoomButton(painterResource(R.drawable.ic_apple_rotate_ccw), "重置") { controller.reset() }
                            CropZoomButton(painterResource(R.drawable.ic_apple_plus), "放大") { controller.zoom(1.25f) }
                        }
                        MuseButton(onClick = { controller.confirm()?.let(onConfirm) }) { Text("确定") }
                    }
                } else {
                    Spacer(Modifier.navigationBarsPadding().height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun CropZoomButton(icon: Painter, desc: String, onClick: () -> Unit) {
    MuseIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, desc, tint = Color.White)
    }
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    aspectRatio: Float,
    controller: CropController,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val canvasW = constraints.maxWidth.toFloat()
        val canvasH = constraints.maxHeight.toFloat()
        val iw = bitmap.width.toFloat()
        val ih = bitmap.height.toFloat()

        // 裁剪框：默认宽占画布 88%，高度按比例；超高超限时以高度为准反算
        val rawCropW = canvasW * 0.88f
        val rawCropH = rawCropW / aspectRatio
        val cropW = if (rawCropH <= canvasH * 0.8f) rawCropW else canvasH * 0.8f * aspectRatio
        val cropH = cropW / aspectRatio
        val fitScale = max(cropW / iw, cropH / ih)

        // 几何就绪后同步到控制器；画布尺寸/图片尺寸变化时重置变换
        LaunchedEffect(canvasW, canvasH, iw, ih, bitmap) {
            controller.fitScale = fitScale
            controller.minScale = fitScale
            controller.maxScale = fitScale * 6f
            controller.canvasW = canvasW
            controller.canvasH = canvasH
            controller.cropW = cropW
            controller.cropH = cropH
            controller.iw = iw
            controller.ih = ih
            controller.bitmap = bitmap
            controller.scale = fitScale
            controller.offset = Offset.Zero
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap) {
                detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                    controller.scale = (controller.scale * zoom).coerceIn(controller.minScale, controller.maxScale)
                    controller.pan(pan)
                }
            }
        ) {
            // 绘制阶段读取变换状态：缩放/平移只触发重绘
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val dispW = iw * controller.scale
            val dispH = ih * controller.scale
            val left = centerX - dispW / 2f + controller.offset.x
            val top = centerY - dispH / 2f + controller.offset.y

            // 画图片（保持原始像素，避免重复采样）
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawBitmap(
                    bitmap,
                    null,
                    android.graphics.Rect(left.toInt(), top.toInt(), (left + dispW).toInt(), (top + dispH).toInt()),
                    null
                )
            }

            // 裁剪框暗罩（框外压暗）
            val cropLeft = centerX - cropW / 2f
            val cropTop = centerY - cropH / 2f
            val scrim = Color.Black.copy(alpha = 0.55f)
            drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, cropTop))
            drawRect(scrim, topLeft = Offset(0f, cropTop + cropH), size = Size(size.width, size.height - cropTop - cropH))
            drawRect(scrim, topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropH))
            drawRect(scrim, topLeft = Offset(cropLeft + cropW, cropTop), size = Size(size.width - cropLeft - cropW, cropH))

            // 边框 + 九宫格
            val strokeWidth = 1.5.dp.toPx()
            drawRect(
                Color.White.copy(alpha = 0.9f),
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropW, cropH),
                style = Stroke(strokeWidth)
            )
            for (i in 1..2) {
                val x = cropLeft + cropW * i / 3f
                drawLine(Color.White.copy(alpha = 0.25f), Offset(x, cropTop), Offset(x, cropTop + cropH), strokeWidth)
                val y = cropTop + cropH * i / 3f
                drawLine(Color.White.copy(alpha = 0.25f), Offset(cropLeft, y), Offset(cropLeft + cropW, y), strokeWidth)
            }
        }
    }
}
