package com.caipan.music.ui.components

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class MuseGlassConfig(
    val cornerRadius: Float = 24f,
    val blurRadius: Float = 12f,
    val refractionHeight: Float = 12f,
    val refractionAmount: Float = 24f,
    val chromaticAberration: Float = 1f,
    val themeColorIntensity: Float = 0.18f,
    val elasticity: Float = 0.5f,
    val pressScale: Float = 1.5f
) {
    fun toJson() = JSONObject().put("cornerRadius", cornerRadius).put("blurRadius", blurRadius)
        .put("refractionHeight", refractionHeight).put("refractionAmount", refractionAmount)
        .put("chromaticAberration", chromaticAberration).put("themeColorIntensity", themeColorIntensity)
        .put("elasticity", elasticity).put("pressScale", pressScale)

    companion object {
        fun fromJson(json: JSONObject) = MuseGlassConfig(
            json.optDouble("cornerRadius", 24.0).toFloat().coerceIn(0f, 80f),
            json.optDouble("blurRadius", 12.0).toFloat().coerceIn(0f, 64f),
            json.optDouble("refractionHeight", 12.0).toFloat().coerceIn(0f, 80f),
            json.optDouble("refractionAmount", 24.0).toFloat().coerceIn(0f, 80f),
            json.optDouble("chromaticAberration", 1.0).toFloat().coerceIn(0f, 1f),
            json.optDouble("themeColorIntensity", 0.18).toFloat().coerceIn(0f, 1f),
            json.optDouble("elasticity", 0.5).toFloat().coerceIn(0f, 1f),
            json.optDouble("pressScale", 1.5).toFloat().coerceIn(1f, 1.8f)
        )
    }
}

class MuseGlassConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("muse_glass", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<MuseGlassConfig> = _state.asStateFlow()

    @Synchronized fun set(config: MuseGlassConfig): MuseGlassConfig {
        val safe = MuseGlassConfig.fromJson(config.toJson())
        prefs.edit().putString("config", safe.toJson().toString()).apply()
        _state.value = safe
        return safe
    }

    /** 仅更新内存中的配置用于实时预览，不写盘；拖动/动画结束后应调用 [set] 落盘。 */
    @Synchronized fun setPreview(config: MuseGlassConfig): MuseGlassConfig {
        val safe = MuseGlassConfig.fromJson(config.toJson())
        _state.value = safe
        return safe
    }

    @Synchronized fun update(json: JSONObject): MuseGlassConfig {
        val merged = _state.value.toJson()
        json.keys().forEach { key -> if (key in setOf("cornerRadius", "blurRadius", "refractionHeight", "refractionAmount", "chromaticAberration", "themeColorIntensity", "elasticity", "pressScale")) merged.put(key, json.getDouble(key)) }
        return set(MuseGlassConfig.fromJson(merged))
    }

    @Synchronized fun reset(): MuseGlassConfig = set(MuseGlassConfig())

    private fun load() = runCatching { MuseGlassConfig.fromJson(JSONObject(prefs.getString("config", "{}"))) }.getOrDefault(MuseGlassConfig())
}
