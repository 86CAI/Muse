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
import com.caipan.music.data.OnlineMusicPreferences
import com.caipan.music.data.MusicMode
import com.caipan.music.data.NeteaseSession
import com.caipan.music.data.Playlist
import com.caipan.music.data.PlaylistManager
import com.caipan.music.data.WebdavConfig
import com.caipan.music.data.WebdavManager
import com.caipan.music.model.Song
import okhttp3.OkHttpClient
import okhttp3.Request
import com.caipan.music.online.KugouCatalog
import com.caipan.music.online.KuwoCatalog
import com.caipan.music.online.LxSourceDescriptor
import com.caipan.music.online.MusicCapability
import com.caipan.music.online.OnlineCatalog
import com.caipan.music.online.OnlineSearchResult
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.NeteaseComment
import com.caipan.music.online.NeteaseHomeContent
import com.caipan.music.online.NeteaseHomePodcast
import com.caipan.music.online.NeteaseProfileDetails
import com.caipan.music.online.RemotePlaylistDetail
import com.caipan.music.online.RemotePlaylistSummary
import com.caipan.music.online.QQMusicCatalog
import com.caipan.music.online.toOnlineTrack
import com.caipan.music.online.toSong
import com.caipan.music.plugin.PluginInfo
import com.caipan.music.plugin.PluginManager
import com.caipan.music.plugin.PluginNetworkRequest
import com.caipan.music.plugin.PluginWebUiSession
import com.caipan.music.player.EqualizerManager
import com.caipan.music.player.LyricLine
import com.caipan.music.player.MusicPlayer
import com.caipan.music.player.PlayerUiState
import com.caipan.music.player.RepeatMode
import com.caipan.music.MuseApplication
import com.caipan.music.ui.components.MuseGlassConfig
import com.caipan.music.lan.LanRemoteManager
import com.caipan.music.lan.LanRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class UiStyle {
    /** MeloX 风格：悬浮胶囊底栏 + 大标题首页 + 胶囊迷你播放器（移植自 NEORUAA/Mei_MeloX_Android）。 */
    MELOX,
    /** Muse's existing classic hierarchy, renamed to 云版 (Cloud). */
    CLOUD,
    /** Liquid Glass：折射玻璃、RGB 色散与弹性阴影（基于 Kyant0/AndroidLiquidGlass）。 */
    LIQUID,
    /** Legacy persisted value; migrated to [CLOUD] during preference loading. */
    @Deprecated("Use CLOUD")
    MONET
}

/** 解析持久化的版式；旧版 "APPLE"（Apple Music 版式）已由 MeloX 移植版取代。 */
fun resolveUiStyle(raw: String?): UiStyle = when (raw) {
    null -> UiStyle.LIQUID
    "APPLE" -> UiStyle.MELOX
    else -> try { UiStyle.valueOf(raw) } catch (_: Exception) { UiStyle.LIQUID }
}

private const val FAVORITES_PLAYLIST_ID = "favorites"
private const val FAVORITES_PLAYLIST_NAME = "我喜欢的音乐"

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
    val uiStyle: UiStyle = UiStyle.LIQUID,
    val profileName: String = "Muse 用户",
    val profileAvatar: Uri? = null,
    val listeningTimeMs: Long = 0,
    val completedPlays: Int = 0,
    val repeatCount: Int = 0,
    val repeatCountsBySongId: Map<Long, Int> = emptyMap(),
    val plugins: List<PluginInfo> = emptyList(),
    val pluginInstalling: Boolean = false,
    val pluginMessage: String? = null,
    val onlineSearchEnabled: Boolean = false,
    val musicMode: MusicMode = MusicMode.LOCAL,
    val neteaseSession: NeteaseSession? = null,
    val onlineHome: NeteaseHomeContent? = null,
    val onlineProfileDetails: NeteaseProfileDetails? = null,
    val onlinePlaylists: List<RemotePlaylistSummary> = emptyList(),
    val onlineLikedSongs: List<OnlineTrack> = emptyList(),
    val onlineRecentSongs: List<OnlineTrack> = emptyList(),
    val onlinePlaylistDetail: RemotePlaylistDetail? = null,
    val onlineLoading: Boolean = false,
    val onlineError: String? = null,
    val neteaseCommentsSongId: Long? = null,
    val neteaseHotComments: List<NeteaseComment> = emptyList(),
    val neteaseLatestComments: List<NeteaseComment> = emptyList(),
    val neteaseCommentsTotal: Int = 0,
    val neteaseCommentsHasMore: Boolean = false,
    val neteaseCommentsNextOffset: Int = 0,
    val neteaseCommentsNextBeforeTime: Long = 0L,
    val neteaseCommentsInitialLoading: Boolean = false,
    val neteaseCommentsRefreshing: Boolean = false,
    val neteaseCommentsLoadingMore: Boolean = false,
    val neteaseCommentsError: String? = null,
    val onlineSources: List<LxSourceDescriptor> = emptyList(),
    val onlineSourceImporting: Boolean = false,
    val onlineSourceMessage: String? = null,
    val playbackSettings: com.caipan.music.player.PlaybackSettings = com.caipan.music.player.PlaybackSettings(),
    val sleepTimerRemainingMs: Long = 0
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
    private val onlineSourceManager = museApplication.onlineSourceManager
    private val onlinePreferences = OnlineMusicPreferences(application)
    val onlineCatalog = museApplication.onlineCatalog
    val onlineCatalogs: List<OnlineCatalog> = listOf(
        onlineCatalog,
        KuwoCatalog(),
        KugouCatalog(),
        QQMusicCatalog()
    )
    val player = museApplication.musicPlayer
    val playbackSettingsStore = museApplication.playbackSettingsStore
    val audioEffectsManager = com.caipan.music.player.AudioEffectsManager()
    val playHistoryManager = com.caipan.music.data.PlayHistoryManager(application)
    val externalPlayerMonitor = museApplication.externalPlayerMonitorPlugin
    val externalPlayerState = externalPlayerMonitor.state
    val eqManager = EqualizerManager(application)
    val playlistManager = PlaylistManager(application)
    val webdavManager = WebdavManager()
    val lanRemoteManager: LanRemoteManager = (application as MuseApplication).lanRemoteManager

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    private var wallpaperSaveJob: Job? = null
    private var neteaseCommentsJob: Job? = null
    private var neteaseCommentsLoadMoreJob: Job? = null

    init {
        loadSongs()
        startProgressUpdater()
        loadPrefs()
        viewModelScope.launch {
            museApplication.neteaseSessionStore.session.collect { session ->
                _uiState.value = _uiState.value.copy(neteaseSession = session)
                if (_uiState.value.musicMode == MusicMode.ONLINE) {
                    refreshOnlineContent()
                }
            }
        }
        audioEffectsManager.syncFromSettings(playbackSettingsStore.state.value)
        player.onAudioSessionChanged = { sessionId ->
            eqManager.attach(sessionId)
            audioEffectsManager.attach(sessionId)
        }
    }

    private fun prefs() = getApplication<Application>().getSharedPreferences("muse_prefs", 0)

    private fun loadPrefs() {
        val p = prefs()
        // 一次性迁移：旧版（< 2026-08）把 Liquid Glass 存成了 "MONET"
        if (!p.getBoolean("ui_style_migrated", false)) {
            val raw = p.getString("ui_style", null)
            // Before the dedicated Apple route existed, the APPLE label was
            // used for Muse's existing cloud/classic hierarchy. Preserve that
            // saved choice under its new name; newly selected Apple mode is
            // written after this one-time migration has completed.
            if (raw == "MONET" || raw == "APPLE") {
                p.edit().putString("ui_style", "CLOUD").apply()
            }
            p.edit().putBoolean("ui_style_migrated", true).apply()
        }
        // MELOX 是 Apple Music 版式的替代路线；CLOUD 承接旧版 MONET 的迁移。
        val wallpaperPath = p.getString("wallpaper_path", null)
        val videoPath = p.getString("video_path", null)
        val lightTheme = p.getBoolean("light_theme", false)
        val bgMode = com.caipan.music.player.PlayerBgMode.fromName(p.getString("player_bg_mode", null))
        // 解析 UiStyle；旧版 "MONET" 已迁移为 CLOUD，旧版 "APPLE" 映射为 MELOX。
        val style = resolveUiStyle(p.getString("ui_style", "LIQUID"))
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
            plugins = pluginManager.pluginInfo(),
            onlineSearchEnabled = onlinePreferences.onlineSearchEnabled,
            musicMode = onlinePreferences.musicMode,
            neteaseSession = museApplication.neteaseSessionStore.session.value,
            onlineSources = onlineSourceManager.listSources(),
            playbackSettings = playbackSettingsStore.state.value
        )
    }

    fun setOnlineSearchEnabled(enabled: Boolean) {
        setMusicMode(if (enabled) MusicMode.ONLINE else MusicMode.LOCAL)
    }

    fun setMusicMode(mode: MusicMode) {
        onlinePreferences.musicMode = mode
        _uiState.value = _uiState.value.copy(
            musicMode = mode,
            onlineSearchEnabled = mode == MusicMode.ONLINE,
            onlineError = null
        )
        if (mode == MusicMode.ONLINE) refreshOnlineContent()
    }

    fun refreshOnlineContent() {
        if (_uiState.value.onlineLoading) return
        _uiState.value = _uiState.value.copy(onlineLoading = true, onlineError = null)
        viewModelScope.launch(Dispatchers.IO) {
            val session = museApplication.neteaseSessionStore.session.value
            val userId = session?.userId?.takeIf { it > 0L }
            val homeRequest = async { museApplication.neteaseClient.home() }
            val profileRequest = userId?.let { id ->
                async { museApplication.neteaseClient.userDetail(id) }
            }
            val playlistsRequest = userId?.let { id ->
                async { museApplication.neteaseClient.userPlaylists(id) }
            }
            val likedSongsRequest = userId?.let { id ->
                async { museApplication.neteaseClient.likedSongs(id) }
            }
            val recentSongsRequest = userId?.let {
                async { museApplication.neteaseClient.recentSongs() }
            }
            val home = homeRequest.await()
            val profile = profileRequest?.await()
            val playlists = playlistsRequest?.await()
            val likedSongs = likedSongsRequest?.await()
            val recentSongs = recentSongsRequest?.await()
            withContext(Dispatchers.Main) {
                val previous = _uiState.value
                val homeContent = home.getOrNull()
                val profileContent = profile?.getOrNull()
                val playlistContent = playlists?.getOrNull()
                val likedContent = likedSongs?.getOrNull()
                val recentContent = recentSongs?.getOrNull()
                val firstFailure = listOfNotNull(
                    home.exceptionOrNull(),
                    playlists?.exceptionOrNull(),
                    likedSongs?.exceptionOrNull(),
                    recentSongs?.exceptionOrNull()
                ).firstOrNull()
                val currentUserId = museApplication.neteaseSessionStore.session.value
                    ?.userId
                    ?.takeIf { it > 0L }
                _uiState.value = _uiState.value.copy(
                    onlineHome = homeContent ?: previous.onlineHome,
                    // A failed profile refresh keeps the last usable details;
                    // an account switch/logout never lets an in-flight result
                    // restore the previous account's identity.
                    onlineProfileDetails = if (currentUserId == userId && userId != null) {
                        profileContent ?: previous.onlineProfileDetails?.takeIf { it.userId == userId }
                    } else {
                        null
                    },
                    onlinePlaylists = if (userId == null) emptyList() else playlistContent ?: previous.onlinePlaylists,
                    onlineLikedSongs = if (userId == null) emptyList() else likedContent ?: previous.onlineLikedSongs,
                    onlineRecentSongs = if (userId == null) emptyList() else recentContent ?: previous.onlineRecentSongs,
                    onlineLoading = false,
                    onlineError = when {
                        homeContent == null && previous.onlineHome == null ->
                            home.exceptionOrNull()?.message ?: "Unable to load NetEase recommendations"
                        userId != null && playlistContent == null && likedContent == null && recentContent == null &&
                            previous.onlinePlaylists.isEmpty() && previous.onlineLikedSongs.isEmpty() && previous.onlineRecentSongs.isEmpty() ->
                            firstFailure?.message ?: "Unable to load your NetEase library"
                        else -> null
                    }
                )
            }
        }
    }

    fun acceptNeteaseCookie(cookie: String, onResult: (Result<NeteaseSession>) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = com.caipan.music.data.NeteaseSessionStore.normalizeCookie(cookie)
            val result = if (!com.caipan.music.data.NeteaseSessionStore.containsMusicU(normalized)) {
                Result.failure(IllegalArgumentException("NetEase login cookie is missing MUSIC_U"))
            } else com.caipan.music.online.NeteaseOnlineClient(cookieProvider = { normalized }).account().map { account ->
                NeteaseSession(normalized, account.userId, account.nickname, account.avatarUrl)
            }
            result.onSuccess { museApplication.neteaseSessionStore.save(it) }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun logoutNetease() {
        museApplication.neteaseSessionStore.clear()
        neteaseCommentsJob?.cancel()
        neteaseCommentsLoadMoreJob?.cancel()
        _uiState.value = _uiState.value.copy(
            neteaseSession = null,
            onlineProfileDetails = null,
            onlinePlaylists = emptyList(),
            onlineLikedSongs = emptyList(),
            onlineRecentSongs = emptyList(),
            neteaseCommentsSongId = null,
            neteaseHotComments = emptyList(),
            neteaseLatestComments = emptyList(),
            neteaseCommentsTotal = 0,
            neteaseCommentsHasMore = false,
            neteaseCommentsNextOffset = 0,
            neteaseCommentsNextBeforeTime = 0L,
            neteaseCommentsInitialLoading = false,
            neteaseCommentsRefreshing = false,
            neteaseCommentsLoadingMore = false,
            neteaseCommentsError = null
        )
    }

    fun loadOnlinePlaylistDetail(id: Long) {
        _uiState.value = _uiState.value.copy(onlineLoading = true, onlineError = null, onlinePlaylistDetail = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = museApplication.neteaseClient.playlistDetail(id)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    onlineLoading = false,
                    onlinePlaylistDetail = result.getOrNull(),
                    onlineError = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun clearOnlinePlaylistDetail() {
        _uiState.value = _uiState.value.copy(onlinePlaylistDetail = null, onlineLoading = false, onlineError = null)
    }

    /**
     * Loads the first page of NetEase comments for the supplied remote song.  This is deliberately
     * separate from player settings so a comment gesture never shares a recognizer or state with
     * the playback menu.
     */
    fun loadNeteaseComments(songId: Long, refresh: Boolean = false) {
        if (songId <= 0L) return
        val previous = _uiState.value
        val sameSong = previous.neteaseCommentsSongId == songId
        if (!refresh && sameSong && (
                previous.neteaseCommentsInitialLoading ||
                    previous.neteaseHotComments.isNotEmpty() ||
                    previous.neteaseLatestComments.isNotEmpty()
                )
        ) return

        neteaseCommentsJob?.cancel()
        neteaseCommentsLoadMoreJob?.cancel()
        _uiState.value = previous.copy(
            neteaseCommentsSongId = songId,
            neteaseHotComments = if (sameSong && refresh) previous.neteaseHotComments else emptyList(),
            neteaseLatestComments = if (sameSong && refresh) previous.neteaseLatestComments else emptyList(),
            neteaseCommentsTotal = if (sameSong && refresh) previous.neteaseCommentsTotal else 0,
            neteaseCommentsHasMore = false,
            neteaseCommentsNextOffset = 0,
            neteaseCommentsNextBeforeTime = 0L,
            neteaseCommentsInitialLoading = !sameSong || !refresh,
            neteaseCommentsRefreshing = sameSong && refresh,
            neteaseCommentsLoadingMore = false,
            neteaseCommentsError = null
        )
        neteaseCommentsJob = viewModelScope.launch(Dispatchers.IO) {
            val result = museApplication.neteaseClient.songComments(songId)
            withContext(Dispatchers.Main) {
                val current = _uiState.value
                if (current.neteaseCommentsSongId != songId) return@withContext
                val page = result.getOrNull()
                _uiState.value = current.copy(
                    neteaseHotComments = page?.hotComments ?: current.neteaseHotComments,
                    neteaseLatestComments = page?.recentComments ?: current.neteaseLatestComments,
                    neteaseCommentsTotal = page?.totalCount ?: current.neteaseCommentsTotal,
                    neteaseCommentsHasMore = page?.hasMore ?: false,
                    neteaseCommentsNextOffset = page?.nextOffset ?: 0,
                    neteaseCommentsNextBeforeTime = page?.nextBeforeTime ?: 0L,
                    neteaseCommentsInitialLoading = false,
                    neteaseCommentsRefreshing = false,
                    neteaseCommentsLoadingMore = false,
                    neteaseCommentsError = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun loadMoreNeteaseComments() {
        val current = _uiState.value
        val songId = current.neteaseCommentsSongId ?: return
        if (!current.neteaseCommentsHasMore || current.neteaseCommentsLoadingMore ||
            current.neteaseCommentsInitialLoading || current.neteaseCommentsRefreshing
        ) return

        _uiState.value = current.copy(neteaseCommentsLoadingMore = true, neteaseCommentsError = null)
        neteaseCommentsLoadMoreJob?.cancel()
        neteaseCommentsLoadMoreJob = viewModelScope.launch(Dispatchers.IO) {
            val result = museApplication.neteaseClient.songComments(
                songId = songId,
                offset = current.neteaseCommentsNextOffset,
                beforeTime = current.neteaseCommentsNextBeforeTime
            )
            withContext(Dispatchers.Main) {
                val latest = _uiState.value
                if (latest.neteaseCommentsSongId != songId) return@withContext
                val page = result.getOrNull()
                val merged = if (page != null) {
                    (latest.neteaseLatestComments + page.recentComments).distinctBy(NeteaseComment::id)
                } else latest.neteaseLatestComments
                _uiState.value = latest.copy(
                    neteaseLatestComments = merged,
                    neteaseCommentsTotal = page?.totalCount ?: latest.neteaseCommentsTotal,
                    neteaseCommentsHasMore = page?.hasMore ?: latest.neteaseCommentsHasMore,
                    neteaseCommentsNextOffset = page?.nextOffset ?: latest.neteaseCommentsNextOffset,
                    neteaseCommentsNextBeforeTime = page?.nextBeforeTime ?: latest.neteaseCommentsNextBeforeTime,
                    neteaseCommentsLoadingMore = false,
                    neteaseCommentsError = result.exceptionOrNull()?.message
                )
            }
        }
    }

    suspend fun searchOnlineTracks(query: String, sourceId: String): Result<OnlineSearchResult> {
        val catalog = onlineCatalogs.firstOrNull { it.sourceId == sourceId } ?: onlineCatalog
        return catalog.search(query).map { tracks ->
            OnlineSearchResult(sourceLabel = catalog.displayName, tracks = tracks)
        }.onFailure { error ->
            runCatching {
                val logFile = java.io.File(getApplication<Application>().filesDir, "search_errors.log")
                val stamp = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
                ).format(java.util.Date())
                logFile.appendText(
                    "[$stamp] v${com.caipan.music.BuildConfig.VERSION_NAME} [$sourceId] \"$query\" -> ${error.toString()}\n"
                )
            }
        }
    }

    fun importOnlineSource(url: String) {
        if (_uiState.value.onlineSourceImporting) return
        _uiState.value = _uiState.value.copy(onlineSourceImporting = true, onlineSourceMessage = null)
        viewModelScope.launch {
            val result = onlineSourceManager.importFromUrl(url.trim())
            _uiState.value = _uiState.value.copy(
                onlineSourceImporting = false,
                onlineSources = onlineSourceManager.listSources(),
                onlineSourceMessage = result.fold(
                    onSuccess = { source ->
                        "已导入 ${source.name}（SHA-256 ${source.sha256.take(12)}…），请确认来源后启用"
                    },
                    onFailure = { error -> "导入失败：${error.message ?: "无法下载脚本"}" }
                )
            )
        }
    }

    fun importOnlineSourceFromFile(uri: Uri, fileName: String) {
        if (_uiState.value.onlineSourceImporting) return
        _uiState.value = _uiState.value.copy(onlineSourceImporting = true, onlineSourceMessage = null)
        viewModelScope.launch {
            val result = runCatching {
                val script = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IOException("无法读取文件")
                val name = fileName.substringBeforeLast('.').ifBlank { "本地音源" }
                onlineSourceManager.importFromText(name, script).getOrThrow()
            }
            _uiState.value = _uiState.value.copy(
                onlineSourceImporting = false,
                onlineSources = onlineSourceManager.listSources(),
                onlineSourceMessage = result.fold(
                    onSuccess = { source ->
                        "已导入 ${source.name}（SHA-256 ${source.sha256.take(12)}…），请确认来源后启用"
                    },
                    onFailure = { error -> "导入失败：${error.message ?: "无法读取脚本"}" }
                )
            )
        }
    }

    fun setOnlineSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val result = onlineSourceManager.setEnabled(id, enabled)
            _uiState.value = _uiState.value.copy(
                onlineSources = onlineSourceManager.listSources(),
                onlineSourceMessage = result.fold(
                    onSuccess = { if (enabled) "已启用 ${it.name}" else "已停用 ${it.name}" },
                    onFailure = { "启用失败：${it.message ?: "脚本不兼容"}" }
                )
            )
        }
    }

    fun deleteOnlineSource(id: String) {
        viewModelScope.launch {
            val result = onlineSourceManager.delete(id)
            _uiState.value = _uiState.value.copy(
                onlineSources = onlineSourceManager.listSources(),
                onlineSourceMessage = result.fold(
                    onSuccess = { "已删除在线音源" },
                    onFailure = { "删除失败：${it.message ?: "无法删除脚本"}" }
                )
            )
        }
    }

    fun playOnlineTracks(tracks: List<OnlineTrack>, selected: OnlineTrack) {
        val uniqueTracks = tracks.distinctBy(OnlineTrack::stableId)
        val songs = uniqueTracks.map(OnlineTrack::toSong)
        val index = uniqueTracks.indexOfFirst { it.stableId == selected.stableId }
        if (songs.isNotEmpty() && index in songs.indices) {
            player.setQueue(songs, index)
            _uiState.value = _uiState.value.copy(showPlayer = true)
        }
    }

    fun playOnlinePodcast(podcast: NeteaseHomePodcast) {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = museApplication.neteaseClient.podcastPrograms(podcast.id).getOrNull().orEmpty()
            val first = tracks.firstOrNull() ?: return@launch
            withContext(Dispatchers.Main) { playOnlineTracks(tracks, first) }
        }
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
                if (payload.has("uiStyle")) setUiStyle(resolveUiStyle(payload.getString("uiStyle")))
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

    /** 保存裁剪后的头像（JPEG 压缩落盘）。 */
    fun saveProfileAvatar(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val avatarDir = File(getApplication<Application>().filesDir, "profile_avatars").apply { mkdirs() }
            val target = File(avatarDir, "avatar_${UUID.randomUUID()}.jpg")
            FileOutputStream(target).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            withContext(Dispatchers.Main) {
                prefs().edit().putString("profile_avatar", target.absolutePath).apply()
                _uiState.value = _uiState.value.copy(profileAvatar = Uri.fromFile(target))
            }
        }
    }

    /**
     * 从 MChat 头像 URL 下载并保存为本地档案头像。
     * 下载失败时通过 [onResult] 回调 false（不影响昵称同步与登录态）。
     */
    fun saveProfileAvatarUrl(url: String, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val avatarFile = runCatching {
                val client = OkHttpClient()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val bytes = response.body?.bytes() ?: return@runCatching null
                    val avatarDir = File(getApplication<Application>().filesDir, "profile_avatars").apply { mkdirs() }
                    val target = File(avatarDir, "avatar_${UUID.randomUUID()}.jpg")
                    target.writeBytes(bytes)
                    target
                }
            }.getOrNull()
            if (avatarFile == null) {
                withContext(Dispatchers.Main) { onResult?.invoke(false) }
                return@launch
            }
            withContext(Dispatchers.Main) {
                prefs().edit().putString("profile_avatar", avatarFile.absolutePath).apply()
                _uiState.value = _uiState.value.copy(profileAvatar = Uri.fromFile(avatarFile))
                onResult?.invoke(true)
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
                    // 背景二选一：设置图片壁纸时清除视频背景
                    val oldVideoPath = prefs().getString("video_path", null)
                    _uiState.value = _uiState.value.copy(wallpaperUri = fileUri, videoUri = null)
                    prefs().edit().putString("wallpaper_path", destFile.absolutePath).apply()
                    if (oldVideoPath != null) {
                        File(oldVideoPath).delete()
                        prefs().edit().remove("video_path").apply()
                    }
                    if (oldPath != null && oldPath != destFile.absolutePath) File(oldPath).delete()
                    Log.d("Muse", "Wallpaper saved: ${destFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("Muse", "Failed to save wallpaper", e)
            }
        }
    }

    /** 保存裁剪后的壁纸（JPEG 压缩落盘）。 */
    fun saveWallpaper(bitmap: Bitmap) {
        wallpaperSaveJob?.cancel()
        wallpaperSaveJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            try {
                val destDir = File(ctx.filesDir, "wallpapers").apply { mkdirs() }
                val destFile = File(destDir, "wallpaper_${UUID.randomUUID()}.jpg")
                val tempFile = File(destDir, "${destFile.name}.tmp")
                withContext(Dispatchers.IO) {
                    try {
                        FileOutputStream(tempFile).use { output ->
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                                "Unable to encode wallpaper image"
                            }
                        }
                        check(tempFile.renameTo(destFile)) { "Unable to finalize wallpaper image" }
                    } catch (error: Throwable) {
                        tempFile.delete()
                        throw error
                    }
                }
                if (destFile.exists()) {
                    val oldPath = prefs().getString("wallpaper_path", null)
                    val fileUri = Uri.fromFile(destFile)
                    // 背景二选一：设置图片壁纸时清除视频背景
                    val oldVideoPath = prefs().getString("video_path", null)
                    _uiState.value = _uiState.value.copy(wallpaperUri = fileUri, videoUri = null)
                    prefs().edit().putString("wallpaper_path", destFile.absolutePath).apply()
                    if (oldVideoPath != null) {
                        File(oldVideoPath).delete()
                        prefs().edit().remove("video_path").apply()
                    }
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
                    // 背景二选一：设置视频背景时清除图片壁纸
                    val oldWallpaperPath = prefs().getString("wallpaper_path", null)
                    _uiState.value = _uiState.value.copy(videoUri = fileUri, wallpaperUri = null)
                    prefs().edit().putString("video_path", destFile.absolutePath).apply()
                    if (oldWallpaperPath != null) {
                        File(oldWallpaperPath).delete()
                        prefs().edit().remove("wallpaper_path").apply()
                    }
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

    suspend fun loadLyrics(song: Song): List<LyricLine> {
        if (song.isOnline) {
            val lrc = try {
                val track = song.toOnlineTrack()
                val official = if (track.source == com.caipan.music.online.NeteaseCatalog.NETEASE_SOURCE) {
                    museApplication.neteaseClient.lyrics(track).getOrNull()
                } else null
                official
                    ?: onlineCatalogs.firstOrNull { it.sourceId == track.source && it.supports(MusicCapability.Lyrics) }
                        ?.resolveLyrics(track)
                        ?.getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: onlineSourceManager.resolveLyrics(track).getOrNull()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            return if (lrc.isNullOrBlank()) emptyList() else com.caipan.music.player.LyricsManager.parseLrc(lrc)
        }
        return loadLyrics(song.id)
    }
    fun setSearchQuery(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query) }
    fun refresh() = loadSongs()

    // ── Playlist helpers ──
    fun getAllPlaylists(): List<Playlist> = playlistManager.getAll()

    suspend fun getPlaylistSongs(playlistId: String): List<Song> {
        val playlist = playlistManager.getAll().find { it.id == playlistId } ?: return emptyList()
        if (playlist.songIds.isEmpty()) return emptyList()
        val local = repository.getSongsByIds(playlist.songIds)
        val online = playlist.songPayloads.values.mapNotNull { Song.fromPlaylistPayload(it) }
        val byId = local.associateBy { it.id } + online.associateBy { it.id }
        return playlist.songIds.mapNotNull { byId[it] }
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

    /** 保存裁剪后的歌单封面（JPEG 压缩落盘）。 */
    fun setPlaylistCover(playlistId: String, bitmap: Bitmap, onSaved: (Playlist) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val destDir = File(ctx.filesDir, "playlist_covers").apply { mkdirs() }
                val destFile = File(destDir, "cover_${playlistId}_${UUID.randomUUID()}.jpg")
                withContext(Dispatchers.IO) {
                    FileOutputStream(destFile).use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
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

    /** 双击封面收藏/取消收藏；首次收藏时自动创建收藏歌单。返回 true 表示已收藏。 */
    fun toggleFavorite(song: Song): Boolean {
        val playlists = playlistManager.getAll()
        val existing = playlists.firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
            ?: playlists.firstOrNull { it.name == FAVORITES_PLAYLIST_NAME }
        val favorites = existing ?: Playlist(FAVORITES_PLAYLIST_ID, FAVORITES_PLAYLIST_NAME)
            .also { playlistManager.save(it) }
        val isFavorite = favorites.songIds.contains(song.id)
        if (isFavorite) {
            playlistManager.removeSongs(favorites.id, listOf(song.id))
        } else {
            playlistManager.addSongsWithPayloads(favorites.id, mapOf(song.id to song.toPlaylistPayload()))
        }
        return !isFavorite
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

    /** 随机播放歌单：开启随机模式后从队列头开始播放。 */
    fun playPlaylistShuffled(playlistId: String) {
        viewModelScope.launch {
            val songs = getPlaylistSongs(playlistId)
            if (songs.isNotEmpty()) {
                player.setShuffle(true)
                player.setQueue(songs, 0)
                _uiState.value = _uiState.value.copy(showPlayer = true)
            }
        }
    }

    /** 顺序播放歌单：关闭随机模式后从队列头开始播放。 */
    fun playPlaylistSequential(playlistId: String) {
        viewModelScope.launch {
            val songs = getPlaylistSongs(playlistId)
            if (songs.isNotEmpty()) {
                player.setShuffle(false)
                player.setQueue(songs, 0)
                _uiState.value = _uiState.value.copy(showPlayer = true)
            }
        }
    }

    // ── Playback settings ──
    fun updatePlaybackSettings(transform: (com.caipan.music.player.PlaybackSettings) -> com.caipan.music.player.PlaybackSettings) {
        val old = playbackSettingsStore.state.value
        val new = playbackSettingsStore.update(transform)
        val current = playbackSettingsStore.state.value
        if (current.playbackSpeed != old.playbackSpeed || current.preservePitch != old.preservePitch) {
            player.setPlaybackSpeed(current.playbackSpeed)
        }
        if (current.bassBoostEnabled != old.bassBoostEnabled || current.bassBoostStrength != old.bassBoostStrength) {
            audioEffectsManager.setBassBoost(current.bassBoostEnabled, current.bassBoostStrength)
        }
        if (current.virtualizerEnabled != old.virtualizerEnabled || current.virtualizerStrength != old.virtualizerStrength) {
            audioEffectsManager.setVirtualizer(current.virtualizerEnabled, current.virtualizerStrength)
        }
        if (current.reverbPreset != old.reverbPreset) {
            audioEffectsManager.setReverb(current.reverbPreset)
        }
        if (current.preferredQuality != old.preferredQuality) {
            onlinePreferences.preferredQuality = current.preferredQuality
            // 在线歌曲按新音质重新请求播放（重新 resolve URL）
            if (player.uiState.value.currentSong?.isOnline == true) {
                val requested = current.preferredQuality
                player.replay()
                viewModelScope.launch {
                    // 轮询等解析完成（最多 5 秒），比较实际音质是否降级
                    var actual: String? = null
                    val start = System.currentTimeMillis()
                    while (System.currentTimeMillis() - start < 5_000L) {
                        actual = player.uiState.value.quality
                        if (actual != null) break
                        delay(200L)
                    }
                    if (actual != null && qualityRank(actual) < qualityRank(requested.lxKey)) {
                        _uiState.value = _uiState.value.copy(
                            onlineSourceMessage = "当前音质不可用，已降至 ${qualityLabelText(actual)}"
                        )
                    }
                }
            }
        }
        if (current.sleepTimerMinutes != old.sleepTimerMinutes || current.sleepTimerEndOfSong != old.sleepTimerEndOfSong) {
            startSleepTimer()
        }
        _uiState.value = _uiState.value.copy(playbackSettings = current)
    }

    private fun qualityRank(q: String): Int = when (q.lowercase().trim()) {
        "flac24bit", "hires", "hi-res", "hi_res" -> 4
        "flac", "lossless" -> 3
        "320k", "320", "high" -> 2
        "128k", "128", "standard" -> 1
        else -> 0
    }

    private fun qualityLabelText(q: String): String = when (q.lowercase().trim()) {
        "flac24bit", "hires", "hi-res", "hi_res" -> "Hi-Res"
        "flac", "lossless" -> "无损"
        "320k", "320", "high" -> "高品"
        "128k", "128", "standard" -> "标准"
        else -> q
    }

    // ── Smart playlists ──
    fun getRecentSongs(limit: Int = 100): List<Song> {
        val ids = playHistoryManager.recentSongs(limit)
        val byId = _uiState.value.songs.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun getMostPlayedSongs(limit: Int = 100): List<Song> {
        val ids = playHistoryManager.mostPlayedSongs(limit)
        val byId = _uiState.value.songs.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun getSongPlayCount(songId: Long): Int = playHistoryManager.playCount(songId)

    // ── Sleep timer ──
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndTime: Long = 0

    private fun startSleepTimer() {
        sleepTimerJob?.cancel()
        val settings = playbackSettingsStore.state.value
        if (!settings.sleepTimerActive) {
            sleepTimerEndTime = 0
            _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = 0)
            return
        }
        if (settings.sleepTimerEndOfSong) {
            sleepTimerEndTime = 0
            _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = 0)
            return
        }
        sleepTimerEndTime = System.currentTimeMillis() + settings.sleepTimerMinutes * 60_000L
        sleepTimerJob = viewModelScope.launch {
            while (true) {
                val remaining = sleepTimerEndTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    if (player.uiState.value.isPlaying) player.togglePlay()
                    playbackSettingsStore.clearSleepTimer()
                    _uiState.value = _uiState.value.copy(
                        sleepTimerRemainingMs = 0,
                        playbackSettings = playbackSettingsStore.state.value
                    )
                    sleepTimerEndTime = 0
                    break
                }
                _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = remaining)
                delay(1000L)
            }
        }
    }

    private fun checkSleepTimerEndOfSong() {
        val settings = playbackSettingsStore.state.value
        if (settings.sleepTimerEndOfSong) {
            val state = player.uiState.value
            val remaining = state.durationMs - state.progressMs
            if (remaining in 1..2000L) {
                if (player.uiState.value.isPlaying) player.togglePlay()
                playbackSettingsStore.clearSleepTimer()
                _uiState.value = _uiState.value.copy(
                    sleepTimerRemainingMs = 0,
                    playbackSettings = playbackSettingsStore.state.value
                )
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
                // Record play history when song changes
                if (songId != null && songId != lastSongId) {
                    playHistoryManager.record(songId)
                }
                lastSongId = songId
                lastProgress = state.progressMs
                _uiState.value = _uiState.value.copy(playerState = state)
                // Attach EQ and audio effects when audio session is ready
                if (state.audioSessionId != 0 && state.audioSessionId != lastSessionId) {
                    lastSessionId = state.audioSessionId
                    eqManager.attach(state.audioSessionId)
                    audioEffectsManager.attach(state.audioSessionId)
                }
                // Check sleep timer
                checkSleepTimerEndOfSong()
                delay(250)
            }
        }
    }

    override fun onCleared() { super.onCleared(); eqManager.release(); audioEffectsManager.release() }
}
