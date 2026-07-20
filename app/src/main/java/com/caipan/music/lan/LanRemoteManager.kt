package com.caipan.music.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import com.caipan.music.model.Song

private const val TAG = "MuseLanRemote"
private const val SERVICE_TYPE = "_muse-remote._tcp."
private const val PROTOCOL_VERSION = 1
private const val PAIR_CODE_LIFETIME_MS = 3 * 60_000L
private const val MAX_TRANSFER_BYTES = 2L * 1024 * 1024 * 1024

data class LanDiscoveredDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val paired: Boolean
)

data class LanPairedDevice(val id: String, val name: String, val host: String, val port: Int)

data class LanRemoteState(
    val hosting: Boolean = false,
    val port: Int = 0,
    val pairingCode: String? = null,
    val pairingExpiresAt: Long = 0,
    val discovering: Boolean = false,
    val discovered: List<LanDiscoveredDevice> = emptyList(),
    val pairedClients: List<Pair<String, String>> = emptyList(),
    val pairedDevices: List<LanPairedDevice> = emptyList(),
    val message: String? = null
)

/** Muse 专用 LAN 通道。插件不能提供 URL、IP、端口或认证头。 */
class LanRemoteManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("muse_lan_remote", Context.MODE_PRIVATE)
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val random = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val deviceId = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device_id", it).apply()
    }
    private val deviceName: String
        get() = Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME)
            ?.take(48)?.ifBlank { null } ?: "Muse ${Build.MODEL.take(24)}"

    private val _state = MutableStateFlow(LanRemoteState())
    val state: StateFlow<LanRemoteState> = _state.asStateFlow()
    private var server: MuseHttpServer? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var pairingCode: String? = null
    private var pairingExpiresAt = 0L
    private var pairingFailures = 0

    /** 由宿主绑定；返回播放器公开状态。 */
    @Volatile var stateProvider: (() -> JSONObject)? = null
    /** 由宿主绑定；只接受固定枚举命令。 */
    @Volatile var commandHandler: ((String, JSONObject) -> JSONObject)? = null
    /** 由宿主绑定；返回 key 到接收端本地歌曲 ID 的匹配结果。 */
    @Volatile var transferPrepareHandler: ((JSONArray) -> JSONObject)? = null
    /** 由宿主绑定；将认证后的歌曲流导入媒体库并返回歌曲 ID。 */
    @Volatile var transferImportHandler: ((JSONObject, InputStream) -> Long)? = null
    /** 由宿主绑定；使用接收端歌曲 ID 重建队列并恢复播放。 */
    @Volatile var transferCommitHandler: ((List<Long>, JSONObject) -> Unit)? = null
    private val incomingTransfers = ConcurrentHashMap<String, IncomingTransfer>()

    init { refreshState() }

    fun startHosting(): Result<Unit> = runCatching {
        if (server != null) return@runCatching
        val created = MuseHttpServer().apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        server = created
        registerService(created.listeningPort)
        prefs.edit().putBoolean("hosting", true).apply()
        refreshState("已允许局域网中的已配对 Muse 控制此设备")
    }.onFailure { refreshState("启动失败：${it.message}") }

    fun stopHosting() {
        registrationListener?.let { runCatching { nsd.unregisterService(it) } }
        registrationListener = null
        server?.stop(); server = null
        pairingCode = null; pairingExpiresAt = 0
        prefs.edit().putBoolean("hosting", false).apply()
        refreshState("LAN Remote 已关闭")
    }

    fun generatePairingCode(): String {
        check(server != null) { "请先开启 LAN Remote" }
        pairingCode = random.nextInt(1_000_000).toString().padStart(6, '0')
        pairingExpiresAt = System.currentTimeMillis() + PAIR_CODE_LIFETIME_MS
        pairingFailures = 0
        refreshState()
        return pairingCode!!
    }

    fun startDiscovery(): Result<Unit> = runCatching {
        if (discoveryListener != null) return@runCatching
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) { refreshState() }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType != SERVICE_TYPE || info.serviceName.endsWith(deviceId.take(8))) return
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        val id = resolved.attributes["id"]?.toString(Charsets.UTF_8) ?: return
                        val name = resolved.attributes["name"]?.toString(Charsets.UTF_8) ?: resolved.serviceName
                        val item = LanDiscoveredDevice(id, name, host, resolved.port, pairedDevice(id) != null)
                        _state.value = _state.value.copy(discovered = (_state.value.discovered.filterNot { it.id == id } + item).sortedBy { it.name })
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _state.value = _state.value.copy(discovered = _state.value.discovered.filterNot { info.serviceName.contains(it.id.take(8)) })
            }
            override fun onDiscoveryStopped(type: String) { discoveryListener = null; refreshState() }
            override fun onStartDiscoveryFailed(type: String, code: Int) { discoveryListener = null; refreshState("发现启动失败：$code") }
            override fun onStopDiscoveryFailed(type: String, code: Int) { discoveryListener = null; refreshState("停止发现失败：$code") }
        }
        discoveryListener = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        refreshState()
    }.onFailure { discoveryListener = null; refreshState("发现失败：${it.message}") }

    fun stopDiscovery() {
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discoveryListener = null
        refreshState()
    }

    suspend fun pair(deviceId: String, code: String, clientName: String = deviceName): Result<JSONObject> = runCatching {
        require(Regex("^\\d{6}$").matches(code)) { "配对码必须为 6 位数字" }
        val target = discovered(deviceId)
        val payload = JSONObject().put("code", code).put("clientId", this.deviceId).put("clientName", clientName.take(48))
        val result = request(target.host, target.port, "/v1/pair", payload, null)
        val token = result.getString("token")
        savePairedDevice(target.id, target.name, target.host, target.port, token)
        refreshState("已与 ${target.name} 配对")
        JSONObject().put("device", deviceJson(pairedDevice(target.id)!!))
    }

    suspend fun getRemoteState(deviceId: String): Result<JSONObject> = runCatching {
        val target = requirePairedDevice(deviceId)
        request(target.host, target.port, "/v1/state", JSONObject(), tokenFor(deviceId))
    }

    suspend fun command(deviceId: String, command: String, payload: JSONObject): Result<JSONObject> = runCatching {
        require(command in setOf("play", "pause", "next", "previous", "seek", "setShuffle", "setRepeatMode")) { "不支持的远程命令" }
        val target = requirePairedDevice(deviceId)
        request(target.host, target.port, "/v1/command", JSONObject().put("command", command).put("payload", payload), tokenFor(deviceId))
    }

    suspend fun transferPlayback(
        deviceId: String,
        queue: List<Song>,
        currentIndex: Int,
        progressMs: Long,
        isPlaying: Boolean,
        isShuffled: Boolean,
        repeatMode: String
    ): Result<JSONObject> = runCatching {
        require(queue.isNotEmpty() && queue.size <= 500) { "流转队列必须包含 1 到 500 首歌曲" }
        require(currentIndex in queue.indices) { "当前歌曲索引无效" }
        val target = requirePairedDevice(deviceId)
        val songs = JSONArray(queue.mapIndexed { index, song -> transferSongJson(index.toString(), song) })
        val prepared = request(target.host, target.port, "/v1/transfer/prepare",
            JSONObject().put("songs", songs), tokenFor(deviceId))
        val transferId = prepared.getString("transferId")
        val missing = prepared.getJSONArray("missing")
        for (index in 0 until missing.length()) {
            val key = missing.getString(index)
            val song = queue.getOrNull(key.toIntOrNull() ?: -1) ?: error("接收端返回了无效歌曲")
            uploadSong(target, transferId, key, song, tokenFor(deviceId))
        }
        val state = JSONObject().put("currentIndex", currentIndex).put("progressMs", progressMs)
            .put("isPlaying", isPlaying).put("isShuffled", isShuffled).put("repeatMode", repeatMode)
        request(target.host, target.port, "/v1/transfer/commit",
            JSONObject().put("transferId", transferId).put("state", state), tokenFor(deviceId))
    }

    fun forgetDevice(deviceId: String) {
        prefs.edit().remove("remote_$deviceId").apply(); refreshState("已忘记设备")
    }

    fun revokeClient(clientId: String) {
        prefs.edit().remove("client_$clientId").apply(); refreshState("已撤销控制授权")
    }

    fun discoveredJson(): JSONObject = JSONObject().put("devices", JSONArray(_state.value.discovered.map {
        JSONObject().put("id", it.id).put("name", it.name).put("paired", it.paired)
    }))

    fun pairedJson(): JSONObject = JSONObject().put("devices", JSONArray(loadPairedDevices().map(::deviceJson)))

    fun localStateJson(): JSONObject = _state.value.let { state ->
        JSONObject().put("hosting", state.hosting).put("port", state.port)
            .put("pairingCode", state.pairingCode ?: JSONObject.NULL)
            .put("pairingExpiresAt", state.pairingExpiresAt)
            .put("discovering", state.discovering)
            .put("message", state.message ?: JSONObject.NULL)
            .put("clients", JSONArray(state.pairedClients.map { (id, name) ->
                JSONObject().put("id", id).put("name", name)
            }))
    }

    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "Muse-${deviceName.take(20)}-${deviceId.take(8)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("id", deviceId)
            setAttribute("name", deviceName)
            setAttribute("v", PROTOCOL_VERSION.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) { refreshState() }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { refreshState("服务广播失败：$errorCode") }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private inner class MuseHttpServer : NanoHTTPD(0) {
        override fun serve(session: IHTTPSession): Response {
            return try {
                if (session.uri.startsWith("/v1/transfer/upload/")) {
                    authenticate(session)
                    return json(200, handleTransferUpload(session))
                }
                val body = readBody(session)
                when (session.uri) {
                    "/v1/info" -> json(200, JSONObject().put("id", deviceId).put("name", deviceName).put("version", PROTOCOL_VERSION))
                    "/v1/pair" -> handlePair(body, session.remoteIpAddress)
                    "/v1/state" -> {
                        authenticate(session)
                        json(200, stateProvider?.invoke() ?: error("播放器尚未就绪"))
                    }
                    "/v1/command" -> {
                        authenticate(session)
                        val command = body.getString("command")
                        require(command in setOf("play", "pause", "next", "previous", "seek", "setShuffle", "setRepeatMode")) { "不支持的命令" }
                        val result = runBlocking { withContext(Dispatchers.Main) { commandHandler?.invoke(command, body.optJSONObject("payload") ?: JSONObject()) ?: error("播放器尚未就绪") } }
                        json(200, result)
                    }
                    "/v1/transfer/prepare" -> {
                        authenticate(session)
                        json(200, handleTransferPrepare(body))
                    }
                    "/v1/transfer/commit" -> {
                        authenticate(session)
                        json(200, handleTransferCommit(body))
                    }
                    else -> json(404, JSONObject().put("error", "not_found"))
                }
            } catch (e: SecurityException) { json(401, JSONObject().put("error", e.message ?: "unauthorized"))
            } catch (e: Exception) { Log.w(TAG, "LAN request failed", e); json(400, JSONObject().put("error", e.message ?: "bad_request")) }
        }
    }

    private fun handleTransferPrepare(body: JSONObject): JSONObject {
        val songs = body.getJSONArray("songs")
        require(songs.length() in 1..500) { "流转队列无效" }
        val metadata = linkedMapOf<String, JSONObject>()
        for (index in 0 until songs.length()) {
            val song = songs.getJSONObject(index)
            val key = song.getString("key")
            require(key.length <= 32 && key !in metadata) { "歌曲标识无效" }
            require(song.getLong("sizeBytes") in 1..MAX_TRANSFER_BYTES) { "歌曲文件大小无效" }
            metadata[key] = song
        }
        val matches = transferPrepareHandler?.invoke(songs) ?: error("流转接收端尚未就绪")
        val localIds = mutableMapOf<String, Long>()
        matches.keys().forEach { key ->
            if (key in metadata) localIds[key] = matches.getLong(key)
        }
        val transferId = UUID.randomUUID().toString()
        incomingTransfers[transferId] = IncomingTransfer(metadata, localIds, System.currentTimeMillis())
        removeExpiredTransfers()
        return JSONObject().put("transferId", transferId)
            .put("missing", JSONArray(metadata.keys.filterNot { it in localIds }))
    }

    private fun handleTransferUpload(session: NanoHTTPD.IHTTPSession): JSONObject {
        require(session.method == NanoHTTPD.Method.POST) { "上传必须使用 POST" }
        val parts = session.uri.removePrefix("/v1/transfer/upload/").split('/')
        require(parts.size == 2) { "上传路径无效" }
        val transfer = incomingTransfers[parts[0]] ?: error("流转会话不存在或已过期")
        val metadata = transfer.metadata[parts[1]] ?: error("歌曲不属于该流转会话")
        require(parts[1] !in transfer.localIds) { "歌曲已经上传" }
        val expected = metadata.getLong("sizeBytes")
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: error("缺少文件大小")
        require(contentLength == expected && contentLength in 1..MAX_TRANSFER_BYTES) { "歌曲文件大小不匹配" }
        val bounded = ExactLengthInputStream(session.inputStream, expected)
        val id = transferImportHandler?.invoke(metadata, bounded) ?: error("流转导入端尚未就绪")
        require(bounded.remaining == 0L) { "歌曲数据不完整" }
        transfer.localIds[parts[1]] = id
        return JSONObject().put("received", true)
    }

    private fun handleTransferCommit(body: JSONObject): JSONObject {
        val transferId = body.getString("transferId")
        val transfer = incomingTransfers.remove(transferId) ?: error("流转会话不存在或已过期")
        val ids = transfer.metadata.keys.map { transfer.localIds[it] ?: error("歌曲尚未完整上传") }
        transferCommitHandler?.invoke(ids, body.getJSONObject("state")) ?: error("流转播放端尚未就绪")
        return JSONObject().put("committed", true)
    }

    private fun handlePair(body: JSONObject, remoteIp: String?): NanoHTTPD.Response {
        val expected = pairingCode
        if (expected == null || System.currentTimeMillis() >= pairingExpiresAt) throw SecurityException("配对码已过期")
        if (++pairingFailures > 8) { pairingCode = null; throw SecurityException("尝试次数过多") }
        if (!MessageDigest.isEqual(expected.toByteArray(), body.getString("code").toByteArray())) throw SecurityException("配对码错误")
        val clientId = body.getString("clientId").take(128)
        val clientName = body.optString("clientName", "Muse device").take(48)
        val token = randomBytes(32)
        prefs.edit().putString("client_$clientId", JSONObject().put("name", clientName).put("tokenHash", sha256(token)).put("ip", remoteIp).toString()).apply()
        pairingCode = null; pairingExpiresAt = 0; pairingFailures = 0; refreshState("已授权 $clientName")
        return json(200, JSONObject().put("token", token).put("deviceId", deviceId).put("deviceName", deviceName))
    }

    private fun authenticate(session: NanoHTTPD.IHTTPSession) {
        val token = session.headers["authorization"]?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() } ?: throw SecurityException("缺少认证")
        val hash = sha256(token)
        val valid = prefs.all.filterKeys { it.startsWith("client_") }.values.any { raw ->
            runCatching { MessageDigest.isEqual(JSONObject(raw as String).getString("tokenHash").toByteArray(), hash.toByteArray()) }.getOrDefault(false)
        }
        if (!valid) throw SecurityException("认证无效")
    }

    private fun readBody(session: NanoHTTPD.IHTTPSession): JSONObject {
        if (session.method == NanoHTTPD.Method.GET) return JSONObject()
        require(session.method == NanoHTTPD.Method.POST) { "仅支持 GET/POST" }
        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"].orEmpty()
        require(raw.length <= 64 * 1024) { "请求体过大" }
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun json(code: Int, body: JSONObject): NanoHTTPD.Response = NanoHTTPD.newFixedLengthResponse(
        when (code) { 200 -> NanoHTTPD.Response.Status.OK; 400 -> NanoHTTPD.Response.Status.BAD_REQUEST; 401 -> NanoHTTPD.Response.Status.UNAUTHORIZED; else -> NanoHTTPD.Response.Status.NOT_FOUND },
        "application/json; charset=utf-8", body.toString()
    ).apply { addHeader("Cache-Control", "no-store") }

    private suspend fun request(host: String, port: Int, path: String, body: JSONObject, token: String?): JSONObject = withContext(Dispatchers.IO) {
        val address = InetAddress.getByName(host)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) { "目标不再位于局域网" }
        val urlHost = if (host.contains(':')) "[$host]" else host
        val request = Request.Builder().url("http://$urlHost:$port$path")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply { if (token != null) header("Authorization", "Bearer $token") }.build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty().take(256 * 1024)
            val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (!response.isSuccessful) error(json.optString("error", "远程请求失败 (${response.code})"))
            json
        }
    }

    private suspend fun uploadSong(target: LanPairedDevice, transferId: String, key: String, song: Song, token: String) = withContext(Dispatchers.IO) {
        val address = InetAddress.getByName(target.host)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress) { "目标不再位于局域网" }
        val urlHost = if (target.host.contains(':')) "[${target.host}]" else target.host
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = song.sizeBytes
            override fun writeTo(sink: okio.BufferedSink) {
                appContext.contentResolver.openInputStream(song.uri)?.use { input -> sink.writeAll(input.source()) }
                    ?: error("无法读取 ${song.title}")
            }
        }
        val request = Request.Builder().url("http://$urlHost:${target.port}/v1/transfer/upload/$transferId/$key")
            .header("Authorization", "Bearer $token").post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty().take(256 * 1024)
            if (!response.isSuccessful) error(runCatching { JSONObject(raw).optString("error") }.getOrNull().orEmpty().ifBlank { "歌曲上传失败 (${response.code})" })
        }
    }

    private fun transferSongJson(key: String, song: Song) = JSONObject().put("key", key)
        .put("title", song.title).put("artist", song.artist).put("album", song.album)
        .put("durationMs", song.durationMs).put("mimeType", song.mimeType.ifBlank { "audio/mpeg" })
        .put("sizeBytes", song.sizeBytes).put("fileName", song.fileName.ifBlank { "${song.title}.mp3" }.take(180))
        .apply {
            val basePath = song.filePath.substringBeforeLast('.', song.filePath)
            val lyrics = sequenceOf(java.io.File("$basePath.lrc"), java.io.File("$basePath.LRC"))
                .firstOrNull { it.isFile && it.length() in 1..128_000 }
                ?.runCatching { readText() }?.getOrNull()
            if (!lyrics.isNullOrBlank()) put("lyrics", lyrics)
        }

    private fun removeExpiredTransfers() {
        val cutoff = System.currentTimeMillis() - 15 * 60_000L
        incomingTransfers.entries.removeIf { it.value.createdAt < cutoff }
    }

    private fun discovered(id: String) = _state.value.discovered.firstOrNull { it.id == id } ?: error("设备未发现或已离线")
    private fun requirePairedDevice(id: String) = pairedDevice(id) ?: error("设备尚未配对")
    private fun pairedDevice(id: String): LanPairedDevice? = prefs.getString("remote_$id", null)?.let { raw ->
        runCatching { JSONObject(raw).let { LanPairedDevice(id, it.getString("name"), it.getString("host"), it.getInt("port")) } }.getOrNull()
    }
    private fun tokenFor(id: String): String = JSONObject(prefs.getString("remote_$id", null) ?: error("设备尚未配对")).getString("token")
    private fun savePairedDevice(id: String, name: String, host: String, port: Int, token: String) {
        prefs.edit().putString("remote_$id", JSONObject().put("name", name).put("host", host).put("port", port).put("token", token).toString()).apply()
    }
    private fun loadPairedDevices(): List<LanPairedDevice> = prefs.all.keys.filter { it.startsWith("remote_") }.mapNotNull { pairedDevice(it.removePrefix("remote_")) }
    private fun loadClients(): List<Pair<String, String>> = prefs.all.filterKeys { it.startsWith("client_") }.mapNotNull { (key, value) ->
        runCatching { key.removePrefix("client_") to JSONObject(value as String).getString("name") }.getOrNull()
    }
    private fun deviceJson(device: LanPairedDevice) = JSONObject().put("id", device.id).put("name", device.name)
    private fun randomBytes(count: Int) = ByteArray(count).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun refreshState(message: String? = _state.value.message) {
        _state.value = _state.value.copy(hosting = server != null, port = server?.listeningPort ?: 0,
            pairingCode = pairingCode?.takeIf { System.currentTimeMillis() < pairingExpiresAt }, pairingExpiresAt = pairingExpiresAt,
            discovering = discoveryListener != null, pairedClients = loadClients(), pairedDevices = loadPairedDevices(), message = message)
    }

    private data class IncomingTransfer(
        val metadata: LinkedHashMap<String, JSONObject>,
        val localIds: MutableMap<String, Long>,
        val createdAt: Long
    )

    private class ExactLengthInputStream(private val delegate: InputStream, length: Long) : InputStream() {
        var remaining = length
            private set
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = delegate.read()
            if (value >= 0) remaining--
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val read = delegate.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (read > 0) remaining -= read
            return read
        }
    }
}
