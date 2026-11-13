package com.example.core.audio.usb

import android.hardware.usb.UsbDeviceConnection

data class UsbAudioDeviceInfo(
    val connection: UsbDeviceConnection,
    val fd: Int,
    val deviceName: String,
    val interfaceId: Int,
    val endpointOutAddress: Int,
    val endpointFeedbackAddress: Int,
    val maxPacketSize: Int,
    val altSettingCount: Int,
    val clockSourceId: Int,
    val bestAltSetting: Int,
    val bestBitDepth: Int,
    val featureUnitId: Int = -1,
    val audioControlInterfaceId: Int = 0,
    val uacVersion: Int = 2,
    val usbSpeed: String = "Unknown"
)
