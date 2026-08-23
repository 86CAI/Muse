/*
 * LX Music 自定义音源脚本的宿主环境。
 *
 * BOOTSTRAP 中构造的 globalThis.lx（EVENT_NAMES / on / send / request / env /
 * version / currentScriptInfo / utils.crypto / utils.buffer）遵循
 * lyswhut/lx-music-desktop 的自定义音源脚本 API 约定，以便直接运行用户导入的
 * LX 兼容脚本。该接口为 clean-room 实现，未复制 LX Music 的源代码。
 *
 * Muse 不内置、不分发任何音源脚本；脚本由用户自行导入并在受限 V8 沙箱中运行。
 *
 * Upstream API reference: https://github.com/lyswhut/lx-music-desktop (Apache-2.0)
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.exceptions.JavetException
import com.caoccao.javet.exceptions.JavetTerminatedException
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.primitive.V8ValueBoolean
import com.caoccao.javet.values.primitive.V8ValueNull
import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.primitive.V8ValueUndefined
import com.caoccao.javet.values.reference.V8ValueArray
import com.caoccao.javet.values.reference.V8ValueFunction
import com.caoccao.javet.values.reference.V8ValueObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Sandboxed, host-controlled runtime for a user-imported LX source.
 *
 * Powered by Javet (V8). Network calls are synchronous and exposed as callbacks
 * plus an immediate thenable. This keeps source scripts deterministic while
 * public methods always run on Dispatchers.IO.
 */
class LxSourceHost(
    initialDescriptor: LxSourceDescriptor,
    private val scriptSource: String,
    private val appContext: android.content.Context
) : AutoCloseable {
    private val handlers = linkedMapOf<String, MutableList<V8ValueFunction>>()
    private val sentPayloads = linkedMapOf<String, String>()
    private val scheduledCallbacks = linkedMapOf<Int, ScheduledCallback>()
    private var nextCallbackId = 1
    private var v8Runtime: V8Runtime? = null
    private var microtaskPumpInfo: String = ""
    private var closed = false

    @Volatile
    var descriptor: LxSourceDescriptor = initialDescriptor
        private set

    suspend fun initialize(): Result<LxSourceDescriptor> = withContext(Dispatchers.IO) {
        try {
            Result.success(initializeBlocking())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun resolveMusicUrl(
        track: OnlineTrack,
        quality: String = "320k"
    ): Result<LxResolvedMusicUrl> = withContext(Dispatchers.IO) {
        try {
            Result.success(resolveMusicUrlBlocking(track, quality))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    @Synchronized
    private fun initializeBlocking(): LxSourceDescriptor {
        check(!closed) { "LX source host is closed" }
        v8Runtime?.let { return descriptor }
        val scriptBytes = scriptSource.toByteArray(Charsets.UTF_8).size
        if (scriptBytes !in 1..MAX_SCRIPT_BYTES) throw IOException("LX source script size is invalid")
        // 释放旧 handler（weak 引用需 forceClose）
        handlers.values.forEach { list -> list.forEach { runCatching { it.close(true) } } }
        handlers.clear()
        sentPayloads.clear()
        scheduledCallbacks.values.forEach { runCatching { it.function.close(true) } }
        scheduledCallbacks.clear()

        // autoCloseable=false：runtime 完全由我们手动管理，避免 Javet Cleaner
        // 在后台线程自动 close 导致 "Runtime is already closed"
        val runtime: V8Runtime = V8Host.getV8Instance().createV8Runtime(
            false,
            com.caoccao.javet.interop.options.V8RuntimeOptions()
        )
        val callbacks = LxHostCallbacks(runtime)
        try {
            executeWithTimeout<Unit>(runtime, INIT_TIMEOUT_NANOS) {
                val global = runtime.globalObject
                global.bind(callbacks)
                global.set("__lxScriptInfoJson", buildScriptInfoJson())
                runtime.getExecutor(BOOTSTRAP).executeVoid()
                runtime.getExecutor(scriptSource).executeVoid()
                // Android V8 的 microtask 不随 executeVoid 自动 flush，原生 Promise 的
                // .then/.catch 回调不会执行。显式泵事件循环让 Promise 链继续。
                val pump = flushMicrotasks(runtime)
                drainTimers(runtime)
                microtaskPumpInfo = pump
            }
        } catch (error: Throwable) {
            logHostError("init-exec", descriptor.name, error.toString(), callbacks.dumpDiagnostics("init-exec"))
            runCatching { runtime.close() }
            v8Runtime = null
            throw error
        }
        // 部分音源（如长青 SVIP）用异步版本检查后才发送 inited，初始化完成后额外
        // 泵事件循环 + 跑定时器，等待异步 inited 事件，避免误报「did not send inited」。
        if (sentPayloads[EVENT_INITED] == null) {
            val waitDeadline = System.nanoTime() + EXTRA_INIT_WAIT_NANOS
            while (System.nanoTime() < waitDeadline && sentPayloads[EVENT_INITED] == null) {
                runCatching {
                    flushMicrotasks(runtime)
                    drainTimers(runtime)
                }
                if (runDueCallbacks(runtime)) continue
                Thread.sleep(40L)
            }
        }
        if (sentPayloads[EVENT_INITED] == null) {
            val lastError = callbacks.lastNetworkError
            logHostError(
                "init-no-inited", descriptor.name,
                lastError?.toString() ?: "inited payload missing",
                callbacks.dumpDiagnostics("init-no-inited")
            )
            runCatching { runtime.close() }
            v8Runtime = null
            if (lastError != null) {
                val url = callbacks.lastNetworkUrl ?: "?"
                throw IOException("音源初始化失败：网络请求 ${url} 失败：${lastError.message ?: lastError}")
            }
            throw IOException("LX source did not send the inited event")
        }
        v8Runtime = runtime
        // 初始化成功后的健康快照：直接回答"新 runtime 是否创建即关闭"
        runCatching {
            if (runtime.isClosed()) {
                val logFile = java.io.File(appContext.filesDir, "lx_host_errors.log")
                val stamp = java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
                ).format(java.util.Date())
                logFile.appendText(
                    "[$stamp] v${com.caipan.music.BuildConfig.VERSION_NAME} [init-check] source=${descriptor.name} " +
                        "RUNTIME CLOSED IMMEDIATELY AFTER INIT\n\n"
                )
            }
        }
        return descriptor
    }

    @Synchronized
    private fun resolveMusicUrlBlocking(track: OnlineTrack, quality: String): LxResolvedMusicUrl {
        check(!closed) { "LX source host is closed" }
        // 每次解析强制重建 runtime 与 handler：健康检查证明 runtime 存活但 handler
        // 可能关联已关闭的旧 runtime，重建可彻底绕开 "Runtime is already closed"
        runCatching { v8Runtime?.close() }
        v8Runtime = null
        initializeBlocking()
        return try {
            resolveMusicUrlWithRuntime(v8Runtime ?: throw IOException("LX source is not initialized"), track, quality)
        } catch (error: JavetException) {
            if (error.message?.contains("already closed", ignoreCase = true) == true) {
                logRuntimeDiagnostics(track, "resolve", error)
                runCatching { v8Runtime?.close() }
                v8Runtime = null
                initializeBlocking()
                resolveMusicUrlWithRuntime(v8Runtime ?: throw IOException("LX source is not initialized"), track, quality)
            } else {
                throw error
            }
        }
    }

    private fun logRuntimeDiagnostics(track: OnlineTrack, phase: String, error: Throwable) {
        runCatching {
            val logFile = java.io.File(appContext.filesDir, "lx_host_errors.log")
            val stamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
            val runtimeState = try {
                val runtime = v8Runtime ?: "null"
                val nativeField = runtime.javaClass.getDeclaredField("v8NativeObject")
                nativeField.isAccessible = true
                val native = nativeField.get(runtime)
                val releasedField = native.javaClass.getDeclaredField("released")
                releasedField.isAccessible = true
                "nativeReleased=${releasedField.getBoolean(native)}"
            } catch (_: Exception) {
                "nativeState=unknown"
            }
            logFile.appendText(
                "[$stamp] v${com.caipan.music.BuildConfig.VERSION_NAME} [diag-$phase] source=${descriptor.name} closed=$closed " +
                    "v8Runtime=${v8Runtime != null} runtimeIdentity=${System.identityHashCode(v8Runtime)} " +
                    "requestHandlers=${handlers[EVENT_REQUEST]?.size ?: 0} $runtimeState track=${track.source}:${track.sourceId} " +
                    "-> ${error.toString()}\n" +
                    error.stackTrace.take(6).joinToString("\n") { "    at $it" } + "\n\n"
            )
        }
    }

    private fun resolveMusicUrlWithRuntime(
        runtime: V8Runtime,
        track: OnlineTrack,
        quality: String
    ): LxResolvedMusicUrl {
        // 健康检查：若 runtime 本身已失效，直接抛错走自愈路径（区分引擎级 vs handler 级）
        val healthy = runCatching {
            runtime.getExecutor("1+1").execute<V8Value>().close()
        }.isSuccess
        if (!healthy) {
            logRuntimeDiagnostics(track, "health-fail", JavetException(com.caoccao.javet.exceptions.JavetError.RuntimeAlreadyClosed))
            throw JavetException(com.caoccao.javet.exceptions.JavetError.RuntimeAlreadyClosed)
        }
        val requestHandlers = handlers[EVENT_REQUEST].orEmpty().toList()
        if (requestHandlers.isEmpty()) throw IOException("LX source has no request handler")
        sentPayloads.remove("url")
        sentPayloads.remove("musicUrl")

        return executeWithTimeout<LxResolvedMusicUrl>(runtime, RESOLVE_TIMEOUT_NANOS) {
            val requestJson = buildMusicRequest(track, "musicUrl", quality)
            val request = parseJsonV8(runtime, requestJson.toString())
            var lastError: Exception? = null
            val handler = storedFunction(runtime, "__lxStored_$EVENT_REQUEST")
                ?: throw IOException("LX source has no request handler")
            try {
                val rawResult = handler.call<V8Value>(null, request)
                val result = awaitThenable(runtime, rawResult)
                parseResolvedResult(result, quality)?.let { return@executeWithTimeout it }
            } catch (error: Exception) {
                lastError = error
            } finally {
                runCatching { handler.close() }
            }
            listOf("musicUrl", "url").forEach { event ->
                sentPayloads[event]?.let { json ->
                    parseResolvedJson(JSONTokener(json).nextValue(), quality)?.let { return@executeWithTimeout it }
                }
            }
            throw lastError ?: IOException("LX source did not return a music URL")
        }
    }

    suspend fun resolveLyrics(track: OnlineTrack): Result<String?> = withContext(Dispatchers.IO) {
        try {
            Result.success(resolveLyricsBlocking(track))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    @Synchronized
    private fun resolveLyricsBlocking(track: OnlineTrack): String? {
        check(!closed) { "LX source host is closed" }
        runCatching { v8Runtime?.close() }
        v8Runtime = null
        initializeBlocking()
        return try {
            resolveLyricsWithRuntime(v8Runtime ?: throw IOException("LX source is not initialized"), track)
        } catch (error: JavetException) {
            if (error.message?.contains("already closed", ignoreCase = true) == true) {
                runCatching { v8Runtime?.close() }
                v8Runtime = null
                initializeBlocking()
                resolveLyricsWithRuntime(v8Runtime ?: throw IOException("LX source is not initialized"), track)
            } else {
                throw error
            }
        }
    }

    private fun resolveLyricsWithRuntime(runtime: V8Runtime, track: OnlineTrack): String? {
        val requestHandlers = handlers[EVENT_REQUEST].orEmpty().toList()
        if (requestHandlers.isEmpty()) throw IOException("LX source has no request handler")
        sentPayloads.remove("lyric")
        sentPayloads.remove("musicLyric")

        return executeWithTimeout<String?>(runtime, RESOLVE_TIMEOUT_NANOS) {
            val requestJson = buildMusicRequest(track, "musicLyric")
            val request = parseJsonV8(runtime, requestJson.toString())
            var lastError: Exception? = null
            val handler = storedFunction(runtime, "__lxStored_$EVENT_REQUEST")
                ?: throw IOException("LX source has no request handler")
            try {
                val rawResult = handler.call<V8Value>(null, request)
                val result = awaitThenable(runtime, rawResult)
                parseLyricResult(result)?.let { return@executeWithTimeout it }
            } catch (error: Exception) {
                lastError = error
            } finally {
                runCatching { handler.close() }
            }
            listOf("musicLyric", "lyric").forEach { event ->
                sentPayloads[event]?.let { json ->
                    parseLyricPayload(JSONTokener(json).nextValue())?.let { return@executeWithTimeout it }
                }
            }
            throw lastError ?: IOException("LX source did not return lyrics")
        }
    }

    /** True when any provider declared search capability (LX sources declare it via `actions`). */
    val supportsSearch: Boolean
        get() = descriptor.providers.any { "search" in it.actions }

    suspend fun search(
        query: String,
        source: String? = null,
        page: Int = 1
    ): Result<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        try {
            Result.success(searchBlocking(query, source, page))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    @Synchronized
    private fun searchBlocking(query: String, source: String?, page: Int): List<OnlineTrack> {
        check(!closed) { "LX source host is closed" }
        initializeBlocking()
        val runtime = v8Runtime ?: throw IOException("LX source is not initialized")
        val requestHandlers = handlers[EVENT_REQUEST].orEmpty().toList()
        if (requestHandlers.isEmpty()) throw IOException("LX source has no request handler")
        sentPayloads.remove("search")
        sentPayloads.remove("searchResult")

        return executeWithTimeout<List<OnlineTrack>>(runtime, RESOLVE_TIMEOUT_NANOS) {
            val requestJson = buildSearchRequest(query, source, page)
            val request = parseJsonV8(runtime, requestJson.toString())
            var lastError: Exception? = null
            val handler = storedFunction(runtime, "__lxStored_$EVENT_REQUEST")
                ?: throw IOException("LX source has no request handler")
            try {
                val rawResult = handler.call<V8Value>(null, request)
                val result = awaitThenable(runtime, rawResult)
                parseSearchResult(result)?.let { return@executeWithTimeout it }
            } catch (error: Exception) {
                lastError = error
            } finally {
                runCatching { handler.close() }
            }
            listOf("searchResult", "search").forEach { event ->
                sentPayloads[event]?.let { json ->
                    parseSearchPayload(JSONTokener(json).nextValue())?.let { return@executeWithTimeout it }
                }
            }
            throw lastError ?: IOException("LX source did not return search results")
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        // weak 引用的 handler 需要 forceClose 才能真正释放
        handlers.values.forEach { list -> list.forEach { runCatching { it.close(true) } } }
        handlers.clear()
        sentPayloads.clear()
        scheduledCallbacks.values.forEach { runCatching { it.function.close(true) } }
        scheduledCallbacks.clear()
        v8Runtime?.let { runCatching { it.close() } }
        v8Runtime = null
    }

    @Synchronized
    private fun logHostError(phase: String, source: String, detail: String, diagnostics: String = "") {
        runCatching {
            val logFile = java.io.File(appContext.filesDir, "lx_host_errors.log")
            val stamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
            logFile.appendText(
                "[$stamp] v${com.caipan.music.BuildConfig.VERSION_NAME} [$phase] $source -> $detail\n" +
                    (if (diagnostics.isBlank()) "" else "$diagnostics\n") + "\n"
            )
        }
    }

    private fun buildScriptInfoJson(): String = JSONObject()
        .put("name", descriptor.name)
        .put("description", descriptor.description)
        .put("version", effectiveScriptVersion())
        .put("author", descriptor.author)
        .put("homepage", descriptor.originalSource)
        .put("rawScript", scriptSource)
        .toString()

    private fun effectiveScriptVersion(): String {
        if (!descriptor.version.isBlank() && descriptor.version != "unknown") return descriptor.version
        return Regex("@version\\s+([^\\r\\n@*]+)").find(scriptSource)
            ?.groupValues?.get(1)?.trim().orEmpty().trimStart('*', ' ', '\t').trim()
            .takeIf { it.isNotBlank() } ?: descriptor.version
    }

    private inner class LxHostCallbacks(private val runtime: V8Runtime) {

        private val consoleBuffer = ArrayDeque<String>()
        var lastNetworkError: Throwable? = null
            private set
        var lastNetworkUrl: String? = null
            private set
        var requestCount: Int = 0
            private set

        @Synchronized
        fun dumpDiagnostics(tag: String): String {
            val sb = StringBuilder()
            val sent = sentPayloads.keys.joinToString(",") { it }.ifEmpty { "(none)" }
            sb.append("[diagnostics] $tag sentPayloads=$sent requestCount=$requestCount")
            if (microtaskPumpInfo.isNotBlank()) sb.append(" $microtaskPumpInfo")
            if (lastNetworkError != null) {
                sb.append(" lastNetwork=${lastNetworkUrl ?: "?"} error=${lastNetworkError}")
            }
            if (consoleBuffer.isNotEmpty()) {
                sb.append("\n[console]\n").append(consoleBuffer.joinToString("\n"))
            }
            return sb.toString()
        }

        @V8Function
        fun __lxConsole(level: String, message: String): Boolean {
            synchronized(consoleBuffer) {
                consoleBuffer.addLast("[console.$level] $message")
                while (consoleBuffer.size > 300) consoleBuffer.removeFirst()
            }
            return true
        }

        @V8Function
        fun __lxOn(event: String, handler: V8ValueFunction): Boolean {
            val name = event.trim()
            if (name.isBlank() || name.length > 64) throw RuntimeException("Invalid LX event name")
            // Javet 会在 @V8Function 回调返回后自动 close 全部参数（V8FunctionCallback.finally safeClose）。
            // 转 weak 引用后 close() 变 no-op（V8ValueReference.close 只对强引用生效），
            // 同时挂到 global 数组持有 V8 强引用，防止 V8 GC 回收脚本函数。
            runCatching { handler.setWeak() }
            runCatching {
                val global = runtime.globalObject
                val registry = if (global.has("__lxHandlerRegistry")) {
                    global.get<V8ValueArray>("__lxHandlerRegistry")
                } else {
                    runtime.createV8ValueArray().also { global.set("__lxHandlerRegistry", it) }
                }
                registry.push(handler)
                registry.close()
                // 存 JS 侧强引用：调用时取 fresh wrapper，绕开 Javet 回调参数被 safeClose 的问题
                global.set("__lxStored_$name", handler)
                global.close()
            }
            handlers.getOrPut(name) { mutableListOf() }.add(handler)
            return true
        }

        @V8Function
        fun __lxSend(event: String, payload: V8Value?): Boolean {
            val name = event.trim()
            if (name.isBlank() || name.length > 64) throw RuntimeException("Invalid LX event name")
            val json = if (payload.isNullOrUndefined()) "null"
                       else stringifyV8(runtime, payload!!)
            if (json.toByteArray(Charsets.UTF_8).size > MAX_EVENT_BYTES) {
                throw RuntimeException("LX event payload is too large")
            }
            sentPayloads[name] = json
            if (name == EVENT_INITED) applyInitializationPayload(json)
            return true
        }

        @V8Function
        fun __lxHostRequest(url: String, options: V8ValueObject?): V8Value {
            requestCount++
            val request = buildNetworkRequest(runtime, url, options)
            val response = try {
                SafeOnlineHttp.execute(request)
            } catch (error: Exception) {
                lastNetworkUrl = url
                lastNetworkError = error
                throw RuntimeException(error.message ?: "LX network request failed")
            }
            val resp = runtime.createV8ValueObject()
            resp.set("statusCode", response.statusCode)
            resp.set("status", response.statusCode)
            resp.set("body", response.body)
            resp.set("bodyBase64", response.bodyBase64)
            runtime.createV8ValueObject().use { headers ->
                response.headers.forEach { (k, v) -> headers.set(k, v) }
                resp.set("headers", headers)
            }
            resp.set("url", response.finalUrl)
            return resp
        }

        @V8Function
        fun __lxDigest(algorithm: String, input: String): String {
            val jvmAlgorithm = when (algorithm.uppercase()) {
                "MD5" -> "MD5"
                "SHA1", "SHA-1" -> "SHA-1"
                "SHA256", "SHA-256" -> "SHA-256"
                else -> throw RuntimeException("Unsupported digest algorithm")
            }
            return MessageDigest.getInstance(jvmAlgorithm).digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        @V8Function
        fun __lxHmac(algorithm: String, key: String, input: String): String {
            val jvmAlgorithm = when (algorithm.uppercase()) {
                "SHA1", "SHA-1" -> "HmacSHA1"
                "SHA256", "SHA-256" -> "HmacSHA256"
                else -> throw RuntimeException("Unsupported HMAC algorithm")
            }
            val mac = Mac.getInstance(jvmAlgorithm)
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), jvmAlgorithm))
            return mac.doFinal(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }

        @V8Function
        fun __lxBufferConvert(input: String, from: String, to: String): String =
            encodeBytes(decodeBytes(input, from.lowercase()), to.lowercase())

        @V8Function
        fun __lxRandomHex(count: Int): String {
            val size = if (count <= 0) 16 else count.coerceAtMost(1024)
            return ByteArray(size).also(SECURE_RANDOM::nextBytes).joinToString("") { "%02x".format(it) }
        }

        @V8Function
        fun __lxSetTimeout(callback: V8ValueFunction, delayMs: Int, args: V8ValueArray?): Int {
            // 转 weak：Javet 回调返回后会自动 close 参数，weak 使 close 变 no-op
            runCatching { callback.setWeak() }
            val delay = delayMs.toLong().coerceIn(0L, MAX_TIMER_DELAY_MS)
            val argsJson = if (args != null && args.length > 0) stringifyV8(runtime, args) else "[]"
            if (scheduledCallbacks.size >= MAX_TIMERS) throw RuntimeException("Too many pending timers")
            val id = nextCallbackId
            nextCallbackId = if (nextCallbackId == Int.MAX_VALUE) 1 else nextCallbackId + 1
            // 存 JS 侧强引用：触发时取 fresh wrapper（同 handler 修复）
            runCatching { runtime.globalObject.set("__lxTimer_$id", callback) }
            scheduledCallbacks[id] = ScheduledCallback(
                dueNanos = System.nanoTime() + delay * 1_000_000L,
                function = callback,
                argumentsJson = argsJson
            )
            return id
        }

        @V8Function
        fun __lxClearTimeout(id: Int): Boolean {
            scheduledCallbacks.remove(id)
            return true
        }
    }

    private fun buildNetworkRequest(
        runtime: V8Runtime,
        url: String,
        options: V8ValueObject?
    ): OnlineHttpRequest {
        val optsJson = if (!options.isNullOrUndefined()) {
            stringifyV8(runtime, options!!)
        } else "{}"
        val opts = runCatching { JSONObject(optsJson) }.getOrDefault(JSONObject())
        val method = opts.optString("method").uppercase().ifBlank { "GET" }
        val rawHeaders = opts.optJSONObject("headers")
        val mutableHeaders = linkedMapOf(
            "Accept" to "application/json",
            "User-Agent" to LX_REQUEST_USER_AGENT
        )
        if (rawHeaders != null) {
            if (rawHeaders.length() > MAX_HEADERS) throw RuntimeException("Too many LX request headers")
            rawHeaders.keys().forEach { name -> mutableHeaders[name] = rawHeaders.optString(name) }
        }
        var body: String? = opts.opt("body")?.takeUnless { it == JSONObject.NULL }?.toString()
        opts.optJSONObject("form")?.let { form ->
            body = form.keys().asSequence().joinToString("&") { key ->
                "${urlEncode(key)}=${urlEncode(form.optString(key))}"
            }
            mutableHeaders.putIfAbsent("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        }
        opts.opt("json")?.takeUnless { it == JSONObject.NULL || it == false }?.let {
            body = it.toString()
            mutableHeaders.putIfAbsent("Content-Type", "application/json; charset=utf-8")
        }
        val timeout = opts.opt("timeout")?.toString()?.toLongOrNull()?.takeIf { it > 0L } ?: SafeOnlineHttp.DEFAULT_TIMEOUT_MS
        return OnlineHttpRequest(
            method = method,
            url = url,
            headers = mutableHeaders,
            body = body,
            timeoutMs = timeout,
            requireHttps = false,
            maxResponseBytes = MAX_NETWORK_RESPONSE_BYTES
        )
    }

    private fun buildMusicRequest(track: OnlineTrack, action: String, quality: String = "320k"): JSONObject {
        val raw = JSONObject().apply { track.metadata.forEach { (key, value) -> put(key, value) } }
        raw.put("source", track.source)
        if (!raw.has("id")) raw.put("id", track.sourceId)
        if (!raw.has("name")) raw.put("name", track.title)
        if (!raw.has("title")) raw.put("title", track.title)
        if (!raw.has("artist")) raw.put("artist", track.artist)
        if (!raw.has("singer")) raw.put("singer", track.artist)
        if (!raw.has("album")) raw.put("album", track.album)
        if (!raw.has("duration")) raw.put("duration", track.durationMs)
        val provider = track.source
        val info = JSONObject()
            .put("type", if (action == "musicUrl") quality else "song")
            .put("quality", quality)
            .put("musicInfo", raw)
        return JSONObject()
            .put("action", action)
            .put("source", provider)
            .put("type", if (action == "musicUrl") quality else "song")
            .put("quality", quality)
            .put("musicInfo", raw)
            .put("info", info)
    }

    private fun buildSearchRequest(query: String, source: String?, page: Int): JSONObject {
        val info = JSONObject()
            .put("type", "song")
            .put("text", query)
            .put("page", page)
            .put("pageSize", SEARCH_PAGE_SIZE)
        return JSONObject()
            .put("action", "search")
            .put("source", source ?: JSONObject.NULL)
            .put("type", "song")
            .put("text", query)
            .put("page", page)
            .put("pageSize", SEARCH_PAGE_SIZE)
            .put("info", info)
    }

    private fun parseSearchResult(result: V8Value?): List<OnlineTrack>? {
        if (result.isNullOrUndefined()) return null
        val json = stringifyV8(v8Runtime!!, result!!)
        return parseSearchPayload(runCatching { JSONTokener(json).nextValue() }.getOrNull())
    }

    private fun parseSearchPayload(value: Any?): List<OnlineTrack>? {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> {
                listOf("data", "list", "result", "songs", "songList").firstNotNullOfOrNull { key ->
                    when (val nested = value.opt(key)) {
                        is JSONArray -> nested
                        is JSONObject -> listOf("list", "data", "result", "songs")
                            .firstNotNullOfOrNull { nested.opt(it) }
                            ?.let { it as? JSONArray }
                        else -> null
                    }
                } ?: return null
            }
            else -> return null
        }
        val tracks = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseSearchItem(item)?.let(::add)
            }
        }
        return tracks.takeIf(List<OnlineTrack>::isNotEmpty)
    }

    private fun parseSearchItem(item: JSONObject): OnlineTrack? {
        val sourceId = firstPresent(item, "songmid", "id", "hash")?.toString()?.trim().orEmpty()
        if (sourceId.isBlank() || sourceId == "null") return null
        val title = firstPresent(item, "name", "title", "songname")?.toString()?.trim().orEmpty()
        if (title.isBlank()) return null
        val source = firstPresent(item, "source")?.toString()?.takeIf(String::isNotBlank) ?: "wy"
        val artists = when (val raw = firstPresent(item, "singer", "artist", "singers")) {
            is JSONArray -> buildList {
                for (index in 0 until raw.length()) {
                    when (val entry = raw.opt(index)) {
                        is JSONObject -> entry.optString("name").takeIf(String::isNotBlank)?.let(::add)
                        is String -> entry.trim().takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }
            is String -> raw.split('/', '、').map(String::trim).filter(String::isNotBlank)
            else -> listOf(item.optString("artist", "未知歌手"))
        }.ifEmpty { listOf(item.optString("singer", "未知歌手")) }
        val album = firstPresent(item, "album", "albumName")?.toString()?.trim().orEmpty()
        val artwork = firstPresent(item, "picUrl", "pic", "albumPic")?.toString()
            ?.takeIf { it.startsWith("http") }
        val metadata = buildMap {
            item.keys().forEach { key -> put(key, item.opt(key)?.toString().orEmpty()) }
            put("songmid", sourceId)
            put("id", sourceId)
            put("hash", firstPresent(item, "hash")?.toString() ?: sourceId)
            put("name", title)
            put("title", title)
        }
        return OnlineTrack(
            source = source,
            sourceId = sourceId,
            title = title,
            artists = artists,
            album = album,
            durationMs = parseSearchDurationMs(item),
            artworkUrl = artwork,
            metadata = metadata
        )
    }

    private fun parseSearchDurationMs(item: JSONObject): Long {
        when (val duration = firstPresent(item, "duration", "durationMs", "time")) {
            is Number -> {
                val value = duration.toLong()
                if (value > 0L) return if (value < 10_000L) value * 1_000L else value
            }
            is String -> duration.trim().toLongOrNull()?.let {
                if (it > 0L) return if (it < 10_000L) it * 1_000L else it
            }
            else -> Unit
        }
        when (val interval = firstPresent(item, "interval")) {
            is Number -> {
                val value = interval.toLong()
                if (value > 0L) return value * 1_000L
            }
            is String -> interval.trim().toLongOrNull()?.let { if (it > 0L) return it * 1_000L }
            else -> Unit
        }
        return 0L
    }

    private fun firstPresent(item: JSONObject, vararg keys: String): Any? {
        for (key in keys) {
            if (!item.has(key)) continue
            return item.opt(key)
        }
        return null
    }

    private fun parseResolvedResult(result: V8Value?, requestedQuality: String): LxResolvedMusicUrl? {
        if (result.isNullOrUndefined()) return null
        val json = stringifyV8(v8Runtime!!, result!!)
        if (json.startsWith("\"")) {
            val text = runCatching { JSONTokener(json).nextValue() as? String }.getOrNull()
            return text?.let { validateResolved(LxResolvedMusicUrl(it, quality = requestedQuality)) }
        }
        return parseResolvedJson(JSONTokener(json).nextValue(), requestedQuality)
    }

    private fun parseResolvedJson(value: Any?, requestedQuality: String): LxResolvedMusicUrl? {
        if (value is String) return validateResolved(LxResolvedMusicUrl(value, quality = requestedQuality))
        val json = value as? JSONObject ?: return null
        val nested = json.opt("data")
        if (json.optString("url").isBlank() && nested != null && nested !== JSONObject.NULL) {
            return parseResolvedJson(nested, requestedQuality)
        }
        val url = json.optString("url", json.optString("musicUrl", "")).trim()
        if (url.isBlank()) return null
        val headers = json.optJSONObject("headers")?.let { objectValue ->
            buildMap {
                objectValue.keys().forEach { name ->
                    if (name.lowercase() in SAFE_MEDIA_HEADERS) put(name, objectValue.optString(name))
                }
            }
        }.orEmpty()
        return validateResolved(LxResolvedMusicUrl(
            url = url,
            headers = headers,
            quality = json.optString("quality", json.optString("type", requestedQuality)).ifBlank { requestedQuality },
            expiresAtEpochMs = json.optLong("expiresAtEpochMs", 0L).takeIf { it > 0L }
        ))
    }

    private fun parseLyricResult(result: V8Value?): String? {
        if (result.isNullOrUndefined()) return null
        val json = stringifyV8(v8Runtime!!, result!!)
        return parseLyricPayload(runCatching { JSONTokener(json).nextValue() }.getOrNull())
    }

    private fun parseLyricPayload(value: Any?): String? {
        if (value is String) return value.takeIf { it.isNotBlank() }
        val json = value as? JSONObject ?: return null
        val direct = json.optString("lyric", json.optString("lrc", json.optString("musicLyric", ""))).trim()
        if (direct.isNotBlank()) return direct
        val nested = json.opt("data")
        return if (nested != null && nested !== JSONObject.NULL) parseLyricPayload(nested) else null
    }

    private fun validateResolved(value: LxResolvedMusicUrl): LxResolvedMusicUrl = value.copy(
        url = SafeOnlineHttp.validateMediaUrl(value.url),
        headers = value.headers.filterKeys { it.lowercase() in SAFE_MEDIA_HEADERS }
    )

    private fun applyInitializationPayload(jsonText: String) {
        val json = runCatching { JSONObject(jsonText) }
            .getOrElse { throw RuntimeException("Invalid LX inited payload") }
        if (!json.optBoolean("status", true)) {
            throw RuntimeException(json.optString("message", "LX source initialization failed"))
        }
        val providersObject = json.optJSONObject("sources") ?: JSONObject()
        val providers = buildList {
            providersObject.keys().forEach { id ->
                val source = providersObject.optJSONObject(id) ?: JSONObject()
                val qualities = when (val raw = source.opt("qualitys")) {
                    is JSONArray -> raw.toStringList()
                    is JSONObject -> raw.keys().asSequence().toList()
                    else -> source.optJSONArray("qualities")?.toStringList().orEmpty()
                }
                add(LxProviderDescriptor(
                    id = id,
                    name = source.optString("name", id),
                    actions = source.optJSONArray("actions")?.toStringList().orEmpty(),
                    qualities = qualities.distinct()
                ))
            }
        }
        descriptor = descriptor.copy(
            name = json.optString("name", descriptor.name).ifBlank { descriptor.name },
            version = json.optString("version", descriptor.version).ifBlank { descriptor.version },
            author = json.optString("author", descriptor.author).ifBlank { descriptor.author },
            description = json.optString("description", descriptor.description).ifBlank { descriptor.description },
            providers = providers
        )
    }

    private fun awaitThenable(runtime: V8Runtime, value: V8Value?): V8Value? {
        if (value.isNullOrUndefined()) return value
        val v = value as V8Value
        val obj = v as? V8ValueObject ?: return v
        val hasThen = runCatching {
            val then = obj.get<V8Value>("then")
            then is V8ValueFunction
        }.getOrDefault(false)
        if (!hasThen) return v
        val awaitFn = runtime.getExecutor(
            "(function(t){if(t===null||t===undefined||typeof t.then!=='function')return{settled:true,value:t,error:null};var s={settled:false,value:null,error:null};try{t.then(function(v){s.settled=true;s.value=v;},function(e){s.settled=true;s.error=e;});}catch(e){s.settled=true;s.error=e;}return s;})"
        ).execute() as V8ValueFunction
        val state = awaitFn.call<V8ValueObject>(null, v)
        awaitFn.close()
        val timerDeadline = System.nanoTime() + MAX_TIMER_WAIT_NANOS
        var idleRounds = 0
        while (idleRounds < MAX_MICROTASK_ROUNDS) {
            // Android V8 不自动 flush microtask，必须显式泵事件循环
            runCatching { runtime.await(com.caoccao.javet.enums.V8AwaitMode.RunOnce) }
            runCatching { runtime.getExecutor("void 0;").executeVoid() }
            val settled = (state.get<V8Value>("settled") as? V8ValueBoolean)?.value ?: false
            if (settled) break
            if (runDueCallbacks(runtime)) {
                idleRounds = 0
                continue
            }
            val nextDelay = nextCallbackDelayNanos()
            if (nextDelay == null) {
                idleRounds++
                continue
            }
            val remaining = timerDeadline - System.nanoTime()
            if (remaining <= 0L) break
            val sleepNanos = minOf(nextDelay.coerceAtLeast(0L), remaining, TIMER_POLL_NANOS)
            if (sleepNanos > 0L) {
                Thread.sleep(sleepNanos / 1_000_000L, (sleepNanos % 1_000_000L).toInt())
            }
        }
        val error = state.get<V8Value>("error")
        if (!error.isNullOrUndefined()) {
            state.close()
            throw IOException(jsErrorMessage(error!!))
        }
        val resolved = state.get<V8Value>("value")
        state.close()
        return resolved
    }

    private fun flushMicrotasks(runtime: V8Runtime): String {
        val sb = StringBuilder()
        var awaitOk = 0
        var awaitFail = 0
        var scriptOk = 0
        var scriptFail = 0
        // RunOnce 每轮泵一次事件循环（V8 microtask + 短 macrotask），有限轮数避免死循环。
        // 部分 Android 构建上 await() 可能未实现，同时用空脚本执行尝试触发 microtask flush。
        repeat(MAX_MICROTASK_ROUNDS) {
            val ran = runCatching { runtime.await(com.caoccao.javet.enums.V8AwaitMode.RunOnce) }.getOrDefault(false)
            if (ran) awaitOk++ else awaitFail++
            try {
                runtime.getExecutor("void 0;").executeVoid()
                scriptOk++
            } catch (_: Throwable) {
                scriptFail++
            }
        }
        sb.append("microtaskPump(awaitOk=$awaitOk awaitFail=$awaitFail scriptOk=$scriptOk scriptFail=$scriptFail)")
        return sb.toString()
    }

    private fun drainTimers(runtime: V8Runtime) {
        repeat(MAX_MICROTASK_ROUNDS) {
            runDueCallbacks(runtime)
        }
    }

    private fun runDueCallbacks(runtime: V8Runtime): Boolean {
        val now = System.nanoTime()
        val due = scheduledCallbacks.entries.filter { it.value.dueNanos <= now }
        due.forEach { entry ->
            scheduledCallbacks.remove(entry.key)
            val callback = entry.value
            // fresh wrapper：saved 的 V8ValueFunction 可能已被 Javet 回收
            val fn = storedFunction(runtime, "__lxTimer_${entry.key}") ?: return@forEach
            val applyFn = runtime.getExecutor("(function(fn,args){return fn.apply(null,args);})").execute() as V8ValueFunction
            runCatching { applyFn.callVoid(null, fn, parseJsonV8(runtime, callback.argumentsJson)) }
            applyFn.close()
            runCatching { fn.close() }
        }
        return due.isNotEmpty()
    }

    private fun nextCallbackDelayNanos(): Long? {
        val nextDue = scheduledCallbacks.values.minOfOrNull(ScheduledCallback::dueNanos) ?: return null
        return nextDue - System.nanoTime()
    }

    private fun jsErrorMessage(value: V8Value): String {
        val obj = value as? V8ValueObject
        if (obj != null) {
            val msg = runCatching { obj.get<V8Value>("message") }.getOrNull()
            if (!msg.isNullOrUndefined()) {
                val text = msg.toString().trim()
                if (text.isNotBlank()) return text
            }
        }
        val text = value.toString().trim()
        if (text.isNotBlank()) return text
        return "LX source error"
    }

    private fun V8Value?.isNullOrUndefined(): Boolean =
        this == null || this is V8ValueUndefined || this is V8ValueNull

    private fun parseJsonV8(runtime: V8Runtime, json: String): V8Value {
        val fn = runtime.getExecutor("JSON.parse").execute() as V8ValueFunction
        return fn.call<V8Value>(null, json).also { fn.close() }
    }

    /** 从 global 取 fresh wrapper：@V8Function 回调参数会被 Javet safeClose，保存的引用不可靠。 */
    private fun storedFunction(runtime: V8Runtime, key: String): V8ValueFunction? = runCatching {
        val value = runtime.globalObject.get<V8Value>(key)
        if (value.isNullOrUndefined()) null
        else if (value is V8ValueFunction) value
        else { value.close(); null }
    }.getOrNull()

    private fun stringifyV8(runtime: V8Runtime, value: V8Value): String {
        val fn = runtime.getExecutor("JSON.stringify").execute() as V8ValueFunction
        val result = fn.call<V8Value>(null, value)
        return try { result.toString() } finally { runCatching { result.close() }; runCatching { fn.close() } }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun decodeBytes(value: String, encoding: String): ByteArray = when (encoding) {
        "utf8", "utf-8", "text" -> value.toByteArray(Charsets.UTF_8)
        "base64" -> Base64.getDecoder().decode(value)
        "hex" -> {
            if (value.length % 2 != 0 || !value.matches(Regex("^[0-9a-fA-F]*$"))) {
                throw RuntimeException("Invalid hex data")
            }
            ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }
        else -> throw RuntimeException("Unsupported input encoding")
    }

    private fun encodeBytes(value: ByteArray, encoding: String): String = when (encoding) {
        "utf8", "utf-8", "text" -> value.toString(Charsets.UTF_8)
        "base64" -> Base64.getEncoder().encodeToString(value)
        "hex" -> value.joinToString("") { "%02x".format(it) }
        else -> throw RuntimeException("Unsupported output encoding")
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class ScheduledCallback(
        val dueNanos: Long,
        val function: V8ValueFunction,
        val argumentsJson: String
    )

    private fun <T> executeWithTimeout(runtime: V8Runtime, timeoutNanos: Long, block: () -> T): T {
        val deadline = System.nanoTime() + timeoutNanos
        val timedOut = AtomicBoolean(false)
        val watchdog = Thread {
            try {
                while (System.nanoTime() < deadline) {
                    Thread.sleep(10)
                    if (Thread.currentThread().isInterrupted) return@Thread
                }
                if (timedOut.compareAndSet(false, true)) {
                    runCatching { runtime.terminateExecution() }
                }
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }
        return try {
            block()
        } catch (error: JavetException) {
            if (timedOut.get()) {
                // terminateExecution 后 V8 runtime 无法继续复用，必须关闭并置空，下次调用重建
                runCatching { runtime.close() }
                v8Runtime = null
                throw LxScriptTimeoutException()
            }
            throw error
        } catch (error: RuntimeException) {
            if (timedOut.get() && error.message?.contains("terminat", ignoreCase = true) != false) {
                runCatching { runtime.close() }
                v8Runtime = null
                throw LxScriptTimeoutException()
            }
            throw error
        } finally {
            watchdog.interrupt()
            try { watchdog.join(50) } catch (_: InterruptedException) {}
        }
    }

    private class LxScriptTimeoutException : RuntimeException("LX source script timed out")

    private companion object {
        const val EVENT_INITED = "inited"
        const val EVENT_REQUEST = "request"
        const val EXTRA_INIT_WAIT_NANOS = 5_000_000_000L
        const val MAX_SCRIPT_BYTES = 4 * 1024 * 1024
        const val MAX_EVENT_BYTES = 2 * 1024 * 1024
        const val MAX_NETWORK_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_HEADERS = 32
        const val MAX_TIMERS = 128
        const val MAX_MICROTASK_ROUNDS = 32
        const val MAX_TIMER_DELAY_MS = 60_000L
        const val MAX_TIMER_WAIT_NANOS = 10_000_000_000L
        const val TIMER_POLL_NANOS = 25_000_000L
        const val INIT_TIMEOUT_NANOS = 60_000_000_000L
        const val RESOLVE_TIMEOUT_NANOS = 45_000_000_000L
        const val SEARCH_PAGE_SIZE = 30
        val SECURE_RANDOM = SecureRandom()
        val SAFE_MEDIA_HEADERS = setOf("accept", "accept-language", "user-agent", "referer", "origin", "range")
        const val LX_REQUEST_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 Chrome/69.0.3497.100 Safari/537.36"

        val BOOTSTRAP = """
            (function (root) {
              'use strict';
              function logToHost(level, args) {
                try { __lxConsole(level, Array.prototype.slice.call(args).join(' ')); } catch (_) {}
              }
              if (!root.console) root.console = {};
              var levels = ['log', 'info', 'warn', 'error', 'debug', 'group', 'groupEnd'];
              for (var i = 0; i < levels.length; i++) {
                (function (level) {
                  if (typeof root.console[level] !== 'function') {
                    root.console[level] = function () { logToHost(level, arguments); };
                  }
                })(levels[i]);
              }
              function resolved(ok, value) {
                var thenable = {};
                thenable.then = function (onValue, onError) {
                  try {
                    if (ok) {
                      var next = typeof onValue === 'function' ? onValue(value) : value;
                      return next && typeof next.then === 'function' ? next : resolved(true, next);
                    }
                    if (typeof onError !== 'function') return resolved(false, value);
                    var recovered = onError(value);
                    return recovered && typeof recovered.then === 'function' ? recovered : resolved(true, recovered);
                  } catch (error) {
                    return resolved(false, error);
                  }
                };
                thenable.catch = function (onError) { return thenable.then(null, onError); };
                thenable.finally = function (callback) {
                  return thenable.then(
                    function (result) { if (typeof callback === 'function') callback(); return result; },
                    function (error) { if (typeof callback === 'function') callback(); throw error; }
                  );
                };
                return thenable;
              }

              root.setTimeout = function (callback, timeout) {
                return __lxSetTimeout(callback, Number(timeout) || 0, Array.prototype.slice.call(arguments, 2));
              };
              root.clearTimeout = function (id) { return __lxClearTimeout(id); };

              var lx = Object.create(null);
              lx.EVENT_NAMES = Object.freeze({
                inited: 'inited',
                request: 'request',
                updateAlert: 'updateAlert',
                url: 'url',
                musicUrl: 'musicUrl'
              });
              lx.on = function (event, handler) {
                try { __lxOn(event, handler); return resolved(true); }
                catch (error) { return resolved(false, error); }
              };
              lx.send = function (event, payload) {
                try { __lxSend(event, payload); return resolved(true); }
                catch (error) { return resolved(false, error); }
              };
              lx.request = function (url, options, callback) {
                if (typeof options === 'function') { callback = options; options = {}; }
                options = options || {};
                try {
                  var response = __lxHostRequest(String(url), options);
                  var body = response.body;
                  if (typeof body === 'string') {
                    try { body = JSON.parse(body); } catch (_) {}
                  }
                  response.body = body;
                  if (typeof callback === 'function') callback(null, response, body);
                } catch (rawError) {
                  var error = new Error(rawError && rawError.message ? rawError.message : String(rawError));
                  if (typeof callback === 'function') callback(error, null, null);
                }
                return function () {};
              };
              lx.env = 'mobile';
              lx.version = '2.0.0';
              try { lx.currentScriptInfo = JSON.parse(__lxScriptInfoJson); }
              catch (_) { lx.currentScriptInfo = {}; }
              lx.utils = Object.create(null);
              lx.utils.crypto = {
                md5: function (value) { return __lxDigest('MD5', String(value)); },
                sha1: function (value) { return __lxDigest('SHA1', String(value)); },
                sha256: function (value) { return __lxDigest('SHA256', String(value)); },
                hmacSha1: function (key, value) { return __lxHmac('SHA1', String(key), String(value)); },
                hmacSha256: function (key, value) { return __lxHmac('SHA256', String(key), String(value)); },
                randomBytes: function (size) { return __lxRandomHex(size || 16); }
              };
              lx.utils.buffer = {
                from: function (value, encoding) {
                  var inputEncoding = encoding || 'utf8';
                  return {
                    toString: function (outputEncoding) {
                      return __lxBufferConvert(String(value), inputEncoding, outputEncoding || 'utf8');
                    }
                  };
                },
                bufToString: function (buffer, format) {
                  if (buffer && typeof buffer.toString === 'function') return buffer.toString(format || 'utf8');
                  return __lxBufferConvert(String(buffer), 'utf8', format || 'utf8');
                },
                convert: function (value, from, to) { return __lxBufferConvert(String(value), from, to); }
              };
              lx.utils.base64Encode = function (value) { return __lxBufferConvert(String(value), 'utf8', 'base64'); };
              lx.utils.base64Decode = function (value) { return __lxBufferConvert(String(value), 'base64', 'utf8'); };
              lx.utils.randomString = function (size) { return __lxRandomHex(Math.ceil((size || 16) / 2)).slice(0, size || 16); };
              lx.utils.urlEncode = function (value) { return encodeURIComponent(String(value)); };
              lx.utils.urlDecode = function (value) { return decodeURIComponent(String(value)); };
              root.lx = lx;
            })(typeof globalThis !== 'undefined' ? globalThis : this);
        """.trimIndent()
    }
}
