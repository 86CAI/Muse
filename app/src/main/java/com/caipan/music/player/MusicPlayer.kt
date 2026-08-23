package com.caipan.music.player

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.session.MediaButtonReceiver
import com.caipan.music.model.Song
import com.caipan.music.plugin.NextRequest
import com.caipan.music.plugin.NextTrigger
import com.caipan.music.plugin.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RepeatMode { NONE, ALL, ONE }

data class CompletionEvent(val serial: Long = 0, val songId: Long? = null, val wasSingleRepeat: Boolean = false)

data class ResolvedStream(
    val uri: Uri,
    val headers: Map<String, String> = emptyMap(),
    val quality: String? = null
)

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isShuffled: Boolean = false,
    val queue: List<Song> = emptyList(),
    val errorMessage: String? = null,
    val errorSerial: Long = 0,
    val audioSessionId: Int = 0,
    val isLoading: Boolean = false,
    val completionEvent: CompletionEvent = CompletionEvent(),
    val playbackSpeed: Float = 1.0f,
    val quality: String? = null
)

class MusicPlayer(
    context: Context,
    private val pluginManager: PluginManager? = null,
    private val externalPlayerPauser: (() -> Unit)? = null,
    val settingsStore: PlaybackSettingsStore? = null
) {
    private var mediaPlayer: MediaPlayer = MediaPlayer()
    private var crossfadePlayer: MediaPlayer? = null
    private var crossfadeJob: Job? = null
    private var crossfadeIndex = -1
    private var isCrossfading = false
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var songQueue: List<Song> = emptyList()
    private var currentIndex = -1
    private var originalQueue: List<Song> = emptyList()
    private var mediaSession: MediaSessionCompat? = null
    private var isPrepared = false
    private var playRequestId = 0L
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var resolveJob: Job? = null
    private var onlineResolver: (suspend (Song) -> ResolvedStream)? = null

    var onAudioSessionChanged: ((Int) -> Unit)? = null

    init {
        mediaSession = MediaSessionCompat(appContext, "MusePlayer").also { it.isActive = true }

        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { play() }
            override fun onPause() { if (_uiState.value.isPlaying) togglePlay() }
            override fun onSkipToNext() { next(NextTrigger.SYSTEM) }
            override fun onSkipToPrevious() { previous() }
            override fun onSeekTo(pos: Long) { seekTo(pos) }
            override fun onPlayFromMediaId(id: String, extras: Bundle?) {
                val idx = songQueue.indexOfFirst { it.id.toString() == id }
                if (idx >= 0) playAt(idx)
            }
        })

        val ch = android.app.NotificationChannel("muse_playback", "Music Playback",
            android.app.NotificationManager.IMPORTANCE_LOW).apply {
            description = "Music playback controls"; setShowBadge(false) }
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .createNotificationChannel(ch)

        setupCompletionListener(mediaPlayer)

        settingsStore?.state?.let { state ->
            _uiState.value = _uiState.value.copy(playbackSpeed = state.value.playbackSpeed)
        }
    }

    private fun setupCompletionListener(player: MediaPlayer) {
        player.setOnCompletionListener {
            if (isCrossfading) {
                try { player.stop() } catch (_: Exception) {}
                return@setOnCompletionListener
            }
            val completedState = _uiState.value
            completedState.currentSong?.let { pluginManager?.notifyTrackFinished(it) }
            _uiState.value = completedState.copy(completionEvent = CompletionEvent(
                serial = completedState.completionEvent.serial + 1,
                songId = completedState.currentSong?.id,
                wasSingleRepeat = completedState.repeatMode == RepeatMode.ONE
            ))
            when (completedState.repeatMode) {
                RepeatMode.ONE -> if (isPrepared) {
                    mediaPlayer.seekTo(0)
                    mediaPlayer.start()
                }
                RepeatMode.NONE -> advance(NextTrigger.COMPLETION, wrapAtEnd = false)
                RepeatMode.ALL -> advance(NextTrigger.COMPLETION, wrapAtEnd = true)
            }
        }
        player.setOnErrorListener { _, w, e ->
            isPrepared = false
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                errorMessage = "Player error: w=$w e=$e",
                errorSerial = _uiState.value.errorSerial + 1
            )
            true
        }
    }

    private fun applyPlaybackParams(player: MediaPlayer) {
        val speed = settingsStore?.state?.value?.playbackSpeed ?: 1.0f
        val preservePitch = settingsStore?.state?.value?.preservePitch ?: true
        if (speed != 1.0f) {
            try {
                val params = PlaybackParams().setSpeed(speed)
                if (preservePitch) params.setPitch(1.0f)
                player.playbackParams = params
            } catch (_: Exception) {}
        }
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun setPlaybackSpeed(speed: Float) {
        settingsStore?.update { it.copy(playbackSpeed = speed) }
        try {
            val params = PlaybackParams().setSpeed(speed)
            if (settingsStore?.state?.value?.preservePitch != false) params.setPitch(1.0f)
            mediaPlayer.playbackParams = params
            crossfadePlayer?.playbackParams = params
        } catch (_: Exception) {}
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun setPreservePitch(preserve: Boolean) {
        settingsStore?.update { it.copy(preservePitch = preserve) }
        val speed = settingsStore?.state?.value?.playbackSpeed ?: 1.0f
        try {
            val params = PlaybackParams().setSpeed(speed)
            if (preserve) params.setPitch(1.0f)
            mediaPlayer.playbackParams = params
        } catch (_: Exception) {}
    }

    private fun stopPlayback() {
        cancelNotif()
        _uiState.value = _uiState.value.copy(isPlaying = false)
        mediaSession?.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1f).build())
    }

    private fun updateSession(song: Song?) {
        val s = mediaSession ?: return
        if (song == null) { stopPlayback(); return }
        s.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.albumArtUri?.toString())
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)
            .build())

        val state = if (_uiState.value.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP
        s.setPlaybackState(PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, _uiState.value.progressMs, 1f)
            .setActiveQueueItemId(song.id)
            .build())
        showNotification(song)
    }

    private fun showNotification(song: Song) {
        val s = mediaSession ?: return
        val playing = _uiState.value.isPlaying
        val n = NotificationCompat.Builder(appContext, "muse_playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title).setContentText(song.artist)
            .setOngoing(playing)
            .setShowWhen(false)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(s.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .addAction(android.R.drawable.ic_media_previous, "Previous",
                MediaButtonReceiver.buildMediaButtonPendingIntent(appContext, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(appContext,
                    if (playing) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY))
            .addAction(android.R.drawable.ic_media_next, "Next",
                MediaButtonReceiver.buildMediaButtonPendingIntent(appContext, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
        try { NotificationManagerCompat.from(appContext).notify(1, n) } catch (_: SecurityException) {}
    }

    private fun cancelNotif() { try { NotificationManagerCompat.from(appContext).cancel(1) } catch (_: Exception) {} }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        cancelCrossfade()
        originalQueue = songs.toList(); currentIndex = startIndex
        songQueue = if (_uiState.value.isShuffled) pluginManager?.runOnShuffle(originalQueue)
            ?: originalQueue.shuffled() else originalQueue.toList()
        if (_uiState.value.isShuffled) { val c = originalQueue.getOrNull(startIndex); currentIndex = c?.let { songQueue.indexOf(it) }.takeIf { it != -1 } ?: 0 }
        playAt(currentIndex)
    }

    fun setOnlineResolver(resolver: suspend (Song) -> ResolvedStream) {
        onlineResolver = resolver
    }

    /** 重新解析并播放当前歌曲（用于在线音质切换后按新音质重新请求）。 */
    fun replay() {
        if (currentIndex in songQueue.indices) playAt(currentIndex)
    }

    fun play() {
        if (!isPrepared || _uiState.value.currentSong == null) return
        try {
            if (mediaPlayer.isPlaying) return
            externalPlayerPauser?.invoke()
            mediaPlayer.start()
            _uiState.value = _uiState.value.copy(isPlaying = true, errorMessage = null)
            updateSession(_uiState.value.currentSong)
        } catch (e: IllegalStateException) {
            isPrepared = false
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                errorMessage = "Failed: ${e.message}",
                errorSerial = _uiState.value.errorSerial + 1
            )
        }
    }

    private fun playAt(index: Int) {
        cancelCrossfade()
        val song = songQueue.getOrNull(index) ?: return; currentIndex = index
        externalPlayerPauser?.invoke()
        val requestId = ++playRequestId
        try {
            resolveJob?.cancel()
            isPrepared = false
            mediaPlayer.reset()
            _uiState.value = _uiState.value.copy(currentSong = song, isPlaying = false, isLoading = true,
                durationMs = song.durationMs, progressMs = 0, errorMessage = null, queue = songQueue, quality = null)
            if (song.isOnline && song.remoteUri.isNullOrBlank()) {
                val resolver = onlineResolver ?: error("尚未配置在线音源解析器")
                resolveJob = playerScope.launch {
                    val resolved = runCatching { withContext(Dispatchers.IO) { resolver(song) } }
                    if (requestId != playRequestId) return@launch
                    resolved.fold(
                        onSuccess = { prepareDataSource(song, requestId, it) },
                        onFailure = { showPrepareError(song, requestId, it) }
                    )
                }
            } else {
                prepareDataSource(song, requestId, ResolvedStream(song.uri))
            }
        } catch (e: Exception) {
            showPrepareError(song, requestId, e)
        }
    }

    private fun prepareDataSource(song: Song, requestId: Long, stream: ResolvedStream) {
        if (requestId != playRequestId) return
        val scheme = stream.uri.scheme?.lowercase()
        if (song.isOnline && scheme !in setOf("http", "https")) {
            showPrepareError(song, requestId, IllegalArgumentException("音源返回了不受支持的播放地址"))
            return
        }
        try {
            mediaPlayer.setOnPreparedListener { player ->
                if (requestId != playRequestId) return@setOnPreparedListener
                try {
                    isPrepared = true
                    applyPlaybackParams(player)
                    player.start()
                    _uiState.value = _uiState.value.copy(isPlaying = true, isLoading = false,
                        durationMs = player.duration.toLong(), audioSessionId = player.audioSessionId,
                        quality = stream.quality)
                    onAudioSessionChanged?.invoke(player.audioSessionId)
                    updateSession(song)
                } catch (e: IllegalStateException) {
                    isPrepared = false
                    _uiState.value = _uiState.value.copy(isPlaying = false, isLoading = false,
                        errorMessage = "播放失败：${e.message ?: "播放器状态异常"}",
                        errorSerial = _uiState.value.errorSerial + 1)
                }
            }
            val headers = if (song.isOnline) DEFAULT_STREAM_HEADERS + stream.headers else stream.headers
            if (headers.isEmpty()) mediaPlayer.setDataSource(appContext, stream.uri)
            else mediaPlayer.setDataSource(appContext, stream.uri, headers)
            mediaPlayer.prepareAsync()
        } catch (error: Exception) {
            showPrepareError(song, requestId, error)
        }
    }

    private fun showPrepareError(song: Song, requestId: Long, error: Throwable) {
        if (requestId != playRequestId) return
        isPrepared = false
        try { mediaPlayer.reset() } catch (_: Exception) {}
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            isPlaying = false,
            isLoading = false,
            progressMs = 0,
            durationMs = song.durationMs,
            errorMessage = "播放失败：${error.message ?: "无法加载音频"}",
            errorSerial = _uiState.value.errorSerial + 1,
            queue = songQueue
        )
    }

    fun togglePlay() {
        if (!isPrepared || _uiState.value.currentSong == null) return
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                _uiState.value = _uiState.value.copy(isPlaying = false)
            } else {
                mediaPlayer.start()
                _uiState.value = _uiState.value.copy(isPlaying = true, errorMessage = null)
            }
            updateSession(_uiState.value.currentSong)
        } catch (e: IllegalStateException) {
            isPrepared = false
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                errorMessage = "Failed: ${e.message}",
                errorSerial = _uiState.value.errorSerial + 1
            )
        }
    }

    fun next(trigger: NextTrigger = NextTrigger.MANUAL) { advance(trigger, wrapAtEnd = true) }
    private fun advance(trigger: NextTrigger, wrapAtEnd: Boolean) {
        if (songQueue.isEmpty()) return
        val selected = pluginManager?.runOnNextTrack(NextRequest(
            trigger = trigger,
            currentSong = songQueue.getOrNull(currentIndex),
            queue = songQueue.toList(),
            currentIndex = currentIndex
        ))
        val selectedIndex = selected?.let { chosen -> songQueue.indexOfFirst { it.id == chosen.id } } ?: -1
        when {
            selectedIndex >= 0 -> playAt(selectedIndex)
            currentIndex < songQueue.lastIndex -> playAt(currentIndex + 1)
            wrapAtEnd -> playAt(0)
            else -> stopPlayback()
        }
    }
    fun previous() { if (songQueue.isNotEmpty()) playAt(if (currentIndex > 0) currentIndex - 1 else songQueue.lastIndex) }
    fun seekTo(p: Long) {
        if (!isPrepared) return
        try {
            mediaPlayer.seekTo(p.toInt())
            _uiState.value = _uiState.value.copy(progressMs = p)
        } catch (_: IllegalStateException) {
            isPrepared = false
            _uiState.value = _uiState.value.copy(isPlaying = false)
        }
    }
    fun setRepeatMode(m: RepeatMode) { _uiState.value = _uiState.value.copy(repeatMode = m) }
    fun setShuffle(enabled: Boolean) {
        if (_uiState.value.isShuffled != enabled) toggleShuffle()
    }
    fun toggleShuffle() {
        val ns = !_uiState.value.isShuffled
        if (ns) { val c = songQueue.getOrNull(currentIndex); songQueue = pluginManager?.runOnShuffle(originalQueue) ?: originalQueue.shuffled(); currentIndex = c?.let { songQueue.indexOf(it) }.takeIf { it != -1 } ?: 0 }
        else { val c = songQueue.getOrNull(currentIndex); songQueue = originalQueue.toList(); currentIndex = c?.let { songQueue.indexOf(it) }.takeIf { it != -1 } ?: 0 }
        _uiState.value = _uiState.value.copy(isShuffled = ns, queue = songQueue.toList())
        updateSession(_uiState.value.currentSong)
    }

    fun updateProgress() {
        try {
            if (mediaPlayer.isPlaying) {
                val pos = mediaPlayer.currentPosition.toLong()
                val dur = mediaPlayer.duration.toLong()
                _uiState.value = _uiState.value.copy(progressMs = pos, durationMs = dur)
                mediaSession?.let { s ->
                    val pb = s.controller.playbackState ?: return
                    s.setPlaybackState(PlaybackStateCompat.Builder()
                        .setActions(pb.actions)
                        .setState(pb.state, pos, 1f)
                        .setActiveQueueItemId(_uiState.value.currentSong?.id ?: -1)
                        .build())
                }
                checkCrossfade()
            }
        } catch (_: Exception) {}
    }

    private fun checkCrossfade() {
        val settings = settingsStore?.state?.value ?: return
        if (!settings.crossfadeEnabled || isCrossfading || crossfadePlayer != null) return
        if (_uiState.value.repeatMode == RepeatMode.ONE) return
        if (songQueue.size < 2) return
        val remaining = _uiState.value.durationMs - _uiState.value.progressMs
        val crossfadeMs = settings.crossfadeMs.toLong()
        if (remaining > 0 && remaining <= crossfadeMs) {
            startCrossfade(settings.crossfadeMs)
        }
    }

    private fun startCrossfade(durationMs: Int) {
        val nextIndex = when {
            currentIndex < songQueue.lastIndex -> currentIndex + 1
            _uiState.value.repeatMode == RepeatMode.ALL -> 0
            else -> return
        }
        val nextSong = songQueue.getOrNull(nextIndex) ?: return
        isCrossfading = true
        crossfadeIndex = nextIndex
        val cfPlayer = MediaPlayer().also { crossfadePlayer = it }
        setupCompletionListener(cfPlayer)
        val requestId = ++playRequestId

        try {
            cfPlayer.setOnPreparedListener { player ->
                if (requestId != playRequestId || !isCrossfading) {
                    try { player.release() } catch (_: Exception) {}
                    crossfadePlayer = null
                    return@setOnPreparedListener
                }
                applyPlaybackParams(player)
                player.setVolume(0f, 0f)
                player.start()
                runVolumeRamp(mediaPlayer, player, durationMs)
            }
            if (nextSong.isOnline && nextSong.remoteUri.isNullOrBlank()) {
                val resolver = onlineResolver ?: run { cancelCrossfade(); return }
                playerScope.launch {
                    val resolved = runCatching { withContext(Dispatchers.IO) { resolver(nextSong) } }
                    if (requestId != playRequestId || !isCrossfading) {
                        crossfadePlayer?.release(); crossfadePlayer = null; isCrossfading = false
                        return@launch
                    }
                    resolved.fold(
                        onSuccess = { stream ->
                            val scheme = stream.uri.scheme?.lowercase()
                            if (nextSong.isOnline && scheme !in setOf("http", "https")) {
                                cancelCrossfade(); return@fold
                            }
                            try {
                                val headers = if (nextSong.isOnline) DEFAULT_STREAM_HEADERS + stream.headers else stream.headers
                                if (headers.isEmpty()) cfPlayer.setDataSource(appContext, stream.uri)
                                else cfPlayer.setDataSource(appContext, stream.uri, headers)
                                cfPlayer.prepareAsync()
                            } catch (e: Exception) { cancelCrossfade() }
                        },
                        onFailure = { cancelCrossfade() }
                    )
                }
            } else {
                cfPlayer.setDataSource(appContext, nextSong.uri)
                cfPlayer.prepareAsync()
            }
        } catch (e: Exception) {
            cancelCrossfade()
        }
    }

    private fun runVolumeRamp(oldPlayer: MediaPlayer, newPlayer: MediaPlayer, durationMs: Int) {
        crossfadeJob = playerScope.launch {
            var completed = false
            try {
                val steps = (durationMs / 50).coerceAtLeast(2)
                for (i in 1..steps) {
                    val ratio = i.toFloat() / steps
                    try {
                        oldPlayer.setVolume(1f - ratio, 1f - ratio)
                        newPlayer.setVolume(ratio, ratio)
                    } catch (_: Exception) { break }
                    delay(50L)
                }
                completed = true
            } finally {
                if (completed) {
                    // 正常完成：旧播放器静音收尾、新播放器音量确保满格
                    try { oldPlayer.setVolume(0f, 0f) } catch (_: Exception) {}
                    try { newPlayer.setVolume(1f, 1f) } catch (_: Exception) {}
                } else {
                    // 取消或中断：恢复旧播放器满音量，避免声音永久变小
                    try { oldPlayer.setVolume(1f, 1f) } catch (_: Exception) {}
                }
            }
            if (!completed) return@launch
            try { oldPlayer.stop() } catch (_: Exception) {}
            try { oldPlayer.release() } catch (_: Exception) {}
            mediaPlayer = newPlayer
            crossfadePlayer = null
            isCrossfading = false
            currentIndex = crossfadeIndex
            val song = songQueue.getOrNull(currentIndex)
            if (song != null) {
                _uiState.value = _uiState.value.copy(
                    currentSong = song,
                    isPlaying = true,
                    isLoading = false,
                    durationMs = newPlayer.duration.toLong(),
                    audioSessionId = newPlayer.audioSessionId,
                    queue = songQueue
                )
                onAudioSessionChanged?.invoke(newPlayer.audioSessionId)
                updateSession(song)
            }
        }
    }

    private fun cancelCrossfade() {
        crossfadeJob?.cancel(); crossfadeJob = null
        isCrossfading = false
        // 恢复当前播放器满音量，防止淡入淡出中断后声音残留变小
        try { mediaPlayer.setVolume(1f, 1f) } catch (_: Exception) {}
        crossfadePlayer?.let { p ->
            try { p.stop() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
        crossfadePlayer = null
    }

    fun release() {
        cancelCrossfade()
        resolveJob?.cancel()
        playerScope.cancel()
        isPrepared = false
        try { mediaPlayer.release() } catch (_: Exception) {}
        mediaSession?.isActive = false; mediaSession?.release(); mediaSession = null
        cancelNotif()
    }

    private companion object {
        val DEFAULT_STREAM_HEADERS = mapOf(
            "Accept" to "*/*",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Mobile Safari/537.36"
        )
    }
}
