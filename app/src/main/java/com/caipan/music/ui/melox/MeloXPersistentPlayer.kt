/*
 * Persistent MeloX player host.
 *
 * This follows the central rule of NEORUAA/Mei_MeloX_Android's
 * BottomSheetPlayer: collapsed and expanded representations remain in one
 * composition tree and are driven by one reversible Animatable progress.
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.caipan.music.model.Song
import com.caipan.music.player.LyricLine
import com.caipan.music.player.PlayerBgMode
import com.caipan.music.player.RepeatMode
import com.kyant.backdrop.Backdrop
import coil.compose.AsyncImage
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch

@Composable
fun MeloXPersistentPlayer(
    expanded: Boolean,
    song: Song,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    isShuffled: Boolean,
    qualityLabel: String?,
    artworkUri: android.net.Uri?,
    backdrop: Backdrop,
    compactNavigationProgress: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onRepeatToggle: () -> Unit,
    onShuffleToggle: () -> Unit,
    onCycleQuality: () -> Unit,
    lyricsLoader: suspend (Song) -> List<LyricLine>,
    onOpenActions: () -> Unit,
    onOpenComments: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onProgressChange: (Float) -> Unit,
    bgMode: PlayerBgMode,
    modifier: Modifier = Modifier,
) {
    val progress = remember(song.id) { Animatable(if (expanded) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var playerBounds by remember(song.id) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var miniCoverBounds by remember(song.id) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var lyricsVisible by remember(song.id) { mutableStateOf(false) }

    // A new target cancels the in-flight spring and resumes from the exact
    // interpolated value, which is the interruption behavior of BottomSheetState.
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    val sheetProgress = progress.value.coerceIn(0f, 1f)
    SideEffect { onProgressChange(sheetProgress) }
    val expandedDragModifier = if (expanded) {
        Modifier.pointerInput(song.id) {
            var dragDistance = 0f
            detectVerticalDragGestures(
                onDragStart = { dragDistance = 0f },
                onVerticalDrag = { change, amount ->
                    change.consume()
                    dragDistance += amount
                    scope.launch {
                        progress.snapTo(
                            (progress.value - amount / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                        )
                    }
                },
                onDragEnd = {
                    val target = when {
                        dragDistance < -48f -> true
                        dragDistance > 48f -> false
                        else -> progress.value >= 0.5f
                    }
                    onExpandedChange(target)
                },
                onDragCancel = { onExpandedChange(progress.value >= 0.5f) },
            )
        }
    } else {
        Modifier
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> playerBounds = coordinates.boundsInRoot() },
    ) {
        // AppleMusicPlayer.kt:179-235. Keep one artwork and continuously
        // interpolate its mini/full geometry using the same sheet progress.
        val normalSize = minOf(maxWidth - 48.dp, maxHeight - 300.dp).coerceAtLeast(170.dp)
        val normalStart = (maxWidth - normalSize) / 2f
        val normalTop = 64.dp
        val headerSize = 46.dp
        val headerStart = 24.dp
        val headerTop = 40.dp
        val targetSize = if (lyricsVisible) headerSize else normalSize
        val targetStart = if (lyricsVisible) headerStart else normalStart
        val targetTop = if (lyricsVisible) headerTop else normalTop
        val targetRadius = if (lyricsVisible) 4.dp else 12.dp

        // Upstream reads MiniPlayer.onCoverBoundsChanged and player bounds in
        // root coordinates. Do the same rather than guessing a capsule offset.
        val measuredMini = miniCoverBounds
        val measuredPlayer = playerBounds
        val miniSize = measuredMini?.width?.takeIf { it > 0f }?.let { with(androidx.compose.ui.platform.LocalDensity.current) { it.toDp() } }
            ?: 32.dp
        val miniStart = if (measuredMini != null && measuredPlayer != null) {
            with(androidx.compose.ui.platform.LocalDensity.current) { (measuredMini.left - measuredPlayer.left).toDp() }
        } else {
            20.dp + 60.dp * compactNavigationProgress
        }
        val miniTop = if (measuredMini != null && measuredPlayer != null) {
            with(androidx.compose.ui.platform.LocalDensity.current) { (measuredMini.top - measuredPlayer.top).toDp() }
        } else {
            maxHeight - NavigationBarHeight - MiniPlayerHeight - 20.dp
        }
        val artworkSize = lerp(miniSize, targetSize, sheetProgress)
        val artworkStart = lerp(miniStart, targetStart, sheetProgress)
        val artworkTop = lerp(miniTop, targetTop, sheetProgress)
        val artworkRadius = lerp(ThumbnailCornerRadius, targetRadius, sheetProgress)
        // At rest the collapsed player must not leave an invisible full-screen
        // hit target. Compose the sheet only once it has actually begun opening.
        // Keep the expanded visual alive during the reverse animation. Its
        // gesture modifier is separately removed once the target is collapsed.
        if (sheetProgress > 0.001f) {
            MeloXPlayerScreen(
                song = song,
                isPlaying = isPlaying,
                progressMs = progressMs,
                durationMs = durationMs,
                repeatMode = repeatMode,
                isShuffled = isShuffled,
                qualityLabel = qualityLabel,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onRepeatToggle = onRepeatToggle,
                onShuffleToggle = onShuffleToggle,
                onCycleQuality = onCycleQuality,
                lyricsLoader = lyricsLoader,
                onOpenActions = onOpenActions,
                onOpenComments = onOpenComments,
                onDismiss = { onExpandedChange(false) },
                externalArtUri = artworkUri,
                bgMode = bgMode,
                transitionProgress = sheetProgress,
                // The interpolated shared cover only owns the transition. Once
                // fully expanded, hand rendering back to PlayerScreen so its
                // lyrics switch and control hit targets are unobstructed.
                showArtwork = sheetProgress >= 0.98f,
                onLyricsVisibilityChange = { lyricsVisible = it },
                modifier = Modifier
                    .fillMaxSize()
                    // Once the caller targets the collapsed anchor, remove this
                    // full-screen gesture node immediately. The visual can still
                    // animate out, but it must never capture page swipes/clicks.
                    .then(expandedDragModifier)
                    .graphicsLayer {
                        alpha = sheetProgress
                        val scale = 0.96f + 0.04f * sheetProgress
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }

        if (sheetProgress < 0.98f) {
            AsyncImage(
                model = artworkUri ?: song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = artworkStart, y = artworkTop)
                    .size(artworkSize)
                    .clip(ContinuousRoundedRectangle(artworkRadius)),
            )
        }

        // This is not a second route: it is the collapsed content of the same
        // host and therefore remains alive throughout a reverse transition.
        MeloXMiniPlayer(
            song = song,
            isPlaying = isPlaying,
            compactProgress = compactNavigationProgress,
            backdrop = backdrop,
            artworkUri = artworkUri,
            hasNext = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Normal: mini player sits above the 80dp tab chrome. While
                // browsing with the compact bar, it follows the same 64dp
                // downward travel used by MainActivity's miniPlayerVerticalOffset.
                .offset(
                    y = -(NavigationBarHeight + NavigationBarBottomMargin) +
                        (NavigationBarHeight - 16.dp) * compactNavigationProgress,
                )
                .graphicsLayer {
                    alpha = 1f - (sheetProgress * 4f).coerceAtMost(1f)
                    val scale = 1f - sheetProgress * 0.04f
                    scaleX = scale
                    scaleY = scale
                },
            onClick = { onExpandedChange(true) },
            onTogglePlayPause = onPlayPause,
            onNext = onNext,
            onArtworkBoundsChanged = { bounds -> miniCoverBounds = bounds },
        )
    }
}
