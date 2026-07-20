package com.caipan.music.viewmodel

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.app.PendingIntent
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caipan.music.data.MusicRepository
import com.caipan.music.data.Playlist
import com.caipan.music.data.PlaylistManager
import com.caipan.music.data.WebdavConfig
import com.caipan.music.data.WebdavManager
import com.caipan.music.model.Song
import com.caipan.music.plugin.PluginInfo
import com.caipan.music.plugin.PluginManager
import com.caipan.music.plugin.PluginNetworkRequest
import com.caipan.music.plugin.PluginWebUiSession
import com.caipan.music.player.EqualizerManager
import com.caipan.music.player.MusicPlayer
import com.caipan.music.player.PlayerUiState
import com.caipan.music.player.RepeatMode
import com.caipan.music.MuseApplication
import com.caipan.music.ui.components.MuseGlassConfig
import com.caipan.music.lan.LanRemoteManager
import com.caipan.music.lan.LanRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class UiStyle { APPLE, MONET }

data class MusicUiState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val playerState: PlayerUiState = PlayerUiState(),
    val showPlayer: Boolean = false,
    val searchQuery: String = "",
    val customBgColor: Color? = null,
    val wallpaperUri: Uri? = null,
    val videoUri: Uri? = null,
    val isLightTheme: Boolean = false,
    val playerBgMode: com.caipan.music.player.PlayerBgMode = com.caipan.music.player.PlayerBgMode.ALBUM_EXTEND,
    val batchMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val uiStyle: UiStyle = UiStyle.APPLE,
    val profileName: String = "Muse 用户",
    val profileAvatar: Uri? = null,
    val listeningTimeMs: Long = 0,
    val completedPlays: Int = 0,
    val repeatCount: Int = 0,
    val repeatCountsBySongId: Map<Long, Int> = emptyMap(),
    val plugins: List<PluginInfo> = emptyList(),
    val pluginInstalling: Boolean = false,
    val pluginMessage: String? = null,

) {
    val filteredSongs: List<Song>
        get() = if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    private val museApplication = application as MuseApplication
    private val pluginManager = museApplication.pluginManager
    val player = museApplication.musicPlayer
    val externalPlayerMonitor = museApplication.externalPlayerMonitorPlugin
    val externalPlayerState = externalPlayerMonitor.state
    val eqManager = EqualizerManager(application)
    val playlistManager = PlaylistManager(application)
    val webdavManager = WebdavManager()
    val lanRemoteManager: LanRemoteManager = (application as MuseApplication).lanRemoteManager

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    private var wallpaperSaveJob: Job? = null

    init {
        loadSongs()
        startProgressUpdater()
        loadPrefs()
    }

    private fun prefs() = getApplication<Application>().getSharedPreferences("muse_prefs", 0)

    private fun loadPrefs() {
        val p = prefs()
        val wallpaperPath = p.getString("wallpaper_path", null)
        val videoPath = p.getString("video_path", null)
        val lightTheme = p.getBoolean("light_theme", false)
        val bgMode = com.caipan.music.player.PlayerBgMode.fromName(p.getString("player_bg_mode", null))
        val style = try { UiStyle.valueOf(p.getString("ui_style", "APPLE") ?: "APPLE") } catch (_: Exception) { UiStyle.APPLE }
        val accentColor = if (p.contains("accent_color")) Color(p.getLong("accent_color", 0L).toULong()) else null
        val avatarPath = p.getString("profile_avatar", null)
        val repeatCounts = runCatching {
            val json = JSONObject(p.getString("repeat_counts_by_song", "{}") ?: "{}")
            json.keys().asSequence().associate { it.toLong() to json.getInt(it) }
        }.getOrDefault(emptyMap())
        _uiState.value = _uiState.value.copy(
            wallpaperUri = if (wallpaperPath != null && File(wallpaperPath).exists()) Uri.fromFile(File(wallpaperPath)) else null,
            videoUri = if (videoPath != null && File(videoPath).exists()) Uri.fromFile(File(videoPath)) else null,
            isLightTheme = lightTheme,
            playerBgMode = bgMode,
            customBgColor = accentColor,
            uiStyle = style,
            profileName = p.getString("profile_name", "Muse 用户") ?: "Muse 用户",
            profileAvatar = avatarPath?.let { Uri.fromFile(File(it)) }?.takeIf { File(avatarPath).exists() },
            listeningTimeMs = p.getLong("listening_time_ms", 0),
            completedPlays = p.getInt("completed_plays", 0),
            repeatCount = repeatCounts.values.sum().takeIf { it > 0 } ?: p.getInt("repeat_count", 0),
            repeatCountsBySongId = repeatCounts,
            plugins = pluginManager.pluginInfo()
        )
    }

    fun setPluginEnabled(id: String, enabled: Boolean) {
        pluginManager.setEnabled(id, enabled)
        _uiState.value = _uiState.value.copy(plugins = pluginManager.pluginInfo())
    }

    fun deleteExternalPlugin(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = pluginManager.deleteExternal(id)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    plugins = pluginManager.pluginInfo(),
                    pluginMessage = result.fold({ "已删除外部模块" }, { "删除失败：${it.message ?: "未知错误"}" })
                )
            }
        }
    }

    fun setPluginPermission(id: String, permission: String, granted: Boolean) {
        pluginManager.setPermissionGranted(id, permission, granted)
        _uiState.value = _uiState.value.copy(plugins = pluginManager.pluginInfo())
    }

    fun installPlugin(uri: Uri) {
        if (_uiState.value.pluginInstalling) return
        _uiState.value = _uiState.value.copy(pluginInstalling = true, pluginMessage = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = pluginManager.install(uri)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    plugins = pluginManager.pluginInfo(),
                    pluginInstalling = false,
                    pluginMessage = result.fold(
                        onSuccess = { "已导入 ${it.name}，请确认来源可信后再启用" },
                        onFailure = { "导入失败：${it.message ?: "插件包无效"}" }
                    )
                )
            }
        }
    }

    fun clearPluginMessage() {
        _uiState.value = _uiState.value.copy(pluginMessage = null)
    }

    fun openPluginWebUi(id: String): Result<PluginWebUiSession> = pluginManager.openWebUi(id)
        .onFailure { error ->
            _uiState.value = _uiState.value.copy(pluginMessage = "打开插件失败：${error.message ?: "未知错误"}")
        }

    fun invokePluginPlayerGesture(gesture: String): Result<PluginWebUiSession>? =
        pluginManager.invokePlayerGesture(gesture)

    suspend fun executePluginWebRequest(id: String, request: PluginNetworkRequest) =
        pluginManager.executeWebUiRequest(id, request)

    suspend fun executePluginHostRequest(id: String, type: String, payload: JSONObject): Result<JSONObject> = runCatching {
        when (type) {
            "config.get" -> JSONObject().put("config", pluginManager.readConfig(id))
            "config.set" -> JSONObject().put("config", pluginManager.writeConfig(id, payload.getJSONObject("config")))
            "glass.getConfig" -> {
                pluginManager.requirePermission(id, "glass.read")
                JSONObject().put("config", museApplication.glassConfigStore.state.value.toJson())
            }
            "glass.setConfig" -> {
                pluginManager.requirePermission(id, "glass.write")
                JSONObject().put("config", museApplication.glassConfigStore.update(payload.optJSONObject("config") ?: payload).toJson())
            }
            "glass.resetConfig" -> {
                pluginManager.requirePermission(id, "glass.write")
                JSONObject().put("config", museApplication.glassConfigStore.reset().toJson())
            }
            "player.getState" -> {
                pluginManager.requirePermission(id, "player.read")
                val state = player.uiState.value
                JSONObject()
                    .put("isPlaying", state.isPlaying)
                    .put("progressMs", state.progressMs)
                    .put("durationMs", state.durationMs)
                    .put("isShuffled", state.isShuffled)
                    .put("repeatMode", state.repeatMode.name)
                    .put("currentSong", state.currentSong?.let { song ->
                        JSONObject().put("id", song.id.toString()).put("title", song.title)
                            .put("artist", song.artist).put("album", song.album).put("durationMs", song.durationMs)
                    } ?: JSONObject.NULL)
            }
            "player.play", "player.pause", "player.next", "player.previous", "player.seek" -> {
                pluginManager.requirePermission(id, "player.control")
                when (type) {
                    "player.play" -> if (!player.uiState.value.isPlaying) togglePlayPause()
                    "player.pause" -> if (player.uiState.value.isPlaying) togglePlayPause()
                    "player.next" -> next()
                    "player.previous" -> previous()
                    "player.seek" -> seekTo(payload.getLong("positionMs").coerceAtLeast(0))
                }
                JSONObject().put("accepted", true)
            }
            "player.setRepeatMode", "player.setShuffle", "player.playSong" -> {
                pluginManager.requirePermission(id, "player.control")
                when (type) {
                    "player.setRepeatMode" -> setRepeatMode(RepeatMode.valueOf(payload.getString("mode")))
                    "player.setShuffle" -> player.setShuffle(payload.getBoolean("enabled"))
                    "player.playSong" -> playSong(requireSong(payload.getLong("songId")))
                }
                JSONObject().put("accepted", true)
            }
            "queue.get" -> {
                pluginManager.requirePermission(id, "queue.read")
                val state = player.uiState.value
                JSONObject().put("currentIndex", state.queue.indexOfFirst { it.id == state.currentSong?.id })
                    .put("songs", JSONArray(state.queue.map(::songJson)))
            }
            "queue.playIndex", "queue.replace" -> {
                pluginManager.requirePermission(id, "queue.control")
                if (type == "queue.playIndex") {
                    val queue = player.uiState.value.queue
                    val index = payload.getInt("index")
                    require(index in queue.indices) { "队列索引无效" }
                    playSongFromQueue(queue, index)
                } else {
                    val songs = songsFromIds(payload.getJSONArray("songIds"))
                    require(songs.isNotEmpty()) { "队列不能为空" }
                    val startIndex = payload.optInt("startIndex", 0)
                    require(startIndex in songs.indices) { "起始索引无效" }
                    playSongFromQueue(songs, startIndex)
                }
                JSONObject().put("accepted", true)
            }
            "library.listSongs", "library.search", "library.getSong", "library.getSummary" -> {
                pluginManager.requirePermission(id, "library.read")
                when (type) {
                    "library.getSong" -> songJson(requireSong(payload.getLong("songId")))
                    "library.getSummary" -> {
                        val songs = _uiState.value.songs
                        JSONObject().put("songCount", songs.size)
                            .put("artistCount", songs.map { it.artist }.distinct().size)
                            .put("albumCount", songs.map { it.album }.distinct().size)
                            .put("totalDurationMs", songs.sumOf { it.durationMs })
                    }
                    else -> {
                        val source = if (type == "library.search") {
                            val query = payload.getString("query").trim().take(128)
                            _uiState.value.songs.filter { song ->
                                song.title.contains(query, true) || song.artist.contains(query, true) || song.album.contains(query, true)
                            }
                        } else _uiState.value.songs
                        pagedSongs(source, payload)
                    }
                }
            }
            "library.refresh" -> {
                pluginManager.requirePermission(id, "library.refresh")
                refresh()
                JSONObject().put("accepted", true)
            }
            "playlists.list" -> {
                pluginManager.requirePermission(id, "playlists.read")
                JSONArray(getAllPlaylists().map(::playlistJson)).let { JSONObject().put("playlists", it) }
            }
            "playlists.getSongs" -> {
                pluginManager.requirePermission(id, "playlists.read")
                val songs = getPlaylistSongs(requirePlaylist(payload.getString("playlistId")).id)
                pagedSongs(songs, payload)
            }
            "playlists.play" -> {
                pluginManager.requirePermission(id, "player.control")
                val playlist = requirePlaylist(payload.getString("playlistId"))
                playPlaylist(playlist.id, payload.optInt("startIndex", 0))
                JSONObject().put("accepted", true)
            }
            "playlists.create", "playlists.rename", "playlists.addSongs", "playlists.removeSongs" -> {
                pluginManager.requirePermission(id, "playlists.write")
                when (type) {
                    "playlists.create" -> {
                        val playlist = Playlist(UUID.randomUUID().toString(), playlistName(payload.getString("name")))
                        playlistManager.save(playlist)
                        playlistJson(playlist)
                    }
                    "playlists.rename" -> {
                        val playlist = requirePlaylist(payload.getString("playlistId"))
                        val updated = playlist.copy(name = playlistName(payload.getString("name")))
                        playlistManager.save(updated)
                        playlistJson(updated)
                    }
                    "playlists.addSongs" -> {
                        val playlist = requirePlaylist(payload.getString("playlistId"))
                        playlistManager.addSongs(playlist.id, songIds(payload.getJSONArray("songIds")))
                        JSONObject().put("updated", true)
                    }
                    else -> {
                        val playlist = requirePlaylist(payload.getString("playlistId"))
                        playlistManager.removeSongs(playlist.id, songIds(payload.getJSONArray("songIds")))
                        JSONObject().put("updated", true)
                    }
                }
            }
            "playlists.delete" -> {
                pluginManager.requirePermission(id, "playlists.delete")
                deletePlaylist(requirePlaylist(payload.getString("playlistId")).id)
                JSONObject().put("deleted", true)
            }
            "lyrics.get", "lyrics.getCurrent" -> {
                pluginManager.requirePermission(id, "lyrics.read")
                val songId = if (type == "lyrics.get") payload.getLong("songId")
                    else player.uiState.value.currentSong?.id ?: error("当前没有歌曲")
                requireSong(songId)
                val lines = loadLyrics(songId)
                JSONObject().put("songId", songId.toString()).put("lines", JSONArray(lines.map { line ->
                    JSONObject().put("timeMs", line.timeMs).put("text", line.text)
                }))
            }
            "stats.get" -> {
                pluginManager.requirePermission(id, "stats.read")
                val state = _uiState.value
                JSONObject().put("listeningTimeMs", state.listeningTimeMs)
                    .put("completedPlays", state.completedPlays).put("repeatCount", state.repeatCount)
            }
            "theme.get" -> {
                pluginManager.requirePermission(id, "theme.read")
                val state = _uiState.value
                JSONObject().put("isLight", state.isLightTheme)
                    .put("accent", state.customBgColor?.let { color ->
                        "#%08X".format(color.value.toLong())
                    } ?: JSONObject.NULL)
                    .put("uiStyle", state.uiStyle.name).put("playerBgMode", state.playerBgMode.name)
                    .put("hasWallpaper", state.wallpaperUri != null).put("hasVideo", state.videoUri != null)
            }
            "theme.apply" -> {
                pluginManager.requirePermission(id, "theme.write")
                if (payload.has("isLight")) {
                    val light = payload.getBoolean("isLight")
                    if (_uiState.value.isLightTheme != light) toggleTheme()
                }
                if (payload.has("accent")) {
                    val raw = payload.getString("accent")
                    require(Regex("^#[0-9a-fA-F]{6}$").matches(raw)) { "强调色必须为 #RRGGBB" }
                    val argb = 0xff000000L or raw.drop(1).toLong(16)
                    setBackgroundColor(Color(argb.toULong()))
                }
                if (payload.has("uiStyle")) setUiStyle(UiStyle.valueOf(payload.getString("uiStyle")))
                if (payload.has("playerBgMode")) setPlayerBgMode(
                    com.caipan.music.player.PlayerBgMode.valueOf(payload.getString("playerBgMode")))
                JSONObject().put("applied", true)
            }
            "theme.reset" -> {
                pluginManager.requirePermission(id, "theme.write")
                setBackgroundColor(null)
                JSONObject().put("applied", true)
            }
            "equalizer.get" -> {
                pluginManager.requirePermission(id, "equalizer.read")
                JSONObject().put("enabled", eqManager.isEnabled).put("presetName", eqManager.presetName)
                    .put("presets", JSONArray(eqManager.presets)).put("bands", JSONArray(eqManager.bands.map { band ->
                        JSONObject().put("freqHz", band.freqHz).put("levelDb", band.levelDb)
                            .put("minDb", band.rangeMinDb).put("maxDb", band.rangeMaxDb)
                    }))
            }
            "equalizer.setEnabled", "equalizer.setBand", "equalizer.reset", "equalizer.loadPreset", "equalizer.savePreset", "equalizer.deletePreset" -> {
                pluginManager.requirePermission(id, "equalizer.control")
                when (type) {
                    "equalizer.setEnabled" -> eqManager.setEnabled(payload.getBoolean("enabled"))
                    "equalizer.setBand" -> {
                        val index = payload.getInt("index")
                        require(index in eqManager.bands.indices) { "均衡器频段无效" }
                        eqManager.setBandLevel(index, payload.getDouble("levelDb").toFloat())
                    }
                    "equalizer.reset" -> eqManager.resetAll()
                    "equalizer.loadPreset" -> require(eqManager.loadPreset(payload.getString("name"))) { "预设不存在" }
                    "equalizer.savePreset" -> eqManager.savePreset(playlistName(payload.getString("name")))
                    "equalizer.deletePreset" -> eqManager.deletePreset(payload.getString("name"))
                }
                JSONObject().put("accepted", true)
            }
            "profile.get" -> {
                pluginManager.requirePermission(id, "profile.read")
                JSONObject().put("name", _uiState.value.profileName).put("hasAvatar", _uiState.value.profileAvatar != null)
            }
            "profile.setName" -> {
                pluginManager.requirePermission(id, "profile.write")
                setProfileName(payload.getString("name"))
                JSONObject().put("name", _uiState.value.profileName)
            }
            "lan.discover" -> {
                pluginManager.requirePermission(id, "lan.discovery")
                lanRemoteManager.startDiscovery().getOrThrow()
                lanRemoteManager.discoveredJson()
            }
            "lan.stopDiscovery" -> {
                pluginManager.requirePermission(id, "lan.discovery")
                lanRemoteManager.stopDiscovery()
                JSONObject().put("stopped", true)
            }
            "lan.devices" -> {
                pluginManager.requirePermission(id, "lan.state")
                lanRemoteManager.pairedJson()
            }
            "lan.pair" -> {
                pluginManager.requirePermission(id, "lan.pairing")
                lanRemoteManager.pair(payload.getString("deviceId"), payload.getString("code"), "插件 $id").getOrThrow()
            }
            "lan.getState" -> {
                pluginManager.requirePermission(id, "lan.state")
                lanRemoteManager.getRemoteState(payload.getString("deviceId")).getOrThrow()
            }
            "lan.command" -> {
                pluginManager.requirePermission(id, "lan.control")
                lanRemoteManager.command(payload.getString("deviceId"), payload.getString("command"), payload.optJSONObject("payload") ?: JSONObject()).getOrThrow()
            }
            "lan.transferPlayback" -> {
                pluginManager.requirePermission(id, "lan.transfer")
                val state = player.uiState.value
                val currentSong = state.currentSong ?: error("当前没有可流转的歌曲")
                val result = lanRemoteManager.transferPlayback(payload.getString("deviceId"), listOf(currentSong), 0,
                    state.progressMs, state.isPlaying, state.isShuffled, state.repeatMode.name).getOrThrow()
                if (player.uiState.value.isPlaying) player.togglePlay()
                result
            }
            "lan.localState" -> {
                pluginManager.requirePermission(id, "lan.state")
                lanRemoteManager.localStateJson()
            }
            "lan.setHosting" -> {
                pluginManager.requirePermission(id, "lan.hosting")
                setLanHosting(payload.getBoolean("enabled"))
                JSONObject().put("accepted", true)
            }
            "lan.generatePairingCode" -> {
                pluginManager.requirePermission(id, "lan.hosting")
                val code = lanRemoteManager.generatePairingCode()
                JSONObject().put("code", code).put("expiresAt", lanRemoteManager.state.value.pairingExpiresAt)
            }
            "lan.revokeClient" -> {
                pluginManager.requirePermission(id, "lan.hosting")
                lanRemoteManager.revokeClient(payload.getString("clientId"))
                JSONObject().put("revoked", true)
            }
            "lan.forgetDevice" -> {
                pluginManager.requirePermission(id, "lan.pairing")
                lanRemoteManager.forgetDevice(payload.getString("deviceId"))
                JSONObject().put("forgotten", true)
            }
            else -> error("不支持的宿主消息类型")
        }
    }

    fun setLanHosting(enabled: Boolean) {
        if (enabled) LanRemoteService.start(getApplication()) else LanRemoteService.stop(getApplication())
    }

    private fun songJson(song: Song) = JSONObject().put("id", song.id.toString())
        .put("title", song.title).put("artist", song.artist).put("album", song.album)
        .put("durationMs", song.durationMs).put("albumId", song.albumId.toString())
        .put("mimeType", song.mimeType).put("sizeBytes", song.sizeBytes)
        .put("bitrate", song.bitrate).put("sampleRate", song.sampleRate)

    private fun playlistJson(playlist: Playlist) = JSONObject().put("id", playlist.id)
        .put("name", playlist.name).put("songCount", playlist.songIds.size).put("hasCover", playlist.coverUri != null)

    private fun requireSong(songId: Long): Song = _uiState.value.songs.firstOrNull { it.id == songId }
        ?: error("歌曲不存在")

    private fun requirePlaylist(playlistId: String): Playlist = getAllPlaylists().firstOrNull { it.id == playlistId }
        ?: error("歌单不存在")

    private fun playlistName(raw: String): String = raw.trim().also {
        require(it.isNotEmpty() && it.length <= 64) { "名称长度必须为 1 到 64 个字符" }
    }

    private fun songIds(array: JSONArray): List<Long> {
        require(array.length() in 1..500) { "每次需要提交 1 到 500 首歌曲" }
        return (0 until array.length()).map { array.getLong(it) }.distinct().also { ids ->
            require(ids.all { candidate -> _uiState.value.songs.any { it.id == candidate } }) { "包含不存在的歌曲" }
        }
    }

    private fun songsFromIds(array: JSONArray): List<Song> = songIds(array).map(::requireSong)

    private fun pagedSongs(songs: List<Song>, payload: JSONObject): JSONObject {
        val offset = payload.optInt("offset", 0).coerceAtLeast(0)
        val limit = payload.optInt("limit", 100).coerceIn(1, 200)
        return JSONObject().put("total", songs.size).put("offset", offset).put("limit", limit)
            .put("songs", JSONArray(songs.drop(offset).take(limit).map(::songJson)))
    }

    fun setProfileName(name: String) {
        val clean = name.trim().take(24).ifBlank { "Muse 用户" }
        prefs().edit().putString("profile_name", clean).apply()
        _uiState.value = _uiState.value.copy(profileName = clean)
    }

    fun saveProfileAvatar(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val avatarDir = File(getApplication<Application>().filesDir, "profile_avatars").apply { mkdirs() }
            val target = File(avatarDir, "avatar_${UUID.randomUUID()}.jpg")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            withContext(Dispatchers.Main) {
                prefs().edit().putString("profile_avatar", target.absolutePath).apply()
                _uiState.value = _uiState.value.copy(profileAvatar = Uri.fromFile(target))
            }
        }
    }

    fun saveWallpaper(uri: Uri) {
        wallpaperSaveJob?.cancel()
        wallpaperSaveJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            try {
                val destDir = File(ctx.filesDir, "wallpapers").apply { mkdirs() }
                // A unique path prevents image-loader cache reuse when the wallpaper is replaced.
                val destFile = File(destDir, "wallpaper_${UUID.randomUUID()}.jpg")
                val tempFile = File(destDir, "${destFile.name}.tmp")
                withContext(Dispatchers.IO) {
                    try {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            val bitmap = BitmapFactory.decodeStream(input)
                                ?: error("Unable to decode wallpaper image")
                            try {
                                FileOutputStream(tempFile).use { output ->
                                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                                        "Unable to encode wallpaper image"
                                    }
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        } ?: error("Unable to open wallpaper image")
                        check(tempFile.renameTo(destFile)) { "Unable to finalize wallpaper image" }
                    } catch (error: Throwable) {
                        tempFile.delete()
                        throw error
                    }
                }
                if (destFile.exists()) {
                    val oldPath = prefs().getString("wallpaper_path", null)
                    val fileUri = Uri.fromFile(destFile)
                    _uiState.value = _uiState.value.copy(wallpaperUri = fileUri)
                    prefs().edit().putString("wallpaper_path", destFile.absolutePath).apply()
                    if (oldPath != null && oldPath != destFile.absolutePath) File(oldPath).delete()
                    Log.d("Muse", "Wallpaper saved: ${destFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("Muse", "Failed to save wallpaper", e)
            }
        }
    }

    fun clearWallpaper() {
        val p = prefs()
        val oldPath = p.getString("wallpaper_path", null)
        if (oldPath != null) File(oldPath).delete()
        _uiState.value = _uiState.value.copy(wallpaperUri = null)
        p.edit().remove("wallpaper_path").apply()
    }

    fun saveVideo(uri: Uri) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val destDir = File(ctx.filesDir, "videos").apply { mkdirs() }
                val destFile = File(destDir, "bg_video.mp4")
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                }
                if (destFile.exists()) {
                    val fileUri = Uri.fromFile(destFile)
                    _uiState.value = _uiState.value.copy(videoUri = fileUri)
                    prefs().edit().putString("video_path", destFile.absolutePath).apply()
                }
            } catch (e: Exception) {
                Log.e("Muse", "Failed to save video", e)
            }
        }
    }

    fun clearVideo() {
        val p = prefs()
        val oldPath = p.getString("video_path", null)
        if (oldPath != null) File(oldPath).delete()
        _uiState.value = _uiState.value.copy(videoUri = null)
        p.edit().remove("video_path").apply()
    }

    fun toggleBatchMode() {
        _uiState.value = _uiState.value.copy(
            batchMode = !_uiState.value.batchMode,
            selectedIds = emptySet()
        )
    }

    fun toggleSongSelection(id: Long) {
        val set = _uiState.value.selectedIds.toMutableSet()
        if (id in set) set.remove(id) else set.add(id)
        _uiState.value = _uiState.value.copy(selectedIds = set)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedIds = _uiState.value.filteredSongs.map { it.id }.toSet()
        )
    }

    fun selectSongs(ids: Set<Long>) {
        _uiState.value = _uiState.value.copy(batchMode = true, selectedIds = ids)
    }

    fun addSelectedToPlaylist(playlistId: String) {
        playlistManager.addSongs(playlistId, _uiState.value.selectedIds.toList())
        _uiState.value = _uiState.value.copy(batchMode = false, selectedIds = emptySet())
    }

    fun createDeleteRequest(): PendingIntent? {
        val uris = _uiState.value.selectedIds.map { id ->
            android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        }
        return if (uris.isEmpty()) null else MediaStore.createDeleteRequest(
            getApplication<Application>().contentResolver, uris
        )
    }

    fun onDeleteCompleted() {
        _uiState.value = _uiState.value.copy(batchMode = false, selectedIds = emptySet())
        loadSongs()
    }

    fun toggleTheme() {
        val new = !_uiState.value.isLightTheme
        _uiState.value = _uiState.value.copy(isLightTheme = new)
        prefs().edit().putBoolean("light_theme", new).apply()
    }

    fun setUiStyle(style: UiStyle) {
        _uiState.value = _uiState.value.copy(uiStyle = style)
        prefs().edit().putString("ui_style", style.name).apply()
    }

    fun playSongAt(index: Int) {
        val songs = _uiState.value.filteredSongs
        if (index in songs.indices) {
            player.setQueue(songs, index)
            _uiState.value = _uiState.value.copy(showPlayer = true)
        }
    }

    fun playSong(song: Song) {
        val songs = _uiState.value.songs
        val index = songs.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            player.setQueue(songs, index)
            _uiState.value = _uiState.value.copy(showPlayer = true)
        }
    }

    fun playSongFromQueue(queue: List<Song>, index: Int) {
        if (index in queue.indices) {
            player.setQueue(queue, index)
            _uiState.value = _uiState.value.copy(showPlayer = true)
        }
    }

    fun togglePlayPause() = player.togglePlay()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun setRepeatMode(mode: com.caipan.music.player.RepeatMode) = player.setRepeatMode(mode)
    fun toggleShuffle() = player.toggleShuffle()
    fun setBackgroundColor(color: Color?) {
        _uiState.value = _uiState.value.copy(customBgColor = color)
        prefs().edit().apply {
            if (color == null) remove("accent_color") else putLong("accent_color", color.value.toLong())
        }.apply()
    }
    fun setPlayerBgMode(mode: com.caipan.music.player.PlayerBgMode) {
        _uiState.value = _uiState.value.copy(playerBgMode = mode)
        prefs().edit().putString("player_bg_mode", mode.name).apply()
    }
    suspend fun loadLyrics(songId: Long) = com.caipan.music.player.LyricsManager.loadLyrics(getApplication(), songId)
    fun setSearchQuery(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query) }
    fun refresh() = loadSongs()

    // ── Playlist helpers ──
    fun getAllPlaylists(): List<Playlist> = playlistManager.getAll()

    suspend fun getPlaylistSongs(playlistId: String): List<Song> {
        val ids = playlistManager.getPlaylistSongs(playlistId)
        return if (ids.isEmpty()) emptyList() else repository.getSongsByIds(ids)
    }

    fun deletePlaylist(id: String) { playlistManager.delete(id) }

    fun setPlaylistCover(playlistId: String, uri: Uri, onSaved: (Playlist) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val destDir = File(ctx.filesDir, "playlist_covers").apply { mkdirs() }
                val destFile = File(destDir, "cover_${playlistId}_${UUID.randomUUID()}.jpg")
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        val bitmap = BitmapFactory.decodeStream(input) ?: error("Unable to decode playlist cover")
                        FileOutputStream(destFile).use { output ->
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
                        }
                        bitmap.recycle()
                    }
                }
                if (destFile.exists()) {
                    playlistManager.setCover(playlistId, Uri.fromFile(destFile).toString())
                    playlistManager.getAll().find { it.id == playlistId }?.let(onSaved)
                }
            } catch (e: Exception) {
                Log.e("Muse", "Failed to save playlist cover", e)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        playlistManager.removeSongs(playlistId, listOf(songId))
    }

    fun playPlaylist(playlistId: String, startIndex: Int = 0) {
        viewModelScope.launch {
            val songs = getPlaylistSongs(playlistId)
            if (songs.isNotEmpty() && startIndex in songs.indices) {
                player.setQueue(songs, startIndex)
                _uiState.value = _uiState.value.copy(showPlayer = true)
            }
        }
    }

    // ── WebDAV ──
    fun loadWebdavConfig(): WebdavConfig {
        val p = prefs()
        return WebdavConfig(
            url = p.getString("webdav_url", "") ?: "",
            username = p.getString("webdav_user", "") ?: "",
            password = p.getString("webdav_pass", "") ?: ""
        )
    }

    fun saveWebdavConfig(config: WebdavConfig) {
        prefs().edit()
            .putString("webdav_url", config.url)
            .putString("webdav_user", config.username)
            .putString("webdav_pass", config.password)
            .apply()
    }

    fun importFromWebdav(remotePaths: List<String>, config: WebdavConfig, playlistName: String) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val cr = ctx.contentResolver
                val playlistId = java.util.UUID.randomUUID().toString()
                val songIds = mutableListOf<Long>()

                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    remotePaths.forEach { remotePath ->
                        try {
                            val fileName = remotePath.substringAfterLast('/')
                            val mime = if (fileName.endsWith(".mp3", true)) "audio/mpeg"
                                else if (fileName.endsWith(".flac", true)) "audio/flac"
                                else if (fileName.endsWith(".wav", true)) "audio/wav"
                                else if (fileName.endsWith(".ogg", true)) "audio/ogg"
                                else if (fileName.endsWith(".m4a", true)) "audio/mp4"
                                else "audio/*"

                            // Step 1: Create MediaStore entry (pending)
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, mime)
                                if (android.os.Build.VERSION.SDK_INT >= 29) {
                                    put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
                                }
                            }
                            val uri = cr.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                            if (uri == null) {
                                Log.w("Muse", "MediaStore insert returned null for $fileName")
                                return@forEach
                            }

                            // Step 2: Download remote file content into the MediaStore entry
                            cr.openOutputStream(uri)?.use { output ->
                                val downloadResult = webdavManager.downloadToStream(config, remotePath, output)
                                downloadResult.onFailure { e ->
                                    Log.e("Muse", "Download failed for $fileName", e)
                                    // Clean up failed entry
                                    cr.delete(uri, null, null)
                                    return@forEach
                                }
                            }

                            // Step 3: Mark as not pending (makes it visible to MediaStore queries)
                            if (android.os.Build.VERSION.SDK_INT >= 29) {
                                val finalize = android.content.ContentValues().apply {
                                    put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                                }
                                cr.update(uri, finalize, null, null)
                            }

                            val id = android.content.ContentUris.parseId(uri)
                            songIds.add(id)
                            Log.d("Muse", "Imported $fileName -> MediaStore id=$id")
                        } catch (e: Exception) {
                            Log.e("Muse", "Failed to import $remotePath", e)
                        }
                    }
                }

                if (songIds.isNotEmpty()) {
                    playlistManager.save(Playlist(playlistId, playlistName))
                    playlistManager.addSongs(playlistId, songIds)
                    Log.d("Muse", "Created playlist $playlistName with ${songIds.size} songs")
                }
                loadSongs()
            } catch (e: Exception) {
                Log.e("Muse", "WebDAV import failed", e)
            }
        }
    }

    private fun loadSongs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val songs = repository.loadAllSongs()
                _uiState.value = _uiState.value.copy(songs = songs, isLoading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to load songs")
            }
        }
    }

    private fun startProgressUpdater() {
        viewModelScope.launch {
            var lastSessionId = 0
            var lastSongId: Long? = null
            var lastProgress = 0L
            var pendingListenMs = 0L
            var lastCompletionSerial = 0L
            while (true) {
                player.updateProgress()
                val state = player.uiState.value
                val songId = state.currentSong?.id
                if (lastSongId != null && songId != lastSongId) lastProgress = 0
                val completion = state.completionEvent
                if (completion.serial > lastCompletionSerial) {
                    val increment = (completion.serial - lastCompletionSerial).toInt()
                    val count = _uiState.value.completedPlays + increment
                    val bySong = _uiState.value.repeatCountsBySongId.toMutableMap()
                    if (completion.wasSingleRepeat && completion.songId != null) {
                        bySong[completion.songId] = (bySong[completion.songId] ?: 0) + increment
                    }
                    val repeats = bySong.values.sum()
                    _uiState.value = _uiState.value.copy(completedPlays = count, repeatCount = repeats, repeatCountsBySongId = bySong)
                    val json = JSONObject().apply { bySong.forEach { (id, value) -> put(id.toString(), value) } }
                    prefs().edit().putInt("completed_plays", count).putInt("repeat_count", repeats)
                        .putString("repeat_counts_by_song", json.toString()).apply()
                    lastCompletionSerial = completion.serial
                }
                if (state.isPlaying && songId == lastSongId && state.progressMs > lastProgress) {
                    pendingListenMs += (state.progressMs - lastProgress).coerceAtMost(1000)
                    if (pendingListenMs >= 5000) {
                        val total = _uiState.value.listeningTimeMs + pendingListenMs
                        _uiState.value = _uiState.value.copy(listeningTimeMs = total)
                        prefs().edit().putLong("listening_time_ms", total).apply()
                        pendingListenMs = 0
                    }
                }
                lastSongId = songId
                lastProgress = state.progressMs
                _uiState.value = _uiState.value.copy(playerState = state)
                // Attach EQ when audio session is ready
                if (state.audioSessionId != 0 && state.audioSessionId != lastSessionId) {
                    lastSessionId = state.audioSessionId
                    eqManager.attach(state.audioSessionId)
                }
                delay(250)
            }
        }
    }

    override fun onCleared() { super.onCleared(); eqManager.release() }
}
