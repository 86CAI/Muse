package com.caipan.music.online

import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.util.Base64
import java.util.concurrent.TimeUnit

internal data class OnlineHttpRequest(
    val method: String = "GET",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val timeoutMs: Long = SafeOnlineHttp.DEFAULT_TIMEOUT_MS,
    val requireHttps: Boolean = false,
    val maxResponseBytes: Int = SafeOnlineHttp.DEFAULT_MAX_RESPONSE_BYTES
)

internal data class OnlineHttpResponse(
    val statusCode: Int,
    val body: String,
    val bodyBase64: String,
    val headers: Map<String, String>,
    val finalUrl: String
)

/** Shared fail-closed HTTP boundary for imported source code and catalog clients. */
internal object SafeOnlineHttp {
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    const val MAX_REQUEST_BYTES = 256 * 1024

    private val baseClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    fun execute(input: OnlineHttpRequest): OnlineHttpResponse {
        require(input.maxResponseBytes in 1..MAX_RESPONSE_BYTES) { "Invalid response size limit" }
        var method = input.method.trim().uppercase()
        if (method !in setOf("GET", "POST", "HEAD")) {
            throw IOException("Only GET, POST, and HEAD are allowed")
        }
        var bodyText = input.body
        val bodyBytes = bodyText?.toByteArray(Charsets.UTF_8)?.size ?: 0
        if (bodyBytes > MAX_REQUEST_BYTES) throw IOException("Request body is too large")
        var currentUrl = input.url
        var headers = input.headers.toMutableMap()
        var previousHost: String? = null
        var redirectCount = 0

        while (true) {
            val target = validateTarget(currentUrl, input.requireHttps)
            if (previousHost != null && !previousHost.equals(target.host, ignoreCase = true)) {
                headers.keys.removeAll { it.lowercase() in CROSS_HOST_SENSITIVE_HEADERS }
            }
            headers.forEach { (rawName, rawValue) -> validateHeader(rawName.trim(), rawValue.trim()) }

            val requestBuilder = Request.Builder().url(target.url)
            headers.forEach { (rawName, rawValue) -> requestBuilder.header(rawName.trim(), rawValue.trim()) }
            val contentType = headers.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value?.toMediaTypeOrNull()
            val requestBody = if (method == "POST") (bodyText ?: "").toRequestBody(contentType) else null
            requestBuilder.method(method, requestBody)

            val timeoutMs = input.timeoutMs.coerceIn(1_000L, DEFAULT_TIMEOUT_MS)
            val pinnedClient = baseClient.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .dns(PinnedDns(target.host, target.addresses))
                .build()
            val response = pinnedClient.newCall(requestBuilder.build()).execute()
            try {
                if (response.code in REDIRECT_CODES) {
                    if (redirectCount++ >= MAX_REDIRECTS) throw IOException("Too many redirects")
                    val location = response.header("Location") ?: throw IOException("Redirect has no Location header")
                    val nextUrl = response.request.url.resolve(location) ?: throw IOException("Invalid redirect URL")
                    previousHost = target.host
                    currentUrl = nextUrl.toString()
                    if (response.code == 303 || (response.code in setOf(301, 302) && method == "POST")) {
                        method = "GET"
                        bodyText = null
                        headers.keys.removeAll {
                            it.equals("Content-Type", true) || it.equals("Content-Length", true)
                        }
                    }
                    continue
                }

                val responseBody = response.body
                if (responseBody == null && method != "HEAD") throw IOException("Response body is empty")
                val declaredLength = responseBody?.contentLength() ?: 0L
                if (method != "HEAD" && declaredLength > input.maxResponseBytes) {
                    throw IOException("Response is too large")
                }
                val bytes = if (method == "HEAD") ByteArray(0) else responseBody?.byteStream()?.use { stream ->
                    readLimited(stream, input.maxResponseBytes)
                } ?: ByteArray(0)
                val responseHeaders = linkedMapOf<String, String>()
                response.headers.names().forEach { name ->
                    responseHeaders[name] = response.headers.values(name).joinToString(", ")
                }
                return OnlineHttpResponse(
                    statusCode = response.code,
                    body = bytes.toString(Charsets.UTF_8),
                    bodyBase64 = Base64.getEncoder().encodeToString(bytes),
                    headers = responseHeaders,
                    finalUrl = response.request.url.toString()
                )
            } finally {
                response.close()
            }
        }
    }

    fun validateMediaUrl(url: String): String = validateTarget(url, requireHttps = false).url.toString()

    private fun validateTarget(rawUrl: String, requireHttps: Boolean): ValidatedTarget {
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: throw IOException("Invalid URL")
        val allowedSchemes = if (requireHttps) setOf("https") else setOf("http", "https")
        if (parsed.scheme !in allowedSchemes) throw IOException("URL scheme is not allowed")
        val expectedPort = if (parsed.scheme == "https") 443 else 80
        if (parsed.port != expectedPort) throw IOException("Only standard HTTP ports are allowed")
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw IOException("Credentials in URLs are not allowed")
        }
        val host = runCatching { IDN.toASCII(parsed.host.trimEnd('.')).lowercase() }
            .getOrElse { throw IOException("Invalid host") }
        if (host == "localhost" || host.endsWith(".localhost")) throw IOException("Local hosts are not allowed")
        val addresses = Dns.SYSTEM.lookup(host)
        if (addresses.isEmpty() || addresses.any { !it.isPublicAddress() }) {
            throw IOException("Host does not resolve exclusively to public addresses")
        }
        return ValidatedTarget(parsed, host, addresses)
    }

    private fun validateHeader(name: String, value: String) {
        if (name.isBlank() || name.length > 128 || value.length > 8 * 1024 ||
            name.any { it <= ' ' || it == ':' } || value.any { it == '\r' || it == '\n' }) {
            throw IOException("Invalid request header")
        }
        if (name.lowercase() in BLOCKED_HEADERS) throw IOException("Sensitive request header is not allowed")
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 32 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw IOException("Response is too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun InetAddress.isPublicAddress(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
            return false
        }
        val bytes = address
        if (this is Inet4Address) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            val third = bytes[2].toInt() and 0xff
            if (first == 0 || first == 10 || first == 127 || first >= 224) return false
            if (first == 100 && second in 64..127) return false
            if (first == 169 && second == 254) return false
            if (first == 172 && second in 16..31) return false
            if (first == 192 && second == 168) return false
            if (first == 192 && second == 0 && third in 0..2) return false
            if (first == 198 && second in 18..19) return false
            if (first == 198 && second == 51 && third == 100) return false
            if (first == 203 && second == 0 && third == 113) return false
        }
        if (this is Inet6Address) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if (first and 0xfe == 0xfc) return false
            if (first == 0xfe && second and 0xc0 == 0x80) return false
            if (first == 0x20 && second == 0x01 && (bytes[2].toInt() and 0xff) == 0x0d &&
                (bytes[3].toInt() and 0xff) == 0xb8) return false
        }
        return true
    }

    private data class ValidatedTarget(
        val url: HttpUrl,
        val host: String,
        val addresses: List<InetAddress>
    )

    private class PinnedDns(
        private val expectedHost: String,
        private val addresses: List<InetAddress>
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (!hostname.equals(expectedHost, ignoreCase = true)) throw IOException("Unexpected DNS lookup")
            return addresses
        }
    }

    private val BLOCKED_HEADERS = setOf(
        "proxy-authorization", "host",
        "connection", "proxy-connection", "content-length", "accept-encoding",
        "transfer-encoding", "upgrade", "te", "trailer"
    )
    private val CROSS_HOST_SENSITIVE_HEADERS = setOf("authorization", "cookie", "cookie2")
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    private const val MAX_REDIRECTS = 5
}
