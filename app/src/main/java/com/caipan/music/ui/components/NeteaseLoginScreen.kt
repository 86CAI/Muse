package com.caipan.music.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.caipan.music.data.NeteaseSessionStore
import kotlinx.coroutines.delay

/** NetEase's public web login; the app only imports and verifies its cookies. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NeteaseLoginScreen(
    onCookie: (String) -> Unit,
    onBack: () -> Unit,
    errorMessage: String? = null,
    isChinese: Boolean = true,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var submittedCookie by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Column {
                Text(if (isChinese) "登录网易云音乐" else "Sign in to NetEase Cloud Music", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (isChinese) "Muse 会在本地读取并验证登录 Cookie，验证通过后才保存。"
                    else "Muse reads the session cookie locally and verifies it before saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                    errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView(context).apply {
                    webView = this
                    // WebView defaults to a black surface on some Android
                    // System WebView builds. Give the login page a real canvas
                    // while its mobile shell and images are loading.
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Muse) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Mobile Safari/537.36"
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                        WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                    }
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    webChromeClient = WebChromeClient()
                    // Without an explicit client Android delegates navigations to
                    // the system browser, whose cookie jar is not readable by Muse.
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme.orEmpty().lowercase()
                            return scheme != "http" && scheme != "https"
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            return if (url.startsWith("http://") || url.startsWith("https://")) {
                                false
                            } else {
                                // Do not leak login redirects to another app.
                                true
                            }
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            pageLoading = true
                            pageError = null
                            super.onPageStarted(view, url, favicon)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoading = false
                            cookies.flush()
                            super.onPageFinished(view, url)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                pageLoading = false
                                pageError = error.description?.toString()
                                    ?.takeIf(String::isNotBlank)
                                    ?: "Unable to load NetEase login"
                            }
                            super.onReceivedError(view, request, error)
                        }
                    }
                    // Use the mobile login document directly. The desktop
                    // shell at /# can render as an empty black WebView when
                    // the embedded browser is identified as a desktop client.
                    loadUrl("https://music.163.com/m/login")
                }
            },
                update = { it.settings.javaScriptEnabled = true }
            )
            if (pageLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            pageError?.let { message ->
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    Text(
                        if (isChinese) "请检查网络后重试" else "Check your connection and try again",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    // The WebView reference is assigned from AndroidView's factory. Keying
    // this effect by that mutable reference would dispose the freshly-created
    // view during the first recomposition and leave a black surface behind.
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            val cookie = NeteaseSessionStore.readWebCookies()
            if (NeteaseSessionStore.containsMusicU(cookie) && cookie != submittedCookie) {
                submittedCookie = cookie
                onCookie(cookie)
                // Allow a retry after a transient server/network failure while
                // avoiding a tight loop against the verification endpoint.
                delay(5_000)
                if (submittedCookie == cookie) submittedCookie = null
            }
        }
    }
}
