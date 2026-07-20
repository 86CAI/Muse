package com.caipan.music.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.caipan.music.plugin.PluginNetworkRequest
import com.caipan.music.plugin.PluginWebUiSession
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluginWebUiScreen(
    session: PluginWebUiSession,
    onRequest: suspend (PluginNetworkRequest) -> Result<JSONObject>,
    onHostRequest: suspend (String, JSONObject) -> Result<JSONObject>,
    onDismiss: () -> Unit,
    isLightTheme: Boolean
) {
    val scope = rememberCoroutineScope()
    val background = if (isLightTheme) Color.White else Color(0xFF161616)
    val foreground = if (isLightTheme) Color(0xFF1C1C1E) else Color.White
    var webView: WebView? = remember { null }

    Box(Modifier.fillMaxSize().background(background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp).imePadding(),
            factory = { context ->
                val assetLoader = WebViewAssetLoader.Builder()
                    .setDomain(LOCAL_DOMAIN)
                    .addPathHandler("/plugin/", WebViewAssetLoader.InternalStoragePathHandler(context, session.rootDirectory))
                    .build()
                WebView(context).apply {
                    webView = this
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    settings.databaseEnabled = false
                    settings.setGeolocationEnabled(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            return if (request.url.host == LOCAL_DOMAIN) assetLoader.shouldInterceptRequest(request.url)
                            else WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), "Forbidden".byteInputStream())
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.url.host != LOCAL_DOMAIN

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            if (Uri.parse(url).host != LOCAL_DOMAIN) view.stopLoading()
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) = request.deny()
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        WebViewCompat.addWebMessageListener(this, "museHost", setOf("https://$LOCAL_DOMAIN")) { view, message, _, _, _ ->
                            val raw = message.data ?: return@addWebMessageListener
                            scope.launch {
                                val response = runCatching {
                                    val json = JSONObject(raw)
                                    val id = json.getString("id")
                                    val type = json.getString("type")
                                    val payload = json.getJSONObject("payload")
                                    val result = if (type == "network.request") {
                                        val headersJson = payload.optJSONObject("headers") ?: JSONObject()
                                        val headers = headersJson.keys().asSequence().associateWith { headersJson.getString(it) }
                                        onRequest(PluginNetworkRequest(
                                            method = payload.optString("method", "GET"),
                                            url = payload.getString("url"),
                                            headers = headers,
                                            body = payload.optString("body").takeIf { payload.has("body") }
                                        )).getOrThrow()
                                    } else {
                                        onHostRequest(type, payload).getOrThrow()
                                    }
                                    JSONObject().put("id", id).put("ok", true).put("response", result)
                                }.getOrElse { error ->
                                    val id = runCatching { JSONObject(raw).optString("id") }.getOrDefault("")
                                    JSONObject().put("id", id).put("ok", false).put("error", error.message ?: "请求失败")
                                }
                                view.postWebMessage(android.webkit.WebMessage(response.toString()), Uri.parse("https://$LOCAL_DOMAIN"))
                            }
                        }
                    }
                    loadUrl("https://$LOCAL_DOMAIN/plugin/${session.entry}")
                }
            }
        )

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭", tint = foreground) }
            Text("插件 WebUI", color = foreground, fontSize = 18.sp)
        }
    }

    DisposableEffect(session.pluginId) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}

private const val LOCAL_DOMAIN = "muse-plugin.local"