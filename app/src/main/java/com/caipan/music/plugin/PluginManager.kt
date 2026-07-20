package com.caipan.music.plugin

import android.content.Context
import android.net.Uri
import android.util.Log
import com.caipan.music.model.Song
import org.json.JSONObject
import java.io.File

const val OFFICIAL_LAN_REMOTE_PLUGIN_ID = "com.caipan.muse.lan-remote"
const val OFFICIAL_GLASS_LAB_PLUGIN_ID = "com.caipan.muse.glass-lab"

data class PluginWebUiSession(
    val pluginId: String,
    val rootDirectory: File,
    val entry: String,
    val allowHosts: Set<String>,
    val permissions: Set<String>,
    val grantedPermissions: Set<String>
)

class PluginManager(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("muse_plugins", Context.MODE_PRIVATE)
    private val plugins = linkedMapOf<String, MusePlugin>()
    private val enabledIds = mutableSetOf<String>()
    private val builtInIds = mutableSetOf<String>()
    private val installer = PluginPackageInstaller(context)
    private val installedPlugins = mutableMapOf<String, InstalledPlugin>()
    private val networkProxy = PluginNetworkProxy()

    @Synchronized
    fun register(plugin: MusePlugin) {
        builtInIds += plugin.id
        plugins[plugin.id] = plugin
        if (preferences.getBoolean(enabledKey(plugin.id), plugin.enabledByDefault)) {
            if (enablePlugin(plugin)) enabledIds += plugin.id
        }
        Log.i(TAG, "Registered ${plugin.id}; enabled=${plugin.id in enabledIds}; hooks=${plugin.hooks}")
    }

    @Synchronized
    fun loadInstalledPlugins() {
        installer.listInstalled().forEach { installed ->
            if (installed.manifest.id !in builtInIds) {
                installedPlugins[installed.manifest.id] = installed
                registerExternal(createJsPlugin(installed))
            }
        }
    }

    fun installBundledPlugins() {
        installer.installBundled("plugins/lan-remote-webui.museplugin", builtInIds)
            .onFailure { Log.e(TAG, "Failed to install official LAN Remote plugin", it) }
        installer.installBundled("plugins/glass-lab.museplugin", builtInIds)
            .onSuccess { installed -> initializeTrustedGlassLab(installed) }
            .onFailure { Log.e(TAG, "Failed to install Glass Lab plugin", it) }
    }

    /** The bundled Glass Lab is Muse-authored and useless without its WebUI permissions. */
    private fun initializeTrustedGlassLab(installed: InstalledPlugin) {
        if (installed.manifest.id != OFFICIAL_GLASS_LAB_PLUGIN_ID ||
            preferences.contains(trustedInitializedKey(installed.manifest.id))) return
        preferences.edit()
            .putBoolean(enabledKey(installed.manifest.id), true)
            .putBoolean(permissionKey(installed.manifest.id, "glass.read"), true)
            .putBoolean(permissionKey(installed.manifest.id, "glass.write"), true)
            .putBoolean(trustedInitializedKey(installed.manifest.id), true)
            .apply()
    }

    fun install(uri: Uri): Result<PluginInfo> = installer.install(uri, synchronized(this) { builtInIds.toSet() })
        .mapCatching { installed ->
            val plugin = createJsPlugin(installed)
            synchronized(this) {
                installedPlugins[plugin.id] = installed
                plugins[plugin.id]?.takeIf { plugin.id in enabledIds }?.let { old ->
                    runCatching { old.onDisable() }
                }
                enabledIds -= plugin.id
                preferences.edit().putBoolean(enabledKey(plugin.id), false).apply()
                registerExternal(plugin)
                pluginInfo().first { it.id == plugin.id }
            }
        }

    @Synchronized
    private fun registerExternal(plugin: MusePlugin) {
        if (plugin.id in builtInIds) return
        plugins[plugin.id] = plugin
        if (preferences.getBoolean(enabledKey(plugin.id), false) && enablePlugin(plugin)) {
            enabledIds += plugin.id
        }
        Log.i(TAG, "Loaded external ${plugin.id}; enabled=${plugin.id in enabledIds}; hooks=${plugin.hooks}")
    }

    @Synchronized
    fun pluginInfo(): List<PluginInfo> = plugins.values.map { plugin ->
        val installed = installedPlugins[plugin.id]
        PluginInfo(
            id = plugin.id,
            name = plugin.name,
            version = plugin.version,
            author = plugin.author,
            description = plugin.description,
            hooks = plugin.hooks,
            enabled = plugin.id in enabledIds,
            external = installed != null,
            hasWebUi = installed?.manifest?.webUiEntry != null,
            networkAllowHosts = installed?.manifest?.networkAllowHosts?.sorted().orEmpty(),
            permissions = installed?.manifest?.permissions?.sorted().orEmpty(),
            grantedPermissions = installed?.let { grantedPermissions(it.manifest.id, it.manifest.permissions).sorted() }.orEmpty(),
            playerGestures = installed?.manifest?.playerGestures.orEmpty()
        )
    }

    @Synchronized
    fun openWebUi(pluginId: String, entryOverride: String? = null): Result<PluginWebUiSession> = runCatching {
        require(pluginId in enabledIds) { "插件未启用" }
        val installed = installedPlugins[pluginId] ?: error("仅外部插件支持 WebUI")
        val entry = entryOverride ?: installed.manifest.webUiEntry ?: error("该插件没有 WebUI")
        require(File(installed.directory, entry).isFile) { "插件 WebUI 页面不存在" }
        PluginWebUiSession(pluginId, File(installed.directory, "web"), entry.removePrefix("web/"),
            installed.manifest.networkAllowHosts, installed.manifest.permissions,
            grantedPermissions(pluginId, installed.manifest.permissions))
    }

    @Synchronized
    fun invokePlayerGesture(gesture: String): Result<PluginWebUiSession>? {
        val contribution = installedPlugins.values.firstNotNullOfOrNull { installed ->
            if (installed.manifest.id !in enabledIds) null
            else installed.manifest.playerGestures.firstOrNull { it.gesture == gesture }
                ?.let { installed.manifest.id to it }
        } ?: return null
        return when (contribution.second.action) {
            "openWebUi" -> openWebUi(contribution.first, contribution.second.entry)
            else -> Result.failure(IllegalStateException("不支持的插件手势动作"))
        }
    }

    private fun createJsPlugin(installed: InstalledPlugin) =
        JsMusePlugin(installed) { readConfigSnapshot(installed.manifest.id) }

    private fun readConfigSnapshot(pluginId: String): JSONObject =
        JSONObject(preferences.getString(configKey(pluginId), "{}") ?: "{}")

    @Synchronized
    fun readConfig(pluginId: String): JSONObject {
        requirePermission(pluginId, "config")
        return readConfigSnapshot(pluginId)
    }

    @Synchronized
    fun writeConfig(pluginId: String, config: JSONObject): JSONObject {
        requirePermission(pluginId, "config")
        val text = config.toString()
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_CONFIG_BYTES) { "插件配置不能超过 64 KiB" }
        preferences.edit().putString(configKey(pluginId), text).apply()
        return JSONObject(text)
    }

    @Synchronized
    fun requirePermission(pluginId: String, permission: String) {
        val manifest = installedPlugins[pluginId]?.manifest ?: error("插件不存在")
        require(pluginId in enabledIds) { "插件未启用" }
        require(permission in manifest.permissions) { "插件未声明 $permission 权限" }
        require(permission in grantedPermissions(pluginId, manifest.permissions)) { "用户未授权 $permission 权限" }
    }

    @Synchronized
    fun isEnabledAndGranted(pluginId: String, permission: String): Boolean {
        val manifest = installedPlugins[pluginId]?.manifest ?: return false
        return pluginId in enabledIds && permission in manifest.permissions &&
            permission in grantedPermissions(pluginId, manifest.permissions)
    }

    @Synchronized
    fun setPermissionGranted(pluginId: String, permission: String, granted: Boolean) {
        val manifest = installedPlugins[pluginId]?.manifest ?: error("插件不存在")
        require(permission in manifest.permissions) { "插件未声明 $permission 权限" }
        preferences.edit().putBoolean(permissionKey(pluginId, permission), granted).apply()
    }

    suspend fun executeWebUiRequest(pluginId: String, request: PluginNetworkRequest): Result<JSONObject> = runCatching {
        val allowHosts = synchronized(this) {
            val manifest = installedPlugins[pluginId]?.manifest ?: error("插件不存在")
            require(NETWORK_PERMISSION in manifest.permissions) { "插件未声明 $NETWORK_PERMISSION 权限" }
            require(NETWORK_PERMISSION in grantedPermissions(pluginId, manifest.permissions)) { "用户未授权 $NETWORK_PERMISSION 权限" }
            manifest.networkAllowHosts
        }
        networkProxy.execute(allowHosts, request)
    }

    @Synchronized
    fun deleteExternal(id: String): Result<Unit> = runCatching {
        require(id !in builtInIds) { "内置插件不能删除，只能关闭" }
        val plugin = plugins[id]
        if (plugin != null && id in enabledIds) {
            runCatching { plugin.onDisable() }
            enabledIds.remove(id)
        }
        preferences.edit().remove(enabledKey(id)).remove(configKey(id)).apply()
        installedPlugins.remove(id)
        plugins.remove(id)
        installer.delete(id).getOrThrow()
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        val plugin = plugins[id] ?: return
        if (enabled && id !in enabledIds && enablePlugin(plugin)) {
            enabledIds += id
            preferences.edit().putBoolean(enabledKey(id), true).apply()
            Log.i(TAG, "Enabled $id")
        } else if (!enabled && enabledIds.remove(id)) {
            preferences.edit().putBoolean(enabledKey(id), false).apply()
            runCatching { plugin.onDisable() }
                .onFailure { Log.e(TAG, "Plugin ${plugin.id} failed in onDisable", it) }
            Log.i(TAG, "Disabled $id")
        }
    }

    fun runOnShuffle(queue: List<Song>): List<Song>? {
        if (queue.size < 2) return null
        activePluginsSnapshot().forEach { plugin ->
            val result = runPlugin(plugin, "onShuffle") { onShuffle(queue.toList()) } ?: return@forEach
            if (isValidShuffle(queue, result)) {
                Log.i(TAG, "${plugin.id} handled onShuffle; songs=${queue.size}; first=${result.firstOrNull()?.id}")
                return result.toList()
            }
            Log.w(TAG, "Ignoring invalid shuffle result from ${plugin.id}")
        }
        return null
    }

    fun runOnNextTrack(request: NextRequest): Song? {
        activePluginsSnapshot().forEach { plugin ->
            val result = runPlugin(plugin, "onNextTrack") { onNextTrack(request) } ?: return@forEach
            Log.i(TAG, "${plugin.id} handled onNextTrack; trigger=${request.trigger}; selected=${result.id}")
            return request.queue.firstOrNull { it.id == result.id }
        }
        return null
    }

    fun notifyTrackFinished(song: Song) {
        activePluginsSnapshot().forEach { plugin ->
            runPlugin(plugin, "onTrackFinished") { onTrackFinished(song) }
            if ("onTrackFinished" in plugin.hooks) {
                Log.i(TAG, "${plugin.id} received onTrackFinished; song=${song.id}")
            }
        }
    }

    @Synchronized
    private fun activePluginsSnapshot(): List<MusePlugin> = plugins.values.filter { it.id in enabledIds }

    private fun isValidShuffle(original: List<Song>, result: List<Song>): Boolean =
        original.size == result.size &&
            original.map { it.id }.groupingBy { it }.eachCount() ==
            result.map { it.id }.groupingBy { it }.eachCount()

    private inline fun <T> runPlugin(plugin: MusePlugin, hook: String, block: MusePlugin.() -> T): T? = try {
        plugin.block()
    } catch (error: Exception) {
        Log.e(TAG, "Plugin ${plugin.id} failed in $hook; disabling", error)
        disableAfterFailure(plugin)
        null
    }

    private fun enablePlugin(plugin: MusePlugin): Boolean = try {
        plugin.onEnable()
        true
    } catch (error: Exception) {
        Log.e(TAG, "Plugin ${plugin.id} failed in onEnable", error)
        preferences.edit().putBoolean(enabledKey(plugin.id), false).apply()
        false
    }

    @Synchronized
    private fun disableAfterFailure(plugin: MusePlugin) {
        if (enabledIds.remove(plugin.id)) {
            preferences.edit().putBoolean(enabledKey(plugin.id), false).apply()
            runCatching { plugin.onDisable() }
        }
    }

    private fun enabledKey(id: String) = "enabled_$id"
    private fun configKey(id: String) = "config_$id"
    private fun permissionKey(id: String, permission: String) = "permission_${id}_$permission"
    private fun trustedInitializedKey(id: String) = "trusted_initialized_$id"
    private fun grantedPermissions(id: String, declared: Set<String>): Set<String> =
        declared.filterTo(mutableSetOf()) { preferences.getBoolean(permissionKey(id, it), false) }

    private companion object {
        const val TAG = "MusePlugins"
        const val MAX_CONFIG_BYTES = 64 * 1024
        const val NETWORK_PERMISSION = "network.request"
    }
}
