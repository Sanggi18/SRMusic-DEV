package com.example.ui.common.theme

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.content.Context
import android.content.ContextWrapper

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this

    is ContextWrapper -> baseContext.findActivity()

    else -> null
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark"),
    AMOLED("AMOLED"),
    PITCH_BLACK("Pitch Black"),
    CUSTOM("Custom")
}


private fun blendColor(base: Color, overlay: Color, amount: Float): Color {
    val r = base.red + (overlay.red - base.red) * amount

    val g = base.green + (overlay.green - base.green) * amount

    val b = base.blue + (overlay.blue - base.blue) * amount

    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
}

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = PixelPlayWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = PixelPlayPink,
    onSecondary = PixelPlayWhite,
    secondaryContainer = Color(0xFFFFD8E4),
    onSecondaryContainer = Color(0xFF3B071E),
    tertiary = PixelPlayOrange,
    onTertiary = PixelPlayBlack,
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF341000),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

private val AmoledColorScheme = darkColorScheme(
    primary = PixelPlayPurplePrimary,
    onPrimary = PixelPlayWhite,
    primaryContainer = Color(0xFF371B58),
    onPrimaryContainer = Color(0xFFF3E5F5),
    secondary = PixelPlayPink,
    onSecondary = PixelPlayWhite,
    secondaryContainer = Color(0xFF4A192C),
    onSecondaryContainer = Color(0xFFFFD8E4),
    tertiary = PixelPlayOrange,
    onTertiary = PixelPlayBlack,
    background = AmoledBg,
    onBackground = AmoledOnSurface,
    surface = AmoledSurface,
    onSurface = AmoledOnSurface,
    surfaceVariant = AmoledSurfaceContainer,
    onSurfaceVariant = AmoledOnSurface.copy(alpha = 0.75f),
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = AmoledSurfaceContainerHighest,
    surfaceContainerLow = AmoledBg,
    outline = AmoledBorder,
    outlineVariant = AmoledBorder
)

private val PitchBlackColorScheme = darkColorScheme(
    primary = PixelPlayPurplePrimary,
    onPrimary = PixelPlayWhite,
    primaryContainer = Color(0xFF000000),
    onPrimaryContainer = PixelPlayWhite,
    secondary = PixelPlayPink,
    onSecondary = PixelPlayWhite,
    secondaryContainer = Color(0xFF000000),
    onSecondaryContainer = PixelPlayWhite,
    tertiary = PixelPlayOrange,
    onTertiary = PixelPlayBlack,
    background = Color(0xFF000000),
    onBackground = AmoledOnSurface,
    surface = Color(0xFF000000),
    onSurface = AmoledOnSurface,
    surfaceVariant = Color(0xFF000000),
    onSurfaceVariant = AmoledOnSurface.copy(alpha = 0.75f),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF000000),
    surfaceContainerHighest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    outline = Color(0xFF1F1F24),
    outlineVariant = Color(0xFF16161A)
)

fun createAccentColorScheme(
    accent: Color,
    isDark: Boolean,
    customBg: Color? = null,
    customFontColor: Color? = null
): ColorScheme {
    val bg = customBg ?: if (isDark) PixelPlayPurpleDark else LightBackground

    val surface = customBg ?: if (isDark) PixelPlaySurface else LightSurface

    val bgLuminance = bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f

    val isBgLight = bgLuminance > 0.5f

    val onSurface = customFontColor ?: if (customBg != null) {
        if (isBgLight) LightOnSurface else PixelPlayLightPurple
    } else {
        if (isDark) PixelPlayLightPurple else LightOnSurface
    }

    val onSurfaceVariant = if (customFontColor != null) {
        customFontColor.copy(alpha = 0.72f)
    } else {
        if (isDark) PixelPlayMuted else LightOnSurfaceVariant
    }

    val surfaceContainer = if (isDark) {
        blendColor(PixelPlaySurfaceContainer, accent, 0.08f)
    } else {
        blendColor(Color(0xFFEFF2F8), accent, 0.05f)
    }

    val surfaceContainerHigh = if (isDark) {
        blendColor(PixelPlaySurfaceContainerHigh, accent, 0.14f)
    } else {
        blendColor(Color(0xFFE5E8F0), accent, 0.08f)
    }

    val surfaceContainerHighest = if (isDark) {
        blendColor(PixelPlaySurfaceContainerHighest, accent, 0.20f)
    } else {
        blendColor(Color(0xFFDBDFEA), accent, 0.12f)
    }

    val surfaceContainerLow = if (isDark) {
        blendColor(PixelPlaySurfaceContainerLow, bg, 0.50f)
    } else {
        Color(0xFFF5F6FA)
    }

    val outline = if (isDark) {
        blendColor(PixelPlayOutline, accent, 0.15f)
    } else {
        Color(0xFFCBD5E1)
    }

    return if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = if (accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f > 0.6f) Color.Black else Color.White,
            primaryContainer = blendColor(surface, accent, 0.35f),
            onPrimaryContainer = Color.White,
            secondary = accent,
            onSecondary = if (accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f > 0.6f) Color.Black else Color.White,
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            outline = outline,
            outlineVariant = outline
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = if (accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f > 0.6f) Color.Black else Color.White,
            primaryContainer = blendColor(Color.White, accent, 0.18f),
            onPrimaryContainer = if (accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f > 0.6f) Color(0xFF1E1237) else accent,
            secondary = accent,
            onSecondary = if (accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f > 0.6f) Color.Black else Color.White,
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            outline = outline,
            outlineVariant = outline
        )
    }
}

fun createCustomThemeColorScheme(
    variant: CustomThemeVariant,
    customAccent: Color? = null,
    customBg: Color? = null,
    customFontColor: Color? = null
): ColorScheme {
    val bg = customBg ?: variant.background

    val isTrueBlack = customBg != null && bg == Color(0xFF000000)

    val isLight = (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) > 0.5f

    val primary = customAccent ?: (if (isTrueBlack) SoftLightBlue else variant.primary)

    val onPrimary = if (primary.red * 0.299f + primary.green * 0.587f + primary.blue * 0.114f > 0.6f) Color.Black else Color.White

    val surface = when {
        isTrueBlack -> Color(0xFF000000)
        customBg != null -> blendColor(bg, if (isLight) Color.Black else Color.White, if (isLight) 0.03f else 0.04f)
        else -> variant.surface
    }

    val onSurface = customFontColor ?: (if (isTrueBlack) Color(0xFFFFFFFF) else if (isLight) Color(0xFF1A1C1E) else variant.onSurface)

    val onSurfaceVariant = customFontColor?.copy(alpha = 0.72f) ?: (if (isTrueBlack) Color(0xFFE2E2E6) else (if (isLight) Color(0xFF49454F) else Color(0xFFCAC4D0)))

    val surfaceContainer = if (isTrueBlack) Color(0xFF151518) else (if (customBg != null) blendColor(surface, primary, if (isLight) 0.08f else 0.10f) else (variant.surfaceContainer ?: blendColor(surface, primary, if (isLight) 0.08f else 0.10f)))

    val surfaceContainerHigh = if (isTrueBlack) Color(0xFF202025) else (if (customBg != null) blendColor(surface, primary, if (isLight) 0.14f else 0.16f) else (variant.surfaceContainerHigh ?: blendColor(surface, primary, if (isLight) 0.14f else 0.16f)))

    val surfaceContainerHighest = if (isTrueBlack) Color(0xFF2C2C33) else blendColor(surface, primary, if (isLight) 0.20f else 0.22f)

    val surfaceContainerLow = if (isTrueBlack) Color(0xFF0D0D10) else blendColor(surface, bg, 0.50f)

    val outlineColor = if (isTrueBlack) Color(0xFF383842) else blendColor(surface, primary, 0.25f)

    val outlineVariantColor = if (isTrueBlack) Color(0xFF24242C) else surfaceContainer

    return if (isLight) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = blendColor(Color.White, primary, 0.18f),
            onPrimaryContainer = primary,
            secondary = primary,
            onSecondary = onPrimary,
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            outline = outlineColor,
            outlineVariant = outlineVariantColor
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = blendColor(surface, primary, 0.35f),
            onPrimaryContainer = Color.White,
            secondary = primary,
            onSecondary = onPrimary,
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            outline = outlineColor,
            outlineVariant = outlineVariantColor
        )
    }
}

private fun darkDynamicCopy(dynamic: ColorScheme): ColorScheme {
    return dynamic.copy(
        background = Color(0xFF111215),
        surface = Color(0xFF131418),
        surfaceContainer = blendColor(Color(0xFF1C1D22), dynamic.primary, 0.05f),
        surfaceContainerHigh = blendColor(Color(0xFF24252C), dynamic.primary, 0.08f),
        surfaceContainerHighest = blendColor(Color(0xFF2E2F38), dynamic.primary, 0.12f),
        surfaceContainerLow = Color(0xFF16171B),
        outline = Color(0xFF383944),
        outlineVariant = Color(0xFF262730)
    )
}

private fun lightDynamicCopy(dynamic: ColorScheme): ColorScheme {
    return dynamic.copy(
        background = LightBackground,
        surface = LightSurface,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceVariant = LightSurfaceVariant,
        onSurface = LightOnSurface,
        onSurfaceVariant = LightOnSurfaceVariant,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant
    )
}

@Composable
fun SRMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    isDynamicColor: Boolean = true,
    customAccent: Color = AccentBlue,
    customBackgroundColor: Color? = null,
    customFontColor: Color? = null,
    customThemePresetId: String = "catppuccin_mocha",
    customFontPath: String? = null,
    dpiScalePercent: Int = 100,
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val baseScheme = when {
        isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themeMode != ThemeMode.CUSTOM -> {
            when (themeMode) {
                ThemeMode.AMOLED -> {
                    val dynamic = dynamicDarkColorScheme(context)

                    dynamic.copy(
                        background = AmoledBg,
                        surface = AmoledSurface,
                        surfaceContainer = blendColor(AmoledSurfaceContainer, dynamic.primary, 0.02f),
                        surfaceContainerHigh = blendColor(AmoledSurfaceContainerHigh, dynamic.primary, 0.04f),
                        surfaceContainerHighest = blendColor(AmoledSurfaceContainerHighest, dynamic.primary, 0.06f),
                        surfaceContainerLow = AmoledBg,
                        outline = AmoledBorder,
                        outlineVariant = Color(0xFF1C1C22)
                    )
                }

                ThemeMode.PITCH_BLACK -> {
                    val dynamic = dynamicDarkColorScheme(context)

                    dynamic.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceVariant = Color.Black,
                        surfaceContainer = Color.Black,
                        surfaceContainerHigh = Color.Black,
                        surfaceContainerHighest = Color.Black,
                        surfaceContainerLow = Color.Black,
                        primaryContainer = Color.Black,
                        secondaryContainer = Color.Black,
                        outline = Color(0xFF1F1F24),
                        outlineVariant = Color(0xFF16161A)
                    )
                }

                ThemeMode.LIGHT -> lightDynamicCopy(dynamicLightColorScheme(context))

                ThemeMode.DARK -> darkDynamicCopy(dynamicDarkColorScheme(context))

                ThemeMode.SYSTEM -> {
                    if (systemInDarkTheme) {
                        darkDynamicCopy(dynamicDarkColorScheme(context))
                    } else {
                        lightDynamicCopy(dynamicLightColorScheme(context))
                    }
                }

                else -> dynamicDarkColorScheme(context)
            }
        }

        themeMode == ThemeMode.CUSTOM -> {
            val variant = getVariantById(customThemePresetId)

            createCustomThemeColorScheme(
                variant = variant,
                customAccent = customAccent,
                customBg = customBackgroundColor,
                customFontColor = customFontColor
            )
        }

        themeMode == ThemeMode.AMOLED -> {
            val amoledPrimary = customAccent

            AmoledColorScheme.copy(
                primary = amoledPrimary,
                secondary = amoledPrimary,
                primaryContainer = blendColor(AmoledSurfaceContainerHigh, amoledPrimary, 0.35f),
                onPrimaryContainer = Color.White,
                surfaceContainer = blendColor(AmoledSurfaceContainer, amoledPrimary, 0.02f),
                surfaceContainerHigh = blendColor(AmoledSurfaceContainerHigh, amoledPrimary, 0.04f),
                surfaceContainerHighest = blendColor(AmoledSurfaceContainerHighest, amoledPrimary, 0.06f),
                surfaceContainerLow = AmoledBg,
                outline = AmoledBorder,
                outlineVariant = Color(0xFF1C1C22),
                onSurface = customFontColor ?: AmoledOnSurface,
                onBackground = customFontColor ?: AmoledOnSurface,
                onSurfaceVariant = customFontColor?.copy(alpha = 0.75f) ?: AmoledOnSurface.copy(alpha = 0.75f)
            )
        }

        themeMode == ThemeMode.PITCH_BLACK -> {
            PitchBlackColorScheme.copy(
                primary = customAccent,
                secondary = customAccent,
                onPrimaryContainer = customAccent,
                onSurface = customFontColor ?: AmoledOnSurface,
                onBackground = customFontColor ?: AmoledOnSurface,
                onSurfaceVariant = customFontColor?.copy(alpha = 0.75f) ?: AmoledOnSurface.copy(alpha = 0.75f)
            )
        }

        themeMode == ThemeMode.LIGHT -> {
            createAccentColorScheme(customAccent, isDark = false, customBg = customBackgroundColor, customFontColor = customFontColor)
        }

        themeMode == ThemeMode.DARK -> {
            createAccentColorScheme(customAccent, isDark = true, customBg = customBackgroundColor, customFontColor = customFontColor)
        }

        themeMode == ThemeMode.SYSTEM -> {
            createAccentColorScheme(customAccent, isDark = systemInDarkTheme, customBg = customBackgroundColor, customFontColor = customFontColor)
        }

        else -> LightColorScheme
    }

    val colorScheme = baseScheme

    val customFontFamily = androidx.compose.runtime.remember(customFontPath) {
        if (!customFontPath.isNullOrBlank()) {
            try {
                val file = java.io.File(customFontPath)

                if (file.exists() && file.canRead()) {
                    val typeface = android.graphics.Typeface.createFromFile(file)

                    if (typeface != null) {
                        androidx.compose.ui.text.font.FontFamily(typeface)
                    } else {
                        androidx.compose.ui.text.font.FontFamily.Default
                    }
                } else {
                    androidx.compose.ui.text.font.FontFamily.Default
                }
            } catch (_: Exception) {
                androidx.compose.ui.text.font.FontFamily.Default
            }
        } else {
            androidx.compose.ui.text.font.FontFamily.Default
        }
    }

    val typography = createAppTypography(customFontFamily)

    val view = LocalView.current

    if (!view.isInEditMode) {
        val isDark = colorScheme.background.luminance() <= 0.5f || colorScheme.surface.luminance() <= 0.5f

        DisposableEffect(isDark, colorScheme) {
            val activity = view.context.findActivity()

            val window = activity?.window ?: (view.context as? Activity)?.window

            if (activity != null) {
                activity.enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            if (window != null) {
                val decorView = window.decorView

                val insetsController = WindowCompat.getInsetsController(window, decorView)

                insetsController.isAppearanceLightStatusBars = !isDark

                insetsController.isAppearanceLightNavigationBars = !isDark
            }

            onDispose { }
        }
    }

    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val effectiveDensity = androidx.compose.runtime.remember(baseDensity, dpiScalePercent) {
        val factor = dpiScalePercent.coerceIn(50, 150) / 100f
        androidx.compose.ui.unit.Density(
            density = baseDensity.density * factor,
            fontScale = baseDensity.fontScale
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = {
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides effectiveDensity,
                LocalSpacing provides Spacing(),
                androidx.compose.foundation.LocalOverscrollFactory provides null,
                androidx.compose.foundation.LocalOverscrollConfiguration provides null,
                content = content
            )
        }
    )
}
