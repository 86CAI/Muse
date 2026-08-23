package com.caipan.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.player.LyricLine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentLine: Int,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onTapLine: (Long) -> Unit,
    onClose: () -> Unit,
    showCloseButton: Boolean = true,
    fontSize: Int = 24,
    progressMs: Long = 0L
) {
    val listState = rememberLazyListState()

    // cascade 瀑布拖尾状态：主滚动进度 + 每行追赶进度 + 本次级联距离
    val cascadeScrollProgress = remember { Animatable(1f) }
    val cascadeLineProgress = remember(lyrics) { List(lyrics.size) { Animatable(1f) } }
    var cascadeDistancePx by remember(lyrics) { mutableFloatStateOf(0f) }

    // 焦点行跟随视口中心而不是播放行：手动滚动时滑到哪一行，哪一行就高亮变清晰，
    // 未滚动时由自动滚动把播放行置于中心，两者的焦点自然重合。
    val focusLine = remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportHeight = info.viewportSize.height
            if (viewportHeight == 0 || info.visibleItemsInfo.isEmpty()) {
                currentLine
            } else {
                val center = info.viewportStartOffset + viewportHeight / 2
                info.visibleItemsInfo.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2) - center)
                }?.index ?: currentLine
            }
        }
    }

    LaunchedEffect(currentLine, lyrics.size) {
        if (currentLine !in lyrics.indices) return@LaunchedEffect
        // 用户正在手动滚动/拖动时不要打断阅读，跳过本次自动定位。
        if (listState.isScrollInProgress) {
            // 手动滚动打断级联：清除残留错位，避免行停在半途
            cascadeDistancePx = 0f
            return@LaunchedEffect
        }
        // The first composition can happen before LazyColumn has a measured viewport.
        // Waiting here prevents the initial lyric from remaining off-center.
        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }
        val viewportCenter = listState.layoutInfo.viewportStartOffset + viewportHeight / 2
        val visibleItem = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentLine }

        if (visibleItem == null) {
            // A user seek may target a far-away item; position it directly to avoid
            // a long traversal through the whole lyric list.
            cascadeDistancePx = 0f
            listState.scrollToItem(currentLine)
            return@LaunchedEffect
        }

        val itemCenter = visibleItem.offset + visibleItem.size / 2
        val distance = (itemCenter - viewportCenter).toFloat()
        if (kotlin.math.abs(distance) <= 1f) {
            cascadeDistancePx = 0f
            return@LaunchedEffect
        }

        // cascade 瀑布：目标行先移动，其余可见行按距离延迟追赶（拖尾）。
        val movingIndexes = listState.layoutInfo.visibleItemsInfo
            .map { it.index }
            .filter { it in lyrics.indices }
        if (movingIndexes.isEmpty()) return@LaunchedEffect

        cascadeDistancePx = distance
        movingIndexes.forEach { cascadeLineProgress[it].snapTo(0f) }
        cascadeScrollProgress.snapTo(0f)

        // 自适应时长：取当前行到下一行的 35%，夹在 260..560ms（参考 MeloX 的 sourceFocusAnimationDurationMs）
        val available = if (currentLine + 1 < lyrics.size) {
            lyrics[currentLine + 1].timeMs - lyrics[currentLine].timeMs
        } else 0L
        val durationMs = if (available > 0L) (available * 0.35f).coerceIn(260f, 560f).toInt() else 420

        coroutineScope {
            launch {
                var previous = 0f
                listState.scroll {
                    cascadeScrollProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
                    ) {
                        val delta = (value - previous) * distance
                        scrollBy(delta)
                        previous = value
                    }
                }
            }
            movingIndexes.forEach { index ->
                launch {
                    val order = kotlin.math.abs(index - currentLine)
                    if (order > 0) delay((order * 45L).coerceAtMost(200L))
                    cascadeLineProgress[index].animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMs, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
        // 动画结束后所有行归位，清除级联偏移
        movingIndexes.forEach { cascadeLineProgress[it].snapTo(1f) }
        cascadeDistancePx = 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            .16f to Color.Black,
                            .82f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            contentPadding = PaddingValues(vertical = 200.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val dist = kotlin.math.abs(index - focusLine.value)
                val isCurrent = dist == 0

                val targetAlpha = when {
                    isCurrent -> 1f
                    dist <= 2 -> 0.62f
                    dist <= 4 -> 0.36f
                    else -> 0.26f
                }
                val displayAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    // 卡拉 OK 跟唱:spring 轻微呼吸感,与滚动同族参数
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "lyricAlpha"
                )
                val targetBlur = when {
                    isCurrent -> 0.dp
                    dist == 1 -> 0.4.dp
                    dist == 2 -> 1.0.dp
                    dist <= 4 -> 2.0.dp
                    else -> 3.2.dp
                }
                val displayBlur by animateDpAsState(
                    targetValue = targetBlur,
                    // 模糊不过冲,临界阻尼 spring 与 alpha 同步
                    animationSpec = spring(
                        dampingRatio = 1f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "lyricBlur"
                )
                val interactionSource = remember { MutableInteractionSource() }

                // 当前行逐字卡拉OK：有逐字时间戳时，已播放的字用 accentColor 高亮
                val lyricText = if (isCurrent && line.words.isNotEmpty()) {
                    buildAnnotatedString {
                        line.words.forEach { word ->
                            val start = length
                            append(word.text)
                            if (word.timeMs <= progressMs) {
                                addStyle(SpanStyle(color = accentColor), start, length)
                            }
                        }
                    }
                } else null
                Text(
                    text = lyricText ?: AnnotatedString(line.text),
                    color = if (isCurrent) textPrimary else textSecondary,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 7).sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            // 级联拖尾：行相对列表的错位补偿，让目标行先到位、远行拖后追赶
                            val sp = cascadeScrollProgress.value
                            val lp = cascadeLineProgress[index].value
                            translationY = cascadeDistancePx * sp * (lp - 1f)
                        }
                        .padding(vertical = 8.dp)
                        .alpha(displayAlpha)
                        .blur(displayBlur)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onTapLine(line.timeMs) },
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrent && line.translation != null) {
                    Text(
                        text = line.translation,
                        color = textSecondary.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(bottom = 4.dp)
                            .alpha(displayAlpha)
                    )
                }
                if (isCurrent && line.romanization != null) {
                    Text(
                        text = line.romanization,
                        color = textSecondary.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .padding(bottom = 4.dp)
                            .alpha(displayAlpha)
                    )
                }
            }
        }
    }
}
