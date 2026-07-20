package com.caipan.music.plugin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.net.IDN
import java.util.UUID
import java.util.zip.ZipInputStream

data class PluginManifest(
    val apiVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val entry: String,
    val hooks: List<String>,
    val networkAllowHosts: Set<String>,
    val webUiEntry: String?,
    val permissions: Set<String>,
    val playerGestures: List<PlayerGestureContribution>
)

data class PlayerGestureContribution(
    val gesture: String,
    val action: String,
    val entry: String?
)

data class InstalledPlugin(
    val manifest: PluginManifest,
    val directory: File,
    val sha256: String
)

class PluginPackageInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val pluginsDir = File(appContext.filesDir, "plugins")
    private val stagingDir = File(pluginsDir, ".staging")

    fun install(uri: Uri, reservedIds: Set<String>): Result<InstalledPlugin> = runCatching {
        validateDisplayName(uri)
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选插件包")
        input.use { installPackage(it, reservedIds) }
    }

    fun installBundled(assetPath: String, reservedIds: Set<String>): Result<InstalledPlugin> = runCatching {
        appContext.assets.open(assetPath).use { installPackage(it, reservedIds) }
    }

    private fun installPackage(input: InputStream, reservedIds: Set<String>): InstalledPlugin {
        pluginsDir.mkdirs()
        stagingDir.mkdirs()

        val staging = File(stagingDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val packageFile = File(staging, "package.museplugin")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(packageFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_PACKAGE_BYTES) throw IOException("插件包不能超过 4 MiB")
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            extractPackage(packageFile, staging)
            packageFile.delete()

            val manifestFile = File(staging, MANIFEST_FILE)
            val scriptFile = File(staging, ENTRY_FILE)
            if (!manifestFile.isFile || !scriptFile.isFile) {
                throw IOException("插件包必须包含 plugin.json 和 index.js")
            }
            val manifest = parseManifest(manifestFile.readText(Charsets.UTF_8))
            if (manifest.id in reservedIds) throw IOException("插件 ID 与内置插件冲突")
            manifest.webUiEntry?.let { entry ->
                if (!safeChild(staging, entry).isFile) throw IOException("WebUI 入口不存在")
            }
            File(staging, HASH_FILE).writeText(sha256, Charsets.US_ASCII)

            val destination = File(pluginsDir, manifest.id)
            val backup = File(stagingDir, "${manifest.id}.backup-${UUID.randomUUID()}")
            if (destination.exists() && !destination.renameTo(backup)) {
                throw IOException("无法备份旧版插件")
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                throw IOException("无法完成插件安装")
            }
            backup.deleteRecursively()
            return InstalledPlugin(manifest, destination, sha256)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun delete(pluginId: String): Result<Unit> = runCatching {
        require(ID_PATTERN.matches(pluginId)) { "插件 ID 无效" }
        val target = File(pluginsDir, pluginId)
        require(target.canonicalPath.startsWith(pluginsDir.canonicalPath + File.separator)) { "插件路径无效" }
        require(target.isDirectory) { "插件不存在" }
        target.deleteRecursively()
        require(!target.exists()) { "无法删除插件文件" }
    }

    fun listInstalled(): List<InstalledPlugin> {
        if (!pluginsDir.isDirectory) return emptyList()
        return pluginsDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { directory ->
                runCatching {
                    val manifest = parseManifest(File(directory, MANIFEST_FILE).readText(Charsets.UTF_8))
                    val script = File(directory, manifest.entry)
                    if (!script.isFile || script.length() > MAX_SCRIPT_BYTES) {
                        throw IOException("入口脚本无效")
                    }
                    manifest.webUiEntry?.let { entry ->
                        val webEntry = safeChild(directory, entry)
                        if (!webEntry.isFile) throw IOException("WebUI 入口无效")
                    }
                    val hash = File(directory, HASH_FILE).takeIf { it.isFile }
                        ?.readText(Charsets.US_ASCII)?.trim().orEmpty()
                    InstalledPlugin(manifest, directory, hash)
                }.getOrNull()
            }
            .sortedBy { it.manifest.id }
    }

    private fun validateDisplayName(uri: Uri) {
        val name = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        if (name != null && !name.endsWith(".museplugin", ignoreCase = true)) {
            throw IOException("请选择 .museplugin 插件包")
        }
    }

    private fun extractPackage(packageFile: File, staging: File) {
        val extracted = mutableSetOf<String>()
        var totalBytes = 0L
        var entries = 0
        ZipInputStream(FileInputStream(packageFile)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > MAX_ENTRIES) throw IOException("插件包文件数量过多")
                if (entry.isDirectory) {
                    val name = normalizePackagePath(entry.name.trimEnd('/'))
                    if (name != "web" && !name.startsWith("web/")) throw IOException("插件包包含非法目录：$name")
                    safeChild(staging, name).mkdirs()
                    zip.closeEntry()
                    continue
                }
                val name = normalizePackagePath(entry.name)
                if (!isAllowedPackageFile(name) || !extracted.add(name.lowercase())) {
                    throw IOException("插件包包含非法文件：$name")
                }
                val target = safeChild(staging, name)
                target.parentFile?.mkdirs()
                val entryLimit = when (name) {
                    MANIFEST_FILE -> MAX_MANIFEST_BYTES
                    ENTRY_FILE -> MAX_SCRIPT_BYTES
                    else -> MAX_WEB_FILE_BYTES
                }
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var entryBytes = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalBytes += read
                        if (entryBytes > entryLimit || totalBytes > MAX_EXTRACTED_BYTES) {
                            throw IOException("插件包解压内容过大")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }
        if (MANIFEST_FILE !in extracted || ENTRY_FILE !in extracted) throw IOException("插件包内容不完整")
    }

    private fun parseManifest(text: String): PluginManifest {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) {
            throw IOException("plugin.json 过大")
        }
        val json = JSONObject(text)
        val hooksJson = json.optJSONArray("hooks") ?: JSONArray()
        val hooks = buildList {
            for (index in 0 until hooksJson.length()) add(hooksJson.getString(index))
        }
        if (json.getInt("apiVersion") != 1) throw IOException("不支持的插件 API 版本")
        val id = json.getString("id")
        if (!ID_PATTERN.matches(id)) throw IOException("插件 ID 格式无效")
        val entry = json.optString("entry", ENTRY_FILE)
        if (entry != ENTRY_FILE) throw IOException("当前仅支持 index.js 入口")
        if (hooks.distinct().size != hooks.size || hooks.any { it !in ALLOWED_HOOKS }) {
            throw IOException("插件声明了不支持的 Hook")
        }
        val allowHosts = parseAllowHosts(json.optJSONObject("network"))
        val permissionsJson = json.optJSONArray("permissions") ?: JSONArray()
        val permissions = buildSet {
            for (index in 0 until permissionsJson.length()) {
                val permission = permissionsJson.getString(index)
                if (permission !in ALLOWED_PERMISSIONS || !add(permission)) {
                    throw IOException("插件声明了无效或重复的权限")
                }
            }
        }
        val webUiEntry = json.optJSONObject("webUi")?.let { webUi ->
            normalizeWebUiEntry(webUi.getString("entry"))
        }
        val playerGesturesJson = json.optJSONObject("contributes")?.optJSONArray("playerGestures") ?: JSONArray()
        val playerGestures = buildList<PlayerGestureContribution> {
            if (playerGesturesJson.length() > MAX_PLAYER_GESTURES) throw IOException("播放页手势贡献过多")
            for (index in 0 until playerGesturesJson.length()) {
                val contribution = playerGesturesJson.getJSONObject(index)
                val gesture = contribution.getString("gesture")
                val action = contribution.getString("action")
                val contributionEntry = contribution.optString("entry").takeIf { it.isNotBlank() }
                    ?.let(::normalizeWebUiEntry)
                if (gesture !in ALLOWED_PLAYER_GESTURES || action !in ALLOWED_PLAYER_GESTURE_ACTIONS) {
                    throw IOException("插件声明了不支持的播放页手势")
                }
                if (action == "openWebUi" && webUiEntry == null) {
                    throw IOException("打开 WebUI 的手势贡献需要声明 webUi")
                }
                if (any { existing -> existing.gesture == gesture }) throw IOException("播放页手势贡献重复")
                add(PlayerGestureContribution(gesture, action, contributionEntry))
            }
        }
        return PluginManifest(
            apiVersion = 1,
            id = id,
            name = bounded(json.getString("name"), "名称", 128),
            version = bounded(json.getString("version"), "版本", 64),
            author = bounded(json.optString("author", "未知作者"), "作者", 128),
            description = bounded(json.optString("description", ""), "描述", 1024, allowBlank = true),
            entry = entry,
            hooks = hooks,
            networkAllowHosts = allowHosts,
            webUiEntry = webUiEntry,
            permissions = permissions,
            playerGestures = playerGestures
        )
    }

    private fun parseAllowHosts(network: JSONObject?): Set<String> {
        val array = network?.optJSONArray("allowHosts") ?: return emptySet()
        if (array.length() > MAX_HOSTS) throw IOException("插件申请的网络域名过多")
        return buildSet {
            for (index in 0 until array.length()) {
                val raw = array.getString(index)
                val host = runCatching { IDN.toASCII(raw.trim().trimEnd('.')).lowercase() }
                    .getOrElse { throw IOException("网络域名无效") }
                if (!HOST_PATTERN.matches(host) || host == "localhost" || host.length > 253 ||
                    host.any { it == ':' } || host.split('.').all { it.toIntOrNull() != null }) {
                    throw IOException("网络域名无效：$raw")
                }
                if (!add(host)) throw IOException("网络域名重复：$host")
            }
        }
    }

    private fun normalizeWebUiEntry(value: String): String {
        val path = normalizePackagePath(value)
        if (!path.startsWith("web/") || !path.endsWith(".html", ignoreCase = true)) {
            throw IOException("WebUI 入口必须是 web/ 下的 HTML 文件")
        }
        return path
    }

    private fun normalizePackagePath(value: String): String {
        val normalized = value.replace('\\', '/')
        if (normalized.isBlank() || normalized.length > 240 || normalized.startsWith('/') ||
            normalized.contains(':') || normalized.contains('\u0000') ||
            normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            throw IOException("插件包路径无效")
        }
        return normalized
    }

    private fun isAllowedPackageFile(name: String): Boolean {
        if (name == MANIFEST_FILE || name == ENTRY_FILE) return true
        if (!name.startsWith("web/")) return false
        return name.substringAfterLast('.', "").lowercase() in WEB_EXTENSIONS
    }

    private fun safeChild(root: File, relative: String): File {
        val target = File(root, relative)
        if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
            throw IOException("插件包路径无效")
        }
        return target
    }

    private fun bounded(value: String, field: String, max: Int, allowBlank: Boolean = false): String {
        if ((!allowBlank && value.isBlank()) || value.length > max) throw IOException("插件${field}无效")
        return value
    }

    private companion object {
        const val MANIFEST_FILE = "plugin.json"
        const val ENTRY_FILE = "index.js"
        const val HASH_FILE = "package.sha256"
        const val MAX_PACKAGE_BYTES = 4L * 1024 * 1024
        const val MAX_EXTRACTED_BYTES = 8L * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 64L * 1024
        const val MAX_SCRIPT_BYTES = 1024L * 1024
        const val MAX_WEB_FILE_BYTES = 2L * 1024 * 1024
        const val MAX_ENTRIES = 64
        const val MAX_HOSTS = 16
        const val MAX_PLAYER_GESTURES = 4
        val ID_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_.-]{2,127}$")
        val HOST_PATTERN = Regex("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
        val ALLOWED_HOOKS = setOf("onEnable", "onDisable", "onShuffle", "onNextTrack", "onTrackFinished")
        val ALLOWED_PLAYER_GESTURES = setOf("artwork.swipeUp")
        val ALLOWED_PLAYER_GESTURE_ACTIONS = setOf("openWebUi")
        val ALLOWED_PERMISSIONS = setOf(
            "config",
            "player.read", "player.control",
            "queue.read", "queue.control",
            "library.read", "library.refresh",
            "playlists.read", "playlists.write", "playlists.delete",
            "lyrics.read", "stats.read",
            "theme.read", "theme.write",
            "glass.read", "glass.write",
            "equalizer.read", "equalizer.control",
            "profile.read", "profile.write",
            "externalPlayer.read", "externalPlayer.control",
            "lan.discovery", "lan.pairing", "lan.state", "lan.control", "lan.hosting", "lan.transfer",
            "network.request"
        )
        val WEB_EXTENSIONS = setOf("html", "js", "css", "json", "png", "jpg", "jpeg", "gif", "webp", "svg", "woff", "woff2")
    }
}
