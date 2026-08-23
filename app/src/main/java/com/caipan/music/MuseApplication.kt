/*
 * Muse — Android music player
 * Copyright (C) 2026 Cai & Caiyu
 *
 * Licensed under the GNU Affero General Public License v3.0 or later.
 * See LICENSE at the repository root, or <https://www.gnu.org/licenses/>.
 * Third-party attribution: THIRD_PARTY_NOTICES.md / COPYRIGHT.md
 */
package com.caipan.music

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import com.caipan.music.data.OAuthManager
import com.caipan.music.data.GitHubOAuthClient
import com.caipan.music.data.GitHubSessionStore
import com.caipan.music.data.NeteaseSessionStore
import com.caipan.music.api.OpenApiServer
import com.caipan.music.lan.LanRemoteManager
import com.caipan.music.player.MusicPlayer
import com.caipan.music.player.PlaybackSettingsStore
import com.caipan.music.player.ResolvedStream
import com.caipan.music.online.NeteaseCatalog
import com.caipan.music.online.NeteaseOnlineClient
import com.caipan.music.online.OnlineCatalog
import com.caipan.music.online.PlaybackCapability
import com.caipan.music.online.OnlineSourceManager
import com.caipan.music.online.toOnlineTrack
import com.caipan.music.plugin.GlobalBlurControlPlugin
import com.caipan.music.plugin.PluginManager
import com.caipan.music.plugin.WeightedShufflePlugin
import com.caipan.music.plugin.ExternalPlayerMonitorPlugin
import com.caipan.music.plugin.PerformanceControlPlugin
import com.caipan.music.skin.SkinManager
import com.caipan.music.ui.components.MuseGlassConfigStore
import com.caipan.music.player.RepeatMode
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException

class MuseApplication : Application() {
    val onlineSourceManager: OnlineSourceManager by lazy { OnlineSourceManager(this) }
    val onlineCatalog: OnlineCatalog by lazy { NeteaseCatalog() }
    val neteaseSessionStore: NeteaseSessionStore by lazy { NeteaseSessionStore(this) }
    val neteaseClient: NeteaseOnlineClient by lazy {
        NeteaseOnlineClient(cookieProvider = { neteaseSessionStore.session.value?.cookie.orEmpty() })
    }
    val glassConfigStore: MuseGlassConfigStore by lazy { MuseGlassConfigStore(this) }
    val playbackSettingsStore: PlaybackSettingsStore by lazy { PlaybackSettingsStore(this) }
    val skinManager: SkinManager by lazy { SkinManager(this) }
    val globalBlurControlPlugin: GlobalBlurControlPlugin by lazy { GlobalBlurControlPlugin(this) }
    val externalPlayerMonitorPlugin: ExternalPlayerMonitorPlugin by lazy { ExternalPlayerMonitorPlugin(this) }
    val performanceControlPlugin: PerformanceControlPlugin by lazy { PerformanceControlPlugin(this) }
    val pluginManager: PluginManager by lazy {
        PluginManager(this).apply {
            register(WeightedShufflePlugin(this@MuseApplication))
            register(globalBlurControlPlugin)
            register(externalPlayerMonitorPlugin)
            register(performanceControlPlugin)
            installBundledPlugins()
            loadInstalledPlugins()
        }
    }
    val musicPlayer: MusicPlayer by lazy {
        MusicPlayer(this, pluginManager, { externalPlayerMonitorPlugin.pauseExternalPlayback() }, playbackSettingsStore).apply {
            setOnlineResolver { song ->
                val track = song.toOnlineTrack()
                val preferredQuality = playbackSettingsStore.state.value.preferredQuality.lxKey
                val official = if (track.source == NeteaseCatalog.NETEASE_SOURCE) {
                    neteaseClient.resolvePlayback(track, playbackSettingsStore.state.value.preferredQuality)
                } else (onlineCatalog as? PlaybackCapability)?.resolvePlayback(track)
                    ?: Result.failure(IOException("网易官方直连不可用"))
                val resolved = official.getOrElse { officialError ->
                    onlineSourceManager.resolve(track, preferredQuality).getOrElse { sourceError ->
                        throw IOException(
                            "在线播放解析失败：网易：${officialError.message ?: "不可用"}；" +
                                "LX：${sourceError.message ?: "不可用"}",
                            sourceError
                        )
                    }
                }
                ResolvedStream(Uri.parse(resolved.url), resolved.headers, resolved.quality)
            }
        }
    }
    val oauthManager: OAuthManager by lazy { OAuthManager(this) }
    val gitHubSessionStore: GitHubSessionStore by lazy { GitHubSessionStore(this) }
    val gitHubOAuthClient: GitHubOAuthClient by lazy {
        GitHubOAuthClient(
            context = this,
            clientId = BuildConfig.GITHUB_CLIENT_ID,
            tokenProxyUrl = BuildConfig.GITHUB_TOKEN_PROXY_URL.takeIf { it.isNotBlank() }
        )
    }
    val openApiServer: OpenApiServer by lazy {
        OpenApiServer(
            context = this,
            stateProvider = {
                musicPlayer.updateProgress()
                musicPlayer.uiState.value
            },
            versionName = BuildConfig.VERSION_NAME,
            statsProvider = {
                val prefs = getSharedPreferences("muse_prefs", 0)
                JSONObject()
                    .put("listeningTimeMs", prefs.getLong("listening_time_ms", 0))
                    .put("songCount", songCount())
                    .put("completedPlays", prefs.getInt("completed_plays", 0))
                    .put("repeatCount", prefs.getInt("repeat_count", 0))
            },
            commandHandler = ::handlePlayerCommand,
            accountProvider = { oauthManager.session.value?.account?.takeIf { it.isNotBlank() } }
        )
    }
    val lanRemoteManager: LanRemoteManager by lazy { LanRemoteManager(this) }

    override fun onCreate() {
        super.onCreate()
        // Muse 开放 API：应用启动即常驻监听，供 MChat 等第三方 App 读取播放状态
        openApiServer.startQuietly()
        lanRemoteManager.stateProvider = {
            musicPlayer.updateProgress()
            val state = musicPlayer.uiState.value
            JSONObject().put("isPlaying", state.isPlaying).put("progressMs", state.progressMs)
                .put("durationMs", state.durationMs).put("isShuffled", state.isShuffled)
                .put("repeatMode", state.repeatMode.name).put("currentSong", state.currentSong?.let { song ->
                    JSONObject().put("id", song.id.toString()).put("title", song.title)
                        .put("artist", song.artist).put("album", song.album).put("durationMs", song.durationMs)
                } ?: JSONObject.NULL)
        }
        lanRemoteManager.commandHandler = ::handlePlayerCommand
        lanRemoteManager.transferPrepareHandler = { songs ->
            val local = runBlocking { musicRepository().loadAllSongs() }
            JSONObject().apply {
                for (index in 0 until songs.length()) {
                    val remote = songs.getJSONObject(index)
                    local.firstOrNull { song ->
                        song.title.equals(remote.getString("title"), true) &&
                            song.artist.equals(remote.getString("artist"), true) &&
                            kotlin.math.abs(song.durationMs - remote.getLong("durationMs")) <= 2_000 &&
                            (remote.optLong("sizeBytes", 0) <= 0 || song.sizeBytes == remote.optLong("sizeBytes"))
                    }?.let { put(remote.getString("key"), it.id) }
                }
            }
        }
        lanRemoteManager.transferImportHandler = { metadata, input ->
            val resolver = contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, safeTransferFileName(metadata.optString("fileName")))
                put(MediaStore.Audio.Media.TITLE, metadata.optString("title", "Muse Transfer"))
                put(MediaStore.Audio.Media.ARTIST, metadata.optString("artist", "Unknown"))
                put(MediaStore.Audio.Media.ALBUM, metadata.optString("album", "Muse Transfer"))
                put(MediaStore.Audio.Media.MIME_TYPE, metadata.optString("mimeType", "audio/mpeg"))
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Muse Transfer")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建接收歌曲")
            try {
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output, 128 * 1024) }
                    ?: error("无法写入接收歌曲")
                values.clear(); values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                metadata.optString("lyrics").takeIf { it.isNotBlank() }?.let { lyrics ->
                    resolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                            java.io.File(path.substringBeforeLast('.') + ".lrc").writeText(lyrics)
                        }
                    }
                }
                android.content.ContentUris.parseId(uri)
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
        lanRemoteManager.transferCommitHandler = { ids, state ->
            val queue = runBlocking { musicRepository().getSongsByIds(ids) }
            val byId = queue.associateBy { it.id }
            val ordered = ids.map { byId[it] ?: error("接收歌曲尚未写入媒体库") }
            val index = state.getInt("currentIndex").coerceIn(0, ordered.lastIndex)
            musicPlayer.setRepeatMode(RepeatMode.valueOf(state.optString("repeatMode", RepeatMode.ALL.name)))
            musicPlayer.setShuffle(false)
            musicPlayer.setQueue(ordered, index)
            val progress = state.optLong("progressMs", 0).coerceAtLeast(0)
            if (progress > 0 || !state.optBoolean("isPlaying", true)) {
                android.os.Handler(mainLooper).postDelayed({
                    if (progress > 0) musicPlayer.seekTo(progress)
                    if (!state.optBoolean("isPlaying", true) && musicPlayer.uiState.value.isPlaying) musicPlayer.togglePlay()
                }, 650)
            }
        }
    }

    private fun musicRepository() = com.caipan.music.data.MusicRepository(this)

    @Volatile
    private var cachedSongCount = -1

    private fun songCount(): Int {
        if (cachedSongCount < 0) {
            cachedSongCount = runBlocking { musicRepository().countSongs() }
        }
        return cachedSongCount
    }

    /** 播放器命令统一入口：供 LanRemote 与开放 API 共用。 */
    private fun handlePlayerCommand(command: String, payload: JSONObject): JSONObject {
        when (command) {
            "play" -> musicPlayer.play()
            "pause" -> if (musicPlayer.uiState.value.isPlaying) musicPlayer.togglePlay()
            "toggle" -> musicPlayer.togglePlay()
            "next" -> musicPlayer.next()
            "previous" -> musicPlayer.previous()
            "seek" -> musicPlayer.seekTo(payload.getLong("positionMs").coerceIn(0L, musicPlayer.uiState.value.durationMs.coerceAtLeast(0L)))
            "setShuffle" -> musicPlayer.setShuffle(payload.getBoolean("enabled"))
            "setRepeatMode" -> musicPlayer.setRepeatMode(RepeatMode.valueOf(payload.getString("mode")))
        }
        return JSONObject().put("accepted", true)
    }

    private fun safeTransferFileName(raw: String): String {
        val clean = raw.replace(Regex("[\\/:*?\"<>|]"), "_").trim().take(180)
        return clean.ifBlank { "Muse Transfer ${System.currentTimeMillis()}.mp3" }
    }
}
