package com.caipan.music.online

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Persists user-imported LX scripts without executing them during import. */
class OnlineSourceStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val sourcesDirectory = File(appContext.filesDir, SOURCES_DIRECTORY).apply { mkdirs() }

    suspend fun importFromText(name: String, script: String): Result<LxSourceDescriptor> = withContext(Dispatchers.IO) {
        try {
            val text = script.trim()
            if (text.isEmpty()) throw IOException("脚本内容为空")
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_SCRIPT_BYTES) throw IOException("脚本过大")
            if (text.indexOf('\u0000') >= 0) throw IOException("脚本包含无效数据")
            val sourceUrl = "local://$name"
            Result.success(persistDownloaded(sourceUrl, text, bytes.size.toLong()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun importFromUrl(url: String): Result<LxSourceDescriptor> = withContext(Dispatchers.IO) {
        try {
            val sourceUrl = url.trim()
            val failures = mutableListOf<String>()
            val candidates = downloadCandidates(sourceUrl)
            val attemptTimeoutMs = if (candidates.size > 1) {
                IMPORT_FALLBACK_ATTEMPT_TIMEOUT_MS
            } else {
                IMPORT_TIMEOUT_MS
            }

            for (downloadUrl in candidates) {
                try {
                    val response = SafeOnlineHttp.execute(
                        OnlineHttpRequest(
                            url = downloadUrl,
                            requireHttps = true,
                            headers = mapOf("Accept" to "application/javascript, text/javascript, text/plain;q=0.9"),
                            maxResponseBytes = MAX_SCRIPT_BYTES,
                            timeoutMs = attemptTimeoutMs
                        )
                    )
                    if (response.statusCode !in 200..299) {
                        throw IOException("HTTP ${response.statusCode}")
                    }
                    if (response.isHtmlDocument()) {
                        throw IOException("Server returned an HTML page instead of a source script")
                    }
                    val bytes = response.body.toByteArray(Charsets.UTF_8)
                    if (bytes.isEmpty()) throw IOException("Source script is empty")
                    if (bytes.size > MAX_SCRIPT_BYTES) throw IOException("Source script is too large")
                    if (response.body.indexOf('\u0000') >= 0) throw IOException("Source script contains invalid data")
                    return@withContext Result.success(
                        persistDownloaded(sourceUrl, response.body, bytes.size.toLong())
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += "${displayHost(downloadUrl)}: ${rootCauseMessage(error)}"
                }
            }
            throw IOException(
                "Source download failed (${failures.joinToString("; ")})"
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun downloadCandidates(sourceUrl: String): List<String> {
        val parsed = sourceUrl.toHttpUrlOrNull() ?: return listOf(sourceUrl)
        if (parsed.scheme != "https" || parsed.port != 443 ||
            parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
            parsed.query != null || parsed.fragment != null
        ) {
            return listOf(sourceUrl)
        }
        val pathSegments = when (parsed.host.lowercase()) {
            RAW_GITHUB_HOST -> parsed.encodedPathSegments
            GITHUB_PROXY_HOST -> parsed.encodedPathSegments
                .takeIf { it.firstOrNull() == RAW_GITHUB_HOST }
                ?.drop(1)
            else -> null
        } ?: return listOf(sourceUrl)
        if (pathSegments.size < 4 || pathSegments.any { it.isEmpty() }) return listOf(sourceUrl)

        val rawPath = pathSegments.joinToString("/")
        val rawGithubUrl = "https://$RAW_GITHUB_HOST/$rawPath"
        val proxyUrl = "https://$GITHUB_PROXY_HOST/$RAW_GITHUB_HOST/$rawPath"
        val jsDelivrUrl = buildString {
            append("https://$JSDELIVR_HOST/gh/")
            append(pathSegments[0])
            append('/')
            append(pathSegments[1])
            append('@')
            append(pathSegments[2])
            append('/')
            append(pathSegments.drop(3).joinToString("/"))
        }
        return listOf(sourceUrl, rawGithubUrl, proxyUrl, jsDelivrUrl).distinct()
    }

    private fun OnlineHttpResponse.isHtmlDocument(): Boolean {
        val contentType = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value.orEmpty()
        if (contentType.substringBefore(';').trim().equals("text/html", ignoreCase = true)) return true
        val prefix = body.trimStart().take(32).lowercase()
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html")
    }

    private fun displayHost(url: String): String = runCatching {
        java.net.URI(url).host
    }.getOrNull().orEmpty().ifBlank { "unknown host" }

    private fun rootCauseMessage(error: Throwable): String {
        var cause = error
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
        return cause.message?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
    }

    @Synchronized
    fun list(): List<LxSourceDescriptor> = readAll().sortedBy { it.name.lowercase() }

    @Synchronized
    fun get(id: String): LxSourceDescriptor? = readAll().firstOrNull { it.id == id }

    @Synchronized
    fun readScript(id: String): Result<String> = runCatching {
        val descriptor = requireDescriptor(id)
        val file = scriptFile(id)
        if (!file.isFile || file.length() !in 1..MAX_SCRIPT_BYTES.toLong()) {
            throw IOException("Stored source script is missing or invalid")
        }
        val text = file.readText(Charsets.UTF_8)
        if (sha256(text.toByteArray(Charsets.UTF_8)) != descriptor.sha256) {
            throw IOException("Stored source script hash does not match metadata")
        }
        text
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean): Result<LxSourceDescriptor> = runCatching {
        val all = readAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) throw IOException("Source does not exist")
        if (enabled) readScript(id).getOrThrow()
        val updated = all[index].copy(enabled = enabled)
        all[index] = updated
        writeAll(all)
        updated
    }

    @Synchronized
    fun delete(id: String): Result<Unit> = runCatching {
        val all = readAll().toMutableList()
        if (all.none { it.id == id }) throw IOException("Source does not exist")
        val file = scriptFile(id)
        if (file.exists() && !file.delete()) throw IOException("Unable to delete source script")
        all.removeAll { it.id == id }
        writeAll(all)
    }

    suspend fun openEnabledHost(id: String): Result<LxSourceHost> = withContext(Dispatchers.IO) {
        runCatching {
            val descriptor = get(id) ?: throw IOException("Source does not exist")
            if (!descriptor.enabled) throw IOException("Source is disabled")
            val host = LxSourceHost(descriptor, readScript(id).getOrThrow(), appContext)
            try {
                val runtimeDescriptor = host.initialize().getOrThrow()
                replaceRuntimeMetadata(runtimeDescriptor)
                host
            } catch (error: Exception) {
                host.close()
                throw error
            }
        }
    }

    @Synchronized
    private fun persistDownloaded(sourceUrl: String, script: String, sizeBytes: Long): LxSourceDescriptor {
        val all = readAll().toMutableList()
        val id = "lx_${sha256(sourceUrl.toByteArray(Charsets.UTF_8)).take(24)}"
        val scriptHash = sha256(script.toByteArray(Charsets.UTF_8))
        val existingIndex = all.indexOfFirst { it.id == id }
        if (existingIndex < 0 && all.size >= MAX_SOURCES) throw IOException("Too many imported sources")
        val existing = all.getOrNull(existingIndex)
        val hashChanged = existing != null && existing.sha256 != scriptHash
        val header = parseScriptHeader(script)
        val descriptor = LxSourceDescriptor(
            id = id,
            name = existing?.name ?: header.name ?: sourceDisplayName(sourceUrl),
            version = existing?.version?.takeIf { it != "unknown" } ?: header.version ?: "unknown",
            author = existing?.author?.ifBlank { null } ?: header.author.orEmpty(),
            description = existing?.description?.ifBlank { null } ?: header.description.orEmpty(),
            originalSource = sourceUrl,
            sha256 = scriptHash,
            enabled = existing?.enabled == true && !hashChanged,
            importedAtEpochMs = System.currentTimeMillis(),
            scriptSizeBytes = sizeBytes,
            providers = if (hashChanged) emptyList() else existing?.providers.orEmpty()
        )

        val destination = scriptFile(id)
        val temporary = File(sourcesDirectory, "$id.tmp")
        temporary.writeText(script, Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(), destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (existingIndex >= 0) all[existingIndex] = descriptor else all += descriptor
        writeAll(all)
        return descriptor
    }

    @Synchronized
    private fun replaceRuntimeMetadata(runtime: LxSourceDescriptor) {
        val all = readAll().toMutableList()
        val index = all.indexOfFirst { it.id == runtime.id }
        if (index < 0) return
        val stored = all[index]
        all[index] = stored.copy(
            name = runtime.name.ifBlank { stored.name },
            version = runtime.version.ifBlank { stored.version },
            author = runtime.author.ifBlank { stored.author },
            description = runtime.description.ifBlank { stored.description },
            providers = runtime.providers
        )
        writeAll(all)
    }

    private fun requireDescriptor(id: String): LxSourceDescriptor {
        if (!ID_PATTERN.matches(id)) throw IOException("Invalid source ID")
        return readAll().firstOrNull { it.id == id } ?: throw IOException("Source does not exist")
    }

    private fun scriptFile(id: String): File {
        if (!ID_PATTERN.matches(id)) throw IOException("Invalid source ID")
        val file = File(sourcesDirectory, "$id.js")
        if (!file.canonicalPath.startsWith(sourcesDirectory.canonicalPath + File.separator)) {
            throw IOException("Invalid source path")
        }
        return file
    }

    private fun readAll(): List<LxSourceDescriptor> {
        val text = preferences.getString(SOURCES_KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(text) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                runCatching { descriptorFromJson(array.getJSONObject(index)) }.getOrNull()?.let(::add)
            }
        }
    }

    private fun writeAll(descriptors: List<LxSourceDescriptor>) {
        val json = JSONArray().apply { descriptors.forEach { put(descriptorToJson(it)) } }.toString()
        if (!preferences.edit().putString(SOURCES_KEY, json).commit()) {
            throw IOException("Unable to persist source metadata")
        }
    }

    private fun descriptorToJson(value: LxSourceDescriptor): JSONObject = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("version", value.version)
        .put("author", value.author)
        .put("description", value.description)
        .put("originalSource", value.originalSource)
        .put("sha256", value.sha256)
        .put("enabled", value.enabled)
        .put("importedAtEpochMs", value.importedAtEpochMs)
        .put("scriptSizeBytes", value.scriptSizeBytes)
        .put("providers", JSONArray().apply { value.providers.forEach { put(providerToJson(it)) } })

    private fun descriptorFromJson(json: JSONObject): LxSourceDescriptor = LxSourceDescriptor(
        id = json.getString("id"),
        name = json.getString("name"),
        version = json.optString("version", "unknown"),
        author = json.optString("author", ""),
        description = json.optString("description", ""),
        originalSource = json.getString("originalSource"),
        sha256 = json.getString("sha256"),
        enabled = json.optBoolean("enabled", false),
        importedAtEpochMs = json.optLong("importedAtEpochMs", 0L),
        scriptSizeBytes = json.optLong("scriptSizeBytes", 0L),
        providers = json.optJSONArray("providers")?.let { providers ->
            buildList {
                for (index in 0 until providers.length()) add(providerFromJson(providers.getJSONObject(index)))
            }
        }.orEmpty()
    )

    private fun providerToJson(value: LxProviderDescriptor): JSONObject = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("actions", JSONArray(value.actions))
        .put("qualities", JSONArray(value.qualities))

    private fun providerFromJson(json: JSONObject): LxProviderDescriptor = LxProviderDescriptor(
        id = json.getString("id"),
        name = json.optString("name", json.getString("id")),
        actions = json.optJSONArray("actions")?.toStringList().orEmpty(),
        qualities = json.optJSONArray("qualities")?.toStringList().orEmpty()
    )

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }.filter { it.isNotBlank() }

    private fun sourceDisplayName(url: String): String {
        if (url.startsWith("local://")) {
            return url.removePrefix("local://").trim().take(80).ifBlank { "本地音源" }
        }
        val raw = runCatching {
            val parsed = java.net.URI(url)
            parsed.path.substringAfterLast('/').substringBeforeLast('.').ifBlank { parsed.host }
        }.getOrDefault("Imported LX Source")
        return raw.trim().take(80).ifBlank { "Imported LX Source" }
    }

    private fun parseScriptHeader(script: String): LxScriptHeader = LxScriptHeader(
        name = headerTag(script, "name"),
        version = headerTag(script, "version"),
        author = headerTag(script, "author"),
        description = headerTag(script, "description"),
    )

    private fun headerTag(script: String, tag: String): String? =
        Regex("@$tag\\s+([^\\r\\n@*]+)").find(script)
            ?.groupValues?.get(1)?.trim().orEmpty()
            .trimStart('*', ' ', '\t').trim()
            .ifBlank { null }

    private data class LxScriptHeader(
        val name: String?,
        val version: String?,
        val author: String?,
        val description: String?
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREFERENCES_NAME = "muse_online_sources"
        const val SOURCES_KEY = "sources"
        const val SOURCES_DIRECTORY = "online_sources"
        const val MAX_SCRIPT_BYTES = 4 * 1024 * 1024
        const val MAX_SOURCES = 32
        const val IMPORT_TIMEOUT_MS = 15_000L
        const val IMPORT_FALLBACK_ATTEMPT_TIMEOUT_MS = 10_000L
        const val RAW_GITHUB_HOST = "raw.githubusercontent.com"
        const val GITHUB_PROXY_HOST = "ghproxy.net"
        const val JSDELIVR_HOST = "cdn.jsdelivr.net"
        val ID_PATTERN = Regex("^lx_[a-f0-9]{24}$")
    }
}
