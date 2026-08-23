package com.caipan.music.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Read-only NetEase catalog search. It does not log in, persist cookies, or resolve media URLs.
 * Playback URLs are intentionally delegated to the user's online source plugin.
 */
class NeteaseCatalog(
    private val client: OkHttpClient = defaultClient()
) : OnlineCatalog, PlaybackCapability {
    override val sourceId: String get() = NETEASE_SOURCE
    override val displayName: String get() = "网易云音乐"
    override val capabilities: Set<MusicCapability> =
        setOf(MusicCapability.Search, MusicCapability.Playback, MusicCapability.Lyrics)

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int
    ): Result<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return@withContext Result.success(emptyList())

        try {
            require(limit in 1..MAX_LIMIT) { "limit 必须在 1 到 $MAX_LIMIT 之间" }
            require(offset >= 0) { "offset 不能小于 0" }

            val url = SEARCH_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("s", normalizedQuery)
                .addQueryParameter("type", "1")
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("total", "true")
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw CatalogException("在线搜索失败（HTTP ${response.code}）")
                }

                val body = response.body ?: throw CatalogException("在线搜索返回了空响应")
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_RESPONSE_BYTES) {
                    throw CatalogException("在线搜索响应过大")
                }
                val payload = body.string()
                if (payload.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                    throw CatalogException("在线搜索响应过大")
                }
                Result.success(parseTracks(payload))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(
                when (error) {
                    is CatalogException -> error
                    is JSONException -> CatalogException("在线搜索返回的数据无法解析", error)
                    is IOException -> CatalogException("无法连接在线音乐服务，请检查网络后重试", error)
                    is IllegalArgumentException -> error
                    else -> CatalogException("在线搜索失败", error)
                }
            )
        }
    }

    override suspend fun resolvePlayback(track: OnlineTrack): Result<LxResolvedMusicUrl> = withContext(Dispatchers.IO) {
        if (track.source != NETEASE_SOURCE || !track.sourceId.matches(Regex("^[0-9]+$"))) {
            return@withContext Result.failure(CatalogException("不是有效的网易云歌曲"))
        }
        try {
            var lastError: Exception? = null
            for (bitrate in PLAYBACK_BITRATES) {
                try {
                    val url = PLAYBACK_ENDPOINT.toHttpUrl().newBuilder()
                        .addQueryParameter("ids", "[${track.sourceId}]")
                        .addQueryParameter("br", bitrate.toString())
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .header("Accept", "application/json")
                        .header("Referer", "https://music.163.com/")
                        .header("User-Agent", USER_AGENT)
                        .build()
                    client.newCall(request).await().use { response ->
                        if (!response.isSuccessful) throw CatalogException("网易播放地址请求失败（HTTP ${response.code}）")
                        val body = response.body ?: throw CatalogException("网易播放地址返回了空响应")
                        if (body.contentLength() > MAX_RESPONSE_BYTES) {
                            throw CatalogException("网易播放地址响应过大")
                        }
                        val payload = body.string()
                        if (payload.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                            throw CatalogException("网易播放地址响应过大")
                        }
                        val root = JSONObject(payload)
                        val data = root.optJSONArray("data")?.optJSONObject(0)
                            ?: throw CatalogException("网易未返回播放地址")
                        val mediaUrl = data.stringOrNull("url")
                            ?: throw CatalogException(data.stringOrNull("message") ?: "该歌曲当前不可播放")
                        if (data.optInt("code", 200) != 200 || !mediaUrl.startsWith("http")) {
                            throw CatalogException(data.stringOrNull("message") ?: "该歌曲当前不可播放")
                        }
                        validatePlaybackMetadata(track, data)
                        return@withContext Result.success(LxResolvedMusicUrl(
                            url = SafeOnlineHttp.validateMediaUrl(mediaUrl),
                            headers = mapOf("Referer" to "https://music.163.com/", "User-Agent" to USER_AGENT),
                            quality = if (bitrate >= 320_000) "320k" else "128k",
                            expiresAtEpochMs = data.optLong("expi", 0L).takeIf { it > 0L }
                                ?.let { System.currentTimeMillis() + it * 1_000L }
                        ))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                }
            }
            Result.failure(lastError ?: CatalogException("网易未返回可用播放地址"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun resolveLyrics(track: OnlineTrack): Result<String?> = withContext(Dispatchers.IO) {
        if (track.source != NETEASE_SOURCE || !track.sourceId.matches(Regex("^[0-9]+$"))) {
            return@withContext Result.failure(CatalogException("不是有效的网易云歌曲"))
        }
        try {
            val url = LYRICS_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("id", track.sourceId)
                .addQueryParameter("lv", "-1")
                .addQueryParameter("kv", "-1")
                .addQueryParameter("tv", "-1")
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw CatalogException("网易歌词请求失败（HTTP ${response.code}）")
                }
                val body = response.body ?: throw CatalogException("网易歌词返回了空响应")
                val payload = body.string()
                val root = JSONObject(payload)
                Result.success(root.optJSONObject("lrc")?.stringOrNull("lyric"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun validatePlaybackMetadata(track: OnlineTrack, data: JSONObject) {
        val actualDuration = data.optLong("time", 0L)
        val duration = actualDuration.takeIf { it > 0L } ?: track.durationMs
        if (track.durationMs >= MIN_DURATION_CHECK_MS && actualDuration > 0L &&
            actualDuration < MIN_ACCEPT_DURATION_MS
        ) {
            throw CatalogException("返回的音频片段过短，无法播放")
        }
        val size = data.optLong("size", 0L)
        if (size > 0L && duration >= MIN_DURATION_CHECK_MS && size * 8_000L / duration < MIN_AUDIO_BITRATE) {
            throw CatalogException("返回的音频文件不完整，无法播放")
        }
    }

    private fun parseTracks(payload: String): List<OnlineTrack> {
        val root = JSONObject(payload)
        val code = root.optInt("code", 200)
        if (code != 200) {
            val message = root.stringOrNull("message") ?: "服务返回错误码 $code"
            throw CatalogException("在线搜索失败：$message")
        }

        val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        val tracks = ArrayList<OnlineTrack>(songs.length())
        val seenIds = HashSet<String>()
        for (index in 0 until songs.length()) {
            val song = songs.optJSONObject(index) ?: continue
            val id = song.valueAsString("id") ?: continue
            val title = song.stringOrNull("name") ?: song.stringOrNull("title") ?: continue
            if (id.isBlank() || title.isBlank() || !seenIds.add(id)) continue

            val artistArray = song.optJSONArray("artists") ?: song.optJSONArray("ar")
            val artists = artistArray.readNames()
            val albumObject = song.optJSONObject("album") ?: song.optJSONObject("al")
            val album = albumObject?.stringOrNull("name").orEmpty()
            val duration = song.longOrZero("duration").takeIf { it > 0L }
                ?: song.longOrZero("dt")
            val artworkUrl = normalizeNeteaseImageUrl(albumObject?.stringOrNull("picUrl"))

            tracks += OnlineTrack(
                source = NETEASE_SOURCE,
                sourceId = id,
                title = title,
                artists = artists,
                album = album,
                durationMs = duration.coerceAtLeast(0L),
                artworkUrl = artworkUrl,
                metadata = mapOf("songmid" to id)
            )
        }
        return tracks
    }

    class CatalogException internal constructor(
        message: String,
        cause: Throwable? = null
    ) : IOException(message, cause)

    companion object {
        const val NETEASE_SOURCE = "wy"

        private const val SEARCH_ENDPOINT = "https://music.163.com/api/cloudsearch/pc"
        private const val PLAYBACK_ENDPOINT = "https://music.163.com/api/song/enhance/player/url"
        private const val LYRICS_ENDPOINT = "https://music.163.com/api/song/lyric"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Mobile Safari/537.36"
        private const val DEFAULT_LIMIT = 30
        private const val MAX_LIMIT = 50
        private const val MAX_RESPONSE_BYTES = 2L * 1024L * 1024L
        private const val MIN_DURATION_CHECK_MS = 30_000L
        private const val MIN_ACCEPT_DURATION_MS = 15_000L
        private const val MIN_AUDIO_BITRATE = 12_000L
        private val PLAYBACK_BITRATES = listOf(320_000, 128_000)

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCompleted) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) {
                continuation.resume(response)
            } else {
                response.close()
            }
        }
    })
}

private fun JSONObject.stringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf(String::isNotEmpty)
}

private fun JSONObject.valueAsString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toLong().toString()
        is String -> value.trim().takeIf(String::isNotEmpty)
        else -> null
    }
}

private fun JSONObject.longOrZero(key: String): Long {
    if (!has(key) || isNull(key)) return 0L
    return when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }
}

private fun preferHttps(url: String): String =
    if (url.startsWith("http://")) "https://${url.removePrefix("http://")}" else url

private fun JSONArray?.readNames(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val name = optJSONObject(index)?.stringOrNull("name") ?: continue
            if (name !in this) add(name)
        }
    }
}
