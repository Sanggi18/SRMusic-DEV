package com.example.ui.settings.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.common.ItemPosition
import com.example.ui.common.theme.ThemeMode
import com.example.ui.settings.SettingsDestination
import com.example.ui.settings.appearance.components.ColorPreviewCapsule
import com.example.ui.settings.appearance.components.SectionHeaderLeft
import com.example.ui.settings.appearance.components.SwitchAiPreferenceItem
import com.example.ui.settings.appearance.components.highContrastSwitchColors
import com.example.ui.settings.components.CustomColorPickerDialog
import com.example.ui.settings.components.getItemPosition

@Composable
fun AppThemeScreen(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    isDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    isArtworkAccent: Boolean,
    onArtworkAccentChange: (Boolean) -> Unit,
    currentAccentColor: Color,
    onAccentColorChange: (Color) -> Unit,
    currentBackgroundColor: Color?,
    onBackgroundColorChange: (Color?) -> Unit,
    currentFontColor: Color?,
    onFontColorChange: (Color?) -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
    currentCustomThemePresetId: String = "catppuccin_mocha"
) {
    val context = LocalContext.current

    var showAccentPopup by remember { mutableStateOf(false) }

    var showBackgroundPopup by remember { mutableStateOf(false) }

    var showFontColorPopup by remember { mutableStateOf(false) }

    val activeBgColor = currentBackgroundColor ?: MaterialTheme.colorScheme.background

    val activeFontColor = currentFontColor ?: MaterialTheme.colorScheme.onSurface

    val isDarkCanvas = isSystemInDarkTheme() ||
        currentThemeMode == ThemeMode.AMOLED ||
        currentThemeMode == ThemeMode.PITCH_BLACK ||
        currentThemeMode == ThemeMode.DARK ||
        activeBgColor.red * 0.299f + activeBgColor.green * 0.587f + activeBgColor.blue * 0.114f < 0.5f

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .padding(bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            SectionHeaderLeft("Color Scheme")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SwitchAiPreferenceItem(
                    position = ItemPosition.TOP,
                    title = "Material You",
                    subtitle = "Dynamic accent from system wallpaper",
                    icon = Icons.Rounded.ColorLens,
                    onClick = {
                        if (currentThemeMode == ThemeMode.CUSTOM) {
                            Toast.makeText(
                                context,
                                "Select another theme (System/Light/Dark/Amoled) first to enable Material You",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val next = !isDynamicColor

                            onDynamicColorChange(next)

                            if (next) {
                                onBackgroundColorChange(null)

                                onFontColorChange(null)
                            }
                        }
                    },
                    testTag = "theme_row_material_you"
                ) {
                    Switch(
                        checked = isDynamicColor,
                        onCheckedChange = { checked ->
                            if (currentThemeMode == ThemeMode.CUSTOM) {
                                Toast.makeText(
                                    context,
                                    "Select another theme (System/Light/Dark/Amoled) first to enable Material You",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onDynamicColorChange(checked)

                                if (checked) {
                                    onBackgroundColorChange(null)

                                    onFontColorChange(null)
                                }
                            }
                        },
                        colors = highContrastSwitchColors(isDarkCanvas)
                    )
                }

                SwitchAiPreferenceItem(
                    position = ItemPosition.BOTTOM,
                    title = "Adaptive Artwork",
                    subtitle = "Accent color from current track's artwork",
                    icon = Icons.Rounded.Brush,
                    onClick = { onArtworkAccentChange(!isArtworkAccent) },
                    testTag = "theme_row_artwork_accent"
                ) {
                    Switch(
                        checked = isArtworkAccent,
                        onCheckedChange = onArtworkAccentChange,
                        colors = highContrastSwitchColors(isDarkCanvas)
                    )
                }
            }
        }

        Column {
            SectionHeaderLeft("Theme Mode")

            val themeModes = listOf(
                ThemeModeOption(ThemeMode.SYSTEM, "System Default", "Match device appearance settings", Icons.Rounded.BrightnessAuto),
                ThemeModeOption(ThemeMode.LIGHT, "Light", "Standard light background and surfaces", Icons.Rounded.LightMode),
                ThemeModeOption(ThemeMode.DARK, "Dark", "Standard dark background and surfaces", Icons.Rounded.DarkMode),
                ThemeModeOption(ThemeMode.AMOLED, "AMOLED", "Pure black background with dark surfaces", Icons.Rounded.Nightlight),
                ThemeModeOption(ThemeMode.PITCH_BLACK, "Pitch Black", "Fully black background and surfaces", Icons.Rounded.DarkMode),
                ThemeModeOption(ThemeMode.CUSTOM, "Custom", "User-defined colors from Custom Palette", Icons.Rounded.Palette)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                themeModes.forEachIndexed { index, item ->
                    val isSelected = currentThemeMode == item.mode

                    val position = getItemPosition(index, themeModes.size)

                    SwitchAiPreferenceItem(
                        position = position,
                        title = item.title,
                        subtitle = item.subtitle,
                        icon = item.icon,
                        isSelected = isSelected,
                        onClick = {
                            if (item.mode == ThemeMode.CUSTOM && isDynamicColor) {
                                Toast.makeText(
                                    context,
                                    "Disable Material You first to select Custom Theme",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                if (item.mode == ThemeMode.CUSTOM) {
                                    val (inheritedBg, inheritedFont) = when (currentThemeMode) {
                                        ThemeMode.LIGHT -> Color(0xFFF7F7FA) to Color(0xFF1A1C1E)
                                        ThemeMode.DARK -> Color(0xFF111215) to Color(0xFFE2E2E6)
                                        ThemeMode.AMOLED, ThemeMode.PITCH_BLACK -> Color(0xFF000000) to Color(0xFFFFFFFF)
                                        else -> if (isDarkCanvas) Color(0xFF111215) to Color(0xFFE2E2E6) else Color(0xFFF7F7FA) to Color(0xFF1A1C1E)
                                    }

                                    if (currentBackgroundColor == null) {
                                        onBackgroundColorChange(inheritedBg)
                                    }

                                    if (currentFontColor == null) {
                                        onFontColorChange(inheritedFont)
                                    }
                                }

                                onThemeModeChange(item.mode)

                                if (item.mode != ThemeMode.CUSTOM) {
                                    onBackgroundColorChange(null)

                                    onFontColorChange(null)
                                }
                            }
                        },
                        testTag = "theme_row_mode_${item.title.lowercase().replace(' ', '_')}"
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                    }
                }
            }
        }

        Column {
            SectionHeaderLeft("Custom Palette")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SwitchAiPreferenceItem(
                    position = ItemPosition.TOP,
                    title = "Accent Color",
                    subtitle = "Primary highlight for UI elements",
                    icon = Icons.Rounded.Colorize,
                    onClick = {
                        if (isDynamicColor) {
                            Toast.makeText(
                                context,
                                "Disable Material You first to change Custom Accent",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (isArtworkAccent) {
                            Toast.makeText(
                                context,
                                "Disable Artwork Accent first to change Custom Accent",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showAccentPopup = true
                        }
                    },
                    testTag = "theme_row_custom_accent"
                ) {
                    ColorPreviewCapsule(color = currentAccentColor)
                }

                SwitchAiPreferenceItem(
                    position = ItemPosition.MIDDLE,
                    title = "Background Color",
                    subtitle = "Main application background",
                    icon = Icons.Rounded.FormatColorFill,
                    onClick = {
                        if (isDynamicColor) {
                            Toast.makeText(
                                context,
                                "Disable Material You first to change Custom Background Color",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showBackgroundPopup = true
                        }
                    },
                    testTag = "theme_row_custom_background"
                ) {
                    ColorPreviewCapsule(color = activeBgColor)
                }

                SwitchAiPreferenceItem(
                    position = ItemPosition.MIDDLE,
                    title = "Text Color",
                    subtitle = "Primary typography and icon color",
                    icon = Icons.Rounded.FormatColorText,
                    onClick = {
                        if (isDynamicColor) {
                            Toast.makeText(
                                context,
                                "Disable Material You first to change Custom Font Color",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showFontColorPopup = true
                        }
                    },
                    testTag = "theme_row_custom_font_color"
                ) {
                    ColorPreviewCapsule(color = activeFontColor)
                }

                SwitchAiPreferenceItem(
                    position = ItemPosition.BOTTOM,
                    title = "Theme Presets",
                    subtitle = "Pre-configured color schemes",
                    icon = Icons.Rounded.DashboardCustomize,
                    onClick = {
                        if (isDynamicColor) {
                            Toast.makeText(
                                context,
                                "Disable Material You first to select Theme Preset",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            onNavigate(SettingsDestination.CustomThemes)
                        }
                    },
                    testTag = "theme_row_theme_preset"
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Open theme presets",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showAccentPopup) {
        CustomColorPickerDialog(
            initialColor = currentAccentColor,
            onColorSelected = { color ->
                onAccentColorChange(color)

                onDynamicColorChange(false)

                com.example.core.util.ArtworkPaletteExtractor.clearCache()

                showAccentPopup = false
            },
            onDismiss = { showAccentPopup = false },
            title = "Choose Accent Color"
        )
    }

    if (showBackgroundPopup) {
        CustomColorPickerDialog(
            initialColor = activeBgColor,
            onColorSelected = { color ->
                onBackgroundColorChange(color)

                onThemeModeChange(ThemeMode.CUSTOM)

                onDynamicColorChange(false)

                showBackgroundPopup = false
            },
            onDismiss = { showBackgroundPopup = false },
            title = "Choose Background Color"
        )
    }

    if (showFontColorPopup) {
        CustomColorPickerDialog(
            initialColor = activeFontColor,
            onColorSelected = { color ->
                onFontColorChange(color)

                onThemeModeChange(ThemeMode.CUSTOM)

                onDynamicColorChange(false)

                showFontColorPopup = false
            },
            onDismiss = { showFontColorPopup = false },
            title = "Choose Font Color"
        )
    }
}

private data class ThemeModeOption(
    val mode: ThemeMode,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)
