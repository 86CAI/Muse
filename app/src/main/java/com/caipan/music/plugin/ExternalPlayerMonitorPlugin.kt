package com.caipan.music.plugin

import android.content.Context
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

data class ExternalPlayerState(
    val packageName: String,
    val appName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val progressMs: Long,
    val isPlaying: Boolean,
    val cachedArtPath: String? = null
)

class ExternalPlayerMonitorPlugin(context: Context) : MusePlugin {
    override val id = "com.caipan.muse.external-player-monitor"
    override val name = "外部播放器监听"
    override val version = "1.0.0"
    override val author = "Muse"
    override val description = "监听系统其他音乐播放器，在 Muse 界面实时显示和控制"
    override val hooks = emptyList<String>()
    override val enabledByDefault = true

    private val appContext = context.applicationContext
    private val artCacheDir = File(appContext.cacheDir, "external_art").also { it.mkdirs() }
    private val _state = MutableStateFlow<ExternalPlayerState?>(null)
    val state: StateFlow<ExternalPlayerState?> = _state.asStateFlow()

    private val controllers = mutableListOf<MediaController>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var lastArtSignature: String? = null
    private var lastCachedArtPath: String? = null
    private var pendingArtSignature: String? = null
    private val progressTicker = object : Runnable {
        override fun run() {
            if (listener != null) {
                if (controllers.isEmpty()) runCatching { rescan() } else refreshState()
                mainHandler.postDelayed(this, 500L)
            }
        }
    }

    fun hasListenerAccess(): Boolean = try {
        val mgr = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mgr.getActiveSessions(ComponentName(appContext, ExternalPlayerNotificationListenerService::class.java))
        true
    } catch (_: SecurityException) {
        false
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(meta: MediaMetadata?) {
            // 播放器经常先更新标题、稍后才更新封面，延迟读取避免把上一首封面缓存给当前歌曲。
            mainHandler.postDelayed({ refreshState(forceArt = true) }, 350L)
            refreshState()
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshState()
        override fun onSessionDestroyed() = rescan()
    }

    override fun onEnable() {
        val mgr = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            controllers.forEach { it.unregisterCallback(callback) }; controllers.clear()
            sessions.orEmpty().forEach { ctrl ->
                if (ctrl.packageName != appContext.packageName) {
                    ctrl.registerCallback(callback); controllers.add(ctrl)
                }
            }
            refreshState()
        }
        val component = ComponentName(appContext, ExternalPlayerNotificationListenerService::class.java)
        runCatching { mgr.addOnActiveSessionsChangedListener(listener!!, component) }
        runCatching { rescan() }
        mainHandler.removeCallbacks(progressTicker)
        mainHandler.post(progressTicker)
    }

    override fun onDisable() {
        val mgr = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        listener?.let { mgr.removeOnActiveSessionsChangedListener(it) }; listener = null
        mainHandler.removeCallbacks(progressTicker)
        controllers.forEach { it.unregisterCallback(callback) }; controllers.clear()
        mainHandler.removeCallbacks(artRetry)
        lastArtSignature = null
        lastCachedArtPath = null
        pendingArtSignature = null
        _state.value = null
    }

    private fun rescan() {
        val mgr = appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        controllers.forEach { it.unregisterCallback(callback) }; controllers.clear()
        val component = ComponentName(appContext, ExternalPlayerNotificationListenerService::class.java)
        val sessions = try {
            mgr.getActiveSessions(component).orEmpty()
        } catch (_: SecurityException) {
            _state.value = null
            return
        }
        sessions.forEach { ctrl ->
            if (ctrl.packageName != appContext.packageName) {
                ctrl.registerCallback(callback); controllers.add(ctrl)
            }
        }
        refreshState()
    }

    private val artRetry = Runnable { refreshState(forceArt = true) }

    private fun refreshState(forceArt: Boolean = false) {
        val ctrl = controllers
            .filter { it.packageName != appContext.packageName && it.metadata != null }
            .maxByOrNull { controller ->
                when (controller.playbackState?.state) {
                    PlaybackState.STATE_PLAYING -> 3
                    PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 2
                    PlaybackState.STATE_PAUSED -> 1
                    else -> 0
                }
            }
            ?: run { _state.value = null; return }
        val meta = ctrl.metadata ?: run { _state.value = null; return }
        val pb = ctrl.playbackState
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: run { _state.value = null; return }
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val artSignature = listOf(
            ctrl.packageName, title, artist, album,
            meta.getLong(MediaMetadata.METADATA_KEY_DURATION).toString()
        ).joinToString("|")
        val signatureChanged = artSignature != lastArtSignature
        // 同一次元数据变化可能同时触发延迟回调与 artRetry。只允许仍处于 pending 的
        // 强制刷新写入封面，避免同一张 bitmap 连续生成两个新路径，导致 Coil 重载闪屏。
        val pendingForcedRefresh = forceArt && pendingArtSignature == artSignature
        if (signatureChanged || pendingForcedRefresh || lastCachedArtPath == null) {
            if (signatureChanged && !forceArt && lastArtSignature != null) {
                // 先等播放器完成元数据与封面切换，避免读取到上一首的 bitmap。
                if (pendingArtSignature != artSignature) {
                    pendingArtSignature = artSignature
                    mainHandler.removeCallbacks(artRetry)
                    mainHandler.postDelayed(artRetry, 350L)
                }
                return
            }
            val newArt = try {
                meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            } catch (_: Exception) { null }

            if (signatureChanged && newArt == null && lastArtSignature != null) {
                // 新元数据经常先到、封面后到。此时保持上一帧完整状态，不发布 cachedArtPath=null，
                // 避免 PlayerScreen 在新封面就绪前切到黑背景。
                if (pendingArtSignature != artSignature) {
                    pendingArtSignature = artSignature
                    mainHandler.removeCallbacks(artRetry)
                    mainHandler.postDelayed(artRetry, 350L)
                }
                return
            }
            if (newArt != null) {
                lastCachedArtPath = cacheArt(ctrl.packageName, newArt)
            } else if (signatureChanged) {
                // 首次监听且播放器确实没有封面时，允许正常显示无封面状态。
                lastCachedArtPath = null
            }
            lastArtSignature = artSignature
            pendingArtSignature = null
            mainHandler.removeCallbacks(artRetry)
        }
        val cached = lastCachedArtPath
        val appName = try {
            val info = appContext.packageManager.getApplicationInfo(ctrl.packageName, 0)
            appContext.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) { ctrl.packageName }

        val durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val isPlaying = pb?.state == PlaybackState.STATE_PLAYING
        val rawProgress = if (isPlaying) {
            val elapsed = (SystemClock.elapsedRealtime() - pb.lastPositionUpdateTime).coerceAtLeast(0L)
            pb.position + elapsed
        } else {
            pb?.position ?: 0L
        }
        _state.value = ExternalPlayerState(
            packageName = ctrl.packageName, appName = appName,
            title = title, artist = artist, album = album,
            durationMs = durationMs,
            progressMs = rawProgress.coerceIn(0L, durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE),
            isPlaying = isPlaying,
            cachedArtPath = cached
        )
    }

    private fun cacheArt(pkg: String, bmp: Bitmap): String {
        val key = "${pkg}_${System.currentTimeMillis()}"
        val file = File(artCacheDir, "${key.hashCode().toLong().toString(36)}.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return file.absolutePath
    }

    private fun activeController() = controllers
        .filter { it.packageName != appContext.packageName }
        .maxByOrNull { if (it.playbackState?.state == PlaybackState.STATE_PLAYING) 1 else 0 }

    /** Muse 开始本地播放时调用，避免本地与外部播放器同时出声。 */
    fun pauseExternalPlayback() {
        activeController()?.let { controller ->
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                runCatching { controller.transportControls.pause() }
            }
        }
    }

    fun togglePlay() {
        val c = activeController() ?: return
        val pb = c.playbackState
        if (pb?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() { activeController()?.transportControls?.skipToNext() }
    fun previous() { activeController()?.transportControls?.skipToPrevious() }
    fun seekTo(posMs: Long) { activeController()?.transportControls?.seekTo(posMs) }
}
