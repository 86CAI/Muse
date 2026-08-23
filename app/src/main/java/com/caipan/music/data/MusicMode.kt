package com.caipan.music.data

/** The active catalog shown by Muse. Local data is never mixed with remote data. */
enum class MusicMode {
    LOCAL,
    ONLINE;

    companion object {
        fun fromName(value: String?): MusicMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOCAL
    }
}
