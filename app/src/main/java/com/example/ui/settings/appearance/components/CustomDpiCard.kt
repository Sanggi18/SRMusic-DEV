package com.example.ui.settings.appearance.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.ui.common.ItemPosition
import com.example.ui.common.theme.springPress
import com.example.ui.settings.components.SettingsGroupItem
import kotlin.math.roundToInt

private const val MIN_DPI_PERCENT = 50
private const val MAX_DPI_PERCENT = 150
private const val STEP_DPI_PERCENT = 5

private fun snapToStep5(value: Int): Int {
    val snapped = ((value.toFloat() / STEP_DPI_PERCENT).roundToInt() * STEP_DPI_PERCENT)
    return snapped.coerceIn(MIN_DPI_PERCENT, MAX_DPI_PERCENT)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDpiCard(
    dpiScalePercent: Int,
    onDpiScaleChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftPercent by remember { mutableIntStateOf(snapToStep5(dpiScalePercent)) }
    val sliderInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(dpiScalePercent) {
        draftPercent = snapToStep5(dpiScalePercent)
    }

    SettingsGroupItem(
        position = ItemPosition.SINGLE,
        modifier = modifier.testTag("custom_dpi_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AspectRatio,
                            contentDescription = "Custom Display DPI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Custom Display DPI",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Adjust Display Scale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (draftPercent != 100) {
                    val resetInteraction = remember { MutableInteractionSource() }
                    val resetShape = RoundedCornerShape(8.dp)
                    Surface(
                        shape = resetShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .clip(resetShape)
                            .springPress(resetInteraction, pressedScale = 0.94f)
                            .clickable(
                                interactionSource = resetInteraction,
                                indication = ripple(bounded = true)
                            ) {
                                draftPercent = 100
                                onDpiScaleChange(100)
                            }
                            .testTag("dpi_reset_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Reset DPI to 100%",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = "$draftPercent%",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified
            ) {
                Slider(
                    value = draftPercent.toFloat(),
                    onValueChange = { value ->
                        draftPercent = snapToStep5(value.roundToInt())
                    },
                    onValueChangeFinished = {
                        onDpiScaleChange(draftPercent)
                    },
                    valueRange = MIN_DPI_PERCENT.toFloat()..MAX_DPI_PERCENT.toFloat(),
                    steps = 19,
                    interactionSource = sliderInteractionSource,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = sliderInteractionSource,
                            thumbSize = DpSize(4.dp, 18.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .testTag("dpi_slider")
                )
            }
        }
    }
}
