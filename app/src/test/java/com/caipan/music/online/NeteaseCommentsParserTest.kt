package com.caipan.music.online

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseCommentsParserTest {
    @Test
    fun parsesHotAndRecentCommentsWithDisplayMetadata() {
        val page = NeteaseCommentsParser.parsePage(
            JSONObject(
                """
                {
                  "total": 42,
                  "more": true,
                  "hotComments": [{
                    "commentId": 1,
                    "content": "A hot comment",
                    "time": 1700000000000,
                    "timeStr": "2023-11-14",
                    "likedCount": 99,
                    "liked": true,
                    "user": {
                      "userId": 10,
                      "nickname": "Hot user",
                      "avatarUrl": "http://p1.music.126.net/hot.jpg"
                    }
                  }],
                  "comments": [{
                    "commentId": 2,
                    "content": "A recent reply",
                    "time": 1700000001000,
                    "timeStr": "just now",
                    "likedCount": 5,
                    "replyCount": 3,
                    "user": {
                      "userId": 11,
                      "nickname": "Recent user",
                      "avatarUrl": "//p2.music.126.net/recent.jpg"
                    },
                    "beReplied": [{
                      "beRepliedCommentId": 1,
                      "content": "Original comment",
                      "user": { "userId": 10, "nickname": "Hot user" }
                    }],
                    "ipLocation": { "location": "Shanghai" }
                  }]
                }
                """.trimIndent()
            ),
            requestedOffset = 0,
            requestedBeforeTime = 0L
        )

        assertEquals(1, page.hotComments.size)
        assertEquals(1, page.recentComments.size)
        assertEquals(42, page.totalCount)
        assertTrue(page.hasMore)
        assertEquals(1, page.nextOffset)
        assertEquals(0L, page.nextBeforeTime)

        val hot = page.hotComments.single()
        assertEquals("https://p1.music.126.net/hot.jpg", hot.avatarUrl)
        assertTrue(hot.likedByCurrentUser)
        val recent = page.recentComments.single()
        assertEquals("https://p2.music.126.net/recent.jpg", recent.avatarUrl)
        assertEquals(3, recent.replyCount)
        assertEquals("Shanghai", recent.ipLocation)
        assertNotNull(recent.repliedTo)
        assertEquals(1L, recent.repliedTo!!.id)
    }

    @Test
    fun usesTimeCursorForLargeCommentThreadsAndSkipsHotCommentsOnLaterPages() {
        val page = NeteaseCommentsParser.parsePage(
            JSONObject(
                """{
                    "total": 8000,
                    "more": true,
                    "hotComments": [{"commentId": 1, "user": {"nickname": "Ignored"}}],
                    "comments": [{
                      "commentId": 5000,
                      "content": "Last in offset window",
                      "time": 1700000002000,
                      "user": {"nickname": "Cursor user"}
                    }]
                }"""
            ),
            requestedOffset = 4_999,
            requestedBeforeTime = 0L
        )

        assertTrue(page.hasMore)
        assertEquals(5_000, page.nextOffset)
        assertEquals(1_700_000_002_000L, page.nextBeforeTime)
        assertTrue(page.hotComments.isEmpty())
        assertFalse(page.comments.isEmpty())
    }
}
