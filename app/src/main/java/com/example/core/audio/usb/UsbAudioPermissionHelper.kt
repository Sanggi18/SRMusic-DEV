package com.example.core.audio.usb

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.example.SRMusicApp

object UsbAudioPermissionHelper {
    private const val TAG = "UsbAudioPermission"

    var onDeviceAvailable: ((UsbDevice) -> Unit)? = null

    fun handleIntent(context: Context, intent: Intent): UsbDevice? {
        if (intent.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return null

        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return null

        Log.i(TAG, "USB_DEVICE_ATTACHED: ${device.productName}")

        val app = context.applicationContext as? SRMusicApp

        val isBitPerfectRequested = app?.equalizerPreferences?.loadEqualizerConfig()?.isBitPerfectEnabled == true

        if (!isBitPerfectRequested) {
            Log.i(
                TAG,
                "USB attach ignored for direct claim: Bit Perfect is OFF. Android audio stack retains DAC ownership."
            )

            return null
        }

        val usbAudioDevice = UsbAudioDevice.getInstance(context)

        val audioDevice = usbAudioDevice.findUsbAudioDevice() ?: run {
            Log.w(TAG, "BITPERFECT_BLOCKED reason=NO_DAC: USB device attached is not an audio class device")

            return null
        }

        if (usbAudioDevice.hasPermission(audioDevice)) {
            Log.i(TAG, "USB_RECONNECT_ATTEMPT: Permission already granted for ${audioDevice.productName}; notifying available")

            onDeviceAvailable?.invoke(audioDevice)

            return audioDevice
        } else {
            usbAudioDevice.requestPermission(audioDevice) { granted ->
                Log.i(TAG, "USB permission callback for ${audioDevice.productName}: granted=$granted")

                val stillRequested = app.equalizerPreferences.loadEqualizerConfig().isBitPerfectEnabled == true

                if (granted && stillRequested) {
                    Log.i(TAG, "USB_RECONNECT_ATTEMPT: Permission granted for ${audioDevice.productName}; notifying available")

                    onDeviceAvailable?.invoke(audioDevice)
                } else if (!granted) {
                    Log.w(TAG, "BITPERFECT_BLOCKED reason=PERMISSION_DENIED for ${audioDevice.productName}")
                } else {
                    Log.i(TAG, "USB attach claim aborted: Bit Perfect was turned OFF while waiting for permission")
                }
            }
        }

        return null
    }
}
