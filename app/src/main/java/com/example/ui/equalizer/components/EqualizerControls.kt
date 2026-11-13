package com.example.ui.equalizer.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.example.core.audio.AudioOutputApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun BitPerfectCard(
    isBitPerfectEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isDacConnected: Boolean = true,
    isDsdTrack: Boolean = false,
    onDisabledClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dacName: String? = null
) {
    val effectiveBitPerfect = isBitPerfectEnabled && isDacConnected && !isDsdTrack

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (effectiveBitPerfect)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = (!isDacConnected || isDsdTrack) && onDisabledClick != null) {
                onDisabledClick?.invoke()
            }
            .testTag("bit_perfect_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bit Perfect USB",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Direct playback to a USB DAC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                val (statusText, statusColor) = when {
                    isDsdTrack -> {
                        "● Disabled for DSD · Cannot resample in bit perfect mode" to MaterialTheme.colorScheme.error
                    }
                    !isDacConnected -> {
                        "● No USB DAC connected" to MaterialTheme.colorScheme.error
                    }
                    effectiveBitPerfect -> {
                        val label = dacName?.takeIf { it.isNotBlank() } ?: "USB DAC"
                        "● Active · $label" to MaterialTheme.colorScheme.primary
                    }
                    else -> {
                        val label = dacName?.takeIf { it.isNotBlank() } ?: "USB DAC"
                        "● Connected · $label" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = effectiveBitPerfect,
                onCheckedChange = {
                    if (isDsdTrack || !isDacConnected) {
                        onDisabledClick?.invoke()
                    } else {
                        onToggle(it)
                    }
                },
                enabled = isDacConnected && !isDsdTrack,
                modifier = Modifier.testTag("bit_perfect_switch")
            )
        }
    }
}

@Composable
fun AudioOutputCard(
    selectedApi: com.example.core.audio.AudioOutputApi,
    onApiSelected: (com.example.core.audio.AudioOutputApi) -> Unit,
    modifier: Modifier = Modifier,
    isBitPerfectActive: Boolean = false
) {
    val context = LocalContext.current

    var showDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                if (isBitPerfectActive) {
                    Toast.makeText(
                        context,
                        "Audio output is routed to USB Exclusive when Bit-Perfect is active.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showDialog = true
                }
            }
            .testTag("audio_output_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio Output",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Select Audio Playback Engine",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isBitPerfectActive) "USB Exclusive" else selectedApi.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Audio Output") },
            text = {
                Column {
                    com.example.core.audio.AudioOutputApi.entries.forEach { api ->
                        val description = when (api) {
                            com.example.core.audio.AudioOutputApi.AAUDIO -> "Low Latency Native Audio"
                            com.example.core.audio.AudioOutputApi.OPENSL_ES -> "Legacy Native Audio"
                            com.example.core.audio.AudioOutputApi.AUDIOTRACK -> "Standart Android Audio"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onApiSelected(api)

                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(api.label, style = MaterialTheme.typography.bodyLarge)

                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = selectedApi == api,
                                onClick = {
                                    onApiSelected(api)

                                    showDialog = false
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

@Composable
fun PreampCard(
    preampGain: Float,
    isEqActive: Boolean,
    onPreampGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preamp Gain",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = preampGain.formatDb(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isEqActive && preampGain > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            ExpressiveTrackSlider(
                value = preampGain,
                onValueChange = onPreampGainChange,
                valueRange = 0f..12f,
                enabled = isEqActive,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ToneControlCard(
    bassBoost: Float,
    vocalBoost: Float,
    trebleBoost: Float,
    isEqActive: Boolean,
    onBassBoostChange: (Float) -> Unit,
    onVocalBoostChange: (Float) -> Unit,
    onTrebleBoostChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Tone Control",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            ToneSliderRow(
                title = "Bass Boost",
                value = bassBoost,
                onValueChange = onBassBoostChange,
                enabled = isEqActive
            )

            ToneSliderRow(
                title = "Vocal Boost",
                value = vocalBoost,
                onValueChange = onVocalBoostChange,
                enabled = isEqActive
            )

            ToneSliderRow(
                title = "Treble Boost",
                value = trebleBoost,
                onValueChange = onTrebleBoostChange,
                enabled = isEqActive
            )
        }
    }
}

@Composable
private fun ToneSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(88.dp)
        )

        ExpressiveTrackSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..10f,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value.formatDb(),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled && value > 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
fun ExpressiveTrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color? = null,
    inactiveColor: Color? = null
) {
    var isUserDragging by remember { mutableStateOf(false) }

    var dragFraction by remember { mutableFloatStateOf(0f) }

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)

    val currentFraction = if (isUserDragging) {
        dragFraction.coerceIn(0f, 1f)
    } else {
        ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    }

    val primary = activeColor ?: MaterialTheme.colorScheme.primary

    val trackActive = if (enabled) primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    val trackInactive = inactiveColor ?: (if (enabled) primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))

    val thumbColor = if (enabled) primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .height(32.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    down.consume()

                    val widthPx = size.width.toFloat()

                    if (widthPx > 0f) {
                        val initialFraction = (down.position.x / widthPx).coerceIn(0f, 1f)

                        isUserDragging = true

                        dragFraction = initialFraction

                        val newValue = valueRange.start + initialFraction * rangeSpan

                        onValueChange((newValue * 10).roundToInt() / 10f)
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()

                            val pointerChange = event.changes.firstOrNull() ?: break

                            if (!pointerChange.pressed) {
                                pointerChange.consume()

                                break
                            } else {
                                pointerChange.consume()

                                if (widthPx > 0f) {
                                    val frac = (pointerChange.position.x / widthPx).coerceIn(0f, 1f)

                                    dragFraction = frac

                                    val newValue = valueRange.start + frac * rangeSpan

                                    onValueChange((newValue * 10).roundToInt() / 10f)
                                }
                            }
                        }
                    } finally {
                        isUserDragging = false
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            val canvasWidth = size.width

            val canvasHeight = size.height

            val centerY = canvasHeight / 2f

            val trackHeightPx = 13.dp.toPx()

            val trackRadiusPx = trackHeightPx / 2f

            val trackTop = centerY - (trackHeightPx / 2f)

            val progressX = (canvasWidth * currentFraction).coerceIn(0f, canvasWidth)

            val fullTrackPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = trackTop,
                        right = canvasWidth,
                        bottom = trackTop + trackHeightPx,
                        cornerRadius = CornerRadius(trackRadiusPx, trackRadiusPx)
                    )
                )
            }

            drawPath(path = fullTrackPath, color = trackInactive)

            val dotRadiusPx = 2.75.dp.toPx()

            val dotCenter = Offset(canvasWidth - 10.dp.toPx(), centerY)

            drawCircle(color = trackActive.copy(alpha = 0.5f), radius = dotRadiusPx, center = dotCenter)

            if (progressX > 0f) {
                val activeTrackPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = trackTop,
                            right = progressX,
                            bottom = trackTop + trackHeightPx,
                            cornerRadius = CornerRadius(trackRadiusPx, trackRadiusPx)
                        )
                    )
                }

                drawPath(path = activeTrackPath, color = trackActive)
            }

            val thumbWidthPx = 4.dp.toPx()

            val thumbHeightPx = if (isUserDragging) 26.dp.toPx() else 24.dp.toPx()

            val thumbRadiusCornerPx = 2.dp.toPx()

            val thumbX = (progressX - (thumbWidthPx / 2f)).coerceIn(0f, canvasWidth - thumbWidthPx)

            val thumbY = centerY - (thumbHeightPx / 2f)

            val thumbPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = thumbX,
                        top = thumbY,
                        right = thumbX + thumbWidthPx,
                        bottom = thumbY + thumbHeightPx,
                        cornerRadius = CornerRadius(thumbRadiusCornerPx, thumbRadiusCornerPx)
                    )
                )
            }

            drawPath(path = thumbPath, color = thumbColor)
        }
    }
}
