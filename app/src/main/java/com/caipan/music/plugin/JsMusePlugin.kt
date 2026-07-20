package com.caipan.music.plugin

import com.caipan.music.model.Song
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.io.File

class JsMusePlugin(installed: InstalledPlugin, private val configProvider: () -> JSONObject) : MusePlugin {
    private val manifest = installed.manifest
    private val source = File(installed.directory, manifest.entry).readText(Charsets.UTF_8)
    private val contextFactory = LimitedContextFactory()
    private var scope: Scriptable? = null

    override val id = manifest.id
    override val name = manifest.name
    override val version = manifest.version
    override val author = manifest.author
    override val description = manifest.description
    override val hooks = manifest.hooks

    @Synchronized
    override fun onEnable() {
        scope = contextFactory.execute {
            val newScope = it.initSafeStandardObjects()
            it.evaluateString(newScope, source, "$id/index.js", 1, null)
            val pluginObject = ScriptableObject.getProperty(newScope, PLUGIN_OBJECT)
            require(pluginObject is Scriptable) { "脚本必须定义 globalThis.musePlugin" }
            newScope
        }
        callOptional("onEnable", JSONObject())
    }

    @Synchronized
    override fun onDisable() {
        runCatching { callOptional("onDisable", JSONObject()) }
        scope = null
    }

    @Synchronized
    override fun onShuffle(queue: List<Song>): List<Song>? {
        val result = callOptional("onShuffle", JSONArray().apply { queue.forEach { put(songJson(it)) } })
            ?: return null
        val ids = result as? JSONArray ?: return null
        val byId = queue.associateBy { it.id.toString() }
        return buildList {
            for (index in 0 until ids.length()) {
                val song = byId[ids.getString(index)] ?: return null
                add(song)
            }
        }
    }

    @Synchronized
    override fun onNextTrack(request: NextRequest): Song? {
        val payload = JSONObject()
            .put("trigger", request.trigger.name)
            .put("currentSong", request.currentSong?.let(::songJson) ?: JSONObject.NULL)
            .put("currentIndex", request.currentIndex)
            .put("queue", JSONArray().apply { request.queue.forEach { put(songJson(it)) } })
        val selectedId = callOptional("onNextTrack", payload) as? String ?: return null
        return request.queue.firstOrNull { it.id.toString() == selectedId }
    }

    @Synchronized
    override fun onTrackFinished(song: Song) {
        callOptional("onTrackFinished", songJson(song))
    }

    private fun callOptional(name: String, payload: Any): Any? {
        if (name !in hooks) return null
        val currentScope = scope ?: error("插件尚未启用")
        return contextFactory.execute { context ->
            val config = parseJson(context, currentScope, configProvider().toString())
            ScriptableObject.putProperty(currentScope, "museConfig", config)
            val pluginObject = ScriptableObject.getProperty(currentScope, PLUGIN_OBJECT) as? Scriptable
                ?: error("musePlugin 对象不存在")
            val function = ScriptableObject.getProperty(pluginObject, name)
            if (function !is Function) return@execute null
            val argument = parseJson(context, currentScope, payload.toString())
            val result = function.call(context, currentScope, pluginObject, arrayOf(argument))
            if (result == null || result === org.mozilla.javascript.Undefined.instance) null
            else JSONTokener(stringifyJson(context, currentScope, result)).nextValue()
        }
    }

    private fun parseJson(context: RhinoContext, currentScope: Scriptable, json: String): Any? {
        val jsonObject = ScriptableObject.getProperty(currentScope, "JSON") as Scriptable
        val parse = ScriptableObject.getProperty(jsonObject, "parse") as Function
        return parse.call(context, currentScope, jsonObject, arrayOf(json))
    }

    private fun stringifyJson(context: RhinoContext, currentScope: Scriptable, value: Any?): String {
        val jsonObject = ScriptableObject.getProperty(currentScope, "JSON") as Scriptable
        val stringify = ScriptableObject.getProperty(jsonObject, "stringify") as Function
        val result = stringify.call(context, currentScope, jsonObject, arrayOf(value)).toString()
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_RESULT_BYTES) { "Hook 返回内容过大" }
        return result
    }

    private fun songJson(song: Song) = JSONObject()
        .put("id", song.id.toString())
        .put("title", song.title)
        .put("artist", song.artist)
        .put("album", song.album)
        .put("durationMs", song.durationMs)

    private class LimitedContextFactory : ContextFactory() {
        private val deadlineNanos = ThreadLocal<Long>()

        override fun makeContext(): RhinoContext = super.makeContext().apply {
            optimizationLevel = -1
            languageVersion = RhinoContext.VERSION_ES6
            instructionObserverThreshold = 10_000
            setClassShutter(ClassShutter { false })
        }

        override fun observeInstructionCount(context: RhinoContext, instructionCount: Int) {
            if (System.nanoTime() > (deadlineNanos.get() ?: Long.MAX_VALUE)) {
                throw PluginTimeoutException()
            }
        }

        fun <T> execute(block: (RhinoContext) -> T): T {
            deadlineNanos.set(System.nanoTime() + HOOK_TIMEOUT_NANOS)
            return try {
                call { context -> block(context) }
            } finally {
                deadlineNanos.remove()
            }
        }
    }

    private class PluginTimeoutException : RuntimeException("插件执行超时")

    private companion object {
        const val PLUGIN_OBJECT = "musePlugin"
        const val MAX_RESULT_BYTES = 1024 * 1024
        const val HOOK_TIMEOUT_NANOS = 100_000_000L
    }
}