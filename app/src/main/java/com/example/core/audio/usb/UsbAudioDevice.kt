package com.example.core.audio.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

class UsbAudioDevice private constructor(private val context: Context) {
    private var usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var connection: UsbDeviceConnection? = null

    private var currentDevice: UsbDevice? = null

    private var claimedInterface: UsbInterface? = null

    private var claimedControlInterface: UsbInterface? = null

    private var claimedStreamingInterface: UsbInterface? = null

    private val deviceLock = Any()

    companion object {
        private const val TAG = "UsbAudioDevice"

        private const val ACTION_USB_PERMISSION_SUFFIX = ".USB_AUDIO_PERMISSION"

        @Volatile
        private var instance: UsbAudioDevice? = null

        init {
            System.loadLibrary("usb_audio")
        }

        fun getInstance(context: Context): UsbAudioDevice {
            return instance ?: synchronized(this) {
                instance ?: UsbAudioDevice(context.applicationContext).also { instance = it }
            }
        }
    }

    private external fun nativeReattachKernelDriver(fd: Int, interfaceId: Int): Boolean

    private var lastLoggedScanSignature: String = ""

    private var lastLoggedScanTime: Long = 0L

    fun findUsbAudioDevice(): UsbDevice? {
        val devices = usbManager.deviceList.values

        val currentSig = devices.joinToString(";") { "${it.deviceName}:${it.vendorId}:${it.productId}:${hasPermission(it)}" }

        val now = System.currentTimeMillis()

        val shouldLog = currentSig != lastLoggedScanSignature || (now - lastLoggedScanTime > 3000L)

        if (shouldLog) {
            lastLoggedScanSignature = currentSig

            lastLoggedScanTime = now
        }

        for (device in devices) {
            val vidHex = "0x" + device.vendorId.toString(16).uppercase().padStart(4, '0')

            val pidHex = "0x" + device.productId.toString(16).uppercase().padStart(4, '0')

            val devName = device.productName ?: device.deviceName

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)

                if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                    iface.interfaceSubclass == 2) {
                    Log.i(TAG, "Found USB audio device: $devName " +
                            "(vendor=$vidHex, product=$pidHex)")

                    return device
                }
            }
        }

        Log.d(TAG, "No USB audio device found")

        return null
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    private var pendingPermissionReceiver: BroadcastReceiver? = null

    fun requestPermission(device: UsbDevice, callback: (Boolean) -> Unit) {
        val devName = device.productName ?: device.deviceName

        if (usbManager.hasPermission(device)) {
            Log.i(TAG, "Permission already granted for $devName")

            callback(true)

            return
        }

        pendingPermissionReceiver?.let { stale ->
            try {
                context.unregisterReceiver(stale)
            } catch (_: Exception) {}
            pendingPermissionReceiver = null
        }

        val intent = Intent(context.packageName + ACTION_USB_PERMISSION_SUFFIX)

        intent.setPackage(context.packageName)

        val permissionIntent = PendingIntent.getBroadcast(
                context, 0,
                intent,
                PendingIntent.FLAG_MUTABLE
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == context.packageName + ACTION_USB_PERMISSION_SUFFIX) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    Log.i(TAG, "USB permission result: granted=$granted for $devName")

                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    if (pendingPermissionReceiver === this) {
                        pendingPermissionReceiver = null
                    }

                    callback(granted)
                }
            }
        }

        pendingPermissionReceiver = receiver

        val filter = IntentFilter(context.packageName + ACTION_USB_PERMISSION_SUFFIX)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        usbManager.requestPermission(device, permissionIntent)

        Log.i(TAG, "Permission requested for $devName")
    }

    private var cachedDeviceInfo: UsbAudioDeviceInfo? = null

    val currentDeviceInfo: UsbAudioDeviceInfo?
        get() = cachedDeviceInfo

    fun openDevice(device: UsbDevice): UsbAudioDeviceInfo? {
        val cached = cachedDeviceInfo

        if (cached != null && connection != null) {
            Log.i(TAG, "Device already open, reusing fd=${cached.fd}")

            return cached
        }

        closeDevice()

        val devName = device.productName ?: device.deviceName

        val conn = usbManager.openDevice(device)

        if (conn == null) {
            Log.e(TAG, "Failed to open device $devName")

            return null
        }

        val fd = conn.fileDescriptor

        var streamingInterface: UsbInterface? = null

        var endpointOut = -1

        var endpointFeedback = -1

        var maxPacketSize = 0

        var altSettingCount = 0

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)

            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                iface.interfaceSubclass == 2) {
                altSettingCount++
                if (iface.endpointCount > 0 && streamingInterface == null) {
                    var candidateOut = -1
                    var candidateFb = -1
                    var candidateMaxPkt = 0

                    for (e in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(e)

                        when {
                            ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                                    ep.direction == UsbConstants.USB_DIR_OUT -> {
                                candidateOut = ep.address

                                candidateMaxPkt = ep.maxPacketSize

                                Log.i(TAG, "Found ISO OUT endpoint: address=0x${ep.address.toString(16)}, " +
                                        "maxPacket=$candidateMaxPkt, interval=${ep.interval}")
                            }

                            ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                                    ep.direction == UsbConstants.USB_DIR_IN -> {
                                candidateFb = ep.address

                                Log.i(TAG, "Found ISO IN (feedback) endpoint: address=0x${ep.address.toString(16)}, " +
                                        "interval=${ep.interval}")
                            }
                        }
                    }

                    if (candidateOut >= 0) {
                        streamingInterface = iface
                        endpointOut = candidateOut
                        endpointFeedback = candidateFb
                        maxPacketSize = candidateMaxPkt
                    }
                }
            }
        }

        if (streamingInterface == null || endpointOut < 0) {
            Log.e(TAG, "No suitable AudioStreaming interface/endpoint found")

            conn.close()

            return null
        }

        val controlInterface = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_AUDIO && it.interfaceSubclass == 1 }

        if (controlInterface != null) {
            val claimed = conn.claimInterface(controlInterface, true)

            Log.i(TAG, "Claimed AudioControl interface ${controlInterface.id} force=true: $claimed")

            if (claimed) claimedControlInterface = controlInterface
        }

        val claimed = conn.claimInterface(streamingInterface, true)

        Log.i(TAG, "Claimed AudioStreaming interface ${streamingInterface.id} force=true: $claimed " +
                "(alt=${streamingInterface.alternateSetting}, endpoints=${streamingInterface.endpointCount})")

        if (!claimed) {
            Log.e(TAG, "Failed to claim streaming interface — kernel driver may still be active")

            claimedControlInterface?.let {
                try { conn.releaseInterface(it) } catch (_: Exception) {}
            }

            claimedControlInterface = null

            conn.close()

            return null
        }

        claimedStreamingInterface = streamingInterface

        claimedInterface = streamingInterface

        val zeroAlt = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull {
                    it.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                            it.interfaceSubclass == 2 &&
                            it.id == streamingInterface.id &&
                            it.alternateSetting == 0
                }

        if (zeroAlt != null) {
            conn.setInterface(zeroAlt)

            Log.i(TAG, "Reset streaming to alt=0 (zero-bandwidth)")
        }

        Thread.sleep(100)

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)

            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO && iface.interfaceSubclass == 2) {
                Log.d(TAG, "  AudioStreaming alt=${iface.alternateSetting}: " +
                        "id=${iface.id}, endpoints=${iface.endpointCount}")
            }
        }

        val interfaceId = streamingInterface.id

        connection = conn

        currentDevice = device

        val uacVersion = parseUacVersion(conn)

        val clockSourceId = parseClockSourceId(conn)

        val (bestAlt, bestBits) = parseBestAltSetting(conn, uacVersion, interfaceId)

        getEndpointInfoForAltSetting(bestAlt)?.let { (epOut, epFb, maxPkt) ->
            endpointOut = epOut
            endpointFeedback = epFb
            maxPacketSize = maxPkt
        }

        Log.i(TAG, "Device opened: ${device.productName}, fd=$fd, " +
                "iface=$interfaceId, epOut=0x${endpointOut.toString(16)}, " +
                "epFb=0x${endpointFeedback.toString(16)}, " +
                "maxPacket=$maxPacketSize, altSettings=$altSettingCount")

        val featureUnitId = parseFeatureUnitId(conn)

        val usbSpeedStr = UsbAudioStream.usbSpeedName(fd)

        Log.i(TAG, "Auto-detected: uac=$uacVersion, speed=$usbSpeedStr, clockSourceId=0x${clockSourceId.toString(16)}, " +
                "bestAlt=$bestAlt, bestBits=$bestBits, featureUnitId=0x${featureUnitId.toString(16)}")

        com.example.core.audio.AudioTerminalLogger.log(
            "USB DAC Opened: ${device.productName ?: "USB Audio"} | UAC ${if (uacVersion == 100) "1.0" else "2.0"} | $usbSpeedStr | Max ${bestBits}-bit"
        )

        val info = UsbAudioDeviceInfo(
                connection = conn,
                fd = fd,
                deviceName = device.productName ?: "USB Audio Device",
                interfaceId = interfaceId,
                endpointOutAddress = endpointOut,
                endpointFeedbackAddress = endpointFeedback,
                maxPacketSize = maxPacketSize,
                altSettingCount = altSettingCount,
                clockSourceId = clockSourceId,
                bestAltSetting = bestAlt,
                bestBitDepth = bestBits,
                featureUnitId = featureUnitId,
                audioControlInterfaceId = controlInterface?.id ?: 0,
                uacVersion = uacVersion,
                usbSpeed = usbSpeedStr
        )

        cachedDeviceInfo = info

        return info
    }

    private var parsedClockSourceIds: List<Int> = emptyList()
    private var parsedClockSelectorId: Int = -1
    private var parsedClockSelectorPins: List<Int> = emptyList()
    private var activeClockSourceId: Int = -1

    fun parseUacVersion(conn: UsbDeviceConnection): Int {
        val raw = conn.rawDescriptors ?: return 200
        var i = 0
        var inAudioControl = false

        while (i + 1 < raw.size) {
            val bLength = raw[i].toInt() and 0xFF
            if (bLength < 2 || i + bLength > raw.size) break
            val bDescriptorType = raw[i + 1].toInt() and 0xFF

            if (bDescriptorType == 0x04 && bLength >= 9) {
                val bInterfaceClass = raw[i + 5].toInt() and 0xFF
                val bInterfaceSubClass = raw[i + 6].toInt() and 0xFF
                inAudioControl = (bInterfaceClass == 1 && bInterfaceSubClass == 1)
            }

            if (inAudioControl && bDescriptorType == 0x24 && bLength >= 5) {
                val bDescriptorSubtype = raw[i + 2].toInt() and 0xFF
                if (bDescriptorSubtype == 0x01) {
                    val bcdLo = raw[i + 3].toInt() and 0xFF
                    val bcdHi = raw[i + 4].toInt() and 0xFF
                    val bcd = (bcdHi shl 8) or bcdLo
                    return when (bcdHi) {
                        1 -> 100
                        2 -> 200
                        3 -> 300
                        else -> if (bcd == 0x0100) 100 else 200
                    }
                }
            }
            i += bLength
        }
        return 200
    }

    private fun parseClockSourceId(conn: UsbDeviceConnection): Int {
        val raw = conn.rawDescriptors ?: return -1
        val sources = mutableListOf<Int>()
        var selectorId = -1
        val selectorPins = mutableListOf<Int>()

        var i = 0

        var inAudioControl = false

        while (i + 1 < raw.size) {
            val bLength = raw[i].toInt() and 0xFF

            if (bLength < 2) break

            if (i + bLength > raw.size) break

            val bDescriptorType = raw[i + 1].toInt() and 0xFF

            if (bDescriptorType == 0x04 && bLength >= 9) {
                val bInterfaceClass = raw[i + 5].toInt() and 0xFF

                val bInterfaceSubClass = raw[i + 6].toInt() and 0xFF

                inAudioControl = (bInterfaceClass == 1 && bInterfaceSubClass == 1)
            }

            if (inAudioControl && bDescriptorType == 0x24 && bLength >= 4) {
                val bDescriptorSubtype = raw[i + 2].toInt() and 0xFF

                if (bDescriptorSubtype == 0x0A && bLength >= 5) {
                    val bClockID = raw[i + 3].toInt() and 0xFF

                    if (bClockID > 0 && bClockID !in sources) {
                        sources += bClockID
                        Log.i(TAG, "parseClockSourceId: found CLOCK_SOURCE bClockID=0x${bClockID.toString(16)}")
                    }
                } else if (bDescriptorSubtype == 0x0B && bLength >= 5 && selectorId < 0) {
                    selectorId = raw[i + 3].toInt() and 0xFF
                    val nrPins = raw[i + 4].toInt() and 0xFF
                    for (p in 0 until nrPins) {
                        val off = i + 5 + p
                        if (off < i + bLength) {
                            selectorPins += raw[off].toInt() and 0xFF
                        }
                    }
                    Log.i(TAG, "parseClockSourceId: found CLOCK_SELECTOR id=0x${selectorId.toString(16)} pins=$selectorPins")
                }
            }

            i += bLength
        }

        parsedClockSourceIds = sources
        parsedClockSelectorId = selectorId
        parsedClockSelectorPins = selectorPins
        val primary = sources.firstOrNull() ?: -1
        activeClockSourceId = primary
        if (primary < 0) {
            Log.w(TAG, "parseClockSourceId: no CLOCK_SOURCE descriptor found")
        }
        return primary
    }

    private fun parseFeatureUnitId(conn: UsbDeviceConnection): Int {
        val raw = conn.rawDescriptors ?: return -1

        var i = 0

        var inAudioControl = false

        var isUac2 = false

        while (i + 1 < raw.size) {
            val bLength = raw[i].toInt() and 0xFF

            if (bLength < 2 || i + bLength > raw.size) break

            val bDescriptorType = raw[i + 1].toInt() and 0xFF

            if (bDescriptorType == 0x04 && bLength >= 9) {
                val bInterfaceClass = raw[i + 5].toInt() and 0xFF

                val bInterfaceSubClass = raw[i + 6].toInt() and 0xFF

                inAudioControl = (bInterfaceClass == 1 && bInterfaceSubClass == 1)
            }

            if (inAudioControl && bDescriptorType == 0x24 && bLength >= 6) {
                val bDescriptorSubtype = raw[i + 2].toInt() and 0xFF

                if (bDescriptorSubtype == 0x01 && bLength >= 5) {
                    val bcdADC = (raw[i + 3].toInt() and 0xFF) or ((raw[i + 4].toInt() and 0xFF) shl 8)

                    isUac2 = (bcdADC >= 0x0200)
                } else if (bDescriptorSubtype == 0x06) {
                    val unitId = raw[i + 3].toInt() and 0xFF

                    var hasVolume = false

                    if (isUac2) {
                        if (bLength >= 9) {
                            val masterControls = raw[i + 5].toInt() and 0xFF

                            if ((masterControls and 0x0F) != 0) {
                                hasVolume = true
                            } else if (bLength >= 13) {
                                val ch1Controls = raw[i + 9].toInt() and 0xFF

                                if ((ch1Controls and 0x0F) != 0) hasVolume = true
                            }
                        }
                    } else {
                        val bControlSize = raw[i + 5].toInt() and 0xFF

                        if (bLength >= 6 + bControlSize && bControlSize > 0) {
                            val masterControls = raw[i + 6].toInt() and 0xFF

                            if ((masterControls and 0x0F) != 0) {
                                hasVolume = true
                            } else if (bLength >= 6 + (bControlSize * 2)) {
                                val ch1Controls = raw[i + 6 + bControlSize].toInt() and 0xFF

                                if ((ch1Controls and 0x0F) != 0) hasVolume = true
                            }
                        }
                    }

                    if (unitId == 2) {
                        hasVolume = true
                    }

                    if (hasVolume) {
                        Log.i(TAG, "parseFeatureUnitId: found Feature Unit 0x${unitId.toString(16)} with volume support (uac2=$isUac2)")

                        return unitId
                    }
                }
            }

            i += bLength
        }

        Log.d(TAG, "parseFeatureUnitId: no Feature Unit with volume support found")

        return -1
    }

    private data class AltSettingFormat(
        val altSetting: Int,
        val bitResolution: Int,
        val wireBitDepth: Int,
        val rates: List<IntRange> = emptyList()
    )

    private var parsedAltSettings: List<AltSettingFormat> = emptyList()

    private fun parseBestAltSetting(
        conn: UsbDeviceConnection,
        uacVersion: Int = 200,
        streamingInterfaceId: Int = -1
    ): Pair<Int, Int> {
        val raw = conn.rawDescriptors ?: return Pair(1, 16)

        val altSettings = mutableListOf<AltSettingFormat>()

        var i = 0

        var currentAlt = 0

        var inAudioStreaming = false

        var bestAlt = 1

        var bestBits = 16

        var bestWireBits = 16

        fun u8(at: Int) = raw[at].toInt() and 0xFF
        fun u24(at: Int) = u8(at) or (u8(at + 1) shl 8) or (u8(at + 2) shl 16)

        while (i + 1 < raw.size) {
            val bLength = raw[i].toInt() and 0xFF

            if (bLength < 2) break

            if (i + bLength > raw.size) break

            val bDescriptorType = raw[i + 1].toInt() and 0xFF

            if (bDescriptorType == 0x04 && bLength >= 9) {
                val bInterfaceNumber = raw[i + 2].toInt() and 0xFF

                val bAlternateSetting = raw[i + 3].toInt() and 0xFF

                val bInterfaceClass = raw[i + 5].toInt() and 0xFF

                val bInterfaceSubClass = raw[i + 6].toInt() and 0xFF

                inAudioStreaming = (bInterfaceClass == 1 && bInterfaceSubClass == 2 &&
                        (streamingInterfaceId < 0 || bInterfaceNumber == streamingInterfaceId))

                if (inAudioStreaming) currentAlt = bAlternateSetting
            }

            if (inAudioStreaming && bDescriptorType == 0x24 && bLength >= 6) {
                val bDescriptorSubtype = raw[i + 2].toInt() and 0xFF

                if (bDescriptorSubtype == 0x02) {
                    val bSubslotSize: Int
                    val bBitResolution: Int
                    val rates: List<IntRange>
                    if (uacVersion == 100) {
                        bSubslotSize = if (bLength >= 7) raw[i + 5].toInt() and 0xFF else 2
                        bBitResolution = if (bLength >= 7) raw[i + 6].toInt() and 0xFF else 16
                        rates = if (bLength >= 8) {
                            val n = u8(i + 7)
                            when {
                                n == 0 && bLength >= 14 -> listOf(u24(i + 8)..u24(i + 11))
                                else -> (0 until n).mapNotNull { k ->
                                    val at = i + 8 + 3 * k
                                    if (at + 3 <= i + bLength) u24(at).let { it..it } else null
                                }
                            }.filter { it.first > 0 && it.last >= it.first }
                        } else {
                            emptyList()
                        }
                    } else {
                        bSubslotSize = raw[i + 4].toInt() and 0xFF
                        bBitResolution = raw[i + 5].toInt() and 0xFF
                        rates = emptyList()
                    }

                    val wireBitDepth = when (bSubslotSize) {
                        2 -> 16
                        3 -> 24
                        4 -> 32
                        else -> bBitResolution
                    }

                    Log.i(TAG, "parseBestAltSetting: alt=$currentAlt subslotSize=$bSubslotSize bitResolution=$bBitResolution wireBits=$wireBitDepth rates=$rates")

                    if (currentAlt > 0) {
                        altSettings.add(AltSettingFormat(currentAlt, bBitResolution, wireBitDepth, rates))
                    }

                    if ((bBitResolution > bestBits || (bBitResolution == bestBits && wireBitDepth > bestWireBits)) && currentAlt > 0) {
                        bestBits = bBitResolution

                        bestWireBits = wireBitDepth

                        bestAlt = currentAlt
                    }
                }
            }

            i += bLength
        }

        parsedAltSettings = altSettings

        Log.i(TAG, "parseBestAltSetting: best alt=$bestAlt bits=$bestBits wireBits=$bestWireBits, all=$altSettings")

        return Pair(bestAlt, bestWireBits)
    }

    fun findAltSettingForBitDepth(targetBitDepth: Int, sampleRate: Int = 0): Pair<Int, Int> {
        if (parsedAltSettings.isEmpty()) {
            val info = cachedDeviceInfo ?: return Pair(1, 16)

            return Pair(info.bestAltSetting, info.bestBitDepth)
        }

        val candidates = if (sampleRate > 0) {
            val rateMatched = parsedAltSettings.filter { fmt ->
                fmt.rates.isEmpty() || fmt.rates.any { r -> sampleRate in r }
            }
            if (rateMatched.isNotEmpty()) rateMatched else parsedAltSettings
        } else {
            parsedAltSettings
        }

        val exact = candidates.firstOrNull { it.bitResolution == targetBitDepth }

        if (exact != null) {
            Log.i(TAG, "findAltSettingForBitDepth($targetBitDepth, rate=$sampleRate): exact match alt=${exact.altSetting} res=${exact.bitResolution} wire=${exact.wireBitDepth}")

            return Pair(exact.altSetting, exact.wireBitDepth)
        }

        val higher = candidates
                .filter { it.bitResolution > targetBitDepth }
                .minByOrNull { it.bitResolution }

        if (higher != null) {
            Log.i(TAG, "findAltSettingForBitDepth($targetBitDepth, rate=$sampleRate): next higher alt=${higher.altSetting} res=${higher.bitResolution} wire=${higher.wireBitDepth}")

            return Pair(higher.altSetting, higher.wireBitDepth)
        }

        val best = candidates.maxWithOrNull(compareBy({ it.bitResolution }, { it.wireBitDepth }))

        if (best != null) {
            Log.i(TAG, "findAltSettingForBitDepth($targetBitDepth, rate=$sampleRate): fallback to best alt=${best.altSetting} res=${best.bitResolution} wire=${best.wireBitDepth}")

            return Pair(best.altSetting, best.wireBitDepth)
        }

        return Pair(1, 16)
    }

    fun getEndpointInfoForAltSetting(altSetting: Int): Triple<Int, Int, Int>? {
        val device = currentDevice ?: return null
        val targetIfaceId = claimedStreamingInterface?.id ?: cachedDeviceInfo?.interfaceId ?: -1

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                iface.interfaceSubclass == 2 &&
                (targetIfaceId < 0 || iface.id == targetIfaceId) &&
                iface.alternateSetting == altSetting
            ) {
                var epOut = -1
                var epFb = -1
                var maxPkt = 0
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    when {
                        ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                                ep.direction == UsbConstants.USB_DIR_OUT -> {
                            epOut = ep.address
                            maxPkt = ep.maxPacketSize
                        }
                        ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                                ep.direction == UsbConstants.USB_DIR_IN -> {
                            epFb = ep.address
                        }
                    }
                }
                if (epOut >= 0 && maxPkt > 0) {
                    return Triple(epOut, epFb, maxPkt)
                }
            }
        }
        return null
    }

    fun closeDevice() = synchronized(deviceLock) {
        val conn = connection
        val fd = conn?.fileDescriptor ?: -1

        if (hasHardwareVolumeSupport()) {
            try {
                setHardwareVolume(1.0f)
                Log.i(TAG, "Restored DAC hardware volume to 1.0 (0 dB) in closeDevice")
            } catch (_: Exception) {}
        }

        cachedDeviceInfo = null

        claimedStreamingInterface?.let { iface ->
            val zeroAlt = currentDevice?.let { dev ->
                (0 until dev.interfaceCount)
                    .map { dev.getInterface(it) }
                    .firstOrNull { it.id == iface.id && it.alternateSetting == 0 }
            }
            if (zeroAlt != null) {
                try { conn?.setInterface(zeroAlt) } catch (_: Exception) {}
            }
            try { conn?.releaseInterface(iface) } catch (_: Exception) {}
            if (fd >= 0) {
                try { nativeReattachKernelDriver(fd, iface.id) } catch (_: Exception) {}
            }
            claimedStreamingInterface = null
        }

        claimedInterface = null

        claimedControlInterface?.let { iface ->
            try { conn?.releaseInterface(iface) } catch (_: Exception) {}
            if (fd >= 0) {
                try { nativeReattachKernelDriver(fd, iface.id) } catch (_: Exception) {}
            }
            claimedControlInterface = null
        }

        try { conn?.close() } catch (_: Exception) {}

        connection = null

        currentDevice = null

        Log.i(TAG, "USB device closed and all interfaces released")
    }

    fun hasHardwareVolumeSupport(): Boolean {
        val info = cachedDeviceInfo ?: return false

        return info.featureUnitId > 0
    }

    fun checkHardwareVolumeSupport(device: UsbDevice? = null): Boolean {
        if (cachedDeviceInfo != null) {
            return (cachedDeviceInfo?.featureUnitId ?: -1) > 0
        }
        val target = device ?: currentDevice ?: findUsbAudioDevice() ?: return false
        if (target.vendorId == 0x3302 && target.productId == 0x33E2) return true
        val pName = target.productName ?: ""
        if (pName.contains("CX31993", ignoreCase = true) || pName.contains("JM6PRO", ignoreCase = true)) return true
        if (!usbManager.hasPermission(target)) return false
        return try {
            val conn = usbManager.openDevice(target) ?: return false
            try {
                parseFeatureUnitId(conn) > 0
            } finally {
                conn.close()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun setHardwareVolume(volumeRatio: Float): Boolean = synchronized(deviceLock) {
        val conn = connection ?: run {
            Log.w(TAG, "setHardwareVolume: connection is null")

            return false
        }

        val info = cachedDeviceInfo ?: run {
            Log.w(TAG, "setHardwareVolume: cachedDeviceInfo is null")

            return false
        }

        val fuId = info.featureUnitId

        if (fuId <= 0) {
            Log.w(TAG, "setHardwareVolume: no valid Feature Unit with volume support")

            return false
        }

        val clampedVol = volumeRatio.coerceIn(0f, 1f)

        val val16: Short = if (clampedVol <= 0.0001f) {
            (-24576).toShort()
        } else {
            val db = (20.0 * kotlin.math.log10(clampedVol.toDouble())).coerceIn(-96.0, 0.0)

            (db * 256.0).toInt().toShort()
        }

        val data = ByteArray(2)

        data[0] = (val16.toInt() and 0xFF).toByte()

        data[1] = ((val16.toInt() shr 8) and 0xFF).toByte()

        val ctrlIface = info.audioControlInterfaceId

        val wIndex = (fuId shl 8) or (ctrlIface and 0xFF)

        val ret = conn.controlTransfer(
            0x21,
            0x01,
            0x0200,
            wIndex,
            data,
            data.size,
            250
        )

        if (ret == data.size) {
            Log.i(TAG, "setHardwareVolume($clampedVol): SUCCESS master channel (ret=$ret, dB=${val16.toFloat() / 256f})")

            return true
        }

        val ret1 = conn.controlTransfer(0x21, 0x01, 0x0201, wIndex, data, data.size, 250)

        val ret2 = conn.controlTransfer(0x21, 0x01, 0x0202, wIndex, data, data.size, 250)

        if (ret1 == data.size && ret2 == data.size) {
            Log.i(TAG, "setHardwareVolume($clampedVol): SUCCESS ch1/ch2 (ret1=$ret1, ret2=$ret2, dB=${val16.toFloat() / 256f})")

            return true
        }

        Log.w(TAG, "setHardwareVolume($clampedVol) failed for featureUnitId=0x${fuId.toString(16)} (master ret=$ret, ch1=$ret1, ch2=$ret2, expected=${data.size})")

        return false
    }

    private fun selectClockSelectorPinIfNeeded(conn: UsbDeviceConnection, targetClockSourceId: Int) {
        if (parsedClockSelectorId <= 0 || parsedClockSelectorPins.isEmpty()) return
        val pinZeroIndex = parsedClockSelectorPins.indexOf(targetClockSourceId)
        if (pinZeroIndex < 0) return
        val pinNumber = (pinZeroIndex + 1).toByte()
        val pinData = byteArrayOf(pinNumber)
        val ctrlIface = (cachedDeviceInfo?.audioControlInterfaceId ?: claimedControlInterface?.id ?: 0) and 0xFF
        val wIndex = (parsedClockSelectorId shl 8) or ctrlIface
        val ret = conn.controlTransfer(0x21, 0x01, 0x0100, wIndex, pinData, pinData.size, 1000)
        Log.i(TAG, "selectClockSelectorPin: selector=0x${parsedClockSelectorId.toString(16)} -> pin=$pinNumber (csId=0x${targetClockSourceId.toString(16)}, wIndex=0x${wIndex.toString(16)}, ret=$ret)")
    }

    private fun setUac1EndpointRate(conn: UsbDeviceConnection, rate: Int): Boolean {
        val epOut = cachedDeviceInfo?.endpointOutAddress ?: return false
        if (epOut <= 0) return false
        val data = byteArrayOf(
            (rate and 0xFF).toByte(),
            ((rate shr 8) and 0xFF).toByte(),
            ((rate shr 16) and 0xFF).toByte()
        )
        return conn.controlTransfer(0x22, 0x01, 0x0100, epOut, data, data.size, 1000) >= 0
    }

    private fun readUac1EndpointRate(conn: UsbDeviceConnection): Int {
        val epOut = cachedDeviceInfo?.endpointOutAddress ?: return -1
        if (epOut <= 0) return -1
        val data = ByteArray(3)
        val ret = conn.controlTransfer(0xA2, 0x81, 0x0100, epOut, data, data.size, 1000)
        if (ret < 3) return -1
        val rate = (data[0].toInt() and 0xFF) or
            ((data[1].toInt() and 0xFF) shl 8) or
            ((data[2].toInt() and 0xFF) shl 16)
        return if (rate in 8000..1536000) rate else -1
    }

    fun setSampleRate(sampleRateHz: Int): Boolean {
        val conn = connection ?: return false

        if (cachedDeviceInfo?.uacVersion == 100) {
            val ok = setUac1EndpointRate(conn, sampleRateHz)
            if (ok) {
                Log.i(TAG, "setSampleRate($sampleRateHz Hz): SUCCESS via UAC1 endpoint")
                return true
            }
        }

        val data = ByteArray(4)

        data[0] = (sampleRateHz and 0xFF).toByte()

        data[1] = ((sampleRateHz shr 8) and 0xFF).toByte()

        data[2] = ((sampleRateHz shr 16) and 0xFF).toByte()

        data[3] = ((sampleRateHz shr 24) and 0xFF).toByte()

        val detectedId = cachedDeviceInfo?.clockSourceId ?: -1

        val clockSourceIds = buildList {
            if (parsedClockSourceIds.isNotEmpty()) {
                addAll(parsedClockSourceIds)
            } else if (detectedId > 0) {
                add(detectedId)
            } else {
                addAll(listOf(0x05, 0x09, 0x0A, 0x0B, 0x0C, 0x0D,
                        0x28, 0x29, 0x2A, 0x06, 0x07, 0x08,
                        0x10, 0x11, 0x12, 0x20, 0x21, 0x22))
            }
        }.distinct()

        val ctrlIface = (cachedDeviceInfo?.audioControlInterfaceId ?: claimedControlInterface?.id ?: 0) and 0xFF

        for (csId in clockSourceIds) {
            selectClockSelectorPinIfNeeded(conn, csId)
            val wIndex = (csId shl 8) or ctrlIface

            val ret = conn.controlTransfer(
                    0x21,
                    0x01,
                    0x0100,
                    wIndex,
                    data,
                    data.size,
                    1000
            )

            if (ret >= 0) {
                activeClockSourceId = csId
                Log.i(TAG, "setSampleRate($sampleRateHz Hz): SUCCESS with clockSourceId=0x${csId.toString(16)} (wIndex=0x${wIndex.toString(16)}, ret=$ret)")

                return true
            }
        }

        if (setUac1EndpointRate(conn, sampleRateHz)) {
            Log.i(TAG, "setSampleRate($sampleRateHz Hz): SUCCESS via UAC1 endpoint fallback")
            return true
        }

        Log.w(TAG, "setSampleRate($sampleRateHz Hz): all clock source IDs failed, DAC may auto-detect")

        return false
    }

    fun readSampleRate(): Int {
        val conn = connection ?: return -1

        if (cachedDeviceInfo?.uacVersion == 100) {
            val uac1Rate = readUac1EndpointRate(conn)
            if (uac1Rate > 0) return uac1Rate
        }

        val data = ByteArray(4)

        val detectedId = cachedDeviceInfo?.clockSourceId ?: -1

        val clockSourceIds = buildList {
            if (activeClockSourceId > 0) add(activeClockSourceId)
            if (parsedClockSourceIds.isNotEmpty()) addAll(parsedClockSourceIds)
            else if (detectedId > 0) add(detectedId)
            else addAll(listOf(0x05, 0x09, 0x0A, 0x0B, 0x0C, 0x28, 0x29))
        }.distinct()

        val ctrlIface = (cachedDeviceInfo?.audioControlInterfaceId ?: claimedControlInterface?.id ?: 0) and 0xFF

        for (csId in clockSourceIds) {
            val wIndex = (csId shl 8) or ctrlIface

            val ret = conn.controlTransfer(
                    0xA1,
                    0x01,
                    0x0100,
                    wIndex,
                    data,
                    data.size,
                    1000
            )

            if (ret >= 4) {
                val rate = (data[0].toInt() and 0xFF) or

                        ((data[1].toInt() and 0xFF) shl 8) or

                        ((data[2].toInt() and 0xFF) shl 16) or

                        ((data[3].toInt() and 0xFF) shl 24)

                Log.i(TAG, "readSampleRate: GET_CUR clockSourceId=0x${csId.toString(16)} (wIndex=0x${wIndex.toString(16)}) " +
                        "returned $rate Hz (raw=${data.joinToString(",") { "0x${(it.toInt() and 0xFF).toString(16)}" }})")

                return rate
            }
        }

        val fallbackRate = readUac1EndpointRate(conn)
        if (fallbackRate > 0) return fallbackRate

        Log.w(TAG, "readSampleRate: all GET_CUR attempts failed")

        return -1
    }

    fun readClockValid(): Boolean {
        val conn = connection ?: return false

        if (cachedDeviceInfo?.uacVersion == 100) {
            return true
        }

        val data = ByteArray(1)

        val detectedId = cachedDeviceInfo?.clockSourceId ?: -1

        val clockSourceIds = buildList {
            if (activeClockSourceId > 0) add(activeClockSourceId)
            if (parsedClockSourceIds.isNotEmpty()) addAll(parsedClockSourceIds)
            else if (detectedId > 0) add(detectedId)
            else addAll(listOf(0x05, 0x09, 0x0A, 0x0B, 0x0C, 0x28, 0x29))
        }.distinct()

        val ctrlIface = (cachedDeviceInfo?.audioControlInterfaceId ?: claimedControlInterface?.id ?: 0) and 0xFF

        for (csId in clockSourceIds) {
            val wIndex = (csId shl 8) or ctrlIface

            val ret = conn.controlTransfer(
                    0xA1,
                    0x01,
                    0x0200,
                    wIndex,
                    data,
                    data.size,
                    1000
            )

            if (ret >= 1) {
                val valid = data[0].toInt() and 0x01

                Log.i(TAG, "readClockValid: clockSourceId=0x${csId.toString(16)} (wIndex=0x${wIndex.toString(16)}) valid=$valid")

                return valid == 1
            }
        }

        Log.w(TAG, "readClockValid: all GET_CUR attempts failed")

        return false
    }

    fun setAltSetting(altSetting: Int): Boolean {
        val conn = connection ?: return false

        val device = currentDevice ?: return false

        val targetIfaceId = claimedStreamingInterface?.id ?: cachedDeviceInfo?.interfaceId ?: -1

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)

            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                iface.interfaceSubclass == 2 &&
                (targetIfaceId < 0 || iface.id == targetIfaceId) &&
                iface.alternateSetting == altSetting) {
                val result = conn.setInterface(iface)

                Log.i(TAG, "setAltSetting($altSetting): $result " +
                        "(iface id=${iface.id}, endpoints=${iface.endpointCount})")

                return result
            }
        }

        Log.w(TAG, "setAltSetting($altSetting): no matching UsbInterface found, " +
                "trying all AudioStreaming interfaces...")

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)

            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                iface.interfaceSubclass == 2) {
                Log.d(TAG, "  interface $i: id=${iface.id} alt=${iface.alternateSetting} " +
                        "endpoints=${iface.endpointCount}")
            }
        }

        return false
    }
}
