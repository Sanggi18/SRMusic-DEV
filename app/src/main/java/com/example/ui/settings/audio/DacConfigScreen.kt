package com.example.ui.settings.audio

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.dac.DacSampleRateMode
import com.example.core.audio.dac.DacVolumeMode
import com.example.core.audio.dac.DsdGainCompensation
import com.example.core.audio.dac.DsdOutputMode
import com.example.core.audio.usb.UsbAudioDevice
import com.example.core.audio.usb.UsbAudioTelemetry
import com.example.core.audio.usb.UsbSourceTrackFormat
import com.example.core.data.preferences.EqualizerPreferences
import com.example.core.playback.MusicService
import com.example.ui.settings.components.SettingsCard
import com.example.ui.settings.core.SettingsAction
import com.example.ui.settings.core.SettingsUiState
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
private fun DacSettingItemCard(
    title: String,
    subtitle: String,
    valueText: String,
    onClick: () -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DacConfigScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usbAudioDevice = remember(context) { UsbAudioDevice.getInstance(context) }
    var connectedDevice by remember { mutableStateOf(usbAudioDevice.findUsbAudioDevice()) }
    val telemetry by UsbAudioTelemetry.telemetry.collectAsStateWithLifecycle()

    var isBitPerfectPref by remember {
        mutableStateOf(EqualizerPreferences(context).loadEqualizerConfig().isBitPerfectEnabled)
    }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("srmusic_prefs", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "eq_bit_perfect" || key == null) {
                isBitPerfectPref = prefs.getBoolean("eq_bit_perfect", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    var showVolumeDialog by remember { mutableStateOf(false) }
    var showDsdOutputDialog by remember { mutableStateOf(false) }
    var showDsdGainDialog by remember { mutableStateOf(false) }
    var showBufferDialog by remember { mutableStateOf(false) }
    var showSampleRateModeDialog by remember { mutableStateOf(false) }
    var showFixedSampleRateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            connectedDevice = usbAudioDevice.findUsbAudioDevice()
            delay(1500)
        }
    }

    val isConnected = connectedDevice != null
    val isBitPerfectActive = isConnected && (
        telemetry.isStreaming ||
            MusicService.isBitPerfectActive() ||
            MusicService.isBitPerfectRequested() ||
            isBitPerfectPref
        )

    val manufacturerName = when {
        connectedDevice != null -> {
            connectedDevice?.manufacturerName?.trim()?.takeIf { it.isNotEmpty() }
                ?: connectedDevice?.productName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() }
                ?: "USB Audio Device"
        }
        else -> "No USB DAC Detected"
    }

    val productName = when {
        connectedDevice != null -> {
            connectedDevice?.productName?.trim()?.takeIf { it.isNotEmpty() }
                ?: "High-Resolution Audio Output"
        }
        else -> "Connect a USB DAC via Type-C/OTG"
    }

    val sourceFormat by UsbSourceTrackFormat.sourceFormatFlow.collectAsStateWithLifecycle()
    val sourceFormatText = sourceFormat.takeIf { it.isNotBlank() } ?: "-"

    val usbIdText = if (connectedDevice != null) {
        val vid = (connectedDevice?.vendorId ?: 0).toString(16).uppercase().padStart(4, '0')
        val pid = (connectedDevice?.productId ?: 0).toString(16).uppercase().padStart(4, '0')
        "$vid:$pid"
    } else {
        "-"
    }

    val cachedInfo = usbAudioDevice.currentDeviceInfo
    val activeBitDepth = when {
        !isConnected -> 0
        telemetry.isStreaming && telemetry.bitDepth > 0 -> telemetry.bitDepth
        UsbSourceTrackFormat.sourceBitDepth in listOf(16, 24, 32) -> UsbSourceTrackFormat.sourceBitDepth
        cachedInfo?.bestBitDepth != null && cachedInfo.bestBitDepth > 0 -> cachedInfo.bestBitDepth
        else -> 0
    }
    val depthText = if (activeBitDepth > 0) "$activeBitDepth bit" else "-"

    val activeSampleRate = when {
        !isConnected -> 0
        telemetry.isStreaming && telemetry.sampleRate > 0 -> telemetry.sampleRate
        uiState.dacSampleRateMode == DacSampleRateMode.FIXED && uiState.dacFixedSampleRate > 0 -> uiState.dacFixedSampleRate
        UsbSourceTrackFormat.sourceSampleRate > 0 -> UsbSourceTrackFormat.sourceSampleRate
        else -> 0
    }
    val sampleRateText = when {
        activeSampleRate <= 0 -> "-"
        activeSampleRate % 1000 == 0 -> "${activeSampleRate / 1000}khz"
        else -> String.format(Locale.US, "%.1fkhz", activeSampleRate / 1000f)
    }

    val formatText = if (isConnected && (telemetry.isStreaming || activeSampleRate > 0 || activeBitDepth > 0)) {
        "PCM"
    } else {
        "-"
    }

    val hasHardwareVol = remember(connectedDevice) {
        if (connectedDevice != null) usbAudioDevice.checkHardwareVolumeSupport(connectedDevice) else false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Usb,
                            contentDescription = "USB Exclusive",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "USB EXCLUSIVE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val dotColor = when {
                        !isConnected -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        isBitPerfectActive -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = manufacturerName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = productName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1.15f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "USB ID",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = usbIdText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1.15f))
                    Box(
                        modifier = Modifier.weight(0.75f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = sourceFormatText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1.15f)) {
                        Text(
                            text = "DEPTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = depthText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1.15f)) {
                        Text(
                            text = "SAMPLE RATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sampleRateText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(0.75f)) {
                        Text(
                            text = "FORMAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1.15f)) {
                        Text(
                            text = "Frame",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val framesCount = if (telemetry.isStreaming) telemetry.framesTransferred else 0L
                        Text(
                            text = "$framesCount frames",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val sec = if (telemetry.isStreaming && telemetry.sampleRate > 0) {
                            telemetry.framesTransferred.toDouble() / telemetry.sampleRate.toDouble()
                        } else {
                            0.0
                        }
                        Text(
                            text = String.format(Locale.US, "~%.1fs", sec),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(modifier = Modifier.weight(1.15f)) {
                        Text(
                            text = "Native Write",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val writeCalls = if (telemetry.isStreaming) telemetry.nativeWriteCalls else 0L
                        Text(
                            text = "#$writeCalls",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val sampleCount = if (telemetry.isStreaming) telemetry.lastSamplesCount else 0
                        Text(
                            text = "$sampleCount samples",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(modifier = Modifier.weight(0.75f)) {
                        Text(
                            text = "Write",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val latencyUs = if (telemetry.isStreaming) telemetry.lastWriteLatencyUs else 0L
                        Text(
                            text = "${latencyUs}us",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val latencyMs = if (telemetry.isStreaming) {
                            telemetry.lastWriteLatencyUs / 1000.0f
                        } else {
                            0.0f
                        }
                        Text(
                            text = String.format(Locale.US, "%.1fms", latencyMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        DacSettingItemCard(
            title = "Volume Control",
            subtitle = "Select volume adjustment mode during bit-perfect playback",
            valueText = uiState.dacVolumeMode.label,
            onClick = { showVolumeDialog = true }
        )

        DacSettingItemCard(
            title = "DSD Output",
            subtitle = "Choose how Direct Stream Digital audio is sent to the DAC",
            valueText = uiState.dsdOutputMode.label,
            onClick = { showDsdOutputDialog = true }
        )

        DacSettingItemCard(
            title = "DSD Gain Compensation",
            subtitle = "Apply gain boost for DSD-to-PCM conversion",
            valueText = uiState.dsdGainCompensation.label,
            onClick = { showDsdGainDialog = true }
        )

        DacSettingItemCard(
            title = "USB Buffer Size",
            subtitle = "Audio buffer frames transferred per USB transaction",
            valueText = "${uiState.dacUsbBufferSize} frames",
            onClick = { showBufferDialog = true }
        )

        DacSettingItemCard(
            title = "Sample Rate Mode",
            subtitle = "Follow track native rate or resample to a fixed rate",
            valueText = uiState.dacSampleRateMode.label,
            onClick = { showSampleRateModeDialog = true }
        )

        if (uiState.dacSampleRateMode == DacSampleRateMode.FIXED) {
            DacSettingItemCard(
                title = "Fixed Sample Rate",
                subtitle = "Output sample rate used for all audio playback",
                valueText = "${uiState.dacFixedSampleRate / 1000.0} kHz",
                onClick = { showFixedSampleRateDialog = true }
            )
        }
    }

    if (showVolumeDialog) {
        AlertDialog(
            onDismissRequest = { showVolumeDialog = false },
            title = {
                Text(
                    text = "Volume Control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Triple(
                            DacVolumeMode.AUTO,
                            "Auto",
                            if (hasHardwareVol) "Hardware volume if supported, else software" else "Software attenuation (DAC lacks hardware control)"
                        ),
                        Triple(
                            DacVolumeMode.SOFTWARE,
                            "Software",
                            "High-precision on-device 32-bit digital attenuation"
                        ),
                        Triple(
                            DacVolumeMode.HARDWARE,
                            "Hardware",
                            if (hasHardwareVol) "DAC internal hardware analog/digital mixer" else "Not supported by connected DAC (No UAC Feature Unit)"
                        )
                    ).forEach { (mode, label, desc) ->
                        val isSelected = uiState.dacVolumeMode == mode
                        val isSupported = mode != DacVolumeMode.HARDWARE || hasHardwareVol || connectedDevice == null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = isSupported) {
                                    onAction(SettingsAction.SetDacVolumeMode(mode))
                                    showVolumeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSupported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!isSupported) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(
                                selected = isSelected,
                                onClick = if (isSupported) {
                                    {
                                        onAction(SettingsAction.SetDacVolumeMode(mode))
                                        showVolumeDialog = false
                                    }
                                } else null,
                                enabled = isSupported
                            )
                        }
                    }

                    if (connectedDevice != null && !hasHardwareVol) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Connected DAC has no hardware mixer. Software mode protects against sudden loud volume.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showDsdOutputDialog) {
        AlertDialog(
            onDismissRequest = { showDsdOutputDialog = false },
            title = {
                Text(
                    text = "DSD Output Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DsdOutputMode.entries.forEach { mode ->
                        val isSelected = uiState.dsdOutputMode == mode
                        val desc = when (mode) {
                            DsdOutputMode.AUTO -> "Automatically choose best DSD playback method"
                            DsdOutputMode.DOP -> "DSD audio encapsulated in PCM frames (DoP)"
                            DsdOutputMode.PCM -> "Convert DSD stream to standard high-resolution PCM"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onAction(SettingsAction.SetDsdOutputMode(mode))
                                    showDsdOutputDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onAction(SettingsAction.SetDsdOutputMode(mode))
                                    showDsdOutputDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showDsdGainDialog) {
        AlertDialog(
            onDismissRequest = { showDsdGainDialog = false },
            title = {
                Text(
                    text = "DSD Gain Compensation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        DsdGainCompensation.PLUS_0DB to "Standard output without additional volume boost",
                        DsdGainCompensation.PLUS_6DB to "+6 dB volume boost to match standard PCM playback"
                    ).forEach { (gain, desc) ->
                        val isSelected = uiState.dsdGainCompensation == gain
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onAction(SettingsAction.SetDsdGainCompensation(gain))
                                    showDsdGainDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gain.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onAction(SettingsAction.SetDsdGainCompensation(gain))
                                    showDsdGainDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showBufferDialog) {
        AlertDialog(
            onDismissRequest = { showBufferDialog = false },
            title = {
                Text(
                    text = "USB Buffer Size",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        64 to "Ultra-low latency (Requires fast CPU)",
                        128 to "Low latency for responsive scrubbing",
                        256 to "Balanced buffer (Recommended)",
                        512 to "High stability buffer against underruns",
                        1024 to "Maximum buffer capacity for background playback"
                    ).forEach { (size, desc) ->
                        val isSelected = uiState.dacUsbBufferSize == size
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onAction(SettingsAction.SetDacUsbBufferSize(size))
                                    showBufferDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$size frames",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onAction(SettingsAction.SetDacUsbBufferSize(size))
                                    showBufferDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showSampleRateModeDialog) {
        AlertDialog(
            onDismissRequest = { showSampleRateModeDialog = false },
            title = {
                Text(
                    text = "Sample Rate Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        DacSampleRateMode.FOLLOW_SOURCE to "Automatically switch DAC to match track sampling rate",
                        DacSampleRateMode.FIXED to "Resample all tracks to a selected fixed sample rate"
                    ).forEach { (mode, desc) ->
                        val isSelected = uiState.dacSampleRateMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onAction(SettingsAction.SetDacSampleRateMode(mode))
                                    showSampleRateModeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onAction(SettingsAction.SetDacSampleRateMode(mode))
                                    showSampleRateModeDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showFixedSampleRateDialog) {
        AlertDialog(
            onDismissRequest = { showFixedSampleRateDialog = false },
            title = {
                Text(
                    text = "Fixed Sample Rate",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000).forEach { sr ->
                        val isSelected = uiState.dacFixedSampleRate == sr
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onAction(SettingsAction.SetDacFixedSampleRate(sr))
                                    showFixedSampleRateDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${sr / 1000.0} kHz",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onAction(SettingsAction.SetDacFixedSampleRate(sr))
                                    showFixedSampleRateDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
