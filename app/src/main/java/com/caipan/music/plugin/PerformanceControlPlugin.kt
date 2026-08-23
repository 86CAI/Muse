package com.caipan.music.plugin

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 可独立限制帧率上限的界面场景。 */
enum class FrameRateScene(val label: String, val defaultFps: Int) {
    HOME("主页", 60),
    GALLERY("画廊", 30),
    LYRICS("歌词", 60),
    SEARCH("搜索", 60),
    PLAYER("播放器", 60),
    RAIN("雨滴", 30);

    companion object {
        /** fps == 0 表示不限制，跟随屏幕刷新率。 */
        const val UNLIMITED = 0
        const val MAX_FPS = 120
        val SUPPORTED_FPS = listOf(120, 90, 60, 45, 30, 24)
        fun fromName(name: String?): FrameRateScene =
            entries.firstOrNull { it.name == name } ?: HOME
    }
}

data class PerformancePolicy(
    val masterEnabled: Boolean = true,
    val sceneFps: Map<FrameRateScene, Int> = FrameRateScene.entries.associateWith { it.defaultFps }
) {
    /** 每个场景的帧率上限；master 关闭或值为 0 时表示不限制。 */
    fun fpsFor(scene: FrameRateScene): Int =
        if (masterEnabled) (sceneFps[scene] ?: scene.defaultFps).coerceIn(0, FrameRateScene.MAX_FPS)
        else FrameRateScene.UNLIMITED
}

/**
 * 性能控制插件：按场景限制帧率上限，降低画廊漂移、歌词滚动、
 * 主页玻璃滚动等持续动画的 GPU 占用与耗电。
 *
 * 与 [GlobalBlurControlPlugin] 同构：作为内置 Kotlin 插件注册进插件体系，
 * 在插件页有独立条目与开关；渲染层通过 CompositionLocal 读取 [policy] 后节流对应动画。
 */
class PerformanceControlPlugin(context: Context) : MusePlugin {
    override val id = ID
    override val name = "性能控制"
    override val version = "1.0.0"
    override val author = "Muse"
    override val description = "按场景限制帧率上限，降低画廊/歌词/主页等持续动画的 GPU 占用与耗电。"
    override val hooks = emptyList<String>()
    override val enabledByDefault = true

    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _policy = MutableStateFlow(load())
    val policy: StateFlow<PerformancePolicy> = _policy.asStateFlow()

    fun setMasterEnabled(enabled: Boolean) = update(_policy.value.copy(masterEnabled = enabled))

    fun setSceneFps(scene: FrameRateScene, fps: Int) {
        val map = _policy.value.sceneFps + (scene to fps.coerceIn(0, FrameRateScene.MAX_FPS))
        update(_policy.value.copy(sceneFps = map))
    }

    override fun onEnable() = update(_policy.value.copy(masterEnabled = true))
    override fun onDisable() = update(_policy.value.copy(masterEnabled = false))

    private fun update(policy: PerformancePolicy) {
        save(policy)
        _policy.value = policy
    }

    private fun load(): PerformancePolicy {
        val master = preferences.getBoolean(KEY_MASTER, true)
        val sceneFps = FrameRateScene.entries.associateWith { scene ->
            preferences.getInt("scene_${scene.name}", scene.defaultFps).coerceIn(0, FrameRateScene.MAX_FPS)
        }
        return PerformancePolicy(masterEnabled = master, sceneFps = sceneFps)
    }

    private fun save(policy: PerformancePolicy) {
        preferences.edit().putBoolean(KEY_MASTER, policy.masterEnabled).apply {
            FrameRateScene.entries.forEach { putInt("scene_${it.name}", policy.sceneFps[it] ?: it.defaultFps) }
        }.apply()
    }

    companion object {
        const val ID = "com.caipan.muse.performance-control"
        private const val PREFS = "plugin_performance_control"
        private const val KEY_MASTER = "master"
    }
}
