package com.caipan.music.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class PluginNetworkRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?
)

class PluginNetworkProxy {
    suspend fun execute(allowHosts: Set<String>, request: PluginNetworkRequest): JSONObject = withContext(Dispatchers.IO) {
        val baseClient = CLIENT
        val parsed = request.url.toHttpUrlOrNull() ?: throw IOException("URL 无效")
        if (parsed.scheme != "https" || parsed.port != 443 || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw IOException("仅允许标准端口的 HTTPS 请求")
        }
        val host = IDN.toASCII(parsed.host.trimEnd('.')).lowercase()
        if (host !in allowHosts) throw IOException("插件未获准访问该域名")
        val addresses = Dns.SYSTEM.lookup(host)
        if (addresses.isEmpty() || addresses.any { !it.isPublicAddress() }) throw IOException("目标域名解析到非公网地址")

        val method = request.method.uppercase()
        if (method !in ALLOWED_METHODS) throw IOException("仅允许 GET 和 POST")
        val bodyText = request.body
        if ((bodyText?.toByteArray(Charsets.UTF_8)?.size ?: 0) > MAX_REQUEST_BYTES) throw IOException("请求体过大")
        val builder = Request.Builder().url(parsed)
        request.headers.forEach { (name, value) ->
            if (name.lowercase() !in ALLOWED_HEADERS || value.length > 1024) throw IOException("请求头不受支持：$name")
            builder.header(name, value)
        }
        val contentType = request.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }
            ?.value?.toMediaTypeOrNull()
        val requestBody = if (method == "POST") (bodyText ?: "").toRequestBody(contentType) else null
        builder.method(method, requestBody)

        val client = baseClient.newBuilder().dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = addresses
        }).build()
        client.newCall(builder.build()).execute().use { response ->
            if (response.code in 300..399) throw IOException("插件网络请求不允许重定向")
            val body = response.body ?: throw IOException("响应内容为空")
            if ((body.contentLength() > MAX_RESPONSE_BYTES) && body.contentLength() >= 0) throw IOException("响应内容过大")
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_RESPONSE_BYTES) throw IOException("响应内容过大")
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            JSONObject()
                .put("status", response.code)
                .put("body", bytes.toString(Charsets.UTF_8))
                .put("contentType", response.header("Content-Type").orEmpty())
        }
    }

    private fun InetAddress.isPublicAddress(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) return false
        val bytes = address
        if (this is Inet4Address) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if (first == 0 || first == 10 || first == 127 || first >= 224) return false
            if (first == 100 && second in 64..127) return false
            if (first == 169 && second == 254) return false
            if (first == 172 && second in 16..31) return false
            if (first == 192 && second == 168) return false
            if (first == 198 && second in 18..19) return false
        }
        if (this is Inet6Address) {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if (first and 0xfe == 0xfc || (first == 0xfe && second and 0xc0 == 0x80)) return false
        }
        return true
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 256 * 1024
        const val MAX_RESPONSE_BYTES = 1024 * 1024
        val ALLOWED_METHODS = setOf("GET", "POST")
        val ALLOWED_HEADERS = setOf("accept", "content-type")
        val CLIENT = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}