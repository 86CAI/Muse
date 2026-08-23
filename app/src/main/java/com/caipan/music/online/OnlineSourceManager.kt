package com.caipan.music.online

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Coordinates installed LX scripts and keeps at most one initialized runtime per script. */
class OnlineSourceManager(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val store = OnlineSourceStore(context)
    private val mutex = Mutex()
    private val hosts = linkedMapOf<String, LxSourceHost>()

    fun listSources(): List<LxSourceDescriptor> = store.list()

    suspend fun importFromUrl(url: String): Result<LxSourceDescriptor> = try {
        val imported = store.importFromUrl(url).getOrThrow()
        mutex.withLock { hosts.remove(imported.id)?.close() }
        Result.success(imported)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun importFromText(name: String, script: String): Result<LxSourceDescriptor> = try {
        val imported = store.importFromText(name, script).getOrThrow()
        mutex.withLock { hosts.remove(imported.id)?.close() }
        Result.success(imported)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Result<LxSourceDescriptor> = runCatching {
        if (!enabled) {
            val updated = store.setEnabled(id, false).getOrThrow()
            mutex.withLock { hosts.remove(id)?.close() }
            return@runCatching updated
        }

        store.setEnabled(id, true).getOrThrow()
        try {
            val host = store.openEnabledHost(id).getOrThrow()
            mutex.withLock {
                hosts.remove(id)?.close()
                hosts[id] = host
            }
            host.descriptor
        } catch (error: Exception) {
            store.setEnabled(id, false)
            val name = store.list().firstOrNull { it.id == id }?.name ?: id
            logFailure("init-$name", OnlineTrack("", "", "", emptyList(), "", 0L, null), error)
            throw IOException("音源初始化失败：${error.message ?: "脚本不兼容"}", error)
        }
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        mutex.withLock { hosts.remove(id)?.close() }
        store.delete(id).getOrThrow()
    }

    suspend fun resolve(
        track: OnlineTrack,
        preferredQuality: String = "320k"
    ): Result<LxResolvedMusicUrl> = try {
        val enabled = store.list().filter(LxSourceDescriptor::enabled)
        if (enabled.isEmpty()) throw IOException("请先在插件页导入并启用一个 LX 在线音源")

        val failures = mutableListOf<String>()
        var supported = false
        for (source in enabled) {
            val host = try {
                hostFor(source.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logFailure("init-${source.name}", track, error)
                failures += "${source.name}：${error.message ?: "初始化失败"}"
                continue
            }
            val provider = host.descriptor.providers.firstOrNull { it.id == track.source }
                ?: continue
            if (provider.actions.isNotEmpty() && "musicUrl" !in provider.actions) continue
            supported = true
            val qualities = qualityCandidates(preferredQuality, provider.qualities)
            for (quality in qualities) {
                var resolved = host.resolveMusicUrl(track, quality)
                if (resolved.isSuccess) {
                    return Result.success(resolved.getOrThrow())
                }
                var error = resolved.exceptionOrNull()
                if (error?.message?.contains("already closed", ignoreCase = true) == true) {
                    // Runtime 已关闭：丢弃全部 host 缓存（引擎级残留时全部重建）
                    mutex.withLock {
                        hosts.values.forEach { runCatching { it.close() } }
                        hosts.clear()
                    }
                }
                logFailure("resolve-${source.name}", track, error)
                failures += "${source.name} $quality：${error?.message ?: "解析失败"}"
                if (error.isRetryableResolutionFailure()) {
                    delay(RESOLVE_RETRY_DELAY_MS)
                    resolved = host.resolveMusicUrl(track, quality)
                    if (resolved.isSuccess) {
                        return Result.success(resolved.getOrThrow())
                    }
                    error = resolved.exceptionOrNull()
                    logFailure("resolve-retry-${source.name}", track, error)
                    failures += "${source.name} $quality retry：${error?.message ?: "解析失败"}"
                }
            }
        }

        if (!supported) {
            throw IOException("已启用的音源不支持 ${track.source} 平台")
        }
        throw IOException(failures.joinToString("；").ifBlank { "所有在线音源均解析失败" })
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        logFailure("resolve", track, error)
        Result.failure(error)
    }

    private fun logFailure(phase: String, track: OnlineTrack, error: Throwable?) {
        if (error == null) return
        runCatching {
            val logFile = java.io.File(appContext.filesDir, "lx_host_errors.log")
            val stamp = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
            ).format(java.util.Date())
            val stack = error.stackTrace.take(10).joinToString("\n") { "    at $it" }
            logFile.appendText(
                "[$stamp] v${com.caipan.music.BuildConfig.VERSION_NAME} [$phase] ${track.source}:${track.sourceId} -> ${error.toString()}\n$stack\n\n"
            )
        }
    }

    suspend fun search(query: String, page: Int = 1): Result<List<OnlineTrack>> = try {
        val enabled = store.list().filter(LxSourceDescriptor::enabled)
        if (enabled.isEmpty()) throw IOException("请先在插件页导入并启用一个 LX 在线音源")

        val failures = mutableListOf<String>()
        for (source in enabled) {
            val host = try {
                hostFor(source.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logFailure("init-${source.name}", OnlineTrack("", "", "", emptyList(), "", 0L, null), error)
                failures += "${source.name}：${error.message ?: "初始化失败"}"
                continue
            }
            if (!host.supportsSearch) {
                failures += "${source.name}：不支持搜索"
                continue
            }
            val result = host.search(query, page = page)
            if (result.isSuccess) {
                val tracks = result.getOrThrow().distinctBy(OnlineTrack::stableId)
                if (tracks.isNotEmpty()) return Result.success(tracks)
                failures += "${source.name}：无搜索结果"
                continue
            }
            logFailure("search-${source.name}", OnlineTrack("", "", "", emptyList(), "", 0L, null), result.exceptionOrNull())
            failures += "${source.name}：${result.exceptionOrNull()?.message ?: "搜索失败"}"
        }

        throw IOException(failures.joinToString("；").ifBlank { "所有在线音源均不支持搜索" })
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun resolveLyrics(track: OnlineTrack): Result<String?> = try {
        val enabled = store.list().filter(LxSourceDescriptor::enabled)
        if (enabled.isEmpty()) throw IOException("请先在插件页导入并启用一个 LX 在线音源")

        val failures = mutableListOf<String>()
        var supported = false
        for (source in enabled) {
            val host = try {
                hostFor(source.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += "："
                continue
            }
            val provider = host.descriptor.providers.firstOrNull { it.id == track.source }
                ?: continue
            supported = true
            val resolved = host.resolveLyrics(track)
            if (resolved.isSuccess) {
                val lyrics = resolved.getOrThrow()
                if (!lyrics.isNullOrBlank()) return Result.success(lyrics)
                failures += "：无歌词"
                continue
            }
            failures += "："
        }

        if (!supported) {
            throw IOException("已启用的音源不支持  平台")
        }
        Result.success(null)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        logFailure("lyrics", track, error)
        Result.failure(error)
    }
    private fun Throwable?.isRetryableResolutionFailure(): Boolean {
        val message = this?.message?.lowercase().orEmpty()
        return RETRYABLE_RESOLUTION_ERRORS.any(message::contains)
    }

    private suspend fun hostFor(id: String): LxSourceHost {
        mutex.withLock { hosts[id]?.let { return it } }
        val opened = store.openEnabledHost(id).getOrThrow()
        return mutex.withLock {
            hosts[id]?.also { opened.close() } ?: opened.also { hosts[id] = it }
        }
    }

    private fun qualityCandidates(preferred: String, available: List<String>): List<String> {
        val supported = available.ifEmpty { listOf("320k", "128k") }
        return listOf(preferred, "128k", "320k", "flac", "flac24bit")
            .filter { it in supported }
            .distinct()
            .ifEmpty { listOf(supported.first()) }
    }

    override fun close() {
        hosts.values.forEach(LxSourceHost::close)
        hosts.clear()
    }

    private companion object {
        const val RESOLVE_RETRY_DELAY_MS = 1_200L
        val RETRYABLE_RESOLUTION_ERRORS = listOf(
            "internal server error", "temporarily unavailable", "timeout", "timed out",
            "connection reset", "connection refused", "http 502", "http 503", "http 504"
        )
    }
}
