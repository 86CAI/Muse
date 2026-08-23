package com.caipan.music.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OAuthSession(
    val account: String,
    val nickname: String,
    val avatar: String,
    val token: String,
    val allowProfile: Boolean,
    val allowApiCall: Boolean
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()
}

class OAuthManager(context: Context) {
    private val prefs = runCatching {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "muse_oauth",
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse { error ->
        // Keystore 不可用（少见）时降级为普通 SharedPreferences，保证登录流程不中断；
        // 但 token 将明文存储，需显式告警便于观测
        Log.w(TAG, "EncryptedSharedPreferences 不可用，token 将明文存储", error)
        context.applicationContext.getSharedPreferences("muse_oauth", 0)
    }
    private val _session = MutableStateFlow(load())
    val session: StateFlow<OAuthSession?> = _session.asStateFlow()

    private fun load(): OAuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return OAuthSession(
            account = prefs.getString(KEY_ACCOUNT, "") ?: "",
            nickname = prefs.getString(KEY_NICKNAME, "") ?: "",
            avatar = prefs.getString(KEY_AVATAR, "") ?: "",
            token = token,
            allowProfile = prefs.getBoolean(KEY_ALLOW_PROFILE, false),
            allowApiCall = prefs.getBoolean(KEY_ALLOW_API_CALL, false)
        )
    }

    fun saveSession(session: OAuthSession) {
        _session.value = session
        prefs.edit().apply {
            putString(KEY_ACCOUNT, session.account)
            putString(KEY_NICKNAME, session.nickname)
            putString(KEY_AVATAR, session.avatar)
            putString(KEY_TOKEN, session.token)
            putBoolean(KEY_ALLOW_PROFILE, session.allowProfile)
            putBoolean(KEY_ALLOW_API_CALL, session.allowApiCall)
        }.apply()
    }

    fun clearSession() {
        _session.value = null
        prefs.edit().clear().apply()
    }

    /**
     * 解析 MChat OAuth 回调 URI 并保存会话。
     * 返回 true 表示授权成功，false 表示拒绝或无效。
     */
    fun processCallback(uri: Uri): Boolean {
        val params = uri.queryParameterNames.associateWith { uri.getQueryParameter(it) }
        val session = parseOAuthCallback(params) ?: return false
        saveSession(session)
        return true
    }

    companion object {
        private const val TAG = "OAuthManager"
        private const val KEY_ACCOUNT = "mchat_account"
        private const val KEY_NICKNAME = "mchat_nickname"
        private const val KEY_AVATAR = "mchat_avatar"
        private const val KEY_TOKEN = "mchat_token"
        private const val KEY_ALLOW_PROFILE = "allow_profile"
        private const val KEY_ALLOW_API_CALL = "allow_api_call"
    }
}

/**
 * 纯函数：从回调参数表解析 OAuth 会话。授权成功返回 [OAuthSession]，拒绝或无效返回 null。
 * 不依赖 Context/Uri，便于单元测试。
 */
fun parseOAuthCallback(params: Map<String, String?>): OAuthSession? {
    if (params["status"] != "authorized") return null
    val token = params["mchat_token"] ?: ""
    if (token.isBlank()) return null  // 无有效 token 视为登录失败
    return OAuthSession(
        account = params["mchat_account"] ?: "",
        nickname = params["mchat_nickname"] ?: "",
        avatar = params["mchat_avatar"] ?: "",
        token = token,
        allowProfile = params["allow_profile"] == "true",
        allowApiCall = params["allow_api_call"] == "true"
    )
}