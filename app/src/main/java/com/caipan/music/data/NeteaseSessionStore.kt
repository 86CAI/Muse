package com.caipan.music.data

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NeteaseSession(
    val cookie: String,
    val userId: Long = 0L,
    val nickname: String = "",
    val avatarUrl: String = ""
) {
    val isLoggedIn: Boolean get() = NeteaseSessionStore.containsMusicU(cookie)
}

/** Stores the complete NetEase cookie header; MUSIC_U alone is not sufficient. */
class NeteaseSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = runCatching {
        val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS,
            key,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse { error ->
        Log.w(TAG, "Encrypted preferences unavailable; using compatibility storage", error)
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _session = MutableStateFlow(load())
    val session: StateFlow<NeteaseSession?> = _session.asStateFlow()

    fun save(session: NeteaseSession) {
        val normalized = session.copy(cookie = normalizeCookie(session.cookie))
        prefs.edit()
            .putString(KEY_COOKIE, normalized.cookie)
            .putLong(KEY_USER_ID, normalized.userId)
            .putString(KEY_NICKNAME, normalized.nickname)
            .putString(KEY_AVATAR, normalized.avatarUrl)
            .apply()
        _session.value = normalized
    }

    fun updateProfile(userId: Long, nickname: String, avatarUrl: String) {
        val current = _session.value ?: return
        save(current.copy(userId = userId, nickname = nickname, avatarUrl = avatarUrl))
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    private fun load(): NeteaseSession? {
        val cookie = prefs.getString(KEY_COOKIE, null).orEmpty()
        return cookie.takeIf(::containsMusicU)?.let {
            NeteaseSession(
                cookie = it,
                userId = prefs.getLong(KEY_USER_ID, 0L),
                nickname = prefs.getString(KEY_NICKNAME, "").orEmpty(),
                avatarUrl = prefs.getString(KEY_AVATAR, "").orEmpty()
            )
        }
    }

    companion object {
        private const val TAG = "NeteaseSessionStore"
        private const val PREFS = "muse_netease_session"
        private const val KEY_COOKIE = "cookie_header"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_AVATAR = "avatar_url"

        fun containsMusicU(cookieHeader: String): Boolean =
            parseCookie(cookieHeader)["MUSIC_U"].orEmpty().isNotBlank()

        fun parseCookie(cookieHeader: String): Map<String, String> = cookieHeader
            .split(';')
            .mapNotNull { part ->
                val pair = part.trim().split('=', limit = 2)
                if (pair.size != 2 || pair[0].isBlank()) null else pair[0].trim() to pair[1].trim()
            }
            .toMap()

        fun normalizeCookie(cookieHeader: String): String = parseCookie(cookieHeader)
            .toSortedMap()
            .entries
            .joinToString("; ") { (key, value) -> "$key=$value" }

        /** Extracts a cookie header from CookieManager output for both NetEase domains. */
        fun readWebCookies(): String {
            val manager = CookieManager.getInstance()
            val values = listOf(
                manager.getCookie("https://music.163.com/"),
                manager.getCookie("https://interface.music.163.com/")
            )
            return normalizeCookie(values.filterNotNull().joinToString("; "))
        }
    }
}
