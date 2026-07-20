package com.caipan.music.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class WebdavItem(
    val name: String, val path: String, val isDirectory: Boolean,
    val size: Long = 0, val modified: String = ""
)

data class WebdavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = ""
)

class WebdavManager {
    companion object { private const val TAG = "WebdavManager" }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun listDirectory(config: WebdavConfig, path: String = ""): Result<List<WebdavItem>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = config.url.trimEnd('/')
            val dirUrl = if (path.isEmpty()) {
                baseUrl
            } else if (path.startsWith("/")) {
                // path is a full server path (e.g., "/dav/alist/")
                // need scheme+host
                val schemeHost = try {
                    val uri = java.net.URI(baseUrl)
                    "${uri.scheme}://${uri.host}"
                } catch (_: Exception) { baseUrl }
                "$schemeHost$path".trimEnd('/')
            } else {
                "$baseUrl/$path"
            }

            val body = """<?xml version="1.0" encoding="utf-8"?>
                |<D:propfind xmlns:D="DAV:"><D:prop><D:displayname/>
                |<D:getcontentlength/><D:getlastmodified/><D:resourcetype/></D:prop></D:propfind>""".trimMargin()
                .toRequestBody("application/xml; charset=utf-8".toMediaType())

            val req = Request.Builder()
                .url(dirUrl)
                .method("PROPFIND", body)
                .header("Depth", "1")
                .apply {
                    if (config.username.isNotEmpty()) {
                        header("Authorization", Credentials.basic(config.username, config.password))
                    }
                }
                .build()

            client.newCall(req).execute().use { resp ->
                val code = resp.code
                Log.d(TAG, "PROPFIND $dirUrl -> HTTP $code")
                if (code in 200..299 || code == 207) {
                    val xml = resp.body?.string()
                    if (xml != null && xml.isNotEmpty()) {
                        Log.d(TAG, "Response body length: ${xml.length}")
                        val items = parsePropfindRaw(xml)
                        // Filter out the directory itself
                        // item.path is server path like "/dav/alist/"
                        // Extract the path from dirUrl or use path param as-is
                        val selfPath = if (path.startsWith("/")) {
                            path.trimEnd('/')
                        } else {
                            try {
                                java.net.URI(dirUrl).path.trimEnd('/')
                            } catch (_: Exception) { "" }
                        }
                        val filtered = items.filter { item ->
                            item.path.trimEnd('/') != selfPath
                        }
                        Log.d(TAG, "Parsed ${items.size} items, kept ${filtered.size}")
                        Result.success(filtered)
                    } else {
                        Log.e(TAG, "Empty response body")
                        Result.success(emptyList())
                    }
                } else {
                    Result.failure(Exception("HTTP $code"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PROPFIND failed", e)
            Result.failure(e)
        }
    }

    /**
     * Parse PROPFIND XML using simple string/regex matching.
     * Avoids all XML parser quirks on different Android versions.
     */
    private fun parsePropfindRaw(xml: String): List<WebdavItem> {
        val items = mutableListOf<WebdavItem>()
        try {
            // Split by <D:response> blocks
            val responsePattern = Regex("<D:response[ >](.*?)</D:response>", RegexOption.DOT_MATCHES_ALL)
            val hrefPattern = Regex("<D:href[^>]*>(.*?)</D:href>", RegexOption.DOT_MATCHES_ALL)
            val psPattern = Regex("<D:propstat[ >](.*?)</D:propstat>", RegexOption.DOT_MATCHES_ALL)
            val statusPattern = Regex("<D:status[^>]*>(.*?)</D:status>", RegexOption.DOT_MATCHES_ALL)
            val displaynamePattern = Regex("<D:displayname[^>]*>(.*?)</D:displayname>", RegexOption.DOT_MATCHES_ALL)
            val propPattern = Regex("<D:prop[ >](.*?)</D:prop>", RegexOption.DOT_MATCHES_ALL)

            for (respMatch in responsePattern.findAll(xml)) {
                val respXml = respMatch.groupValues[1]
                val href = hrefPattern.find(respXml)?.groupValues?.getOrNull(1)?.trim() ?: continue

                var name = ""
                var isDir = false
                var has200 = false
                var size = 0L

                for (psMatch in psPattern.findAll(respXml)) {
                    val psXml = psMatch.groupValues[1]
                    val st = statusPattern.find(psXml)?.groupValues?.getOrNull(1) ?: ""
                    val is200 = st.contains("200")
                    if (is200) has200 = true

                    val propXml = propPattern.find(psXml)?.groupValues?.getOrNull(1) ?: ""

                    if (is200) {
                        val dn = displaynamePattern.find(propXml)?.groupValues?.getOrNull(1)
                        if (dn != null) name = dn.trim()
                        if (propXml.contains("<D:collection")) isDir = true
                        // size: getcontentlength
                        val clPattern = Regex("<D:getcontentlength[^>]*>\\s*([\\d.]+)\\s*</D:getcontentlength>", RegexOption.DOT_MATCHES_ALL)
                        val cl = clPattern.find(propXml)?.groupValues?.getOrNull(1)
                        if (cl != null) size = cl.toLongOrNull() ?: 0L
                    }
                }

                if (has200 && href.isNotEmpty()) {
                    val dec = try { URLDecoder.decode(href.trimEnd('/'), "UTF-8") } catch (_: Exception) { href.trimEnd('/') }
                    val nm = name.ifEmpty { dec.substringAfterLast('/') }
                    items.add(WebdavItem(nm, dec, isDir, size))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parsePropfindRaw error", e)
        }
        return items
    }

    suspend fun downloadFile(config: WebdavConfig, remotePath: String, localDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = if (remotePath.startsWith("/")) {
                val schemeHost = try {
                    val uri = java.net.URI(config.url.trimEnd('/'))
                    "${uri.scheme}://${uri.host}"
                } catch (_: Exception) { config.url.trimEnd('/') }
                "$schemeHost$remotePath"
            } else {
                "${config.url.trimEnd('/')}/$remotePath"
            }
            val req = Request.Builder()
                .url(url)
                .get()
                .apply {
                    if (config.username.isNotEmpty()) {
                        header("Authorization", Credentials.basic(config.username, config.password))
                    }
                }
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code == 200) {
                    localDir.mkdirs()
                    val f = File(localDir, remotePath.substringAfterLast('/'))
                    FileOutputStream(f).use { o -> resp.body?.byteStream()?.copyTo(o) }
                    Result.success(f)
                } else Result.failure(Exception("HTTP ${resp.code}"))
            }
        } catch (e: Exception) { Log.e(TAG, "Download failed", e); Result.failure(e) }
    }

    // Download remote file content into a MediaStore OutputStream
    suspend fun downloadToStream(config: WebdavConfig, remotePath: String, output: java.io.OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = if (remotePath.startsWith("/")) {
                val schemeHost = try {
                    val uri = java.net.URI(config.url.trimEnd('/'))
                    "${uri.scheme}://${uri.host}"
                } catch (_: Exception) { config.url.trimEnd('/') }
                "$schemeHost$remotePath"
            } else {
                "${config.url.trimEnd('/')}/$remotePath"
            }
            val req = Request.Builder()
                .url(url)
                .get()
                .apply {
                    if (config.username.isNotEmpty()) {
                        header("Authorization", Credentials.basic(config.username, config.password))
                    }
                }
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code == 200) {
                    resp.body?.byteStream()?.use { input ->
                        input.copyTo(output)
                    }
                    Result.success(Unit)
                } else Result.failure(Exception("HTTP ${resp.code}"))
            }
        } catch (e: Exception) { Log.e(TAG, "downloadToStream failed", e); Result.failure(e) }
    }
}
