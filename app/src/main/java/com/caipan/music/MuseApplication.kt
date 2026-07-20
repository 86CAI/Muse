package com.caipan.music

import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import com.caipan.music.lan.LanRemoteManager
import com.caipan.music.player.MusicPlayer
import com.caipan.music.plugin.GlobalBlurControlPlugin
import com.caipan.music.plugin.PluginManager
import com.caipan.music.plugin.WeightedShufflePlugin
import com.caipan.music.plugin.ExternalPlayerMonitorPlugin
import com.caipan.music.ui.components.MuseGlassConfigStore
import com.caipan.music.player.RepeatMode
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MuseApplication : Application() {
    val glassConfigStore: MuseGlassConfigStore by lazy { MuseGlassConfigStore(this) }
    val globalBlurControlPlugin: GlobalBlurControlPlugin by lazy { GlobalBlurControlPlugin(this) }
    val externalPlayerMonitorPlugin: ExternalPlayerMonitorPlugin by lazy { ExternalPlayerMonitorPlugin(this) }
    val pluginManager: PluginManager by lazy {
        PluginManager(this).apply {
            register(WeightedShufflePlugin(this@MuseApplication))
            register(globalBlurControlPlugin)
            register(externalPlayerMonitorPlugin)
            installBundledPlugins()
            loadInstalledPlugins()
        }
    }
    val musicPlayer: MusicPlayer by lazy {
        MusicPlayer(this, pluginManager) { externalPlayerMonitorPlugin.pauseExternalPlayback() }
    }
    val lanRemoteManager: LanRemoteManager by lazy { LanRemoteManager(this) }

    override fun onCreate() {
        super.onCreate()
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
        lanRemoteManager.commandHandler = { command, payload ->
            when (command) {
                "play" -> musicPlayer.play()
                "pause" -> if (musicPlayer.uiState.value.isPlaying) musicPlayer.togglePlay()
                "next" -> musicPlayer.next()
                "previous" -> musicPlayer.previous()
                "seek" -> musicPlayer.seekTo(payload.getLong("positionMs").coerceIn(0L, musicPlayer.uiState.value.durationMs.coerceAtLeast(0L)))
                "setShuffle" -> musicPlayer.setShuffle(payload.getBoolean("enabled"))
                "setRepeatMode" -> musicPlayer.setRepeatMode(RepeatMode.valueOf(payload.getString("mode")))
            }
            JSONObject().put("accepted", true)
        }
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

    private fun safeTransferFileName(raw: String): String {
        val clean = raw.replace(Regex("[\\/:*?\"<>|]"), "_").trim().take(180)
        return clean.ifBlank { "Muse Transfer ${System.currentTimeMillis()}.mp3" }
    }
}
