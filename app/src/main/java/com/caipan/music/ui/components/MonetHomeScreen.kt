package com.caipan.music.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.caipan.music.model.Song
import com.kyant.backdrop.Backdrop

/** Material You mode shares Muse's content-first layout and only changes its tonal accent. */
@Composable
fun MonetHomeScreen(
    accentColor: Color,
    isLightTheme: Boolean,
    onPlaylistsTap: () -> Unit,
    onLocalMusicTap: () -> Unit,
    onSettingsTap: () -> Unit,
    onWebdavTap: () -> Unit = {},
    onPickAvatar: () -> Unit = {},
    onProfileNameChange: (String) -> Unit = {},
    recentSongs: List<Song> = emptyList(),
    allSongs: List<Song> = recentSongs,
    profileName: String = "Muse 用户",
    profileAvatar: Uri? = null,
    listeningTimeMs: Long = 0,
    completedPlays: Int = 0,
    repeatCount: Int = 0,
    onRecentSongTap: (Song) -> Unit = {},
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onPlayPause: () -> Unit = {},
    onTapPlayer: () -> Unit = {},
    backdrop: Backdrop? = null,
) = HomeScreen(
    accentColor = accentColor,
    isLightTheme = isLightTheme,
    isMonetStyle = true,
    onPlaylistsTap = onPlaylistsTap,
    onLocalMusicTap = onLocalMusicTap,
    onSettingsTap = onSettingsTap,
    onWebdavTap = onWebdavTap,
    onPickAvatar = onPickAvatar,
    onProfileNameChange = onProfileNameChange,
    recentSongs = recentSongs,
    allSongs = allSongs,
    profileName = profileName,
    profileAvatar = profileAvatar,
    listeningTimeMs = listeningTimeMs,
    completedPlays = completedPlays,
    repeatCount = repeatCount,
    onRecentSongTap = onRecentSongTap,
    currentSong = currentSong,
    isPlaying = isPlaying,
    onPlayPause = onPlayPause,
    onTapPlayer = onTapPlayer,
    backdrop = backdrop
)
