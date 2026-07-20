package com.caipan.music.plugin

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BlurLocation(val label: String) {
    FULL_SCREEN("二级页面"),
    CARDS("卡片与列表"),
    SHEETS("弹窗与菜单"),
    BOTTOM_TABS("底部导航"),
    MINI_PLAYER("迷你播放器"),
    PLAYER("全屏播放器")
}

data class BlurPolicy(
    val masterEnabled: Boolean = true,
    val enabledLocations: Set<BlurLocation> = BlurLocation.entries.toSet(),
    val liquidReadabilityBlur: Float = 1f
) {
    fun enabledAt(location: BlurLocation): Boolean = masterEnabled && location in enabledLocations
}

class GlobalBlurControlPlugin(context: Context) : MusePlugin {
    override val id = ID
    override val name = "全局玻璃控制"
    override val version = "1.0.0"
    override val author = "Muse"
    override val description = "控制实时模糊的实施位置、Liquid Glass 可读性与总开关。"
    override val hooks = emptyList<String>()
    override val enabledByDefault = true

    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _policy = MutableStateFlow(load())
    val policy: StateFlow<BlurPolicy> = _policy.asStateFlow()

    override fun onEnable() = publish(enabled = true)
    override fun onDisable() = publish(enabled = false)

    fun setMasterEnabled(enabled: Boolean) = update(_policy.value.copy(masterEnabled = enabled))

    fun setLocationEnabled(location: BlurLocation, enabled: Boolean) {
        val locations = _policy.value.enabledLocations.toMutableSet().apply {
            if (enabled) add(location) else remove(location)
        }
        update(_policy.value.copy(enabledLocations = locations))
    }

    fun setReadabilityBlur(value: Float) = update(_policy.value.copy(liquidReadabilityBlur = value.coerceIn(0f, 1f)))

    private fun update(policy: BlurPolicy) {
        save(policy)
        _policy.value = policy
    }

    private fun publish(enabled: Boolean) {
        val stored = load()
        _policy.value = if (enabled) stored else stored.copy(masterEnabled = false)
    }

    private fun load(): BlurPolicy {
        val locations = BlurLocation.entries.filterTo(mutableSetOf()) {
            preferences.getBoolean("location_${it.name}", true)
        }
        return BlurPolicy(
            masterEnabled = preferences.getBoolean("master", true),
            enabledLocations = locations,
            liquidReadabilityBlur = preferences.getFloat("readability", 1f)
        )
    }

    private fun save(policy: BlurPolicy) {
        preferences.edit().putBoolean("master", policy.masterEnabled)
            .putFloat("readability", policy.liquidReadabilityBlur).apply {
                BlurLocation.entries.forEach { putBoolean("location_${it.name}", it in policy.enabledLocations) }
            }.apply()
    }

    companion object {
        const val ID = "com.caipan.muse.global-blur-control"
        private const val PREFS = "plugin_global_blur_control"
    }
}
