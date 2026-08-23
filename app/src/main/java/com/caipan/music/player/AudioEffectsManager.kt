package com.caipan.music.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioEffectsState(
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE
)

class AudioEffectsManager {
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null
    private var sessionId = 0

    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state.asStateFlow()

    fun attach(sessionId: Int): Boolean {
        if (sessionId == 0) return false
        if (sessionId == this.sessionId && bassBoost != null) return true
        release()
        this.sessionId = sessionId
        return try {
            bassBoost = BassBoost(0, sessionId).apply { enabled = _state.value.bassBoostEnabled }
            virtualizer = Virtualizer(0, sessionId).apply { enabled = _state.value.virtualizerEnabled }
            reverb = PresetReverb(0, sessionId).apply {
                enabled = _state.value.reverbPreset != ReverbPreset.NONE
                preset = _state.value.reverbPreset.androidValue
            }
            applyAll()
            true
        } catch (e: Exception) {
            Log.e("MuseFX", "Attach failed", e)
            release()
            false
        }
    }

    fun setBassBoost(enabled: Boolean, strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(bassBoostEnabled = enabled, bassBoostStrength = clamped)
        applyBassBoost()
    }

    fun setVirtualizer(enabled: Boolean, strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(virtualizerEnabled = enabled, virtualizerStrength = clamped)
        applyVirtualizer()
    }

    fun setReverb(preset: ReverbPreset) {
        _state.value = _state.value.copy(reverbPreset = preset)
        applyReverb()
    }

    fun syncFromSettings(settings: PlaybackSettings) {
        _state.value = AudioEffectsState(
            bassBoostEnabled = settings.bassBoostEnabled,
            bassBoostStrength = settings.bassBoostStrength,
            virtualizerEnabled = settings.virtualizerEnabled,
            virtualizerStrength = settings.virtualizerStrength,
            reverbPreset = settings.reverbPreset
        )
        applyAll()
    }

    private fun applyAll() {
        applyBassBoost()
        applyVirtualizer()
        applyReverb()
    }

    private fun applyBassBoost() {
        val s = _state.value
        try {
            bassBoost?.let { bb ->
                bb.setEnabled(s.bassBoostEnabled)
                if (s.bassBoostEnabled) bb.setStrength(s.bassBoostStrength.toShort())
            }
        } catch (e: Exception) { Log.e("MuseFX", "BassBoost apply failed", e) }
    }

    private fun applyVirtualizer() {
        val s = _state.value
        try {
            virtualizer?.let { v ->
                v.setEnabled(s.virtualizerEnabled)
                if (s.virtualizerEnabled) v.setStrength(s.virtualizerStrength.toShort())
            }
        } catch (e: Exception) { Log.e("MuseFX", "Virtualizer apply failed", e) }
    }

    private fun applyReverb() {
        val s = _state.value
        try {
            reverb?.let { r ->
                r.setEnabled(s.reverbPreset != ReverbPreset.NONE)
                if (s.reverbPreset != ReverbPreset.NONE) r.preset = s.reverbPreset.androidValue
            }
        } catch (e: Exception) { Log.e("MuseFX", "Reverb apply failed", e) }
    }

    fun release() {
        try { bassBoost?.enabled = false; bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.enabled = false; virtualizer?.release() } catch (_: Exception) {}
        try { reverb?.enabled = false; reverb?.release() } catch (_: Exception) {}
        bassBoost = null
        virtualizer = null
        reverb = null
        sessionId = 0
    }
}
