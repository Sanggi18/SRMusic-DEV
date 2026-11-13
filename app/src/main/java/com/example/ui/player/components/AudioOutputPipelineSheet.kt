package com.example.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.audio.AudioOutputInfo
import com.example.core.audio.AudioOutputType
import com.example.core.data.mediastore.tags.AudioStreamProps
import com.example.core.data.mediastore.tags.AudioTechnicalInfo
import com.example.core.model.Song
import com.example.ui.equalizer.core.EqualizerUiState

@Composable
fun AudioOutputPipelineSheet(
    song: Song,
    technicalInfo: AudioTechnicalInfo?,
    streamProps: AudioStreamProps?,
    audioOutputInfo: AudioOutputInfo,
    equalizerUiState: EqualizerUiState,
    onDismissRequest: () -> Unit
) {
    val isBitPerfectActive = equalizerUiState.effectiveBitPerfect

    val rawSampleRate = streamProps?.sampleRateHz ?: technicalInfo?.sampleRate?.let {
        Regex("(\\d+(\\.\\d+)?)").find(it)?.value?.toDoubleOrNull()?.let { num ->
            if (num < 1000) (num * 1000).toInt() else num.toInt()
        }
    } ?: 44100

    val sampleRateStr = if (rawSampleRate % 1000 == 0) {
        "${rawSampleRate / 1000} kHz"
    } else {
        String.format(java.util.Locale.US, "%.1f kHz", rawSampleRate / 1000f)
    }

    val formatStr = (streamProps?.format ?: technicalInfo?.format?.takeIf { it.isNotBlank() } ?: song.displayName?.substringAfterLast('.', "PCM") ?: "PCM").uppercase()

    val bitDepthStr = streamProps?.bitDepth?.let { "${it} bit" }
        ?: technicalInfo?.bitDepth?.takeIf { it.isNotBlank() }
        ?: "16 bit"

    val bitrateStr = if (streamProps?.bitrateKbps != null && streamProps.bitrateKbps > 0) {
        "${streamProps.bitrateKbps} kbps"
    } else if (!technicalInfo?.bitrate.isNullOrBlank()) {
        technicalInfo.bitrate ?: ""
    } else ""

    val primaryAccent = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(max = 440.dp)
                .testTag("audio_output_pipeline_popup")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Audio Output Pipeline",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                PipelineNode(
                    icon = Icons.Rounded.MusicNote,
                    iconTint = if (isBitPerfectActive) primaryAccent else primaryAccent.copy(alpha = 0.85f),
                    nodeTitle = "Audio Source",
                    isFirst = true,
                    isLast = false
                ) {
                    Text(
                        text = "${song.artist} - ${song.title}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = buildString {
                            append(formatStr)
                            if (bitrateStr.isNotBlank()) append(" · $bitrateStr")
                            append(" · $sampleRateStr")
                            if (bitDepthStr.isNotBlank()) append(" · $bitDepthStr")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                PipelineNode(
                    icon = Icons.Rounded.Memory,
                    iconTint = if (isBitPerfectActive) mutedColor else primaryAccent,
                    nodeTitle = "Decoder",
                    isFirst = false,
                    isLast = false
                ) {
                    Text(
                        text = "FFmpeg Built-in Audio Decoder",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                PipelineNode(
                    icon = Icons.Rounded.VolumeUp,
                    iconTint = if (isBitPerfectActive) mutedColor else primaryAccent,
                    nodeTitle = "Volume Control",
                    isFirst = false,
                    isLast = false
                ) {
                    Text(
                        text = if (isBitPerfectActive) "Hardware Volume (Direct Hardware DAC)" else "System Volume (Software Audio Mixer)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val isEqEffectivelyActive = !isBitPerfectActive && equalizerUiState.isEqualizerEnabled

                PipelineNode(
                    icon = Icons.Rounded.Equalizer,
                    iconTint = if (isEqEffectivelyActive) primaryAccent else mutedColor,
                    nodeTitle = "EQ",
                    isFirst = false,
                    isLast = false
                ) {
                    if (isBitPerfectActive) {
                        Text(
                            text = "State: Disable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (equalizerUiState.isEqualizerEnabled) {
                        Text(
                            text = "State: Active",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )
                        )

                        Text(
                            text = "10-Band Equalizer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "State: Disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val isDspEffectivelyActive = !isBitPerfectActive && (equalizerUiState.preampGain > 0f || equalizerUiState.bassBoost > 0f || equalizerUiState.trebleBoost > 0f || equalizerUiState.vocalBoost > 0f)

                PipelineNode(
                    icon = Icons.Rounded.Tune,
                    iconTint = if (isDspEffectivelyActive) primaryAccent else mutedColor,
                    nodeTitle = "DSP",
                    isFirst = false,
                    isLast = false
                ) {
                    if (isBitPerfectActive) {
                        Text(
                            text = "State: Disable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isDspEffectivelyActive) {
                        Text(
                            text = "State: Active",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )
                        )

                        Text(
                            text = buildString {
                                if (equalizerUiState.preampGain > 0f) append("Preamp +${equalizerUiState.preampGain.toInt()}dB ")
                                if (equalizerUiState.bassBoost > 0f) append("· Bass Boost ")
                                if (equalizerUiState.trebleBoost > 0f) append("· Treble Boost")
                            }.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "State: Disabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val outputDeviceName = when {
                    audioOutputInfo.isUsbDac -> audioOutputInfo.displayName.removePrefix("USB Audio : ")
                    audioOutputInfo.type == AudioOutputType.BLUETOOTH -> audioOutputInfo.displayName
                    audioOutputInfo.type == AudioOutputType.WIRED -> audioOutputInfo.displayName
                    else -> "Internal Speaker"
                }

                val outputBackendLabel = if (isBitPerfectActive) {
                    "USB Exclusive (Direct)"
                } else {
                    val label = equalizerUiState.selectedAudioOutputApi.label
                    if (label.contains("Standard", ignoreCase = true) || label.contains("Android Audio Pipeline", ignoreCase = true)) {
                        "Android Audio Pipeline"
                    } else {
                        label
                    }
                }

                val outputBits = bitDepthStr

                PipelineNode(
                    icon = when (audioOutputInfo.type) {
                        AudioOutputType.USB -> Icons.Rounded.Usb
                        AudioOutputType.BLUETOOTH -> Icons.Rounded.Bluetooth
                        AudioOutputType.WIRED -> Icons.Rounded.Headphones
                        AudioOutputType.SPEAKER -> Icons.Rounded.Speaker
                    },
                    iconTint = if (isBitPerfectActive) primaryAccent else primaryAccent.copy(alpha = 0.85f),
                    nodeTitle = "Signal Output",
                    isFirst = false,
                    isLast = true
                ) {
                    Text(
                        text = "Device : $outputDeviceName",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (audioOutputInfo.type == AudioOutputType.BLUETOOTH) {
                        val codecLabel = audioOutputInfo.bluetoothCodec ?: "A2DP Standard"
                        Text(
                            text = "Codec : $codecLabel",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )
                        )
                    }

                    Text(
                        text = "Backend : $outputBackendLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isBitPerfectActive) {
                        Text(
                            text = "Bit Perfect : Active",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bit Depth : $outputBits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(130.dp)
                        )
                        Text(
                            text = "Sample Rate : $sampleRateStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Channels : 2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(130.dp)
                        )
                        Text(
                            text = "Format : PCM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineNode(
    icon: ImageVector,
    iconTint: Color,
    nodeTitle: String,
    isFirst: Boolean,
    isLast: Boolean,
    content: @Composable () -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(lineColor)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.16f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(IntrinsicSize.Min)
                        .background(lineColor)
                )

                Spacer(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(lineColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp, bottom = if (isLast) 4.dp else 18.dp)
        ) {
            Text(
                text = nodeTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(3.dp))

            content()
        }
    }
}
