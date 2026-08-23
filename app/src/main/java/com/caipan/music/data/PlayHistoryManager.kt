package com.caipan.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PlayRecord(val songId: Long, val playedAt: Long)

class PlayHistoryManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("muse_history", 0)

    fun record(songId: Long) {
        val records = loadAll().toMutableList()
        records.add(PlayRecord(songId, System.currentTimeMillis()))
        if (records.size > MAX_RECORDS) {
            val recent = records.takeLast(MAX_RECORDS)
            saveAll(recent)
        } else {
            saveAll(records)
        }
    }

    fun recentSongs(limit: Int = 100): List<Long> {
        val seen = mutableSetOf<Long>()
        return loadAll().reversed().mapNotNull { record ->
            if (seen.add(record.songId)) record.songId else null
        }.take(limit)
    }

    fun mostPlayedSongs(limit: Int = 100): List<Long> {
        return loadAll()
            .groupingBy { it.songId }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    fun playCount(songId: Long): Int = loadAll().count { it.songId == songId }

    fun clear() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    private fun loadAll(): List<PlayRecord> {
        val json = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PlayRecord(obj.getLong("id"), obj.getLong("t"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveAll(records: List<PlayRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().put("id", r.songId).put("t", r.playedAt))
        }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    private companion object {
        const val KEY_RECORDS = "records"
        const val MAX_RECORDS = 5000
    }
}
