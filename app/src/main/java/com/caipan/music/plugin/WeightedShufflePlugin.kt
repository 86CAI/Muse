package com.caipan.music.plugin

import android.content.Context
import com.caipan.music.model.Song
import kotlin.math.ln
import kotlin.random.Random

class WeightedShufflePlugin(context: Context) : MusePlugin {
    override val id = "com.caipan.music.plugin.weighted-shuffle"
    override val name = "加权随机播放"
    override val version = "1.0.0"
    override val author = "Muse"
    override val description = "播放完成次数越多的歌曲，在随机队列中越容易靠前。"
    override val hooks = listOf("onShuffle", "onTrackFinished")

    private val preferences = context.applicationContext
        .getSharedPreferences("plugin_$id", Context.MODE_PRIVATE)
    private val playCounts = mutableMapOf<Long, Int>()

    override fun onEnable() {
        playCounts.clear()
        preferences.all.forEach { (key, value) ->
            val songId = key.toLongOrNull() ?: return@forEach
            val count = value as? Int ?: return@forEach
            playCounts[songId] = count
        }
    }

    override fun onTrackFinished(song: Song) {
        val count = (playCounts[song.id] ?: 0) + 1
        playCounts[song.id] = count
        preferences.edit().putInt(song.id.toString(), count).apply()
    }

    override fun onShuffle(queue: List<Song>): List<Song>? {
        if (queue.size < 2) return null
        return queue
            .map { song ->
                val weight = ((playCounts[song.id] ?: 0) + 1).toDouble()
                val key = -ln(Random.nextDouble().coerceAtLeast(Double.MIN_VALUE)) / weight
                song to key
            }
            .sortedBy { it.second }
            .map { it.first }
    }
}
