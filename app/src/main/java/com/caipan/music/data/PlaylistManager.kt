package com.caipan.music.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: String, val name: String, val songIds: List<Long> = emptyList(), val coverUri: String? = null, val songPayloads: Map<Long, String> = emptyMap())

class PlaylistManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("muse_playlists", 0)

    fun getAll(): List<Playlist> {
        val json = prefs.getString("playlists", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val ids = obj.getJSONArray("songIds")
                val payloads = obj.optJSONObject("songPayloads")
                Playlist(
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
            }
        } catch (e: Exception) { emptyList() }
    }

    fun save(playlist: Playlist) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == playlist.id }
        if (idx >= 0) all[idx] = playlist else all.add(playlist)
        writeAll(all)
    }

    fun delete(id: String) {
        writeAll(getAll().filter { it.id != id })
    }

    fun addSongsWithPayloads(playlistId: String, songs: Map<Long, String>) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val existing = all[idx].songIds.toMutableSet()
            existing.addAll(songs.keys)
            all[idx] = all[idx].copy(
                songIds = existing.toList(),
                songPayloads = all[idx].songPayloads + songs
            )
            writeAll(all)
        }
    }

    fun addSongs(playlistId: String, songIds: List<Long>) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val existing = all[idx].songIds.toMutableSet()
            existing.addAll(songIds)
            all[idx] = all[idx].copy(songIds = existing.toList())
            writeAll(all)
        }
    }

    fun removeSongs(playlistId: String, songIds: List<Long>) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(songIds = all[idx].songIds - songIds.toSet(), songPayloads = all[idx].songPayloads - songIds.toSet())
            writeAll(all)
        }
    }

    fun getPlaylistSongs(playlistId: String): List<Long> {
        return getAll().find { it.id == playlistId }?.songIds ?: emptyList()
    }

    fun setCover(playlistId: String, coverUri: String?) {
        val all = getAll().toMutableList()
        val idx = all.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(coverUri = coverUri)
            writeAll(all)
        }
    }

    private fun writeAll(all: List<Playlist>) {
        val arr = JSONArray()
        all.forEach { p ->
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
        prefs.edit().putString("playlists", arr.toString()).apply()
    }
}