/*
 * MeloX 全屏播放器
 *
 * Ported from NEORUAA/Mei_MeloX_Android（component/player）：
 * 黑底模糊封面、顶部抓手、圆角封面(播放弹性缩放)、PlayerControls 三键传输区
 * （backward.fill 32dp / play.fill 48dp / forward.fill 32dp，白色）、
 * 进度条与时间标签、玻璃音质胶囊。控制回调适配为 Muse 的 MusicViewModel。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.caipan.music.ui.melox

import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.model.Song
import com.caipan.music.player.LyricLine
import com.caipan.music.player.PlayerBgMode
import com.caipan.music.player.RepeatMode
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.roundToLong

@Composable
fun MeloXPlayerScreen(
    song: Song,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    isShuffled: Boolean,
    qualityLabel: String?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    @Suppress("UNUSED_PARAMETER") onRepeatToggle: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onShuffleToggle: () -> Unit,
    onCycleQuality: () -> Unit = {},
    lyricsLoader: (suspend (Song) -> List<LyricLine>)? = null,
    onOpenActions: () -> Unit = {},
    onOpenComments: () -> Unit = {},
    onDismiss: () -> Unit,
    externalArtUri: android.net.Uri? = null,
    bgMode: PlayerBgMode = PlayerBgMode.ALBUM_EXTEND,
    transitionProgress: Float = 1f,
    showArtwork: Boolean = true,
    onLyricsVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val artwork = externalArtUri ?: song.albumArtUri
    var showLyrics by remember { mutableStateOf(false) }
    SideEffect { onLyricsVisibilityChange(showLyrics) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (bgMode != PlayerBgMode.DYNAMIC_COLOR) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.22f; scaleY = 1.22f }
                    .blur(30.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            MeloXGrabber(onDismiss)

            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    (
                        fadeIn(tween(340)) +
                            slideInVertically(tween(340)) { (it * 0.4f).toInt() }
                        ) togetherWith (
                        fadeOut(tween(240)) +
                            slideOutVertically(tween(240)) { (it * 0.4f).toInt() }
                        )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "melox-player-pages",
            ) { lyricsVisible ->
                if (lyricsVisible) {
                    MeloXLyricsPage(
                        song = song,
                        progressMs = progressMs,
                        lyricsLoader = lyricsLoader,
                        onSeek = onSeek,
                    )
                } else {
                    MeloXArtworkPage(
                        song = song,
                        artwork = artwork,
                        isPlaying = isPlaying,
                        transitionProgress = transitionProgress,
                        showArtwork = showArtwork,
                        onMore = onOpenActions,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                MeloXProgressControl(progressMs, durationMs, onSeek)
                Spacer(Modifier.height(10.dp))
                // 三键传输区（与上游 PlayerControls 一致）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.IconButton(onClick = onPrevious) {
                            SfIcon(SfSymbol.BackwardFilled, "上一首", tint = Color.White, size = 32.dp)
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.IconButton(onClick = onPlayPause) {
                            SfIcon(
                                symbol = if (isPlaying) SfSymbol.PauseFilled else SfSymbol.PlayFilled,
                                contentDescription = null,
                                tint = Color.White,
                                size = 48.dp,
                            )
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.IconButton(onClick = onNext) {
                            SfIcon(SfSymbol.ForwardFilled, "下一首", tint = Color.White, size = 32.dp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXQualityChip(title = qualityLabel ?: "标准", onClick = onCycleQuality)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.IconButton(onClick = { showLyrics = !showLyrics }) {
                            Text(
                                "词",
                                color = if (showLyrics) LocalGlassColors.current.accent else Color.White.copy(alpha = 0.72f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = onOpenComments) {
                            Text(
                                "评",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXGrabber(onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "grabber-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 60.dp, height = 5.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.52f)),
        )
    }
}

@Composable
private fun MeloXArtworkPage(
    song: Song,
    artwork: android.net.Uri?,
    isPlaying: Boolean,
    transitionProgress: Float,
    showArtwork: Boolean,
    onMore: () -> Unit,
) {
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.74f,
        animationSpec = spring(
            dampingRatio = if (isPlaying) 0.70f else 0.94f,
            stiffness = if (isPlaying) 280f else 360f,
            visibilityThreshold = 0.001f,
        ),
        label = "artwork-scale",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isPlaying) 26.dp else 14.dp,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 320f),
        label = "artwork-shadow-elevation",
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.34f else 0.18f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 320f),
        label = "artwork-shadow-alpha",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkSize = maxOf(
            170.dp,
            minOf(maxWidth - 16.dp, maxHeight - 92.dp),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            if (showArtwork) Box(
                modifier = Modifier
                    .size(artworkSize)
                    .graphicsLayer {
                        // The same reversible MainScreen Animatable drives the
                        // source mini-player fade, reveal clip and destination art.
                        // This keeps expansion/collapse continuous when interrupted.
                        val handoff = 0.62f + 0.38f * transitionProgress.coerceIn(0f, 1f)
                        scaleX = artworkScale * handoff
                        scaleY = artworkScale * handoff
                        alpha = transitionProgress.coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = artwork ?: song.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = shadowElevation,
                            shape = RoundedCornerShape(12.dp),
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = shadowAlpha),
                            spotColor = Color.Black.copy(alpha = shadowAlpha),
                        )
                        .clip(ContinuousRoundedRectangle(12.dp)),
                )
            }
            else Spacer(Modifier.height(artworkSize))
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title.ifBlank { "正在播放" },
                        color = Color.White,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("•••", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MeloXProgressControl(
    progressMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val sourceProgress = if (durationMs > 0L) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    var scrubbing by remember { mutableStateOf(false) }
    var localProgress by remember { mutableFloatStateOf(sourceProgress) }
    val trackHeight by animateDpAsState(
        targetValue = if (scrubbing) 6.dp else 4.dp,
        animationSpec = tween(120),
        label = "progress-track-height",
    )

    LaunchedEffect(sourceProgress, scrubbing) {
        if (!scrubbing) localProgress = sourceProgress
    }

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = localProgress,
            onValueChange = {
                scrubbing = true
                localProgress = it.coerceIn(0f, 1f)
            },
            onValueChangeFinished = {
                if (durationMs > 0L) onSeek((durationMs * localProgress).roundToLong())
                scrubbing = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(localProgress)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.96f)),
                    )
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatDuration(if (scrubbing) (durationMs * localProgress).roundToLong() else progressMs),
                color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("−" + formatDuration((durationMs - progressMs).coerceAtLeast(0L)),
                color = Color.White.copy(alpha = 0.50f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun MeloXQualityChip(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "quality-chip-press",
    )

    GlassSurface(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(9.dp),
        refractionHeight = 6.dp,
        refractionAmount = 9.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            SfIcon(
                SfSymbol.Waveform,
                contentDescription = null,
                tint = LocalGlassColors.current.content.copy(alpha = 0.86f),
                size = 13.dp,
            )
            Text(
                title,
                color = LocalGlassColors.current.content.copy(alpha = 0.86f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MeloXLyricsPage(
    song: Song,
    progressMs: Long,
    lyricsLoader: (suspend (Song) -> List<LyricLine>)?,
    onSeek: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    var lines by remember(song.id) { mutableStateOf<List<LyricLine>?>(null) }
    var isLoading by remember(song.id) { mutableStateOf(lyricsLoader != null) }
    var activeIndex by remember(song.id) { mutableIntStateOf(-1) }

    LaunchedEffect(song.id, lyricsLoader) {
        val loader = lyricsLoader
        if (loader == null) {
            isLoading = false
            return@LaunchedEffect
        }
        lines = loader(song).ifEmpty { null }
        isLoading = false
    }

    LaunchedEffect(progressMs, lines) {
        val value = lines
        if (value.isNullOrEmpty()) return@LaunchedEffect
        var index = -1
        for (i in value.indices) {
            if (value[i].timeMs <= progressMs) index = i else break
        }
        activeIndex = index
        if (index >= 0) {
            runCatching { listState.animateScrollToItem((index - 2).coerceAtLeast(0)) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
        when {
            isLoading && lines == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            lines.isNullOrEmpty() -> {
                Text(
                    text = "暂无歌词",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 62.dp, bottom = 86.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    itemsIndexed(lines.orEmpty(), key = { index, line -> "${line.timeMs}-$index" }) { index, line ->
                        val active = index == activeIndex
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(line.timeMs) },
                        ) {
                            val emphasisScale by animateFloatAsState(
                                targetValue = if (active) 1f else 0.96f,
                                animationSpec = tween(durationMillis = 180),
                                label = "lyric-line-scale",
                            )
                            Text(
                                text = line.text,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = emphasisScale
                                    scaleY = emphasisScale
                                },
                                color = Color.White.copy(alpha = if (active) 1f else 0.36f),
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 22.sp,
                                lineHeight = 30.sp,
                            )
                            line.translation?.takeIf(String::isNotBlank)?.let { translation ->
                                Text(
                                    text = translation,
                                    modifier = Modifier.padding(top = 6.dp),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = Color.White.copy(alpha = if (active) 0.72f else 0.28f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
