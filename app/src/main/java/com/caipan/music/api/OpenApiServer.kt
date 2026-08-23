package com.caipan.music.api

import android.util.Log
import com.caipan.music.model.Song
import com.caipan.music.player.PlayerUiState
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.IOException

/**
 * Muse 开放 API：供 MChat 等第三方 App 读取播放状态并控制播放。
 *
 * - 独立常驻：Muse 启动即监听固定端口 [PORT]，不依赖局域网遥控服务。
 * - 读取匿名：查询端点无需 token，即插即用。
 * - 控制开放：控制端点同样匿名（局域网内即可用），无配对授权。
 * - 跨域开放：响应带 CORS 头，便于网页工具调试。
 *
 * 读取端点（GET）：
 * - GET /api/info         应用与 API 信息
 * - GET /api/health       健康检查
 * - GET /api/now-playing  正在播放完整信息（核心）
 * - GET /api/state        简版播放状态（兼容 LanRemote 风格）
 * - GET /api/stats        听歌统计（听歌总时间、曲库数量等）
 * - GET /api/artwork      专辑封面图片（?albumId=，代理 MediaStore）
 * - GET /docs             开发文档（Markdown）
 *
 * 控制端点（POST，匿名）：
 * - POST /api/play        播放
 * - POST /api/pause       暂停
 * - POST /api/toggle      播放/暂停切换
 * - POST /api/next        下一首
 * - POST /api/previous    上一首
 * - POST /api/seek        跳转到指定进度（body: {"positionMs": 120000}）
 * - POST /api/shuffle     设置随机播放（body: {"enabled": true}）
 * - POST /api/repeat      设置循环模式（body: {"mode": "ALL"}）
 */
class OpenApiServer(
    private val context: android.content.Context,
    private val stateProvider: () -> PlayerUiState,
    private val versionName: String = "",
    private val statsProvider: () -> JSONObject = { JSONObject() },
    private val commandHandler: ((String, JSONObject) -> JSONObject)? = null,
    private val accountProvider: () -> String? = { null }
) : NanoHTTPD(PORT) {

    private val startedAt = System.currentTimeMillis()

    fun startQuietly() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "Muse 开放 API 已启动: http://<设备IP>:$PORT/api/now-playing")
        } catch (e: IOException) {
            Log.w(TAG, "Muse 开放 API 启动失败（端口 $PORT 可能被占用）: ${e.message}")
        }
    }

    override fun serve(session: IHTTPSession): Response {
        // CORS：开放 API 允许跨域读取与控制
        val corsHeaders = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, Authorization"
        )
        if (session.method == Method.OPTIONS) {
            return json(JSONObject().put("code", 200), corsHeaders)
        }

        val path = session.uri.removeSuffix("/")
        return when (session.method) {
            Method.GET -> handleGet(path, session, corsHeaders)
            Method.POST -> handlePost(path, session, corsHeaders)
            else -> json(error(405, "method_not_allowed"), corsHeaders, Response.Status.METHOD_NOT_ALLOWED)
        }
    }

    private fun handleGet(path: String, session: IHTTPSession, corsHeaders: Map<String, String>): Response {
        return when (path) {
            "/api/info" -> json(info(), corsHeaders)
            "/api/health" -> json(health(), corsHeaders)
            "/api/now-playing" -> json(nowPlaying(), corsHeaders)
            "/api/state" -> json(state(), corsHeaders)
            "/api/stats" -> json(stats(), corsHeaders)
            "/api/artwork" -> artwork(session, corsHeaders)
            "/docs" -> docs()
            else -> json(error(404, "not_found"), corsHeaders, Response.Status.NOT_FOUND)
        }
    }

    private fun handlePost(path: String, session: IHTTPSession, corsHeaders: Map<String, String>): Response {
        val command = when (path) {
            "/api/play" -> "play"
            "/api/pause" -> "pause"
            "/api/toggle" -> "toggle"
            "/api/next" -> "next"
            "/api/previous" -> "previous"
            "/api/seek" -> "seek"
            "/api/shuffle" -> "setShuffle"
            "/api/repeat" -> "setRepeatMode"
            else -> return json(error(404, "not_found"), corsHeaders, Response.Status.NOT_FOUND)
        }
        val handler = commandHandler
            ?: return json(error(503, "control_unavailable"), corsHeaders, Response.Status.SERVICE_UNAVAILABLE)
        return try {
            val payload = normalizePayload(command, readBody(session))
            val result = handler(command, payload)
            json(JSONObject().put("code", 200).put("data", result), corsHeaders)
        } catch (e: Exception) {
            json(error(400, e.message ?: "bad_request"), corsHeaders, Response.Status.BAD_REQUEST)
        }
    }

    /** 读取并解析 JSON 请求体（兼容空 body），限制 64KB。 */
    private fun readBody(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        try { session.parseBody(files) } catch (_: Exception) {}
        val raw = files["postData"].orEmpty()
        if (raw.isBlank()) return JSONObject()
        return try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
    }

    /** 规范化 REST 侧参数，使其兼容底层 command 协议。 */
    private fun normalizePayload(command: String, payload: JSONObject): JSONObject {
        when (command) {
            "seek" -> if (!payload.has("positionMs") && payload.has("positionSeconds")) {
                payload.put("positionMs", (payload.getDouble("positionSeconds") * 1000).toLong())
            }
            "setRepeatMode" -> if (payload.has("mode")) {
                payload.put("mode", payload.getString("mode").uppercase())
            }
        }
        return payload
    }

    // ── 端点实现 ──

    private fun info(): JSONObject {
        val app = JSONObject().put("name", "Muse").put("version", versionName)
        val uid = accountProvider()?.takeIf { it.isNotBlank() }
        return JSONObject()
            .put("code", 200)
            .put("data", JSONObject()
                .put("api", "muse-open-api")
                .put("apiVersion", 2)
                .put("app", app)
                .put("uid", uid ?: JSONObject.NULL)
                .put("documentation", "/docs"))
    }

    /** GET /docs — 返回开发文档（Markdown，assets/openapi.md） */
    private fun docs(): Response {
        val content = runCatching {
            context.assets.open("openapi.md").bufferedReader().use { it.readText() }
        }.getOrDefault("# Muse 开放 API\n\n文档未找到。")
        val response = newFixedLengthResponse(
            Response.Status.OK,
            "text/markdown; charset=utf-8",
            content
        )
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun health(): JSONObject {
        return JSONObject()
            .put("code", 200)
            .put("data", JSONObject()
                .put("status", "ok")
                .put("uptimeMs", System.currentTimeMillis() - startedAt))
    }

    /** GET /api/stats — 听歌统计（听歌总时间、曲库数量等） */
    private fun stats(): JSONObject {
        return JSONObject().put("code", 200).put("data", statsProvider())
    }

    /** GET /api/artwork?albumId=<id> — 代理 MediaStore 专辑封面，返回图片字节流 */
    private fun artwork(session: IHTTPSession, corsHeaders: Map<String, String>): Response {
        val albumId = session.parameters["albumId"]?.firstOrNull()?.toLongOrNull()
        if (albumId == null || albumId <= 0) {
            return json(error(400, "missing_album_id"), corsHeaders, Response.Status.BAD_REQUEST)
        }
        val bytes = readAlbumArt(albumId)
        if (bytes == null) {
            return json(error(404, "artwork_not_found"), corsHeaders, Response.Status.NOT_FOUND)
        }
        val response = newFixedLengthResponse(
            Response.Status.OK,
            "image/jpeg",
            java.io.ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
        response.addHeader("Cache-Control", "public, max-age=86400")  // 封面可缓存一天
        corsHeaders.forEach { (k, v) -> response.addHeader(k, v) }
        return response
    }

    /** 从 MediaStore 读取专辑封面（content://media/external/audio/albumart/<albumId>） */
    private fun readAlbumArt(albumId: Long): ByteArray? = runCatching {
        val uri = android.net.Uri.withAppendedPath(
            android.net.Uri.parse("content://media/external/audio/albumart"),
            albumId.toString()
        )
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    private fun nowPlaying(): JSONObject {
        val state = stateProvider()
        val song = state.currentSong
        val data = JSONObject()
            .put("isPlaying", state.isPlaying)
            .put("isLoading", state.isLoading)
            .put("song", song?.let { songJson(it) } ?: JSONObject.NULL)
            .put("progressMs", state.progressMs)
            .put("durationMs", state.durationMs)
            .put("positionSeconds", state.progressMs / 1000)
            .put("durationSeconds", state.durationMs / 1000)
            .put("repeatMode", state.repeatMode.name)
            .put("isShuffled", state.isShuffled)
            .put("playbackSpeed", state.playbackSpeed.toDouble())
            .put("updatedAt", System.currentTimeMillis())
        return JSONObject().put("code", 200).put("data", data)
    }

    private fun state(): JSONObject {
        val state = stateProvider()
        val data = JSONObject()
            .put("isPlaying", state.isPlaying)
            .put("progressMs", state.progressMs)
            .put("durationMs", state.durationMs)
            .put("isShuffled", state.isShuffled)
            .put("repeatMode", state.repeatMode.name)
            .put("currentSong", state.currentSong?.let { song ->
                JSONObject()
                    .put("id", song.id.toString())
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("durationMs", song.durationMs)
            } ?: JSONObject.NULL)
        return JSONObject().put("code", 200).put("data", data)
    }

    private fun songJson(song: Song): JSONObject = JSONObject()
        .put("id", song.id.toString())
        .put("title", song.title)
        .put("artist", song.artist)
        .put("album", song.album)
        .put("durationMs", song.durationMs)
        .put("artworkUrl", artworkUrlFor(song))
        .put("isOnline", song.isOnline)
        .put("source", if (song.isOnline) song.onlineSource else "local")

    /**
     * 封面 URL 策略：
     * - 在线歌曲：直接用真实 artworkUrl（HTTP 可访问）
     * - 本地歌曲：指向本 API 的 /api/artwork?albumId= 代理端点（MediaStore 封面跨应用不可达）
     */
    private fun artworkUrlFor(song: Song): String? {
        song.artworkUrl?.takeIf { it.isNotBlank() }?.let { return it }
        if (song.albumId > 0) return "/api/artwork?albumId=${song.albumId}"
        return null
    }

    private fun error(code: Int, message: String): JSONObject =
        JSONObject().put("code", code).put("error", message)

    private fun json(obj: JSONObject, headers: Map<String, String> = emptyMap(), status: Response.Status = Response.Status.OK): Response {
        val response = newFixedLengthResponse(status, "application/json; charset=utf-8", obj.toString())
        headers.forEach { (k, v) -> response.addHeader(k, v) }
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    companion object {
        private const val TAG = "OpenApiServer"
        /** 固定监听端口：Muse = MusicPlayer */
        const val PORT = 24880
    }
}
