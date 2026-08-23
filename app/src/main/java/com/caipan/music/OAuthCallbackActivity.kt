package com.caipan.music

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.caipan.music.data.GitHubOAuthClient
import com.caipan.music.data.GitHubSessionStore
import com.caipan.music.data.OAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID

/**
 * 接收 OAuth 授权回调的透明 Activity。
 * 根据 path 分发：
 * - `/callback`  → MChat OAuth
 * - `/github/callback` → GitHub Web Flow
 */
class OAuthCallbackActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }

        val path = uri.path ?: ""
        when {
            path == "/github/callback" -> handleGitHubCallback(uri)
            else -> handleMChatCallback(uri)
        }
    }

    private fun handleMChatCallback(uri: android.net.Uri) {
        val oauthManager = (application as MuseApplication).oauthManager
        val success = oauthManager.processCallback(uri)

        if (success) {
            val nickname = uri.getQueryParameter("mchat_nickname") ?: ""
            Toast.makeText(this, "MChat 登录成功: $nickname", Toast.LENGTH_SHORT).show()
            // 同步 MChat 头像和昵称到 Muse 个人页
            val mchatAvatar = uri.getQueryParameter("mchat_avatar")
            if (mchatAvatar != null) {
                syncGitHubProfileToMuse(nickname, mchatAvatar)
            } else {
                getSharedPreferences("muse_prefs", 0).edit().putString("profile_name", nickname).apply()
            }
        } else {
            val status = uri.getQueryParameter("status")
            if (status == "denied") {
                Toast.makeText(this, "已拒绝 MChat 授权", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }

    private fun handleGitHubCallback(uri: android.net.Uri) {
        val app = application as MuseApplication
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            val error = uri.getQueryParameter("error") ?: "unknown"
            val errorDesc = uri.getQueryParameter("error_description") ?: ""
            Toast.makeText(this, "GitHub 授权失败: $error ($errorDesc)", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val client = app.gitHubOAuthClient
        val store = app.gitHubSessionStore

        Toast.makeText(this, "GitHub 登录中...", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val tokenResp = client.exchangeCodeForToken(code)
                if (tokenResp.isError) {
                    Toast.makeText(
                        this@OAuthCallbackActivity,
                        "GitHub 登录失败: ${tokenResp.error} — ${tokenResp.errorDescription}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val user = client.fetchUser(tokenResp.accessToken)
                    store.saveSession(
                        com.caipan.music.data.GitHubSession(
                            login = user.login,
                            avatarUrl = user.avatarUrl,
                            name = user.name,
                            token = tokenResp.accessToken
                        )
                    )
                    // 同步头像和昵称到 Muse 个人页
                    syncGitHubProfileToMuse(user.name ?: user.login, user.avatarUrl)
                    Toast.makeText(
                        this@OAuthCallbackActivity,
                        "GitHub 登录成功: ${user.name ?: user.login}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@OAuthCallbackActivity,
                    "GitHub 登录失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }

    private fun syncGitHubProfileToMuse(name: String, avatarUrl: String) {
        val prefs = getSharedPreferences("muse_prefs", 0)
        prefs.edit().putString("profile_name", name).apply()
        Thread {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url(avatarUrl).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes() ?: return@Thread
                    val dir = File(filesDir, "profile_avatars").apply { mkdirs() }
                    val file = File(dir, "avatar_${UUID.randomUUID()}.jpg")
                    file.writeBytes(bytes)
                    prefs.edit().putString("profile_avatar", file.absolutePath).apply()
                }
            } catch (_: Exception) { }
        }.start()
    }
}