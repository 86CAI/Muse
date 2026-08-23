package com.caipan.music.online

import org.json.JSONArray
import org.json.JSONObject

/**
 * A comment returned by NetEase's song-comment resource.
 *
 * [timeMs] is kept alongside the server supplied [timeText] so callers can
 * render dates in the app's current locale without having to parse a display
 * string from the service.
 */
data class NeteaseComment(
    val id: Long,
    val userId: Long,
    val userName: String,
    val avatarUrl: String?,
    val content: String,
    val timeMs: Long,
    val timeText: String,
    val likedCount: Long,
    val likedByCurrentUser: Boolean,
    val replyCount: Int = 0,
    val repliedTo: NeteaseCommentReply? = null,
    val ipLocation: String? = null
)

/** A compact representation of the comment quoted by a reply. */
data class NeteaseCommentReply(
    val id: Long,
    val userId: Long,
    val userName: String,
    val avatarUrl: String?,
    val content: String
)

/**
 * One page of song comments.  NetEase returns hot comments only with the
 * first page; [recentComments] is ordered newest-first for every page.
 *
 * After the service's offset window is exhausted, callers should pass both
 * [nextOffset] and [nextBeforeTime] to [NeteaseOnlineClient.songComments].
 */
data class NeteaseCommentsPage(
    val hotComments: List<NeteaseComment> = emptyList(),
    val recentComments: List<NeteaseComment> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val nextBeforeTime: Long = 0L
) {
    /** Compatibility-friendly shorthand for consumers that call these normal comments. */
    val comments: List<NeteaseComment> get() = recentComments

    /** Cursor to pass as `beforeTime` with the next page request. */
    val beforeTime: Long get() = nextBeforeTime
}

/**
 * JSON parsing for `/api/v1/resource/comments/R_SO_4_{songId}`.
 *
 * Keeping it independent from transport makes the response shape easy to
 * unit-test and avoids leaking `JSONObject` into Compose/UI state.
 */
internal object NeteaseCommentsParser {
    // NetEase accepts offset pagination for an initial window.  MeloX switches
    // to the time cursor at this point, which prevents duplicate/missing pages
    // on very large comment threads.
    private const val OFFSET_WINDOW = 5_000

    fun parsePage(
        payload: JSONObject,
        requestedOffset: Int,
        requestedBeforeTime: Long
    ): NeteaseCommentsPage {
        val safeOffset = requestedOffset.coerceAtLeast(0)
        val recent = parseComments(payload.optJSONArray("comments"))
        val hot = if (safeOffset == 0 && requestedBeforeTime <= 0L) {
            parseComments(payload.optJSONArray("hotComments"))
        } else {
            emptyList()
        }
        val nextOffset = safeOffset + recent.size
        val total = payload.optInt("total", nextOffset).coerceAtLeast(nextOffset)
        val hasMore = payload.optBoolean("more", nextOffset < total) && recent.isNotEmpty()
        val lastCommentTime = recent.lastOrNull()?.timeMs?.takeIf { it > 0L } ?: requestedBeforeTime
        val useTimeCursor = requestedBeforeTime > 0L || nextOffset >= OFFSET_WINDOW

        return NeteaseCommentsPage(
            hotComments = hot,
            recentComments = recent,
            totalCount = total,
            hasMore = hasMore,
            nextOffset = nextOffset,
            nextBeforeTime = if (hasMore && useTimeCursor) lastCommentTime else 0L
        )
    }

    fun parseComments(values: JSONArray?): List<NeteaseComment> = buildList {
        val source = values ?: return@buildList
        val ids = HashSet<Long>(source.length())
        for (index in 0 until source.length()) {
            parseComment(source.optJSONObject(index))?.takeIf { ids.add(it.id) }?.let(::add)
        }
    }

    fun parseComment(value: JSONObject?): NeteaseComment? {
        value ?: return null
        val id = value.longValue("commentId").takeIf { it > 0L }
            ?: value.longValue("id").takeIf { it > 0L }
            ?: return null
        val user = value.optJSONObject("user")
        val replied = value.optJSONArray("beReplied")
        return NeteaseComment(
            id = id,
            userId = user.longValue("userId"),
            userName = user?.optString("nickname").orEmpty().trim().ifBlank { "NetEase user" },
            avatarUrl = normalizeNeteaseImageUrl(user?.optString("avatarUrl")),
            content = value.optString("content").takeIf(String::isNotBlank)
                ?: value.optString("richContent"),
            timeMs = value.longValue("time").coerceAtLeast(0L),
            timeText = value.optString("timeStr").trim(),
            likedCount = value.longValue("likedCount").coerceAtLeast(0L),
            likedByCurrentUser = value.optBoolean("liked", value.optBoolean("favorited", false)),
            replyCount = maxOf(value.optInt("replyCount", 0), replied?.length() ?: 0).coerceAtLeast(0),
            repliedTo = parseReply(replied?.optJSONObject(0)),
            ipLocation = parseIpLocation(value.opt("ipLocation"))
        )
    }

    private fun parseReply(value: JSONObject?): NeteaseCommentReply? {
        value ?: return null
        val user = value.optJSONObject("user")
        val id = value.longValue("beRepliedCommentId").takeIf { it > 0L }
            ?: value.longValue("commentId").takeIf { it > 0L }
            ?: 0L
        return NeteaseCommentReply(
            id = id,
            userId = user.longValue("userId"),
            userName = user?.optString("nickname").orEmpty().trim().ifBlank { "NetEase user" },
            avatarUrl = normalizeNeteaseImageUrl(user?.optString("avatarUrl")),
            content = value.optString("content")
        )
    }

    private fun parseIpLocation(value: Any?): String? = when (value) {
        is JSONObject -> value.optString("location").trim().takeIf(String::isNotBlank)
        is String -> value.trim().takeIf(String::isNotBlank)
        else -> null
    }

    private fun JSONObject?.longValue(key: String): Long {
        if (this == null || !has(key) || isNull(key)) return 0L
        return when (val raw = opt(key)) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
}
