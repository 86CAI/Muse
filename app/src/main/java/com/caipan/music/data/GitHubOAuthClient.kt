package com.caipan.music.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ── Data classes ──────────────────────────────────────────────────

data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,  // seconds
    val interval: Int    // seconds, polling interval
)

data class AccessTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val scope: String,
    val error: String? = null,
    val errorDescription: String? = null
) {
    val isError: Boolean get() = error != null
}

data class GitHubUser(
    val login: String,
    val avatarUrl: String,
    val name: String? = null
)

/**
 * GitHub OAuth 客户端，支持两种授权流程：
 * - [Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow)
 *   纯客户端，无需 `client_secret`，适合无后端场景。
 * - [Web Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#web-application-flow)
 *   跳浏览器授权，需自建后端代理换 token（`client_secret` 不进入客户端）。
 *
 * 两种流程拿到的 token 完全等价，后续的 user info / Gist 操作共用。
 */
class GitHubOAuthClient(
    private val context: Context,
    private val clientId: String,
    private val tokenProxyUrl: String? = null  // Web Flow 换 token 的后端端点，可选
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── Device Flow ────────────────────────────────────────────────

    /** 发起 Device Flow 授权，返回 device_code / user_code 供用户输入。 */
    suspend fun requestDeviceCode(): DeviceCodeResponse {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "gist")
            .build()
        val req = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .post(body)
            .build()
        val json = executeJson(req)
        return DeviceCodeResponse(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.getString("verification_uri"),
            expiresIn = json.getInt("expires_in"),
            interval = json.getInt("interval")
        )
    }

    /**
     * 轮询 GitHub 直到用户授权或超时。
     * 返回 [AccessTokenResponse]；用户拒绝或超时抛出 [GitHubOAuthException]。
     * 调用方应在协程中调用，可随时取消。
     */
    suspend fun pollDeviceToken(deviceCode: String, intervalSeconds: Int = 5): AccessTokenResponse {
        var consecutiveNetworkErrors = 0
        while (true) {
            delay((intervalSeconds * 1000L).coerceAtLeast(2000))
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val req = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .post(body)
                .build()
            val json = try {
                executeJson(req)
            } catch (e: java.net.UnknownHostException) {
                consecutiveNetworkErrors++
                if (consecutiveNetworkErrors >= 3) {
                    // 后台网络被限制，等待网络恢复后再试
                    awaitNetwork()
                    consecutiveNetworkErrors = 0
                    continue
                }
                delay(3000)
                continue
            }
            consecutiveNetworkErrors = 0
            val resp = parseTokenResponse(json)
            if (resp.accessToken.isNotBlank()) {
                return resp
            }
            if (resp.error == "authorization_pending") {
                continue
            }
            if (resp.error == "slow_down") {
                delay((intervalSeconds * 1000L).coerceAtLeast(5000))
                continue
            }
            throw GitHubOAuthException(resp.error ?: "unknown", resp.errorDescription ?: "")
        }
    }

    /** 挂起直到网络可用（后台切回前台时恢复）。 */
    private suspend fun awaitNetwork() {
        val active = cm.activeNetwork
        if (active != null) {
            val caps = cm.getNetworkCapabilities(active)
            if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) return
        }
        suspendCancellableCoroutine { cont ->
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cont.resume(Unit)
                }
            }
            cm.registerNetworkCallback(request, callback)
            cont.invokeOnCancellation { cm.unregisterNetworkCallback(callback) }
        }
    }

    // ── Web Flow ───────────────────────────────────────────────────

    /** 构造 Web Flow 授权 URL，调用方用浏览器打开。 */
    fun buildAuthorizeUrl(redirectUri: String): String {
        return "https://github.com/login/oauth/authorize" +
            "?client_id=${clientId}" +
            "&redirect_uri=${redirectUri}" +
            "&scope=gist"
    }

    /**
     * Web Flow 用 code 换 token。
     * 需要 [tokenProxyUrl] 在后端代理，封装 `client_secret`。
     * 如果未配置代理 URL，抛出异常。
     */
    suspend fun exchangeCodeForToken(code: String): AccessTokenResponse {
        val proxyUrl = tokenProxyUrl
            ?: throw GitHubOAuthException("no_proxy",
                "Web Flow 需要后端代理 URL，请在设置中配置 GitHub 换 token 端点")
        val body = JSONObject().apply {
            put("code", code)
            put("client_id", clientId)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(proxyUrl)
            .post(body)
            .build()
        val json = executeJson(req)
        return parseTokenResponse(json)
    }

    // ── User Info (两种流程共用) ──────────────────────────────────

    /** 用 access token 获取 GitHub 用户信息。 */
    suspend fun fetchUser(token: String): GitHubUser {
        val req = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val json = executeJson(req)
        return GitHubUser(
            login = json.getString("login"),
            avatarUrl = json.getString("avatar_url"),
            name = if (json.has("name") && !json.isNull("name")) json.getString("name") else null
        )
    }

    // ── Gist ───────────────────────────────────────────────────────

    /** 创建 (或更新已有的) Github Gist。 */
    suspend fun createGist(
        token: String,
        description: String,
        files: Map<String, String>,
        updateGistId: String? = null
    ): String {
        val body = JSONObject().apply {
            put("description", description)
            put("public", false)
            val filesJson = JSONObject()
            files.forEach { (name, content) ->
                filesJson.put(name, JSONObject().apply {
                    put("content", content)
                })
            }
            put("files", filesJson)
        }
        val url = if (updateGistId != null) {
            "https://api.github.com/gists/$updateGistId"
        } else {
            "https://api.github.com/gists"
        }
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .method(if (updateGistId != null) "PATCH" else "POST",
                body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val json = executeJson(req)
        return json.getString("html_url")
    }

    /** 获取用户 Gist 列表，用于查找已有的 Muse 备份 Gist。 */
    suspend fun listGists(token: String, perPage: Int = 30): List<Pair<String, String>> {
        val req = Request.Builder()
            .url("https://api.github.com/gists?per_page=$perPage")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val jsonArray = executeJsonArray(req)
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            result.add(obj.getString("id") to obj.optString("description", ""))
        }
        return result
    }

    /** 获取指定 Gist 的文件内容。返回 Map<文件名, 内容>。 */
    suspend fun getGist(token: String, gistId: String): Map<String, String> {
        val req = Request.Builder()
            .url("https://api.github.com/gists/$gistId")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val json = executeJson(req)
        val files = json.getJSONObject("files")
        val result = mutableMapOf<String, String>()
        files.keys().forEach { name ->
            val fileObj = files.getJSONObject(name)
            val content = fileObj.optString("content", "")
            result[name] = content
        }
        return result
    }

    // ── Internal ───────────────────────────────────────────────────

    private suspend fun executeJson(request: Request): JSONObject {
        val resp = execute(request)
        val body = resp.body?.string() ?: throw GitHubOAuthException("empty_body", "响应为空")
        Log.d(TAG, "GitHub API response: ${body.take(200)}")
        return JSONObject(body)
    }

    private suspend fun executeJsonArray(request: Request): org.json.JSONArray {
        val resp = execute(request)
        val body = resp.body?.string() ?: throw GitHubOAuthException("empty_body", "响应为空")
        return org.json.JSONArray(body)
    }

    private suspend fun execute(request: Request): okhttp3.Response = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                cont.resumeWithException(e)  // 保持原始异常，让调用方捕获 UnknownHostException
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                cont.resume(response)
            }
        })
    }

    private fun parseTokenResponse(json: JSONObject): AccessTokenResponse {
        return AccessTokenResponse(
            accessToken = json.optString("access_token", ""),
            tokenType = json.optString("token_type", ""),
            scope = json.optString("scope", ""),
            error = if (json.has("error") && !json.isNull("error")) json.getString("error") else null,
            errorDescription = if (json.has("error_description") && !json.isNull("error_description")) json.getString("error_description") else null
        )
    }

    companion object {
        private const val TAG = "GitHubOAuth"
    }
}

class GitHubOAuthException(
    val errorCode: String,
    override val message: String
) : Exception(message)