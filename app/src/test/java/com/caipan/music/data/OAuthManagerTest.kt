package com.caipan.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthManagerTest {

    @Test
    fun authorizedCallbackParsesFullSession() {
        val session = parseOAuthCallback(
            mapOf(
                "status" to "authorized",
                "allow_profile" to "true",
                "allow_api_call" to "true",
                "mchat_account" to "100000001",
                "mchat_nickname" to "张三",
                "mchat_avatar" to "http://example.com/avatars/a.png",
                "mchat_token" to "eyJhbGciOi..."
            )
        )
        assertNotNull(session)
        session!!.let {
            assertEquals("100000001", it.account)
            assertEquals("张三", it.nickname)
            assertEquals("http://example.com/avatars/a.png", it.avatar)
            assertEquals("eyJhbGciOi...", it.token)
            assertTrue(it.allowProfile)
            assertTrue(it.allowApiCall)
            assertTrue(it.isLoggedIn)
        }
    }

    @Test
    fun deniedCallbackReturnsNull() {
        val session = parseOAuthCallback(mapOf("status" to "denied"))
        assertNull(session)
    }

    @Test
    fun missingStatusReturnsNull() {
        val session = parseOAuthCallback(mapOf("mchat_account" to "100000001"))
        assertNull(session)
    }

    @Test
    fun emptyParamsReturnsNull() {
        assertNull(parseOAuthCallback(emptyMap()))
    }

    @Test
    fun profileAndApiFlagsParsedAsFalse() {
        val session = parseOAuthCallback(
            mapOf(
                "status" to "authorized",
                "allow_profile" to "false",
                "allow_api_call" to "false",
                "mchat_account" to "100000002",
                "mchat_nickname" to "n",
                "mchat_token" to "t"
            )
        )
        assertNotNull(session)
        assertFalse(session!!.allowProfile)
        assertFalse(session.allowApiCall)
    }

    @Test
    fun blankTokenMeansNotLoggedIn() {
        // 无有效 token 的 authorized 回调视为登录失败
        val session = parseOAuthCallback(mapOf("status" to "authorized", "mchat_token" to ""))
        assertNull(session)
        val session2 = parseOAuthCallback(mapOf("status" to "authorized", "mchat_token" to "   "))
        assertNull(session2)
    }

    @Test
    fun missingOptionalFieldsFallBackToEmpty() {
        val session = parseOAuthCallback(mapOf("status" to "authorized", "mchat_token" to "tok"))
        assertNotNull(session)
        assertEquals("", session!!.account)
        assertEquals("", session.nickname)
        assertEquals("", session.avatar)
    }
}