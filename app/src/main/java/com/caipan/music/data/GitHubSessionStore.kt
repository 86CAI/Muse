package com.caipan.music.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GitHubSession(
    val login: String,
    val avatarUrl: String,
    val name: String?,
    val token: String,
    val gistId: String? = null  // 上次同步的 Gist ID，用于增量更新
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()
}

/**
 * GitHub 登录会话独立存储，与 MChat OAuth 互不干扰。
 * 使用 EncryptedSharedPreferences 加密存储 token。
 */
class GitHubSessionStore(context: Context) {
    private val prefs = runCatching {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "muse_github_oauth",
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse { error ->
        Log.w(TAG, "EncryptedSharedPreferences 不可用，GitHub token 将明文存储", error)
        context.applicationContext.getSharedPreferences("muse_github_oauth", 0)
    }

    private val _session = MutableStateFlow(load())
    val session: StateFlow<GitHubSession?> = _session.asStateFlow()

    private fun load(): GitHubSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return GitHubSession(
            login = prefs.getString(KEY_LOGIN, "") ?: "",
            avatarUrl = prefs.getString(KEY_AVATAR_URL, "") ?: "",
            name = prefs.getString(KEY_NAME, null),
            token = token,
            gistId = prefs.getString(KEY_GIST_ID, null)
        )
    }

    fun saveSession(session: GitHubSession) {
        _session.value = session
        prefs.edit().apply {
            putString(KEY_LOGIN, session.login)
            putString(KEY_AVATAR_URL, session.avatarUrl)
            putString(KEY_NAME, session.name)
            putString(KEY_TOKEN, session.token)
            session.gistId?.let { putString(KEY_GIST_ID, it) }
        }.apply()
    }

    fun updateGistId(gistId: String) {
        _session.value = _session.value?.copy(gistId = gistId)
        prefs.edit().putString(KEY_GIST_ID, gistId).apply()
    }

    fun clearSession() {
        _session.value = null
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "GitHubSessionStore"
        private const val KEY_LOGIN = "github_login"
        private const val KEY_AVATAR_URL = "github_avatar_url"
        private const val KEY_NAME = "github_name"
        private const val KEY_TOKEN = "github_token"
        private const val KEY_GIST_ID = "github_gist_id"
    }
}