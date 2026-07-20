package com.caipan.music.plugin

import com.caipan.music.model.Song

enum class NextTrigger { COMPLETION, MANUAL, SYSTEM }

data class NextRequest(
    val trigger: NextTrigger,
    val currentSong: Song?,
    val queue: List<Song>,
    val currentIndex: Int
)

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val hooks: List<String>,
    val enabled: Boolean,
    val external: Boolean = false,
    val hasWebUi: Boolean = false,
    val networkAllowHosts: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val grantedPermissions: List<String> = emptyList(),
    val playerGestures: List<PlayerGestureContribution> = emptyList()
)

interface MusePlugin {
    val id: String
    val name: String
    val version: String
    val author: String
    val description: String
    val hooks: List<String>
    val enabledByDefault: Boolean get() = false

    fun onEnable() = Unit
    fun onDisable() = Unit
    fun onShuffle(queue: List<Song>): List<Song>? = null
    fun onNextTrack(request: NextRequest): Song? = null
    fun onTrackFinished(song: Song) = Unit
}
