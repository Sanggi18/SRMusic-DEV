package com.example.core.audio.usb.media3

import com.example.core.audio.dac.DacSampleRateMode
import com.example.core.audio.dac.DacVolumeMode
import com.example.core.audio.dac.DsdGainCompensation
import com.example.core.audio.dac.DsdOutputMode

data class UsbAudioSinkConfig(
    val bitPerfectEnabled: Boolean = true,
    val forceRouteToSpeaker: Boolean = true,
    val dacVolumeMode: DacVolumeMode = DacVolumeMode.AUTO,
    val dacExclusiveUsb: Boolean = true,
    val dsdOutputMode: DsdOutputMode = DsdOutputMode.AUTO,
    val dsdGainCompensation: DsdGainCompensation = DsdGainCompensation.PLUS_0DB,
    val usbBufferSize: Int = 256,
    val dacSampleRateMode: DacSampleRateMode = DacSampleRateMode.FOLLOW_SOURCE,
    val dacFixedSampleRate: Int = 192000
)
