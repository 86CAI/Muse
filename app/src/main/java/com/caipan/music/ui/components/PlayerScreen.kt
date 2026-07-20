package com.caipan.music.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.caipan.music.model.Song
import com.caipan.music.player.LyricLine
import com.caipan.music.player.LyricsManager
import com.caipan.music.player.PlayerBgMode
import com.caipan.music.player.RepeatMode
import com.caipan.music.ui.theme.MuseDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

@Composable
fun rememberAlbumColors(albumArtUri: Any?, context: android.content.Context): List<Color> {
    var colors by remember(albumArtUri) { mutableStateOf(listOf(MuseDesign.Red, Color(0xFF312B38))) }
    LaunchedEffect(albumArtUri) {
        if (albumArtUri == null) return@LaunchedEffect
        runCatching {
            val drawable = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context).data(albumArtUri).size(180).allowHardware(false).build()
                (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            } ?: return@runCatching
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bitmap.width, bitmap.height); drawable.draw(Canvas(bitmap))
            val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).maximumColorCount(10).generate() }
            colors = listOf(Color(palette.vibrantSwatch?.rgb ?: MuseDesign.Red.value.toInt()), Color(palette.darkMutedSwatch?.rgb ?: 0xFF312B38.toInt()))
        }
    }
    return colors
}

@Composable
fun PlayerScreen(
    song: Song, isPlaying: Boolean, progressMs: Long, durationMs: Long,
    repeatMode: RepeatMode, isShuffled: Boolean,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSeek: (Long) -> Unit, onRepeatToggle: () -> Unit, onShuffleToggle: () -> Unit,
    onTransferUp: () -> Unit = {},
    onDismiss: () -> Unit, onLandscapeToggle: () -> Unit = {}, customBgColor: Color? = null, wallpaperUri: Uri? = null,
    isLightTheme: Boolean = false, bgMode: PlayerBgMode = PlayerBgMode.ALBUM_EXTEND,
    lyricsLoader: (suspend (Long) -> List<LyricLine>)? = null, backdrop: Backdrop? = null, modifier: Modifier = Modifier,
    externalArtUri: Uri? = null
) {
    val liquidGlass = LocalMuseLiquidGlass.current
    val playerBlurEnabled = LocalMuseBlurPolicy.current.enabledAt(BlurLocation.PLAYER)
    val context = LocalContext.current
    val artUri = externalArtUri ?: song.albumArtUri.takeIf { song.albumId > 0 }
    val albumColors = rememberAlbumColors(artUri, context)
    val accent = customBgColor ?: albumColors.first()
    val primary = if (isLightTheme) Color(0xFF18181B) else Color.White
    val secondary = primary.copy(alpha = .58f)
    val playerBackdrop = rememberLayerBackdrop()
    var lyrics by remember(song.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var showLyrics by remember(song.id) { mutableStateOf(false) }
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = with(LocalDensity.current) { 120.dp.toPx() }
    val currentLine = remember(lyrics, progressMs) { LyricsManager.currentLineIndex(lyrics, progressMs) }
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else .92f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "artScale"
    )
    LaunchedEffect(song.id) { lyrics = lyricsLoader?.invoke(song.id).orEmpty() }

    Box(modifier.fillMaxSize().graphicsLayer { translationY = verticalDrag }) {
        Box(Modifier.matchParentSize().layerBackdrop(playerBackdrop)) {
            PlayerBackdrop(bgMode, artUri, wallpaperUri, albumColors, isLightTheme)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                if (isLightTheme) Color.White.copy(.30f) else Color.Black.copy(.18f),
                if (isLightTheme) Color.White.copy(.55f) else Color.Black.copy(.62f)
            ))))
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) {
                LandscapePlayerContent(song, isPlaying, progressMs, durationMs, repeatMode, isShuffled,
                    onPlayPause, onNext, onPrevious, onSeek, onRepeatToggle, onShuffleToggle,
                    onTransferUp, onDismiss, onLandscapeToggle, accent, primary, secondary, artScale, lyrics, currentLine,
                    artUri = artUri,
                    onVerticalDrag = { amount -> verticalDrag = (verticalDrag + amount).coerceAtLeast(0f) },
                    onVerticalDragEnd = {
                        if (verticalDrag >= dismissThreshold) onDismiss()
                        verticalDrag = 0f
                    })
            } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().height(60.dp).pointerInput(dismissThreshold) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            verticalDrag = (verticalDrag + amount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (verticalDrag >= dismissThreshold) onDismiss()
                            verticalDrag = 0f
                        },
                        onDragCancel = { verticalDrag = 0f }
                    )
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(MuseDesign.MinTouch)) {
                    Icon(Icons.Default.ExpandMore, "收起播放器", tint = primary, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("正在播放", color = secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    Text(song.album.ifBlank { "Muse" }, color = primary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onLandscapeToggle, modifier = Modifier.size(MuseDesign.MinTouch)) {
                    Icon(Icons.Default.ScreenRotation, "横屏大播放器", tint = secondary, modifier = Modifier.size(22.dp))
                }
            }

            Crossfade(targetState = showLyrics && lyrics.isNotEmpty(), modifier = Modifier.weight(1f), animationSpec = tween(280), label = "playerContent") { lyricsVisible ->
                if (lyricsVisible) {
                    LyricsDetailsPager(
                        song, lyrics, currentLine, accent, primary, secondary,
                        onTapLine = onSeek, onCloseLyrics = { showLyrics = false }
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(top = 42.dp), contentAlignment = Alignment.Center) {
                        SwipeableAlbumArt(song, artScale, primary, secondary, accent, onPlayPause, onNext, onPrevious, onTransferUp, artUri)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = song,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(160)) },
                label = "metadata"
            ) { targetSong ->
                Column(Modifier.fillMaxWidth()) {
                    Text(targetSong.title, color = primary, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(targetSong.artist, color = secondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))
            val progress = if (durationMs > 0) (progressMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            LaunchedEffect(progress, isSeeking) {
                if (!isSeeking) seekProgress = progress
            }
            Box(
                Modifier.fillMaxWidth().height(64.dp).then(
                    if (playerBlurEnabled) Modifier.drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = { RoundedCornerShape(24.dp) },
                        effects = {
                            vibrancy()
                            blur((if (liquidGlass) 2.dp else 18.dp).toPx())
                            if (liquidGlass) lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true, chromaticAberration = true)
                        },
                        highlight = if (liquidGlass) ({ Highlight.Default }) else null,
                        shadow = if (liquidGlass) ({ Shadow.Default }) else null,
                        innerShadow = if (liquidGlass) ({ InnerShadow(radius = 5.dp, alpha = .6f) }) else null,
                        onDrawSurface = {
                            drawRect(
                                if (liquidGlass) Color.White.copy(alpha = .055f)
                                else primary.copy(alpha = if (isLightTheme) .16f else .10f)
                            )
                        }
                    ) else Modifier.background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(24.dp)
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                Slider(
                    value = if (isSeeking) seekProgress else progress,
                    onValueChange = { isSeeking = true; seekProgress = it },
                    onValueChangeFinished = {
                        onSeek((seekProgress * durationMs).toLong())
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                    thumbColor = primary, activeTrackColor = primary, inactiveTrackColor = primary.copy(.18f)
                ), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatPlayerTime(progressMs), color = secondary, fontSize = 12.sp)
                Text("-${formatPlayerTime((durationMs - progressMs).coerceAtLeast(0))}", color = secondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                PlayerControlButton("随机播放", false, onClick = onShuffleToggle) { MusicShuffleIcon(Modifier.size(22.dp), if (isShuffled) accent else secondary) }
                if (lyrics.isNotEmpty()) TextButton(onClick = { showLyrics = !showLyrics }, modifier = Modifier.height(MuseDesign.MinTouch)) {
                    Text(if (showLyrics) "封面" else "歌词", color = if (showLyrics) accent else primary, fontWeight = FontWeight.SemiBold)
                } else Spacer(Modifier.width(64.dp))
                PlayerControlButton("循环模式", false, onClick = onRepeatToggle) {
                    Box(contentAlignment = Alignment.Center) {
                        MusicRepeatIcon(Modifier.size(22.dp), if (repeatMode != RepeatMode.NONE) accent else secondary)
                        if (repeatMode == RepeatMode.ONE) Text("1", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        }
    }
}

@Composable
private fun LandscapePlayerContent(
    song: Song, isPlaying: Boolean, progressMs: Long, durationMs: Long, repeatMode: RepeatMode,
    isShuffled: Boolean, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSeek: (Long) -> Unit, onRepeatToggle: () -> Unit, onShuffleToggle: () -> Unit,
    onTransferUp: () -> Unit, onDismiss: () -> Unit, onLandscapeToggle: () -> Unit, accent: Color, primary: Color,
    secondary: Color, artScale: Float, lyrics: List<LyricLine>, currentLine: Int, artUri: Uri?,
    onVerticalDrag: (Float) -> Unit, onVerticalDragEnd: () -> Unit
) {
    Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, amount ->
                    if (amount > 0) change.consume()
                    onVerticalDrag(amount)
                },
                onDragEnd = onVerticalDragEnd,
                onDragCancel = onVerticalDragEnd
            )
        }
        .padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.fillMaxHeight().weight(.9f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxHeight(.68f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            SwipeableAlbumArt(song, artScale, primary, secondary, accent, onPlayPause, onNext, onPrevious, onTransferUp, artUri)
            }
            Spacer(Modifier.height(12.dp))
            Text(song.title, color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未知艺人" },
                color = secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(36.dp))
        Column(Modifier.fillMaxHeight().weight(1.1f)) {
            Box(Modifier.fillMaxSize()) {
                if (lyrics.isNotEmpty()) {
                    LyricsDetailsPager(
                        song, lyrics, currentLine, accent, primary, secondary,
                        onTapLine = onSeek, onCloseLyrics = {}, showCloseButton = false
                    )
                } else {
                    LandscapeSongDetails(song, primary, secondary)
                }
                IconButton(
                    onClick = onLandscapeToggle,
                    modifier = Modifier.align(Alignment.TopEnd).size(MuseDesign.MinTouch)
                ) {
                    Icon(Icons.Default.ScreenRotation, "切换屏幕方向", tint = secondary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun LyricsDetailsPager(
    song: Song, lyrics: List<LyricLine>, currentLine: Int, accent: Color, primary: Color, secondary: Color,
    onTapLine: (Long) -> Unit, onCloseLyrics: () -> Unit, showCloseButton: Boolean = true
) {
    var showDetails by remember(song.id) { mutableStateOf(false) }
    var horizontalDrag by remember { mutableFloatStateOf(0f) }

    AnimatedContent(
        targetState = showDetails,
        modifier = Modifier.fillMaxSize().pointerInput(song.id, showDetails) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    horizontalDrag += amount
                },
                onDragEnd = {
                    if (kotlin.math.abs(horizontalDrag) >= 72f) showDetails = !showDetails
                    horizontalDrag = 0f
                },
                onDragCancel = { horizontalDrag = 0f }
            )
        },
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(160)) },
        label = "lyricsDetails"
    ) { detailsVisible ->
        if (detailsVisible) LandscapeSongDetails(song, primary, secondary)
        else LyricsView(lyrics, currentLine, accent, primary, secondary, onTapLine, onCloseLyrics, showCloseButton)
    }
}

@Composable
private fun LandscapeSongDetails(song: Song, primary: Color, secondary: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp).verticalScroll(rememberScrollState())) {
        Text("歌曲详细信息", color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LandscapeDetailLine("文件", song.fileName.ifBlank { "未知" }, primary, secondary)
        LandscapeDetailLine("编码", song.mimeType.ifBlank { song.formatLabel }, primary, secondary)
        LandscapeDetailLine("时长", song.formattedDuration, primary, secondary)
        LandscapeDetailLine("采样率", if (song.sampleRate > 0) "${song.sampleRate / 1000f} kHz" else "未知", primary, secondary)
        LandscapeDetailLine("码率", if (song.bitrate > 0) "${song.bitrate / 1000} kbps" else "未知", primary, secondary)
        LandscapeDetailLine("专辑", song.album.ifBlank { "未知专辑" }, primary, secondary)
        LandscapeDetailLine("艺人", song.artist.ifBlank { "未知艺人" }, primary, secondary)
        LandscapeDetailLine("路径", song.filePath.ifBlank { song.folderPath }, primary, secondary)
    }
}

@Composable
private fun LandscapeDetailLine(label: String, value: String, primary: Color, secondary: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, color = secondary, fontSize = 11.sp)
        Text(value, color = primary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlayerControlButton(description: String, primary: Boolean, onClick: () -> Unit = {}, content: @Composable BoxScope.() -> Unit): Modifier {
    val modifier = Modifier.size(if (primary) 72.dp else 56.dp).clip(CircleShape)
        .background(if (primary) Color.White else Color.Transparent).clickable(onClick = onClick)
    Box(modifier, contentAlignment = Alignment.Center, content = content)
    return Modifier
}

@Composable
private fun SwipeableAlbumArt(
    song: Song, artScale: Float, primary: Color, secondary: Color, accent: Color,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onTransferUp: () -> Unit,
    artUri: Uri?
) {
    val dragX = remember(song.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latestNext by rememberUpdatedState(onNext)
    val latestPrevious by rememberUpdatedState(onPrevious)
    val latestPlayPause by rememberUpdatedState(onPlayPause)
    val latestTransferUp by rememberUpdatedState(onTransferUp)
    var dragY by remember(song.id) { mutableFloatStateOf(0f) }
    val baseModifier = Modifier.fillMaxWidth().aspectRatio(1f).scale(artScale)
        .graphicsLayer { translationX = dragX.value }
        .shadow(32.dp, RoundedCornerShape(26.dp), ambientColor = accent.copy(.32f), spotColor = accent.copy(.42f))
        .clip(RoundedCornerShape(26.dp)).background(primary.copy(.10f))
        .clickable { latestPlayPause() }
        .pointerInput(song.id) {
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + amount) }
                },
                onDragEnd = {
                    val offset = dragX.value
                    scope.launch {
                        if (offset > 130f) latestPrevious() else if (offset < -130f) latestNext()
                        dragX.animateTo(0f, tween(180))
                    }
                },
                onDragCancel = { scope.launch { dragX.animateTo(0f, tween(180)) } }
            )
        }
        .pointerInput(song.id) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, amount ->
                    if (amount < 0 || dragY < 0) {
                        change.consume()
                        dragY = (dragY + amount).coerceAtMost(0f)
                    }
                },
                onDragEnd = {
                    if (dragY <= -120f) latestTransferUp()
                    dragY = 0f
                },
                onDragCancel = { dragY = 0f }
            )
        }
    AlbumArtwork(song, baseModifier, "${song.title}专辑封面", artUriOverride = artUri)
}

@Composable
private fun PlayerBackdrop(mode: PlayerBgMode, album: Uri?, wallpaper: Uri?, colors: List<Color>, light: Boolean) {
    val blurEnabled = LocalMuseBlurPolicy.current.enabledAt(BlurLocation.PLAYER)
    Box(Modifier.fillMaxSize()) {
        when (mode) {
            PlayerBgMode.CUSTOM -> if (wallpaper != null || album != null) AsyncImage(
                wallpaper ?: album, null,
                Modifier.matchParentSize().scale(1.15f).then(if (blurEnabled) Modifier.blur(30.dp) else Modifier),
                contentScale = ContentScale.Crop
            )
            PlayerBgMode.ALBUM_EXTEND -> if (album != null) AsyncImage(
                album, null,
                Modifier.matchParentSize().scale(1.25f).then(if (blurEnabled) Modifier.blur(55.dp) else Modifier),
                contentScale = ContentScale.Crop
            )
            PlayerBgMode.DYNAMIC_COLOR -> Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(colors[0], colors.getOrElse(1) { colors[0] }))))
        }
        Box(Modifier.matchParentSize().background(if (light) Color.White.copy(.22f) else Color.Black.copy(.26f)))
    }
}

@Composable
internal fun AnimatedGlowHalos(albumColors: List<Color>, isPlaying: Boolean) {
    // Compatibility layer for the legacy AppleMusicScreen; intentionally static for performance.
    val alpha = if (isPlaying) .18f else .10f
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(albumColors.first().copy(alpha), Color.Transparent))))
}

private fun formatPlayerTime(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
