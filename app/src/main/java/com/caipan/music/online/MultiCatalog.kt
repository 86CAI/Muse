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
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 酷我音乐搜索（公开 r.s 接口，无需登录）。 */
class KuwoCatalog(
    private val client: OkHttpClient = defaultClient()
) : OnlineCatalog {
    override val sourceId: String get() = "kw"
    override val displayName: String get() = "酷我音乐"
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Search, MusicCapability.Lyrics)

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int
    ): Result<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val url = SEARCH_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("all", q)
                .addQueryParameter("ft", "music")
                .addQueryParameter("itemset", "web_2013")
                .addQueryParameter("client", "kt")
                .addQueryParameter("pn", (offset / limit.coerceAtLeast(1)).toString())
                .addQueryParameter("rn", limit.toString())
                .addQueryParameter("rformat", "json")
                .addQueryParameter("encoding", "utf8")
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "http://www.kuwo.cn/")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("酷我搜索失败（HTTP ${response.code}）")
                val body = response.body ?: throw IOException("酷我搜索返回了空响应")
                // 酷我 r.s 返回单引号伪 JSON；实测为 GBK 编码（UTF-8 解码会出现替换字符），
                // 用替换字符检测做编码回退，避免乱码解析
                val rawBytes = body.bytes()
                val utf8Text = rawBytes.toString(StandardCharsets.UTF_8)
                val text = if ('\uFFFD' in utf8Text) {
                    rawBytes.toString(KUWO_CHARSET)
                } else {
                    utf8Text
                }
                Result.success(parseTracks(text))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun parseTracks(raw: String): List<OnlineTrack> {
        // 注意：abslist 内每个 item 都含 'SUBLIST':[] 字段，必须贪婪匹配到 abslist 数组真正的结尾
        val abslistMatch = Regex("'abslist'\\s*:\\s*\\[(.*)]", RegexOption.DOT_MATCHES_ALL).find(raw)
            ?: return emptyList()
        val abslistContent = abslistMatch.groupValues[1]
        val tracks = ArrayList<OnlineTrack>()
        val seen = HashSet<String>()
        for (obj in topLevelObjects(abslistContent)) {
            val rid = field(obj, "MUSICRID").removePrefix("MUSIC_").trim()
            if (rid.isBlank() || !seen.add(rid)) continue
            val title = decodeHtml(field(obj, "SONGNAME")).trim()
            if (title.isBlank()) continue
            val artist = decodeHtml(field(obj, "ARTIST")).trim()
            val album = decodeHtml(field(obj, "ALBUM")).trim()
            val durationSec = field(obj, "DURATION").toLongOrNull() ?: 0L
            val pic = field(obj, "PICPATH").trim()
            val albumPic = field(obj, "web_albumpic_short").trim()
            val artistPic = field(obj, "web_artistpic_short").trim()
            val artwork = when {
                pic.startsWith("http") -> pic
                pic.isNotBlank() -> "http://img1.kuwo.cn/star/albumcover/$pic"
                albumPic.isNotBlank() -> "https://img1.kwcdn.kuwo.cn/star/albumcover/${albumPic.replaceBefore('/', "240")}" 
                artistPic.isNotBlank() -> "https://star.kuwo.cn/star/starheads/$artistPic"
                else -> null
            }
            tracks += OnlineTrack(
                source = sourceId,
                sourceId = rid,
                title = title,
                artists = listOf(artist).filter(String::isNotBlank),
                album = album,
                durationMs = durationSec * 1_000L,
                artworkUrl = artwork,
                metadata = mapOf(
                    "songmid" to rid,
                    "name" to title,
                    "singer" to artist,
                    "album" to album,
                    "duration" to (durationSec * 1_000L).toString()
                )
            )
        }
        return tracks
    }

    override suspend fun resolveLyrics(track: OnlineTrack): Result<String?> = withContext(Dispatchers.IO) {
        try {
            if (track.source != sourceId) throw IOException("不是酷我歌曲")
            val url = LYRICS_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("musicId", track.sourceId)
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("Referer", "https://www.kuwo.cn/")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("酷我歌词请求失败（HTTP ${response.code}）")
                val body = response.body ?: throw IOException("酷我歌词返回了空响应")
                val root = JSONObject(body.string())
                val lrclist = root.optJSONObject("data")?.optJSONArray("lrclist") ?: return@use Result.success(null)
                val builder = StringBuilder()
                for (index in 0 until lrclist.length()) {
                    val line = lrclist.optJSONObject(index) ?: continue
                    val time = line.optString("time")
                    val text = line.optString("lineLyric")
                    if (text.isBlank()) continue
                    if (time.isBlank()) builder.append(text).append('\n')
                    else builder.append('[').append(time).append(']').append(text).append('\n')
                }
                Result.success(builder.toString().takeIf { it.isNotBlank() })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    companion object {
        private const val SEARCH_ENDPOINT = "https://search.kuwo.cn/r.s"
        private const val LYRICS_ENDPOINT = "https://m.kuwo.cn/newh5/singles/songinfoandlrc"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Mobile Safari/537.36"
        private val KUWO_CHARSET: Charset = Charset.forName("GBK")

        private fun field(obj: String, key: String): String {
            val match = Regex("'$key'\\s*:\\s*'((?:[^'\\\\]|\\\\.)*)'").find(obj)
            return match?.groupValues?.get(1)?.replace("\\'", "'") ?: ""
        }

        private fun topLevelObjects(content: String): List<String> {
            val objects = mutableListOf<String>()
            var depth = 0
            var start = -1
            var quote = '\u0000'
            var escaped = false
            content.forEachIndexed { index, char ->
                if (quote != '\u0000') {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == quote) quote = '\u0000'
                    return@forEachIndexed
                }
                if (char == '\'' || char == '"') {
                    quote = char
                } else if (char == '{') {
                    if (depth++ == 0) start = index
                } else if (char == '}' && depth > 0 && --depth == 0 && start >= 0) {
                    objects += content.substring(start, index + 1)
                    start = -1
                }
            }
            return objects
        }

        private fun decodeHtml(text: String): String =
            android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    }
}

/** 酷狗音乐搜索（mobilecdn 公开接口，无需登录）。 */
class KugouCatalog(
    private val client: OkHttpClient = defaultClient()
) : OnlineCatalog {
    override val sourceId: String get() = "kg"
    override val displayName: String get() = "酷狗音乐"
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Search, MusicCapability.Lyrics)

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int
    ): Result<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val url = SEARCH_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("keyword", q)
                .addQueryParameter("page", (offset / limit.coerceAtLeast(1) + 1).toString())
                .addQueryParameter("pagesize", limit.toString())
                .addQueryParameter("format", "json")
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("酷狗搜索失败（HTTP ${response.code}）")
                val body = response.body ?: throw IOException("酷狗搜索返回了空响应")
                // mobilecdn/msearch 实测返回 GBK；UTF-8 解码出现替换字符时回退 GBK
                // （不能以 JSON 是否可解析作判据：GBK 乱码下 JSON 结构同样能解析成功）
                val rawBytes = body.bytes()
                val utf8Text = rawBytes.toString(StandardCharsets.UTF_8)
                val payload = if ('\uFFFD' in utf8Text) {
                    rawBytes.toString(KUGOU_CHARSET)
                } else {
                    utf8Text
                }
                Result.success(parseTracks(payload))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun parseTracks(payload: String): List<OnlineTrack> {
        val root = JSONObject(payload)
        if (root.optInt("status", 1) != 1) {
            throw IOException(root.optString("err_msg", "酷狗搜索返回错误"))
        }
        val info = root.optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
        val tracks = ArrayList<OnlineTrack>(info.length())
        val seen = HashSet<String>()
        for (index in 0 until info.length()) {
            val item = info.optJSONObject(index) ?: continue
            val hash = item.optString("hash").trim()
            if (hash.isBlank() || !seen.add(hash)) continue
            val title = item.optString("songname").trim()
            if (title.isBlank()) continue
            val artist = item.optString("singername").trim()
            val album = item.optString("album_name").trim()
            val durationSec = item.optLong("duration", 0L)
            val artwork = runCatching {
                val transParam = item.optJSONObject("trans_param")
                    ?: JSONObject(item.optString("trans_param"))
                transParam.optString("union_cover")
                    .replace("{size}", "240")
                    .replace("http://", "https://")
            }.getOrNull()?.takeIf { it.startsWith("https://") }
            tracks += OnlineTrack(
                source = sourceId,
                sourceId = hash,
                title = title,
                artists = listOf(artist).filter(String::isNotBlank),
                album = album,
                durationMs = durationSec * 1_000L,
                artworkUrl = artwork,
                metadata = mapOf(
                    "hash" to hash,
                    "songmid" to hash,
                    "name" to title,
                    "singer" to artist,
                    "album" to album,
                    "duration" to (durationSec * 1_000L).toString()
                )
            )
        }
        return tracks
    }

    override suspend fun resolveLyrics(track: OnlineTrack): Result<String?> = withContext(Dispatchers.IO) {
        try {
            if (track.source != sourceId) throw IOException("不是酷狗歌曲")
            val searchUrl = LYRICS_SEARCH_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", "pc")
                .addQueryParameter("hash", track.metadata["hash"] ?: track.sourceId)
                .build()
            val candidate = client.newCall(Request.Builder().url(searchUrl).get()
                .header("Accept", "application/json").header("User-Agent", USER_AGENT).build()).await().use { response ->
                if (!response.isSuccessful) throw IOException("酷狗歌词搜索失败（HTTP ${response.code}）")
                val root = JSONObject(response.body?.string() ?: throw IOException("酷狗歌词搜索返回了空响应"))
                val candidates = root.optJSONArray("candidates") ?: return@use null
                (0 until candidates.length()).mapNotNull(candidates::optJSONObject)
                    .minByOrNull { candidate ->
                        kotlin.math.abs(candidate.optLong("duration") - track.durationMs)
                    }
            } ?: return@withContext Result.success(null)
            val downloadUrl = LYRICS_DOWNLOAD_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("client", "pc")
                .addQueryParameter("id", candidate.optString("id"))
                .addQueryParameter("accesskey", candidate.optString("accesskey"))
                .addQueryParameter("fmt", "lrc")
                .addQueryParameter("charset", "utf8")
                .build()
            client.newCall(Request.Builder().url(downloadUrl).get()
                .header("Accept", "application/json").header("User-Agent", USER_AGENT).build()).await().use { response ->
                if (!response.isSuccessful) throw IOException("酷狗歌词下载失败（HTTP ${response.code}）")
                val root = JSONObject(response.body?.string() ?: throw IOException("酷狗歌词返回了空响应"))
                val encoded = root.optString("content").takeIf(String::isNotBlank)
                    ?: return@use Result.success(null)
                val lyrics = Base64.getDecoder().decode(encoded).toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
                Result.success(lyrics.takeIf(String::isNotBlank))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    companion object {
        // mobilecdn.kugou.com 的 https 证书是 *.cdn.myqcloud.com，标准校验必失败；
        // msearch.kugou.com 是同一接口（/api/v3/search/song），证书正常，走 https
        private const val SEARCH_ENDPOINT = "https://msearch.kugou.com/api/v3/search/song"
        private const val LYRICS_SEARCH_ENDPOINT = "https://lyrics.kugou.com/search"
        private const val LYRICS_DOWNLOAD_ENDPOINT = "https://lyrics.kugou.com/download"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Mobile Safari/537.36"
        private val KUGOU_CHARSET: Charset = Charset.forName("GBK")
    }}

/** QQ 音乐搜索（c.y.qq.com 公开接口，无需登录）。 */
class QQMusicCatalog(
    private val client: OkHttpClient = defaultClient()
) : OnlineCatalog {
    override val sourceId: String get() = "tx"
    override val displayName: String get() = "QQ音乐"
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Search, MusicCapability.Lyrics)

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int
    ): Result<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val url = SEARCH_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("w", q)
                .addQueryParameter("p", (offset / limit.coerceAtLeast(1) + 1).toString())
                .addQueryParameter("n", limit.toString())
                .addQueryParameter("format", "json")
                .addQueryParameter("cr", "1")
                .addQueryParameter("g_tk", "5381")
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .header("Referer", "https://y.qq.com/")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("QQ音乐搜索失败（HTTP ${response.code}）")
                val body = response.body ?: throw IOException("QQ音乐搜索返回了空响应")
                Result.success(parseTracks(body.string()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun parseTracks(payload: String): List<OnlineTrack> {
        val root = JSONObject(payload)
        if (root.optInt("code", 0) != 0) {
            throw IOException("QQ音乐搜索返回错误码 ${root.optInt("code")}")
        }
        val list = root.optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list") ?: return emptyList()
        val tracks = ArrayList<OnlineTrack>(list.length())
        val seen = HashSet<String>()
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val songmid = item.optString("songmid").trim()
            if (songmid.isBlank() || !seen.add(songmid)) continue
            val title = item.optString("songname").trim()
            if (title.isBlank()) continue
            val artists = item.optJSONArray("singer")?.readSingerNames().orEmpty()
            val album = item.optString("albumname").trim()
            val durationSec = item.optLong("interval", 0L)
            val strMediaMid = item.optString("strMediaMid").trim()
            val albumMid = item.optString("albummid").trim()
            val artwork = albumMid.takeIf(String::isNotBlank)
                ?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" }
            tracks += OnlineTrack(
                source = sourceId,
                sourceId = songmid,
                title = title,
                artists = artists,
                album = album,
                durationMs = durationSec * 1_000L,
                artworkUrl = artwork,
                metadata = buildMap {
                    put("songmid", songmid)
                    put("strMediaMid", strMediaMid)
                    put("name", title)
                    put("singer", artists.joinToString(" / "))
                    put("album", album)
                    put("duration", (durationSec * 1_000L).toString())
                }
            )
        }
        return tracks
    }

    override suspend fun resolveLyrics(track: OnlineTrack): Result<String?> = withContext(Dispatchers.IO) {
        try {
            if (track.source != sourceId) throw IOException("不是QQ音乐歌曲")
            val url = LYRICS_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("songmid", track.metadata["songmid"] ?: track.sourceId)
                .addQueryParameter("format", "json")
                .addQueryParameter("nobase64", "1")
                .build()
            client.newCall(Request.Builder().url(url).get()
                .header("Accept", "application/json")
                .header("Referer", "https://y.qq.com/")
                .header("User-Agent", USER_AGENT)
                .build()).await().use { response ->
                if (!response.isSuccessful) throw IOException("QQ音乐歌词请求失败（HTTP ${response.code}）")
                val root = JSONObject(response.body?.string() ?: throw IOException("QQ音乐歌词返回了空响应"))
                if (root.optInt("retcode", root.optInt("code", -1)) != 0) {
                    throw IOException("QQ音乐未返回歌词")
                }
                Result.success(root.optString("lyric").takeIf(String::isNotBlank))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun JSONArray.readSingerNames(): List<String> = buildList {
        for (index in 0 until length()) {
            val name = optJSONObject(index)?.optString("name")?.trim() ?: continue
            if (name.isNotBlank() && name !in this) add(name)
        }
    }

    companion object {
        private const val SEARCH_ENDPOINT = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
        private const val LYRICS_ENDPOINT = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Mobile Safari/537.36"
    }
}

internal fun defaultCatalogClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .cookieJar(okhttp3.CookieJar.NO_COOKIES)
    .build()

private fun defaultClient(): OkHttpClient = defaultCatalogClient()

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
