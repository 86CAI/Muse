/*
 * MeloX 首页卡片
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (ui/component/home/RecommendCard.kt, ui/component/home/PlaylistCard.kt,
 * constants/Dimensions.kt)。图片取色改用 androidx.palette（原仓库为 kmpalette）。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow as TextShadowStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import com.kyant.capsule.ContinuousRoundedRectangle
import java.time.LocalTime

// ── constants/Dimensions.kt ──
internal val RecommendCardWidth = 160.dp
internal val RecommendCardHeight = 160.dp
internal val PlaylistCardSize = 120.dp
internal val MiniPlayerHeight = 48.dp
internal val NavigationBarHeight = 80.dp
internal val NavigationBarBottomMargin = 8.dp
internal val CommonImageRadius = 8.dp

/** utils/DateUtils.getGreeting 的等价实现。 */
fun meloXGetGreeting(): String {
    val currentTime = LocalTime.now()
    return when {
        currentTime.isBefore(LocalTime.of(6, 0)) -> "凌晨好"
        currentTime.isBefore(LocalTime.of(12, 0)) -> "早上好"
        currentTime.isBefore(LocalTime.of(14, 0)) -> "中午好"
        currentTime.isBefore(LocalTime.of(18, 0)) -> "下午好"
        else -> "晚上好"
    }
}

data class CardExtInfo(val icon: String? = null, val text: String)

/** 歌单卡片数据（首页分区/发现页共用）。 */
data class MeloXCollectionCard(
    val key: String,
    val title: String,
    val coverUrl: String? = null,
    val coverUri: android.net.Uri? = null,
    val creatorName: String = "",
    val playCount: Long = 0L,
    val description: String? = null,
    val onClick: () -> Unit = {},
)

internal fun compactCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

/**
 * 每日推荐/热歌榜大卡：封面 + 上部渐变遮罩 + 底部同色标题条，
 * 取色来自封面上半部分采样（与上游一致）。
 */
@Composable
fun MeloXRecommendCard(
    cover: Any?,
    title: String? = null,
    extInfo: CardExtInfo,
    showPlay: Boolean = false,
    cardWidth: Dp = RecommendCardWidth,
    cardHeight: Dp = RecommendCardHeight,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    var upperHalfColor by remember(cover) { mutableStateOf<Color?>(null) }
    var resolvedColor by remember(cover) { mutableStateOf<Color?>(null) }

    // Resolve the artwork color once per cover.
    LaunchedEffect(cover) {
        resolvedColor = null
        if (cover == null) {
            upperHalfColor = Color.DarkGray
            resolvedColor = Color.DarkGray
            return@LaunchedEffect
        }
        try {
            val bitmap = meloXLoadBitmap(context, cover)
            if (bitmap != null) {
                upperHalfColor = runCatching { sampleUpperHalfColor(bitmap) }
                    .getOrElse { Color.DarkGray }
                resolvedColor = extractDominantColor(bitmap)
            } else {
                upperHalfColor = Color.DarkGray
                resolvedColor = Color.DarkGray
            }
        } catch (_: Throwable) {
            upperHalfColor = Color.DarkGray
            resolvedColor = Color.DarkGray
        }
    }

    val baseColor = resolvedColor ?: Color.DarkGray
    val imageForegroundColor = imageForeground(upperHalfColor ?: Color.DarkGray)
    val footerForegroundColor = footerForeground(baseColor)
    val contentReady = upperHalfColor != null && resolvedColor != null

    Box(
        modifier = Modifier
            .width(cardWidth)
            .clip(ContinuousRoundedRectangle(8.dp))
            .clickable { onClick() }
    ) {
        Column {
            // Cover image and the top metadata row.
            Box(
                modifier = Modifier
                    .size(cardWidth, cardHeight)
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    // The upstream page always receives a network cover. Muse can
                    // legitimately have no matching local artwork, so preserve the
                    // card composition with an intentional colored cover instead of
                    // leaving a missing/black image region.
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(baseColor)
                    )
                }

                // Top gradient overlay for text readability.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    baseColor.copy(alpha = 0.6f),
                                    baseColor.copy(alpha = 0.1f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = 200f
                            )
                        )
                )

                CompositionLocalProvider(LocalContentColor provides imageForegroundColor) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (extInfo.icon == null || extInfo.text.isNotEmpty()) {
                            Text(
                                text = extInfo.text,
                                fontSize = 14.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (showPlay) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = "Play",
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(28.dp)
                        )
                    }
                }
            }

            // Bottom title area.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                baseColor.copy(alpha = 0.9f),
                                baseColor
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                CompositionLocalProvider(LocalContentColor provides footerForegroundColor) {
                    Text(
                        text = title ?: "",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Keep the full card blank and opaque until colors have resolved.
        if (!contentReady) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ContinuousRoundedRectangle(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
}

/** 歌单卡：封面圆角 8dp + 右上播放量（耳机图标）+ 左下副标题 + 标题两行。 */
@Composable
fun MeloXPlaylistCard(
    title: String,
    coverImg: Any?,
    showPlay: Boolean = false,
    subTitle: List<String>? = null,
    extInfo: String? = null,
    cardSize: Dp = PlaylistCardSize,
    onClick: () -> Unit,
) {
    // 常用阴影样式，提取出来复用
    val textShadow = TextShadowStyle(
        color = Color.Black.copy(alpha = 0.7f),
        offset = Offset(2f, 2f),
        blurRadius = 4f
    )

    Column(
        modifier = Modifier
            .width(cardSize)
            .clip(ContinuousRoundedRectangle(8.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.size(cardSize)
        ) {
            AsyncImage(
                model = coverImg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(ContinuousRoundedRectangle(8.dp)),
            )

            // 顶部播放量
            if (extInfo != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headset,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = extInfo,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = textShadow, letterSpacing = 1.sp)
                    )
                }
            }

            // 底部左侧副标题 (如果有)
            if (subTitle != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                ) {
                    subTitle.forEach { t ->
                        Text(
                            text = t,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            style = TextStyle(shadow = textShadow, letterSpacing = 1.sp)
                        )
                    }
                }
            }

            // 右下角播放按钮
            if (showPlay) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 标题
        Text(
            text = title,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun imageForeground(baseColor: Color): Color {
    val opaqueBaseColor = baseColor.copy(alpha = 1f).toArgb()
    val luminance = ColorUtils.calculateLuminance(opaqueBaseColor)
    return if (luminance < 0.5) Color.White else Color.Black
}

private fun footerForeground(baseColor: Color): Color {
    val opaqueBaseColor = baseColor.copy(alpha = 1f).toArgb()
    val luminance = ColorUtils.calculateLuminance(opaqueBaseColor)
    return if (luminance < 0.5) Color.White else Color.Black
}

private fun sampleUpperHalfColor(bitmap: Bitmap): Color {
    if (bitmap.width == 0 || bitmap.height == 0) return Color.DarkGray

    val sampleColumns = 16
    val sampleRows = 8
    val upperHalfHeight = (bitmap.height / 2).coerceAtLeast(1)
    var red = 0f
    var green = 0f
    var blue = 0f
    var weight = 0f

    for (row in 0 until sampleRows) {
        val y = ((row + 0.5f) * upperHalfHeight / sampleRows)
            .toInt()
            .coerceIn(0, bitmap.height - 1)
        for (column in 0 until sampleColumns) {
            val x = ((column + 0.5f) * bitmap.width / sampleColumns)
                .toInt()
                .coerceIn(0, bitmap.width - 1)
            val pixel = bitmap.getPixel(x, y)
            val alpha = AndroidColor.alpha(pixel) / 255f
            red += AndroidColor.red(pixel) * alpha
            green += AndroidColor.green(pixel) * alpha
            blue += AndroidColor.blue(pixel) * alpha
            weight += alpha
        }
    }

    if (weight <= 0f) return Color.DarkGray
    return Color(
        AndroidColor.rgb(
            (red / weight).toInt().coerceIn(0, 255),
            (green / weight).toInt().coerceIn(0, 255),
            (blue / weight).toInt().coerceIn(0, 255),
        )
    )
}

private fun extractDominantColor(bitmap: Bitmap): Color {
    return runCatching {
        val palette = androidx.palette.graphics.Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
        swatch?.rgb?.let { Color(it) }
    }.getOrNull() ?: Color.DarkGray
}

private suspend fun meloXLoadBitmap(
    context: android.content.Context,
    source: Any?,
): Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    runCatching {
        val request = coil.request.ImageRequest.Builder(context)
            .data(source)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: result.drawable?.toBitmap()
    }.getOrNull()
}
