package com.example.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

enum class AudioOutputType {
    SPEAKER,
    WIRED,
    BLUETOOTH,
    USB
}

data class AudioOutputInfo(
    val displayName: String,
    val type: AudioOutputType,
    val bluetoothCodec: String? = null
) {
    val isUsbDac: Boolean get() = type == AudioOutputType.USB
}

object AudioOutputDetector {
    private fun cleanDacName(raw: String): String {
        var name = raw

        val dashIndex = name.lastIndexOf(" - ")

        if (dashIndex != -1) {
            name = name.substring(dashIndex + 3)
        }

        return name.trim().takeIf { it.isNotEmpty() } ?: raw.trim()
    }

    fun getCurrentOutput(context: Context): AudioOutputInfo {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val usb = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        if (usb != null) {
            val raw = usb.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }

            val displayName = if (raw != null) cleanDacName(raw) else "USB DAC"

            return AudioOutputInfo(displayName = displayName, type = AudioOutputType.USB)
        }

        if (audioManager.isBluetoothA2dpOn) {
            val bt = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }

            val name = bt?.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Bluetooth Audio"

            val codec = BluetoothCodecDetector.getActiveCodec(context)

            return AudioOutputInfo(
                displayName = name,
                type = AudioOutputType.BLUETOOTH,
                bluetoothCodec = codec
            )
        }

        if (audioManager.isWiredHeadsetOn) {
            val wired = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }

            val name = wired?.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }

                ?: "Wired Headset"

            return AudioOutputInfo(displayName = name, type = AudioOutputType.WIRED)
        }

        return AudioOutputInfo(displayName = "Phone Speaker", type = AudioOutputType.SPEAKER)
    }

    fun isDacConnected(context: Context): Boolean {
        val current = getCurrentOutput(context)

        if (current.isUsbDac) return true

        val usbDevice = try {
            com.example.core.audio.usb.UsbAudioDevice.getInstance(context).findUsbAudioDevice()
        } catch (_: Throwable) {
            null
        }

        return usbDevice != null
    }

    fun getConnectedDacName(context: Context): String? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val usb = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val raw = usb?.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (raw != null) return cleanDacName(raw)

        val usbDevice = try {
            com.example.core.audio.usb.UsbAudioDevice.getInstance(context).findUsbAudioDevice()
        } catch (_: Throwable) {
            null
        }
        return usbDevice?.productName?.takeIf { it.isNotBlank() } ?: (if (isDacConnected(context)) "USB DAC" else null)
    }
}
