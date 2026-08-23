package com.caipan.music.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioQuality(val label: String, val lxKey: String) {
    STANDARD("标准 128k", "128k"),
    HIGH("高品 320k", "320k"),
    LOSSLESS("无损 FLAC", "flac"),
    HI_RES("Hi-Res", "flac24bit");

    companion object {
        fun fromName(name: String?): AudioQuality = entries.firstOrNull { it.name == name } ?: HIGH
    }
}

enum class ReverbPreset(val label: String, val androidValue: Short) {
    NONE("关闭", 0),
    SMALL_ROOM("小房间", 1),
    MEDIUM_ROOM("中等房间", 2),
    LARGE_ROOM("大房间", 3),
    MEDIUM_HALL("中等音乐厅", 4),
    LARGE_HALL("大音乐厅", 5),
    PLATE("板式", 6);

    companion object {
        fun fromOrdinal(index: Int): ReverbPreset = entries.getOrElse(index) { NONE }
    }
}

data class PlaybackSettings(
    val crossfadeEnabled: Boolean = false,
    val crossfadeMs: Int = 4000,
    val playbackSpeed: Float = 1.0f,
    val preservePitch: Boolean = true,
    val preferredQuality: AudioQuality = AudioQuality.HIGH,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 300,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 300,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
    val sleepTimerMinutes: Int = 0,
    val sleepTimerEndOfSong: Boolean = false,
    val lyricsFontSize: Int = 24
) {
    val sleepTimerActive: Boolean get() = sleepTimerMinutes > 0 || sleepTimerEndOfSong
}

class PlaybackSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("muse_playback", 0)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<PlaybackSettings> = _state.asStateFlow()

    private fun load() = PlaybackSettings(
        crossfadeEnabled = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false),
        crossfadeMs = prefs.getInt(KEY_CROSSFADE_MS, 4000),
        playbackSpeed = prefs.getFloat(KEY_SPEED, 1.0f),
        preservePitch = prefs.getBoolean(KEY_PRESERVE_PITCH, true),
        preferredQuality = AudioQuality.fromName(prefs.getString(KEY_QUALITY, null)),
        bassBoostEnabled = prefs.getBoolean(KEY_BASS_ENABLED, false),
        bassBoostStrength = prefs.getInt(KEY_BASS_STRENGTH, 300),
        virtualizerEnabled = prefs.getBoolean(KEY_VIRT_ENABLED, false),
        virtualizerStrength = prefs.getInt(KEY_VIRT_STRENGTH, 300),
        reverbPreset = ReverbPreset.fromOrdinal(prefs.getInt(KEY_REVERB, 0)),
        sleepTimerMinutes = prefs.getInt(KEY_SLEEP_MINUTES, 0),
        sleepTimerEndOfSong = prefs.getBoolean(KEY_SLEEP_END_SONG, false),
        lyricsFontSize = prefs.getInt(KEY_LYRICS_FONT_SIZE, 24)
    )

    fun update(transform: (PlaybackSettings) -> PlaybackSettings) {
        val updated = transform(_state.value)
        _state.value = updated
        prefs.edit().apply {
            putBoolean(KEY_CROSSFADE_ENABLED, updated.crossfadeEnabled)
            putInt(KEY_CROSSFADE_MS, updated.crossfadeMs)
            putFloat(KEY_SPEED, updated.playbackSpeed)
            putBoolean(KEY_PRESERVE_PITCH, updated.preservePitch)
            putString(KEY_QUALITY, updated.preferredQuality.name)
            putBoolean(KEY_BASS_ENABLED, updated.bassBoostEnabled)
            putInt(KEY_BASS_STRENGTH, updated.bassBoostStrength)
            putBoolean(KEY_VIRT_ENABLED, updated.virtualizerEnabled)
            putInt(KEY_VIRT_STRENGTH, updated.virtualizerStrength)
            putInt(KEY_REVERB, updated.reverbPreset.ordinal)
            putInt(KEY_SLEEP_MINUTES, updated.sleepTimerMinutes)
            putBoolean(KEY_SLEEP_END_SONG, updated.sleepTimerEndOfSong)
            putInt(KEY_LYRICS_FONT_SIZE, updated.lyricsFontSize)
        }.apply()
    }

    fun setSleepTimer(minutes: Int, endOfSong: Boolean) {
        update { it.copy(sleepTimerMinutes = minutes, sleepTimerEndOfSong = endOfSong) }
    }

    fun clearSleepTimer() {
        update { it.copy(sleepTimerMinutes = 0, sleepTimerEndOfSong = false) }
    }

    private companion object {
        const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        const val KEY_CROSSFADE_MS = "crossfade_ms"
        const val KEY_SPEED = "playback_speed"
        const val KEY_PRESERVE_PITCH = "preserve_pitch"
        const val KEY_QUALITY = "preferred_quality"
        const val KEY_BASS_ENABLED = "bass_boost_enabled"
        const val KEY_BASS_STRENGTH = "bass_boost_strength"
        const val KEY_VIRT_ENABLED = "virtualizer_enabled"
        const val KEY_VIRT_STRENGTH = "virtualizer_strength"
        const val KEY_REVERB = "reverb_preset"
        const val KEY_SLEEP_MINUTES = "sleep_minutes"
        const val KEY_SLEEP_END_SONG = "sleep_end_song"
        const val KEY_LYRICS_FONT_SIZE = "lyrics_font_size"
    }
}
