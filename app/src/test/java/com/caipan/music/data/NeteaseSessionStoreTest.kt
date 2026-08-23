package com.caipan.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.caipan.music.online.neteaseImageRequestUrl

class NeteaseSessionStoreTest {
    @Test
    fun cookieHeaderIsNormalizedWithoutDroppingValues() {
        val value = NeteaseSessionStore.normalizeCookie("MUSIC_U=abc; __csrf=def; MUSIC_U=abc")
        assertEquals("MUSIC_U=abc; __csrf=def", value)
        assertTrue(NeteaseSessionStore.containsMusicU(value))
    }

    @Test
    fun cookieWithoutMusicUIsNotAuthenticated() {
        assertFalse(NeteaseSessionStore.containsMusicU("__csrf=def; NMTID=ghi"))
    }

    @Test
    fun imageUrlsUseHttpsAndBoundedArtworkSize() {
        assertEquals(
            "https://p2.music.126.net/cover.jpg?param=360y360",
            neteaseImageRequestUrl("http://p2.music.126.net/cover.jpg", 360)
        )
    }
}
