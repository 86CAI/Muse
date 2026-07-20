package com.caipan.music.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class EqBand(val freqHz: Int, val levelDb: Float, val rangeMinDb: Float, val rangeMaxDb: Float)

class EqualizerManager(context: Context) {
    private var eq: Equalizer? = null
    private val prefs: SharedPreferences = context.getSharedPreferences("muse_eq", 0)
    private var lastSessionId = 0

    var bands: List<EqBand> = emptyList()
        private set
    var isEnabled: Boolean = false
        private set
    var presetName: String = ""
        private set
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()
    val presets: List<String>
        get() {
            val raw = prefs.getString("eq_presets", "") ?: ""
            return if (raw.isBlank()) emptyList() else raw.split("|")
        }

    init {
        isEnabled = prefs.getBoolean("eq_enabled", false)
        presetName = prefs.getString("eq_preset_name", "") ?: ""
    }

    fun attach(sessionId: Int): Boolean {
        if (sessionId == 0) return false
        if (sessionId == lastSessionId && eq != null) return true
        releaseEffect()
        lastSessionId = sessionId
        return try {
            val effect = Equalizer(0, sessionId)
            val range = effect.bandLevelRange
            bands = (0 until effect.numberOfBands.toInt()).map { index ->
                val centerHz = effect.getCenterFreq(index.toShort()) / 1000
                val minDb = range[0] / 100f
                val maxDb = range[1] / 100f
                val saved = prefs.getInt("eq_band_$index", 0) / 100f
                EqBand(centerHz, saved.coerceIn(minDb, maxDb), minDb, maxDb)
            }
            bands.forEachIndexed { index, band -> effect.setBandLevel(index.toShort(), (band.levelDb * 100).toInt().toShort()) }
            effect.enabled = isEnabled
            eq = effect
            _revision.value++
            true
        } catch (e: Exception) {
            Log.e("MuseEQ", "Attach failed", e)
            eq = null
            false
        }
    }

    fun setBandLevel(index: Int, levelDb: Float) {
        // Auto-enable EQ when user adjusts a band so the change is audible
        if (!isEnabled) setEnabled(true)
        val band = bands.getOrNull(index) ?: return
        val safeLevel = levelDb.coerceIn(band.rangeMinDb, band.rangeMaxDb)
        val mb = (safeLevel * 100).toInt()
        try { eq?.setBandLevel(index.toShort(), mb.toShort()) } catch (e: Exception) {
            Log.e("MuseEQ", "Band update failed", e)
        }
        prefs.edit().putInt("eq_band_$index", mb).apply()
        if (index in bands.indices) {
            bands = bands.toMutableList().also { it[index] = it[index].copy(levelDb = safeLevel) }
        }
        _revision.value++
        Log.d("MuseEQ", "Band $index set to ${safeLevel}dB")
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            eq?.enabled = enabled
            // Re-apply band levels when enabling to ensure they take effect
            if (enabled) {
                eq?.let { e ->
                    bands.forEachIndexed { i, band ->
                        val mb = (band.levelDb * 100).toInt().coerceIn((band.rangeMinDb * 100).toInt(), (band.rangeMaxDb * 100).toInt())
                        e.setBandLevel(i.toShort(), mb.toShort())
                    }
                }
            }
        } catch (e: Exception) { Log.e("MuseEQ", "setEnabled failed", e) }
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        _revision.value++
        Log.d("MuseEQ", "Enabled=$enabled, eq=$eq")
    }

    fun resetAll() { bands.forEachIndexed { i, _ -> setBandLevel(i, 0f) } }

    fun savePreset(name: String) {
        val list = presets.toMutableList()
        if (!list.contains(name)) list.add(name)
        prefs.edit().putString("eq_presets", list.joinToString("|")).apply()
        bands.forEachIndexed { i, band -> prefs.edit().putFloat("eq_${name}_$i", band.levelDb).apply() }
        presetName = name
        prefs.edit().putString("eq_preset_name", name).apply()
        _revision.value++
    }

    fun loadPreset(name: String): Boolean {
        if (name !in presets) return false
        bands.forEachIndexed { i, _ ->
            val level = prefs.getFloat("eq_${name}_$i", Float.NaN)
            if (!level.isNaN()) setBandLevel(i, level)
        }
        presetName = name
        prefs.edit().putString("eq_preset_name", name).apply()
        _revision.value++
        return true
    }

    fun deletePreset(name: String) {
        val list = presets.toMutableList(); list.remove(name)
        prefs.edit().putString("eq_presets", list.joinToString("|")).apply()
        bands.indices.forEach { i -> prefs.edit().remove("eq_${name}_$i").apply() }
        if (presetName == name) { presetName = ""; prefs.edit().remove("eq_preset_name").apply() }
        _revision.value++
    }

    fun importFromUri(context: Context, uri: Uri): Result<String> {
        return try {
            val r = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)!!))
            val lines = r.readLines().filter { it.isNotBlank() }; r.close()
            if (lines.isEmpty()) return Result.failure(Exception("Empty"))
            var name = "Imported"; val gains = mutableListOf<Float>()
            for (line in lines) {
                val t = line.trim()
                if (t.startsWith("#")) { name = t.removePrefix("#").trim(); continue }
                val parts = t.split(":", "=", "\t", " ").filter { it.isNotBlank() }
                when {
                    parts.size >= 2 -> {
                        val v = parts[1].replace("dB", "").replace("db", "").trim().toFloatOrNull() ?: continue
                        if (parts[0].lowercase().contains("preamp")) prefs.edit().putFloat("eq_preamp", v).apply()
                        else gains.add(v)
                    }
                    parts.size == 1 -> parts[0].toFloatOrNull()?.let { gains.add(it) }
                }
            }
            if (gains.isEmpty()) return Result.failure(Exception("No values"))
            for (i in 0 until minOf(bands.size, gains.size)) setBandLevel(i, gains[i])
            presetName = name; prefs.edit().putString("eq_preset_name", name).apply(); savePreset(name)
            Result.success(name)
        } catch (e: Exception) { Log.e("MuseEQ", "Import failed", e); Result.failure(e) }
    }

    fun exportToUri(context: Context, uri: Uri, name: String) {
        val w = BufferedWriter(OutputStreamWriter(context.contentResolver.openOutputStream(uri)!!))
        w.write("# $name"); w.newLine()
        bands.forEachIndexed { i, band -> w.write("${band.freqHz} Hz: ${band.levelDb} dB"); w.newLine() }
        w.close()
    }

    fun release() {
        releaseEffect()
        lastSessionId = 0
        Log.d("MuseEQ", "Released")
    }

    private fun releaseEffect() {
        try { eq?.enabled = false; eq?.release() } catch (_: Exception) {}
        eq = null
    }
}