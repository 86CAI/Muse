/*
 * 网易云首页 block 响应解析。
 *
 * Ported from lladlam/MeloX-Android (GPL-3.0)
 * (android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseHomeBlockParser.kt)：
 * block code 判定、中文标题匹配顺序与 resources()/normalizedTitle() 辅助逻辑基本一致，
 * 仅将 DTO 替换为 Muse 自身的 RemotePlaylistSummary / OnlineTrack。
 *
 * Upstream: https://github.com/lladlam/MeloX-Android
 * License: GNU General Public License v3.0 —— 见 licenses/GPL-3.0.txt
 */
package com.caipan.music.online

import org.json.JSONArray
import org.json.JSONObject

/** Parses NetEase's homepage block response. */
internal object NeteaseHomeBlockParser {
    data class Blocks(
        val recommendedPlaylists: List<RemotePlaylistSummary> = emptyList(),
        val recentlyTrending: List<OnlineTrack> = emptyList(),
        val tailoredSongs: List<OnlineTrack> = emptyList(),
        val chartPlaylists: List<RemotePlaylistSummary> = emptyList(),
        val radarPlaylists: List<RemotePlaylistSummary> = emptyList(),
        val personalPlaylists: List<RemotePlaylistSummary> = emptyList(),
        val regionalSongs: List<OnlineTrack> = emptyList(),
        val roamingSongs: List<OnlineTrack> = emptyList(),
        val similarSongs: List<OnlineTrack> = emptyList(),
        val podcasts: List<NeteaseHomePodcast> = emptyList()
    )

    fun parse(response: JSONObject): Blocks {
        val blocks = response.optJSONObject("data")?.optJSONArray("blocks") ?: JSONArray()
        var recommended = emptyList<RemotePlaylistSummary>()
        var recent = emptyList<OnlineTrack>()
        var tailored = emptyList<OnlineTrack>()
        var charts = emptyList<RemotePlaylistSummary>()
        var radar = emptyList<RemotePlaylistSummary>()
        var personal = emptyList<RemotePlaylistSummary>()
        var regional = emptyList<OnlineTrack>()
        var roaming = emptyList<OnlineTrack>()
        var similar = emptyList<OnlineTrack>()
        var podcasts = emptyList<NeteaseHomePodcast>()

        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            val code = block.optString("blockCode").uppercase()
            val title = normalizedTitle(block)
            val resources = resources(block)
            val blockPlaylists = resources.mapNotNull(::parsePlaylist).distinctBy { it.id }
            val blockSongs = resources.mapNotNull(::parseSong).distinctBy { it.sourceId }
            val blockPodcasts = resources.mapNotNull(::parsePodcast).distinctBy { it.id }
            when {
                (code == "HOMEPAGE_BLOCK_PLAYLIST_RCMD" || title == "推荐歌单") && recommended.isEmpty() && blockPlaylists.isNotEmpty() -> recommended = blockPlaylists
                title.contains("近期云村热播") && recent.isEmpty() && blockSongs.isNotEmpty() -> recent = blockSongs
                (code.contains("TOPLIST") || code.contains("RANK") || title == "排行榜") && charts.isEmpty() && blockPlaylists.isNotEmpty() -> charts = blockPlaylists
                (code == "HOMEPAGE_BLOCK_MGC_PLAYLIST" || title.contains("雷达歌单")) && radar.isEmpty() && blockPlaylists.isNotEmpty() -> radar = blockPlaylists
                title.contains("最近的热门歌曲") && regional.isEmpty() && blockSongs.isNotEmpty() -> regional = blockSongs
                title.contains("从你喜欢的歌开始漫游") && roaming.isEmpty() && blockSongs.isNotEmpty() -> roaming = blockSongs
                title.contains("根据你喜爱的歌曲推荐") && similar.isEmpty() && blockSongs.isNotEmpty() -> similar = blockSongs
                (code == "HOMEPAGE_VOICELIST_RCMD" || title.contains("根据你听过的热门节目推荐")) && podcasts.isEmpty() && blockPodcasts.isNotEmpty() -> podcasts = blockPodcasts
                title.endsWith("的歌单") && title != "推荐歌单" && personal.isEmpty() && blockPlaylists.isNotEmpty() -> personal = blockPlaylists
                title.startsWith("根据") && title.endsWith("为你推荐") && tailored.isEmpty() && blockSongs.isNotEmpty() -> tailored = blockSongs
                code == "HOMEPAGE_BLOCK_STYLE_RCMD" && recent.isEmpty() && blockSongs.isNotEmpty() -> recent = blockSongs
            }
        }
        return Blocks(recommended, recent, tailored, charts, radar, personal, regional, roaming, similar, podcasts)
    }

    private fun resources(block: JSONObject): List<JSONObject> = buildList {
        val direct = block.optJSONArray("resources") ?: JSONArray()
        for (index in 0 until direct.length()) direct.optJSONObject(index)?.let(::add)
        val creatives = block.optJSONArray("creatives") ?: JSONArray()
        for (creativeIndex in 0 until creatives.length()) {
            val values = creatives.optJSONObject(creativeIndex)?.optJSONArray("resources") ?: JSONArray()
            for (index in 0 until values.length()) values.optJSONObject(index)?.let(::add)
        }
    }

    private fun normalizedTitle(block: JSONObject): String {
        val ui = block.optJSONObject("uiElement")
        val raw = ui?.optJSONObject("subTitle")?.optString("title").orEmpty().ifBlank {
            ui?.optJSONObject("mainTitle")?.optString("title").orEmpty()
        }.ifBlank {
            val creatives = block.optJSONArray("creatives") ?: JSONArray()
            var found = ""
            for (index in 0 until creatives.length()) {
                found = creatives.optJSONObject(index)?.optJSONObject("uiElement")?.optJSONObject("mainTitle")?.optString("title").orEmpty()
                if (found.isNotBlank()) break
            }
            found
        }
        return raw.filterNot(Char::isWhitespace)
    }

    private fun parsePlaylist(value: JSONObject): RemotePlaylistSummary? {
        val type = value.optString("resourceType").lowercase()
        if (type !in setOf("list", "playlist")) return null
        val id = longValue(value, "resourceId") ?: return null
        val ui = value.optJSONObject("uiElement")
        val ext = value.optJSONObject("resourceExtInfo")
        return RemotePlaylistSummary(
            id = id,
            name = ui?.optJSONObject("mainTitle")?.optString("title").orEmpty().ifBlank { "未命名歌单" },
            coverUrl = normalize(ui?.optJSONObject("image")?.optString("imageUrl")),
            trackCount = ext?.optInt("trackCount", 0)?.coerceAtLeast(0) ?: 0,
            creatorName = ui?.optJSONObject("subTitle")?.optString("title").orEmpty(),
            playCount = ext?.optLong("playCount", 0L)?.coerceAtLeast(0L) ?: 0L,
            description = ui?.optJSONObject("subTitle")?.optString("title")?.takeIf(String::isNotBlank)
        )
    }

    private fun parseSong(value: JSONObject): OnlineTrack? {
        val ext = value.optJSONObject("resourceExtInfo")
        val full = ext?.optJSONObject("songData") ?: ext?.optJSONObject("song")
        if (full != null) return parseFullSong(full)
        if (!value.optString("resourceType").equals("song", true)) return null
        val id = longValue(value, "resourceId") ?: return null
        val ui = value.optJSONObject("uiElement")
        val artists = buildList {
            val values = ext?.optJSONArray("artists") ?: JSONArray()
            for (index in 0 until values.length()) values.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }
        return OnlineTrack(NeteaseCatalog.NETEASE_SOURCE, id.toString(), ui?.optJSONObject("mainTitle")?.optString("title").orEmpty().ifBlank { "未知歌曲" }, artists.ifEmpty { listOf("未知歌手") }, "", 0L, normalize(ui?.optJSONObject("image")?.optString("imageUrl")))
    }

    private fun parseFullSong(value: JSONObject): OnlineTrack? {
        val id = longValue(value, "id") ?: return null
        val artists = buildList {
            val values = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
            for (index in 0 until values.length()) values.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        return OnlineTrack(NeteaseCatalog.NETEASE_SOURCE, id.toString(), value.optString("name").ifBlank { "未知歌曲" }, artists.ifEmpty { listOf("未知歌手") }, album?.optString("name").orEmpty(), value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L), normalize(album?.optString("picUrl") ?: album?.optString("blurPicUrl")))
    }

    private fun parsePodcast(value: JSONObject): NeteaseHomePodcast? {
        val type = value.optString("resourceType").lowercase()
        if (type !in setOf("voice", "program", "dj_program")) return null
        val ui = value.optJSONObject("uiElement")
        val program = value.optJSONObject("resourceExtInfo")?.optJSONObject("djProgram") ?: return null
        val radio = program.optJSONObject("radio") ?: return null
        val id = radio.optLong("id", 0L).takeIf { it > 0L } ?: return null
        return NeteaseHomePodcast(id, radio.optString("name").ifBlank { program.optString("name").ifBlank { ui?.optJSONObject("mainTitle")?.optString("title").orEmpty().ifBlank { "播客" } } }, normalize(program.optString("coverUrl").ifBlank { radio.optString("picUrl").ifBlank { ui?.optJSONObject("image")?.optString("imageUrl") } }))
    }

    private fun longValue(value: JSONObject, key: String): Long? = when (val raw = value.opt(key)) {
        is Number -> raw.toLong().takeIf { it > 0L }
        is String -> raw.toLongOrNull()?.takeIf { it > 0L }
        else -> null
    }

    private fun normalize(value: String?): String? = value?.takeIf(String::isNotBlank)?.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }
}
