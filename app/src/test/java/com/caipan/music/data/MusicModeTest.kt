package com.caipan.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicModeTest {
    @Test
    fun unknownModeFallsBackToLocal() {
        assertEquals(MusicMode.LOCAL, MusicMode.fromName("something-new"))
    }

    @Test
    fun modeParsingIsCaseInsensitive() {
        assertEquals(MusicMode.ONLINE, MusicMode.fromName("online"))
    }
}
