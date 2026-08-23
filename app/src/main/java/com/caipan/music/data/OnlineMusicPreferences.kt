package com.caipan.music.data

import android.content.Context
import com.caipan.music.player.AudioQuality

class OnlineMusicPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    var onlineSearchEnabled: Boolean
        get() = preferences.getBoolean(KEY_ONLINE_SEARCH_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ONLINE_SEARCH_ENABLED, value).apply()
        }

    /**
     * Replaces the old discoverability switch. Keep the legacy key updated so a
     * downgrade still exposes the existing online-search route instead of
     * silently losing it.
     */
    var musicMode: MusicMode
        get() {
            val persisted = preferences.getString(KEY_MUSIC_MODE, null)
            if (persisted != null) return MusicMode.fromName(persisted)
            return if (onlineSearchEnabled) MusicMode.ONLINE else MusicMode.LOCAL
        }
        set(value) {
            preferences.edit()
                .putString(KEY_MUSIC_MODE, value.name)
                .putBoolean(KEY_ONLINE_SEARCH_ENABLED, value == MusicMode.ONLINE)
                .apply()
        }

    var preferredQuality: AudioQuality
        get() = AudioQuality.fromName(preferences.getString(KEY_PREFERRED_QUALITY, null))
        set(value) {
            preferences.edit().putString(KEY_PREFERRED_QUALITY, value.name).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "online_music_preferences"
        const val KEY_ONLINE_SEARCH_ENABLED = "online_search_enabled"
        const val KEY_MUSIC_MODE = "music_mode"
        const val KEY_PREFERRED_QUALITY = "preferred_quality"
    }
}
