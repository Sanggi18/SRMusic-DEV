package com.example.core.audio

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context

object BluetoothCodecDetector {
    @Volatile
    private var a2dpProfile: BluetoothProfile? = null

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = bluetoothManager.adapter ?: return

        try {
            adapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = proxy
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) {
                        a2dpProfile = null
                    }
                }
            }, BluetoothProfile.A2DP)
            isInitialized = true
        } catch (_: Throwable) {}
    }

    fun getActiveCodec(context: Context): String? {
        init(context)
        val a2dp = a2dpProfile ?: return null

        try {
            val device: BluetoothDevice? = try {
                val method = a2dp.javaClass.getMethod("getActiveDevice")
                method.invoke(a2dp) as? BluetoothDevice
            } catch (_: Throwable) {
                null
            } ?: try {
                a2dp.connectedDevices.firstOrNull()
            } catch (_: Throwable) {
                null
            }

            if (device == null) return null

            val getCodecStatusMethod = try {
                a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            } catch (_: Throwable) {
                null
            }

            val codecStatus = getCodecStatusMethod?.invoke(a2dp, device)
            if (codecStatus != null) {
                val getCodecConfigMethod = try {
                    codecStatus.javaClass.getMethod("getCodecConfig")
                } catch (_: Throwable) {
                    null
                }
                val codecConfig = getCodecConfigMethod?.invoke(codecStatus)
                if (codecConfig != null) {
                    val getCodecTypeMethod = try {
                        codecConfig.javaClass.getMethod("getCodecType")
                    } catch (_: Throwable) {
                        null
                    }
                    val codecType = getCodecTypeMethod?.invoke(codecConfig) as? Int

                    if (codecType != null) {
                        val name = when (codecType) {
                            0 -> "SBC"
                            1 -> "AAC"
                            2 -> "aptX"
                            3 -> "aptX HD"
                            4 -> "LDAC"
                            5 -> "LC3"
                            6 -> "Opus"
                            7 -> "aptX Adaptive"
                            8 -> "aptX TWS+"
                            else -> null
                        }
                        if (name != null) return name
                    }

                    val getCodecNameMethod = try {
                        codecConfig.javaClass.getMethod("getCodecName")
                    } catch (_: Throwable) {
                        null
                    }
                    val codecName = getCodecNameMethod?.invoke(codecConfig) as? String
                    if (!codecName.isNullOrBlank()) {
                        return codecName.uppercase()
                    }
                }
            }
        } catch (_: Throwable) {}

        return null
    }
}
