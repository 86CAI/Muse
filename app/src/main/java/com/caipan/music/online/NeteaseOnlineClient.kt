/*
 * 网易云在线模式传输层。
 *
 * EAPI 请求的签名方式（"nobody{path}use{json}md5forencrypt" 摘要 + AES-ECB）与
 * iOS 客户端 UA 画像参考了 lladlam/MeloX-Android (GPL-3.0) 的
 * core/network/NeteaseAuthenticatedEapi.kt；这些常量本身是多个开源项目共用的
 * 公开协议细节（lx-music、NeteaseCloudMusicApi 等）。请求编排与数据模型为 Muse 自有。
 *
 * Upstream: https://github.com/lladlam/MeloX-Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.online

import android.net.Uri
import com.caipan.music.data.NeteaseSessionStore
import com.caipan.music.player.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small, provider-specific transport used by online mode. It is intentionally isolated from the local repository. */
class NeteaseOnlineClient(
    private val cookieProvider: () -> String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build()
) {
    private val random = SecureRandom()
    private val deviceId = randomHex(26).uppercase()
    private val eapiKey = "e82ckenh8dichen8"

    suspend fun account(): Result<NeteaseAccount> = ioResult {
        // NetEase moved the web account endpoint under /api/w; the old
        // /api/nuser/account route returns the literal "interface not found".
        val json = eapi("/api/w/nuser/account/get", JSONObject(), authenticated = true)
        val profile = json.optJSONObject("profile") ?: json.optJSONObject("account")
        val userId = profile?.optLong("userId", json.optJSONObject("account")?.optLong("id", 0L) ?: 0L)
            ?: json.optJSONObject("account")?.optLong("id", 0L)
            ?: 0L
        if (userId <= 0L) error("NetEase account verification failed")
        NeteaseAccount(
            userId = userId,
            nickname = profile?.optString("nickname").orEmpty().ifBlank { "NetEase user" },
            avatarUrl = profile?.optString("avatarUrl").orEmpty().let(::secureUrl).orEmpty()
        )
    }

    /**
     * Loads the detailed profile used by the online "My" page.
     *
     * MeloX uses this Web-compatible endpoint as its EAPI fallback.  The
     * profile itself is public, but an authenticated request preserves the
     * account-specific response shape when a valid MUSIC_U is available.
     */
    suspend fun userDetail(userId: Long): Result<NeteaseProfileDetails> = ioResult {
        require(userId > 0L) { "Invalid NetEase user id" }
        val json = eapi(
            "/api/w/v1/user/detail/$userId",
            JSONObject().put("all", true).put("userId", userId),
            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        )
        val profile = json.optJSONObject("profile") ?: error("NetEase returned no user profile")
        NeteaseProfileDetails(
            userId = profile.optLong("userId", userId).takeIf { it > 0L } ?: userId,
            nickname = profile.optString("nickname").ifBlank { "NetEase user" },
            avatarUrl = secureUrl(profile.optString("avatarUrl")),
            backgroundUrl = secureUrl(profile.optString("backgroundUrl")),
            signature = profile.optString("signature").takeIf(String::isNotBlank),
            level = json.optInt("level", 0).coerceAtLeast(0),
            listenSongs = json.optInt("listenSongs", 0).coerceAtLeast(0),
            follows = profile.optInt("follows", 0).coerceAtLeast(0),
            followers = profile.optInt("followeds", 0).coerceAtLeast(0),
            playlistCount = profile.optInt("playlistCount", 0).coerceAtLeast(0)
        )
    }

    suspend fun home(limit: Int = 12): Result<NeteaseHomeContent> = ioResult {
        val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        // The homepage block endpoint is the same source MeloX uses. It is
        // account-aware and carries the extra recommendation sections; the
        // two public endpoints below remain the reliable guest fallback.
        val serverBlocks = if (authenticated) {
            runCatching {
                NeteaseHomeBlockParser.parse(
                    eapi("/api/homepage/block/page", JSONObject().put("refresh", false), authenticated = true)
                )
            }.getOrNull()
        } else null
        val playlistResponse = eapi(
            "/api/personalized/playlist",
            JSONObject().put("limit", limit.coerceIn(1, 30)).put("total", true).put("n", 1000),
            authenticated
        )
        val playlists = parsePlaylists(playlistResponse.optJSONArray("result") ?: JSONArray())
        val songResponse = eapi(
            "/api/personalized/newsong",
            JSONObject().put("type", "recommend").put("limit", limit.coerceIn(1, 30)).put("areaId", 0),
            authenticated
        )
        val songs = buildList {
            val values = songResponse.optJSONArray("result") ?: songResponse.optJSONArray("data") ?: JSONArray()
            for (index in 0 until values.length()) {
                val value = values.optJSONObject(index) ?: continue
                parseTrack(value.optJSONObject("song") ?: value)?.let(::add)
            }
        }
        val fallbackRecent = runCatching {
            val json = eapi("/api/top/song", JSONObject().put("type", 0), authenticated)
            parseTracks(json.optJSONArray("data") ?: JSONArray()).take(limit)
        }.getOrElse { emptyList() }
        val fallbackCharts = runCatching {
            val json = eapi("/api/toplist", JSONObject(), authenticated)
            parsePlaylists(json.optJSONArray("list") ?: JSONArray()).take(limit)
        }.getOrElse { emptyList() }
        val fallbackRegional = runCatching {
            val json = eapi("/api/top/song", JSONObject().put("type", 0), authenticated)
            parseTracks(json.optJSONArray("data") ?: JSONArray()).take(limit)
        }.getOrElse { emptyList() }
        val fallbackRoaming = if (authenticated) runCatching {
            val json = eapi("/api/personal/fm", JSONObject().put("limit", limit), authenticated = true)
            parseTracks(json.optJSONArray("data") ?: JSONArray()).take(limit)
        }.getOrElse { emptyList() } else emptyList()
        val accountPlaylists = if (authenticated) runCatching {
            val account = eapi("/api/w/nuser/account/get", JSONObject(), authenticated = true)
            val uid = account.optJSONObject("profile")?.optLong("userId", 0L)
                ?: account.optJSONObject("account")?.optLong("id", 0L)
            if (uid != null && uid > 0L) {
                val json = eapi("/api/user/playlist", JSONObject().put("uid", uid).put("limit", 1000).put("offset", 0), authenticated = true)
                parsePlaylists(json.optJSONArray("playlist") ?: JSONArray())
            } else emptyList()
        }.getOrElse { emptyList() } else emptyList()
        val fallbackRadar = accountPlaylists.filter { it.name.contains("雷达") }.take(limit)
        val fallbackPersonal = accountPlaylists.filterNot { it.name.contains("喜欢") }.take(limit)
        val fallbackPodcasts = runCatching {
            val json = eapi("/api/program/recommend/v1", JSONObject().put("limit", limit).put("offset", 0), authenticated)
            buildList {
                val values = json.optJSONArray("programs") ?: JSONArray()
                for (index in 0 until values.length()) {
                    val program = values.optJSONObject(index) ?: continue
                    val radio = program.optJSONObject("radio") ?: continue
                    val id = radio.optLong("id", 0L).takeIf { it > 0L } ?: continue
                    add(NeteaseHomePodcast(id, radio.optString("name").ifBlank { program.optString("name").ifBlank { "播客" } }, secureUrl(program.optString("coverUrl").ifBlank { radio.optString("picUrl") })))
                }
            }.distinctBy { it.id }.take(limit)
        }.getOrElse { emptyList() }

        fun <T> serverOrFallback(server: List<T>?, fallback: List<T>): List<T> = server?.takeIf { it.isNotEmpty() } ?: fallback
        NeteaseHomeContent(
            playlists = serverOrFallback(serverBlocks?.recommendedPlaylists, playlists),
            newSongs = songs,
            recentlyTrending = serverOrFallback(serverBlocks?.recentlyTrending, fallbackRecent),
            tailoredSongs = serverBlocks?.tailoredSongs.orEmpty(),
            chartPlaylists = serverOrFallback(serverBlocks?.chartPlaylists, fallbackCharts),
            radarPlaylists = serverOrFallback(serverBlocks?.radarPlaylists, fallbackRadar),
            personalPlaylists = serverOrFallback(serverBlocks?.personalPlaylists, fallbackPersonal),
            regionalSongs = serverOrFallback(serverBlocks?.regionalSongs, fallbackRegional),
            roamingSongs = serverOrFallback(serverBlocks?.roamingSongs, fallbackRoaming),
            similarSongs = serverBlocks?.similarSongs.orEmpty(),
            podcasts = serverOrFallback(serverBlocks?.podcasts, fallbackPodcasts)
        )
    }

    suspend fun userPlaylists(userId: Long): Result<List<RemotePlaylistSummary>> = ioResult {
        require(userId > 0L) { "Invalid NetEase user id" }
        val json = eapi(
            "/api/user/playlist",
            JSONObject().put("uid", userId).put("limit", 1000).put("offset", 0).put("includeVideo", true),
            authenticated = true
        )
        parsePlaylists(json.optJSONArray("playlist") ?: JSONArray())
    }

    /**
     * Loads the signed-in account's liked songs in the provider's order.
     *
     * `/api/song/like/get` only returns song ids, so they are resolved in
     * batches through `/api/v3/song/detail` before returning UI-ready tracks.
     */
    suspend fun likedSongs(userId: Long): Result<List<OnlineTrack>> = ioResult {
        require(userId > 0L) { "Invalid NetEase user id" }
        val json = eapi(
            "/api/song/like/get",
            JSONObject().put("uid", userId),
            authenticated = true
        )
        val ids = buildList {
            val values = json.optJSONArray("ids") ?: JSONArray()
            for (index in 0 until values.length()) {
                values.optLong(index, 0L).takeIf { it > 0L }?.let(::add)
            }
        }
        songDetails(ids, authenticated = true)
    }

    /**
     * Loads the account's NetEase listening history. The response already
     * carries complete song objects, so no additional detail lookup is needed.
     */
    suspend fun recentSongs(limit: Int = 100): Result<List<OnlineTrack>> = ioResult {
        val json = eapi(
            "/api/play-record/song/list",
            JSONObject().put("limit", limit.coerceIn(1, MAX_RECENT_SONGS)),
            authenticated = true
        )
        val values = json.optJSONObject("data")?.optJSONArray("list") ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) {
                val record = values.optJSONObject(index) ?: continue
                // The documented shape is `list[].data`; retain the fallback
                // shapes because old accounts can return a flattened record.
                val song = record.optJSONObject("data")
                    ?: record.optJSONObject("song")
                    ?: record
                parseTrack(song)?.let(::add)
            }
        }
    }

    /** Resolves playable episodes for a NetEase podcast/radio. */
    suspend fun podcastPrograms(radioId: Long, limit: Int = 30): Result<List<OnlineTrack>> = ioResult {
        require(radioId > 0L) { "Invalid NetEase podcast id" }
        val json = eapi(
            "/api/dj/program/byradio",
            JSONObject()
                .put("radioId", radioId)
                .put("limit", limit.coerceIn(1, 100))
                .put("offset", 0)
                .put("asc", false),
            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        )
        val values = json.optJSONArray("programs") ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) {
                val program = values.optJSONObject(index) ?: continue
                val mainSong = parseTrack(program.optJSONObject("mainSong")) ?: continue
                val host = program.optJSONObject("dj")?.optString("nickname")?.takeIf(String::isNotBlank)
                val artwork = secureUrl(
                    program.optString("coverUrl").ifBlank {
                        program.optJSONObject("radio")?.optString("picUrl").orEmpty()
                    }
                ) ?: mainSong.artworkUrl
                add(mainSong.copy(
                    title = program.optString("name").ifBlank { mainSong.title },
                    artists = listOf(host ?: "网易云播客"),
                    album = program.optJSONObject("radio")?.optString("name").orEmpty().ifBlank { "播客" },
                    artworkUrl = artwork,
                    durationMs = program.optLong("duration", mainSong.durationMs).coerceAtLeast(0L)
                ))
            }
        }.distinctBy(OnlineTrack::stableId)
    }

    suspend fun playlistDetail(playlistId: Long): Result<RemotePlaylistDetail> = ioResult {
        require(playlistId > 0L) { "Invalid playlist id" }
        val authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        val json = eapi(
            "/api/v6/playlist/detail",
            JSONObject().put("id", playlistId).put("n", 100).put("s", 8),
            authenticated = authenticated
        )
        val playlist = json.optJSONObject("playlist") ?: error("Playlist not found")
        val summary = parsePlaylist(playlist) ?: error("Invalid playlist")
        val embeddedTracks = buildList {
            val values = playlist.optJSONArray("tracks") ?: JSONArray()
            for (index in 0 until values.length()) parseTrack(values.optJSONObject(index))?.let(::add)
        }
        // The detail route embeds only the first page for many large playlists.
        // Resolve the full track-id order through the existing batched detail
        // route so opening a long playlist never silently truncates at 100.
        val orderedTrackIds = buildList {
            val values = playlist.optJSONArray("trackIds") ?: JSONArray()
            for (index in 0 until values.length()) {
                values.optJSONObject(index)?.optLong("id", 0L)?.takeIf { it > 0L }?.let(::add)
            }
        }
        val tracks = if (orderedTrackIds.size > embeddedTracks.size) {
            songDetails(orderedTrackIds, authenticated).ifEmpty { embeddedTracks }
        } else {
            embeddedTracks
        }
        RemotePlaylistDetail(summary, tracks)
    }

    suspend fun search(query: String, limit: Int = 30): Result<List<OnlineTrack>> =
        NeteaseCatalog(httpClient).search(query, limit).map { it }

    suspend fun resolvePlayback(track: OnlineTrack, quality: AudioQuality): Result<LxResolvedMusicUrl> = ioResult {
        val id = track.sourceId.toLongOrNull() ?: error("Invalid NetEase song id")
        val level = when (quality) {
            AudioQuality.STANDARD -> "standard"
            AudioQuality.HIGH -> "exhigh"
            AudioQuality.LOSSLESS -> "lossless"
            AudioQuality.HI_RES -> "hires"
        }
        val json = eapi(
            "/api/song/enhance/player/url/v1",
            JSONObject().put("ids", "[$id]").put("level", level).put("encodeType", "flac"),
            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        )
        val item = json.optJSONArray("data")?.optJSONObject(0) ?: error("NetEase returned no playable URL")
        val url = item.optString("url").takeIf(String::isNotBlank) ?: error("Song is unavailable for this account")
        LxResolvedMusicUrl(
            url = url,
            headers = mapOf("Referer" to "https://music.163.com/", "User-Agent" to USER_AGENT),
            quality = item.optString("level").ifBlank { level },
            expiresAtEpochMs = item.optLong("expi", 0L).takeIf { it > 0L }?.let { System.currentTimeMillis() + it * 1000L }
        )
    }

    suspend fun lyrics(track: OnlineTrack): Result<String?> = ioResult {
        val id = track.sourceId.toLongOrNull() ?: return@ioResult null
        val json = eapi(
            "/api/song/lyric/v1",
            JSONObject().put("id", id).put("lv", -1).put("kv", -1).put("tv", -1),
            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider())
        )
        json.optJSONObject("lrc")?.optString("lyric")?.takeIf(String::isNotBlank)
    }

    /**
     * Loads a page of public song comments.  The route is readable while
     * signed out; an authenticated request additionally lets NetEase mark
     * comments liked by the current user.
     *
     * The request shape follows MeloX's social client:
     * `/api/v1/resource/comments/R_SO_4_{songId}` with `rid`, `offset`, and
     * `beforeTime`.  [NeteaseCommentsPage.nextBeforeTime] becomes non-zero
     * only after NetEase's large-thread offset window.
     */
    suspend fun songComments(
        songId: Long,
        offset: Int = 0,
        beforeTime: Long = 0L,
        limit: Int = 20
    ): Result<NeteaseCommentsPage> = ioResult {
        require(songId > 0L) { "Invalid NetEase song id" }
        require(offset >= 0) { "Comment offset cannot be negative" }
        val path = "/api/v1/resource/comments/R_SO_4_$songId"
        val request = JSONObject()
            .put("rid", songId)
            .put("limit", limit.coerceIn(1, 100))
            .put("offset", offset)
            .put("beforeTime", beforeTime.coerceAtLeast(0L))
        val signedIn = NeteaseSessionStore.containsMusicU(cookieProvider())
        // Comments are public.  A stale MUSIC_U should not make the entire
        // comment sheet unusable, so retry as a guest if authenticated EAPI
        // rejects the saved session.
        val response = if (signedIn) {
            runCatching { eapi(path, request, authenticated = true) }
                .getOrElse { eapi(path, request, authenticated = false) }
        } else {
            eapi(path, request, authenticated = false)
        }
        NeteaseCommentsParser.parsePage(response, offset, beforeTime)
    }

    private suspend fun <T> ioResult(block: () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching(block) }

    private fun eapi(path: String, data: JSONObject, authenticated: Boolean): JSONObject {
        val cookie = cookieProvider()
        if (authenticated && !NeteaseSessionStore.containsMusicU(cookie)) error("Please sign in to NetEase Music")
        val now = System.currentTimeMillis()
        val cookies = NeteaseSessionStore.parseCookie(cookie)
        val header = JSONObject()
            // Match the iOS EAPI profile used by the reference clients. A
            // browser Cookie rarely contains these synthetic device fields.
            .put("osver", cookies["osver"] ?: "16.2")
            .put("deviceId", cookies["deviceId"] ?: deviceId)
            .put("os", cookies["os"] ?: "iPhone OS")
            .put("appver", cookies["appver"] ?: "9.0.90")
            .put("versioncode", cookies["versioncode"] ?: "140")
            .put("mobilename", cookies["mobilename"].orEmpty())
            .put("buildver", cookies["buildver"] ?: (now / 1_000L).toString())
            .put("resolution", cookies["resolution"] ?: "1170x2532")
            .put("channel", cookies["channel"] ?: "distribution")
            .put("requestId", "${now}_${random.nextInt(10000).toString().padStart(4, '0')}")
            .put("__csrf", cookies["__csrf"].orEmpty())
        cookies["MUSIC_U"]?.takeIf(String::isNotBlank)?.let { header.put("MUSIC_U", it) }
        val payload = JSONObject(data.toString()).put("header", header).put("e_r", false)
        val json = payload.toString()
        val digest = md5("nobody${path}use${json}md5forencrypt")
        val plain = "$path-36cd479b6b5-$json-36cd479b6b5-$digest"
        val encrypted = aesEcb(plain.toByteArray(Charsets.UTF_8), eapiKey.toByteArray()).toHex()
        val builder = Request.Builder()
            .url("https://interface.music.163.com${path.replace("/api/", "/eapi/")}")
            .header("Accept", "*/*")
            .header("User-Agent", USER_AGENT)
        if (authenticated) builder.header("Cookie", encodedCookieHeader(header))
        return httpClient.newCall(builder.post(FormBody.Builder().add("params", encrypted).build()).build())
            .execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("NetEase HTTP ${response.code}")
                if (body.isBlank()) error("NetEase returned an empty response")
                val result = JSONObject(body)
                val code = result.optInt("code", response.code)
                if (code !in 200..299) error(result.optString("message").ifBlank { "NetEase request failed ($code)" })
                result
            }
    }

    private fun parsePlaylists(values: JSONArray): List<RemotePlaylistSummary> = buildList {
        for (index in 0 until values.length()) parsePlaylist(values.optJSONObject(index))?.let(::add)
    }

    private fun parseTracks(values: JSONArray): List<OnlineTrack> = buildList {
        for (index in 0 until values.length()) parseTrack(values.optJSONObject(index))?.let(::add)
    }

    /** Resolves ids in at-most-100-song EAPI requests and restores request order. */
    private fun songDetails(ids: List<Long>, authenticated: Boolean): List<OnlineTrack> {
        if (ids.isEmpty()) return emptyList()
        val tracksById = LinkedHashMap<Long, OnlineTrack>()
        ids.distinct().chunked(MAX_SONG_DETAIL_BATCH_SIZE).forEach { batch ->
            val descriptors = JSONArray().apply {
                batch.forEach { songId -> put(JSONObject().put("id", songId)) }
            }
            val json = eapi(
                "/api/v3/song/detail",
                JSONObject().put("c", descriptors.toString()),
                authenticated = authenticated
            )
            val songs = json.optJSONArray("songs") ?: JSONArray()
            for (index in 0 until songs.length()) {
                parseTrack(songs.optJSONObject(index))?.let { track ->
                    track.sourceId.toLongOrNull()?.let { tracksById[it] = track }
                }
            }
        }
        // The song-detail endpoint is not ordered consistently, so map back
        // through the original liked-id sequence rather than its response.
        return ids.mapNotNull(tracksById::get)
    }

    private fun parsePlaylist(value: JSONObject?): RemotePlaylistSummary? {
        value ?: return null
        val id = value.optLong("id", 0L)
        if (id <= 0L) return null
        val creator = value.optJSONObject("creator")
        val description = sequenceOf(
            value.optString("description"),
            value.optString("copywriter")
        ).firstOrNull(String::isNotBlank)
        return RemotePlaylistSummary(
            id = id,
            name = value.optString("name").ifBlank { "Untitled playlist" },
            coverUrl = sequenceOf(
                value.optString("coverImgUrl"),
                value.optString("picUrl"),
                value.optString("coverUrl")
            ).firstOrNull(String::isNotBlank)?.let(::secureUrl),
            trackCount = value.optInt("trackCount", 0).coerceAtLeast(0),
            creatorName = creator?.optString("nickname").orEmpty(),
            creatorUserId = creator?.optLong("userId", 0L)?.takeIf { it > 0L }
                ?: value.optLong("userId", 0L).takeIf { it > 0L },
            playCount = value.optLong("playCount", 0L).coerceAtLeast(0L),
            subscribed = value.optBoolean("subscribed", false),
            specialType = value.optInt("specialType", 0),
            description = description
        )
    }

    private fun parseTrack(value: JSONObject?): OnlineTrack? {
        value ?: return null
        val id = value.optLong("id", 0L)
        if (id <= 0L) return null
        val artists = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
        val names = buildList {
            for (index in 0 until artists.length()) artists.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        val duration = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L)
        return OnlineTrack(
            source = NeteaseCatalog.NETEASE_SOURCE,
            sourceId = id.toString(),
            title = value.optString("name").ifBlank { "Unknown song" },
            artists = names.ifEmpty { listOf("Unknown artist") },
            album = album?.optString("name").orEmpty(),
            durationMs = duration,
            artworkUrl = secureUrl(album?.optString("picUrl").orEmpty()),
            metadata = mapOf("neteaseId" to id.toString())
        )
    }

    private fun secureUrl(raw: String): String? = normalizeNeteaseImageUrl(raw)

    private fun encodedCookieHeader(values: JSONObject): String {
        val keys = buildList {
            val iterator = values.keys()
            while (iterator.hasNext()) add(iterator.next())
        }.sorted()
        return keys.joinToString("; ") { key ->
            "${encodeCookiePart(key)}=${encodeCookiePart(values.optString(key))}"
        }
    }

    private fun encodeCookiePart(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    private fun aesEcb(data: ByteArray, key: ByteArray): ByteArray = Cipher.getInstance("AES/ECB/PKCS5Padding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")); doFinal(data)
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun randomHex(bytes: Int): String = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"
        const val MAX_SONG_DETAIL_BATCH_SIZE = 100
        const val MAX_RECENT_SONGS = 100
    }
}
