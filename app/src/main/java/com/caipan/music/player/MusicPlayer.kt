package com.caipan.music.player

import android.content.Context
import android.media.MediaPlayer
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode { NONE, ALL, ONE }

data class CompletionEvent(val serial: Long = 0, val songId: Long? = null, val wasSingleRepeat: Boolean = false)

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isShuffled: Boolean = false,
    val queue: List<Song> = emptyList(),
    val errorMessage: String? = null,
    val audioSessionId: Int = 0
    ,val isLoading: Boolean = false,
    val completionEvent: CompletionEvent = CompletionEvent()
)

class MusicPlayer(context: Context, private val pluginManager: PluginManager? = null,
    private val externalPlayerPauser: (() -> Unit)? = null) {
    private val mediaPlayer = MediaPlayer()
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var songQueue: List<Song> = emptyList()
    private var currentIndex = -1
    private var originalQueue: List<Song> = emptyList()
    private var mediaSession: MediaSessionCompat? = null
    private var isPrepared = false
    private var playRequestId = 0L

    init {
        mediaSession = MediaSessionCompat(appContext, "MusePlayer").also { it.isActive = true }

        // Handle external control commands
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

        mediaPlayer.setOnCompletionListener {
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
        mediaPlayer.setOnErrorListener { _, w, e ->
            isPrepared = false
            _uiState.value = _uiState.value.copy(isPlaying = false, errorMessage = "Player error: w=$w e=$e")
            true
        }
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
        originalQueue = songs.toList(); currentIndex = startIndex
        songQueue = if (_uiState.value.isShuffled) pluginManager?.runOnShuffle(originalQueue)
            ?: originalQueue.shuffled() else originalQueue.toList()
        if (_uiState.value.isShuffled) { val c = originalQueue.getOrNull(startIndex); currentIndex = c?.let { songQueue.indexOf(it) }.takeIf { it != -1 } ?: 0 }
        playAt(currentIndex)
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
            _uiState.value = _uiState.value.copy(isPlaying = false, errorMessage = "Failed: ${e.message}")
        }
    }

    private fun playAt(index: Int) {
        val song = songQueue.getOrNull(index) ?: return; currentIndex = index
        externalPlayerPauser?.invoke()
        val requestId = ++playRequestId
        try {
            isPrepared = false
            mediaPlayer.reset()
            _uiState.value = _uiState.value.copy(currentSong = song, isPlaying = false, isLoading = true,
                durationMs = song.durationMs, progressMs = 0, errorMessage = null, queue = songQueue)
            mediaPlayer.setOnPreparedListener { player ->
                if (requestId != playRequestId) return@setOnPreparedListener
                try {
                    isPrepared = true
                    player.start()
                    _uiState.value = _uiState.value.copy(isPlaying = true, isLoading = false,
                        durationMs = player.duration.toLong(), audioSessionId = player.audioSessionId)
                    updateSession(song)
                } catch (e: IllegalStateException) {
                    isPrepared = false
                    _uiState.value = _uiState.value.copy(isPlaying = false, isLoading = false,
                        errorMessage = "Failed: ${e.message}")
                }
            }
            mediaPlayer.setDataSource(appContext, song.uri)
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            isPrepared = false
            try { mediaPlayer.reset() } catch (_: Exception) {}
            _uiState.value = _uiState.value.copy(currentSong = song, isPlaying = false, isLoading = false,
                progressMs = 0, durationMs = song.durationMs, errorMessage = "Failed: ${e.message}", queue = songQueue)
        }
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
            _uiState.value = _uiState.value.copy(isPlaying = false, errorMessage = "Failed: ${e.message}")
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
                // Update session position for system UI
                mediaSession?.let { s ->
                    val pb = s.controller.playbackState ?: return
                    s.setPlaybackState(PlaybackStateCompat.Builder()
                        .setActions(pb.actions)
                        .setState(pb.state, pos, 1f)
                        .setActiveQueueItemId(_uiState.value.currentSong?.id ?: -1)
                        .build())
                }
            }
        } catch (_: Exception) {}
    }

    fun release() {
        isPrepared = false
        mediaPlayer.release()
        mediaSession?.isActive = false; mediaSession?.release(); mediaSession = null
        cancelNotif()
    }
}
