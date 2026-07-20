package com.caipan.music.ui.components

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.caipan.music.model.Song
import com.caipan.music.player.LyricLine
import com.caipan.music.player.PlayerBgMode
import com.caipan.music.player.RepeatMode
import com.caipan.music.ui.effects.Sky
import com.caipan.music.ui.effects.frosted
import com.caipan.music.ui.effects.sky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Apple Music-style player screen with real-time frosted glass controls bar.
 *
 * Layout (top to bottom):
 *   - Blurred album art fills entire background
 *   - Dark gradient overlay for text legibility
 *   - Glowing halos (Salamander style)
 *   - Dismiss button (top)
 *   - Album art cover (center, with scale animation)
 *   - Song title + artist
 *   - Progress bar
 *   - Playback controls in frosted glass bar (bottom)
 */
@Composable
fun AppleMusicScreen(
    song: Song,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    isShuffled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onRepeatToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onDismiss: () -> Unit,
    customBgColor: Color? = null,
    wallpaperUri: Uri? = null,
    isLightTheme: Boolean = false,
    bgMode: PlayerBgMode = PlayerBgMode.ALBUM_EXTEND,
    lyricsLoader: (suspend (Long) -> List<LyricLine>)? = null,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f
    val context = LocalContext.current
    val activity = context as? Activity

    // Album palette colors
    val albumColors = rememberAlbumColors(song.albumArtUri, context)
    val accentColor = customBgColor ?: albumColors[0]

    val animatedProgress by animateFloatAsState(progress, tween(150), label = "progress")
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    // Lyrics
    var lyrics by remember(song.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    LaunchedEffect(song.id) { lyrics = lyricsLoader?.invoke(song.id) ?: emptyList() }
    val currentLine = remember(lyrics, progressMs) {
        com.caipan.music.player.LyricsManager.currentLineIndex(lyrics, progressMs)
    }
    var showLyrics by remember { mutableStateOf(false) }

    // System bars
    DisposableEffect(Unit) {
        val ctrl = activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { ctrl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Frosted glass shared state
    val sky = remember { Sky() }

    val textPrimary = if (isLightTheme) Color(0xFF1C1C1E) else Color.White
    val textSecondary = if (isLightTheme) Color(0xFF8E8E93) else Color.White.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .sky(sky) // ← Captures background for frosted glass every frame
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (dragAccum > 180f) onDismiss(); dragAccum = 0f },
                    onVerticalDrag = { _, da -> if (da > 0) dragAccum += da else dragAccum = 0f }
                )
            }
    ) {
        // =====================================================================
        // BACKGROUND — blurred album art (full screen)
        // =====================================================================
        when (bgMode) {
            PlayerBgMode.CUSTOM -> {
                if (wallpaperUri != null) {
                    AsyncImage(model = wallpaperUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize().scale(1.1f),
                        contentScale = ContentScale.Crop)
                } else {
                    AsyncImage(model = song.albumArtUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize().scale(1.3f),
                        contentScale = ContentScale.Crop)
                }
            }
            PlayerBgMode.DYNAMIC_COLOR -> {
                val infinite = rememberInfiniteTransition(label = "breathe")
                val shift by infinite.animateFloat(0f, 1f,
                    infiniteRepeatable(tween(if (isPlaying) 6000 else 12000, easing = LinearEasing),
                        androidx.compose.animation.core.RepeatMode.Reverse), label = "shift")
                val c1 = albumColors.getOrElse(0) { Color(0xFF1DB954) }
                val c2 = albumColors.getOrElse(1) { Color(0xFF2A2A3E) }
                val blended = lerpColor(c1, c2, shift)
                val blended2 = lerpColor(c2, c1, shift)
                Box(Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        colors = listOf(blended, blended2),
                        start = androidx.compose.ui.geometry.Offset(0f, shift * 600f),
                        end = androidx.compose.ui.geometry.Offset(900f, 1200f - shift * 600f)
                    )
                ))
            }
            else -> {
                // ALBUM_EXTEND: album art as wallpaper
                AsyncImage(model = song.albumArtUri, contentDescription = null,
                    modifier = Modifier.fillMaxSize().scale(1.3f),
                    contentScale = ContentScale.Crop)
            }
        }

        // Dark/light overlay for text readability
        Box(Modifier.fillMaxSize().background(
            if (isLightTheme) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)
        ))

        // Glowing halos (from existing PlayerScreen)
        AnimatedGlowHalos(albumColors = albumColors, isPlaying = isPlaying)

        // Scrim gradient
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = if (isLightTheme)
                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent, Color.White.copy(alpha = 0.4f))
                else
                    listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.Black.copy(alpha = 0.5f))
            )
        ))

        // =====================================================================
        // CONTENT COLUMN
        // =====================================================================
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---- Dismiss button ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ExpandMore, "收起",
                        tint = textPrimary.copy(alpha = 0.6f), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))

            // ---- Album art (center) ----
            val artScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.88f,
                animationSpec = tween(600, easing = FastOutSlowInEasing), label = "artScale"
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics && lyrics.isNotEmpty()) {
                    LyricsView(
                        lyrics = lyrics, currentLine = currentLine,
                        accentColor = accentColor, textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onTapLine = { onSeek(it) }, onClose = { showLyrics = false }
                    )
                } else {
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .fillMaxWidth()
                            .scale(artScale)
                            .offset { IntOffset(dragOffset.roundToInt(), 0) }
                            .shadow(32.dp, RoundedCornerShape(16.dp), clip = false,
                                ambientColor = accentColor.copy(alpha = 0.5f),
                                spotColor = accentColor.copy(alpha = 0.6f))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Gray.copy(alpha = 0.15f))
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragOffset > 150f) onPrevious()
                                        else if (dragOffset < -150f) onNext()
                                        dragOffset = 0f
                                    },
                                    onHorizontalDrag = { _, da -> dragOffset += da }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = song.albumArtUri, contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ---- Title + Artist ----
            Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song.title, color = textPrimary, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (lyrics.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { showLyrics = !showLyrics },
                            modifier = Modifier.size(32.dp)) {
                            Text("词",
                                color = if (showLyrics) accentColor else textPrimary.copy(alpha = 0.4f),
                                fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    song.artist, color = textPrimary.copy(alpha = 0.5f),
                    fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---- Progress bar ----
            Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
                Slider(
                    value = animatedProgress,
                    onValueChange = { pos -> onSeek((pos * durationMs).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = textPrimary.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(progressMs), color = textSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium)
                    Text(formatTime(durationMs), color = textSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(28.dp))

            // ---- FROSTED GLASS CONTROLS BAR ----
            // Real-time GPU blur sees through to the album art background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .frosted(
                        sky = sky,
                        radius = 22,
                        tint = if (isLightTheme)
                            Color.White.copy(alpha = 0.25f)
                        else
                            Color.Black.copy(alpha = 0.15f)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = onShuffleToggle, modifier = Modifier.size(44.dp)) {
                        MusicShuffleIcon(
                            tint = if (isShuffled) accentColor else textPrimary.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Previous
                    IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                        MusicSkipPreviousIcon(
                            tint = textPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Play/Pause
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onPlayPause) {
                            if (isPlaying) {
                                MusicPauseIcon(Modifier.size(24.dp), Color.White)
                            } else {
                                MusicPlayIcon(Modifier.size(28.dp).padding(start = 2.dp), Color.White)
                            }
                        }
                    }

                    // Next
                    IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                        MusicSkipNextIcon(
                            tint = textPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Repeat
                    IconButton(onClick = onRepeatToggle, modifier = Modifier.size(44.dp)) {
                        MusicRepeatIcon(
                            tint = if (repeatMode != RepeatMode.NONE) accentColor
                                   else textPrimary.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

// ============================================================================
// Utility
// ============================================================================

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * clamped,
        green = a.green + (b.green - a.green) * clamped,
        blue = a.blue + (b.blue - a.blue) * clamped,
        alpha = a.alpha + (b.alpha - a.alpha) * clamped
    )
}