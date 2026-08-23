package com.caipan.music.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupResult(
    val gistUrl: String,
    val gistId: String,
    val playlistCount: Int,
    val settingsKeys: Int = 0
)

data class RestoreResult(
    val playlistCount: Int,
    val settingsKeys: Int
)

/**
 * Muse 完整云端同步：歌单 + 设置 → 单个 Gist（多文件）。
 * 上传：`muse_playlists.json` + `muse_settings.json`
 * 下载：从 Gist 拉取 → 写入本地 PlaylistManager + SharedPreferences
 */
class GitHubGistBackup(
    private val playlistManager: PlaylistManager,
    private val settingsSync: MuseSettingsSync,
    private val oAuthClient: GitHubOAuthClient
) {
    /** 上传歌单和设置到 Gist。 */
    suspend fun syncAll(token: String, existingGistId: String?): BackupResult = withContext(Dispatchers.IO) {
        val playlists = playlistManager.getAll()
        val playlistsJson = serializePlaylists(playlists)
        val settingsJson = settingsSync.collect()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val description = "Muse backup — $dateStr (${playlists.size} playlists)"
        val files = mapOf(
            "muse_playlists.json" to playlistsJson,
            "muse_settings.json" to settingsJson
        )
        val gistUrl = oAuthClient.createGist(token, description, files, existingGistId)
        val gistId = gistUrl.substringAfterLast("/")
        Log.i(TAG, "同步完成: $gistUrl (${playlists.size} 歌单 + ${settingsJson.length} bytes 设置)")
        BackupResult(
            gistUrl = gistUrl,
            gistId = gistId,
            playlistCount = playlists.size,
            settingsKeys = settingsJson.length
        )
    }

    /**
     * 从 Gist 下载并恢复到本地。
     * 歌单写入 PlaylistManager，设置写入 SharedPreferences（需重启 App 生效）。
     */
    suspend fun restoreAll(token: String, gistId: String): RestoreResult = withContext(Dispatchers.IO) {
        val files = oAuthClient.getGist(token, gistId)
        var playlistCount = 0
        var settingsKeys = 0

        // 恢复歌单
        files["muse_playlists.json"]?.let { json ->
            playlistCount = restorePlaylists(json)
        }

        // 恢复设置
        files["muse_settings.json"]?.let { json ->
            settingsKeys = settingsSync.restore(json)
        }

        Log.i(TAG, "恢复完成: $playlistCount 歌单 + $settingsKeys 设置项")
        RestoreResult(playlistCount = playlistCount, settingsKeys = settingsKeys)
    }

    /** 查找已有的 Muse 备份 Gist。 */
    suspend fun findExistingBackupGist(token: String): String? = withContext(Dispatchers.IO) {
        val gists = oAuthClient.listGists(token)
        gists.firstOrNull { (_, desc) ->
            desc.startsWith("Muse ")  // 宽松匹配 "Muse backup" 和 "Muse playlists backup"
        }?.first
    }

    // ── Internal ───────────────────────────────────────────────────

    private fun serializePlaylists(playlists: List<Playlist>): String {
        val arr = JSONArray()
        playlists.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("songIds", JSONArray(p.songIds))
            if (p.coverUri != null) obj.put("coverUri", p.coverUri)
            if (p.songPayloads.isNotEmpty()) {
                val payloadObj = JSONObject()
                p.songPayloads.forEach { (id, payload) -> payloadObj.put(id.toString(), payload) }
                obj.put("songPayloads", payloadObj)
            }
            arr.put(obj)
        }
        return arr.toString(2)
    }

    private fun restorePlaylists(json: String): Int {
        val arr = JSONArray(json)
        var count = 0
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val ids = obj.getJSONArray("songIds")
                val payloads = obj.optJSONObject("songPayloads")
                val playlist = Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    songIds = (0 until ids.length()).map { ids.getLong(it) },
                    coverUri = if (obj.has("coverUri") && !obj.isNull("coverUri")) obj.getString("coverUri") else null,
                    songPayloads = if (payloads != null) {
                        val map = mutableMapOf<Long, String>()
                        payloads.keys().forEach { key -> key.toLongOrNull()?.let { map[it] = payloads.optString(key) } }
                        map
                    } else emptyMap()
                )
                playlistManager.save(playlist)
                count++
            } catch (e: Exception) {
                Log.w(TAG, "恢复歌单失败: ${e.message}")
            }
        }
        return count
    }

    companion object {
        private const val TAG = "GitHubGistBackup"
    }
}