package com.caipan.music.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.caipan.music.model.Song
import com.caipan.music.player.LyricLine
import com.caipan.music.player.PlayerBgMode
import com.caipan.music.player.RepeatMode

/** Monet keeps its dynamic-color identity while sharing the same interaction and hierarchy. */
@Composable
fun MonetPlayerScreen(
    song: Song, isPlaying: Boolean, progressMs: Long, durationMs: Long,
    repeatMode: RepeatMode, isShuffled: Boolean,
    onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSeek: (Long) -> Unit, onRepeatToggle: () -> Unit, onShuffleToggle: () -> Unit,
    onDismiss: () -> Unit, onLandscapeToggle: () -> Unit = {}, customBgColor: Color? = null, wallpaperUri: Uri? = null,
    isLightTheme: Boolean = false, bgMode: PlayerBgMode = PlayerBgMode.ALBUM_EXTEND,
    lyricsLoader: (suspend (Song) -> List<LyricLine>)? = null, modifier: Modifier = Modifier
) = PlayerScreen(
    song = song, isPlaying = isPlaying, progressMs = progressMs, durationMs = durationMs,
    repeatMode = repeatMode, isShuffled = isShuffled, onPlayPause = onPlayPause,
    onNext = onNext, onPrevious = onPrevious, onSeek = onSeek,
    onRepeatToggle = onRepeatToggle, onShuffleToggle = onShuffleToggle,
    onDismiss = onDismiss, onLandscapeToggle = onLandscapeToggle,
    customBgColor = customBgColor, wallpaperUri = wallpaperUri,
    isLightTheme = isLightTheme, bgMode = bgMode, lyricsLoader = lyricsLoader,
    backdrop = null, modifier = modifier
)
