package com.example.core.model

import com.example.core.audio.AudioOutputApi

data class EqualizerConfig(
    val isBitPerfectEnabled: Boolean = false,
    val isEqualizerEnabled: Boolean = false,
    val selectedAudioOutput: String = "Default",
    val selectedAudioOutputApi: AudioOutputApi = AudioOutputApi.AUDIOTRACK,
    val preampGain: Float = 0f,
    val selectedPreset: String = "Flat",
    val selectedTemplateName: String? = null,
    val selectedCustomName: String? = null,
    val bands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    val bassBoost: Float = 0f,
    val vocalBoost: Float = 0f,
    val trebleBoost: Float = 0f,
    val customPresets: List<EqualizerCustomPreset> = emptyList()
)

data class EqualizerCustomPreset(
    val name: String,
    val bandGains: List<Float>
)
