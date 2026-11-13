package com.example.ui.common.theme

import androidx.compose.ui.graphics.Color

data class CustomThemeVariant(
    val id: String,
    val name: String,
    val primary: Color,
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onPrimary: Color
)

data class CustomThemePreset(
    val id: String,
    val name: String,
    val variants: List<CustomThemeVariant>
)

val PRESET_CUSTOM_THEMES = listOf(
    CustomThemePreset(
        id = "catppuccin",
        name = "Catppuccin",
        variants = listOf(
            CustomThemeVariant(
                id = "catppuccin_mocha",
                name = "Mocha",
                primary = Color(0xFFCBA6F7),
                background = Color(0xFF1E1E2E),
                surface = Color(0xFF181825),
                surfaceContainer = Color(0xFF313244),
                surfaceContainerHigh = Color(0xFF45475A),
                onSurface = Color(0xFFCDD6F4),
                onPrimary = Color(0xFF11111B)
            ),
            CustomThemeVariant(
                id = "catppuccin_macchiato",
                name = "Macchiato",
                primary = Color(0xFFC6A0F6),
                background = Color(0xFF24273A),
                surface = Color(0xFF1E2030),
                surfaceContainer = Color(0xFF363A4F),
                surfaceContainerHigh = Color(0xFF494D64),
                onSurface = Color(0xFFCAD3F5),
                onPrimary = Color(0xFF181926)
            ),
            CustomThemeVariant(
                id = "catppuccin_frappe",
                name = "Frappé",
                primary = Color(0xFFCA9EE6),
                background = Color(0xFF303446),
                surface = Color(0xFF292C3C),
                surfaceContainer = Color(0xFF414559),
                surfaceContainerHigh = Color(0xFF51576D),
                onSurface = Color(0xFFC6D0F5),
                onPrimary = Color(0xFF232634)
            ),
            CustomThemeVariant(
                id = "catppuccin_latte",
                name = "Latte",
                primary = Color(0xFF8839EF),
                background = Color(0xFFEFF1F5),
                surface = Color(0xFFE6E9EF),
                surfaceContainer = Color(0xFFCCD0DA),
                surfaceContainerHigh = Color(0xFFBCC0CC),
                onSurface = Color(0xFF4C4F69),
                onPrimary = Color(0xFFFFFFFF)
            )
        )
    ),
    CustomThemePreset(
        id = "tokyo_night",
        name = "Tokyo Night",
        variants = listOf(
            CustomThemeVariant(
                id = "tokyo_night_dark",
                name = "Night",
                primary = Color(0xFF7AA2F7),
                background = Color(0xFF1A1B26),
                surface = Color(0xFF16161E),
                surfaceContainer = Color(0xFF24283B),
                surfaceContainerHigh = Color(0xFF414868),
                onSurface = Color(0xFFC0CAF5),
                onPrimary = Color(0xFF15161E)
            ),
            CustomThemeVariant(
                id = "tokyo_night_storm",
                name = "Storm",
                primary = Color(0xFFBB9AF7),
                background = Color(0xFF24283B),
                surface = Color(0xFF1F2335),
                surfaceContainer = Color(0xFF2F354A),
                surfaceContainerHigh = Color(0xFF565F89),
                onSurface = Color(0xFFC0CAF5),
                onPrimary = Color(0xFF1F2335)
            ),
            CustomThemeVariant(
                id = "tokyo_night_day",
                name = "Day",
                primary = Color(0xFF34548A),
                background = Color(0xFFE1E2E7),
                surface = Color(0xFFD5D6DB),
                surfaceContainer = Color(0xFFC4C8DA),
                surfaceContainerHigh = Color(0xFFA8AECB),
                onSurface = Color(0xFF2E334D),
                onPrimary = Color(0xFFFFFFFF)
            )
        )
    ),
    CustomThemePreset(
        id = "gruvbox",
        name = "Gruvbox",
        variants = listOf(
            CustomThemeVariant(
                id = "gruvbox_dark",
                name = "Dark Hard",
                primary = Color(0xFFFABD2F),
                background = Color(0xFF282828),
                surface = Color(0xFF1D2021),
                surfaceContainer = Color(0xFF3C3836),
                surfaceContainerHigh = Color(0xFF504945),
                onSurface = Color(0xFFEBDBB2),
                onPrimary = Color(0xFF282828)
            ),
            CustomThemeVariant(
                id = "gruvbox_light",
                name = "Light Soft",
                primary = Color(0xFFB57614),
                background = Color(0xFFFBF1C7),
                surface = Color(0xFFF9F5D7),
                surfaceContainer = Color(0xFFEBDBB2),
                surfaceContainerHigh = Color(0xFFD5C4A1),
                onSurface = Color(0xFF3C3836),
                onPrimary = Color(0xFFFFFFFF)
            )
        )
    ),
    CustomThemePreset(
        id = "nord",
        name = "Nord",
        variants = listOf(
            CustomThemeVariant(
                id = "nord_polar",
                name = "Polar Night",
                primary = Color(0xFF88C0D0),
                background = Color(0xFF2E3440),
                surface = Color(0xFF242933),
                surfaceContainer = Color(0xFF3B4252),
                surfaceContainerHigh = Color(0xFF434C5E),
                onSurface = Color(0xFFECEFF4),
                onPrimary = Color(0xFF2E3440)
            ),
            CustomThemeVariant(
                id = "nord_snow_storm",
                name = "Snow Storm",
                primary = Color(0xFF5E81AC),
                background = Color(0xFFECEFF4),
                surface = Color(0xFFE5E9F0),
                surfaceContainer = Color(0xFFD8DEE9),
                surfaceContainerHigh = Color(0xFFC2D0E0),
                onSurface = Color(0xFF2E3440),
                onPrimary = Color(0xFFFFFFFF)
            )
        )
    ),
    CustomThemePreset(
        id = "dracula",
        name = "Dracula",
        variants = listOf(
            CustomThemeVariant(
                id = "dracula_classic",
                name = "Classic",
                primary = Color(0xFFFF79C6),
                background = Color(0xFF282A36),
                surface = Color(0xFF21222C),
                surfaceContainer = Color(0xFF44475A),
                surfaceContainerHigh = Color(0xFF6272A4),
                onSurface = Color(0xFFF8F8F2),
                onPrimary = Color(0xFF282A36)
            ),
            CustomThemeVariant(
                id = "dracula_vampire",
                name = "Vampire",
                primary = Color(0xFFBD93F9),
                background = Color(0xFF181A24),
                surface = Color(0xFF12131A),
                surfaceContainer = Color(0xFF252838),
                surfaceContainerHigh = Color(0xFF373B52),
                onSurface = Color(0xFFF8F8F2),
                onPrimary = Color(0xFF12131A)
            )
        )
    ),
    CustomThemePreset(
        id = "monokai",
        name = "Monokai Pro",
        variants = listOf(
            CustomThemeVariant(
                id = "monokai_pro",
                name = "Pro Classic",
                primary = Color(0xFFA9DC76),
                background = Color(0xFF2D2A2E),
                surface = Color(0xFF221F22),
                surfaceContainer = Color(0xFF403E41),
                surfaceContainerHigh = Color(0xFF5B595C),
                onSurface = Color(0xFFFCFCFA),
                onPrimary = Color(0xFF2D2A2E)
            ),
            CustomThemeVariant(
                id = "monokai_machine",
                name = "Machine",
                primary = Color(0xFFFF6188),
                background = Color(0xFF273136),
                surface = Color(0xFF1D2529),
                surfaceContainer = Color(0xFF3A4449),
                surfaceContainerHigh = Color(0xFF525D62),
                onSurface = Color(0xFFF2F2F0),
                onPrimary = Color(0xFF1D2529)
            ),
            CustomThemeVariant(
                id = "monokai_octagon",
                name = "Octagon",
                primary = Color(0xFFFFD866),
                background = Color(0xFF282A3A),
                surface = Color(0xFF1E1F2B),
                surfaceContainer = Color(0xFF3A3D52),
                surfaceContainerHigh = Color(0xFF51556E),
                onSurface = Color(0xFFF5F5F7),
                onPrimary = Color(0xFF1E1F2B)
            )
        )
    ),
    CustomThemePreset(
        id = "spotify",
        name = "Spotify Obsidian",
        variants = listOf(
            CustomThemeVariant(
                id = "spotify_dark",
                name = "Pitch Black",
                primary = Color(0xFF1DB954),
                background = Color(0xFF121212),
                surface = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF1E1E1E),
                surfaceContainerHigh = Color(0xFF2A2A2A),
                onSurface = Color(0xFFFFFFFF),
                onPrimary = Color(0xFF000000)
            ),
            CustomThemeVariant(
                id = "spotify_emerald",
                name = "Emerald Deep",
                primary = Color(0xFF1ED760),
                background = Color(0xFF05140C),
                surface = Color(0xFF030D08),
                surfaceContainer = Color(0xFF0C2417),
                surfaceContainerHigh = Color(0xFF163B26),
                onSurface = Color(0xFFE8F8EE),
                onPrimary = Color(0xFF05140C)
            )
        )
    ),
    CustomThemePreset(
        id = "one_dark",
        name = "One Dark Pro",
        variants = listOf(
            CustomThemeVariant(
                id = "one_dark_classic",
                name = "Classic",
                primary = Color(0xFF61AFEF),
                background = Color(0xFF282C34),
                surface = Color(0xFF21252B),
                surfaceContainer = Color(0xFF353B45),
                surfaceContainerHigh = Color(0xFF4B5263),
                onSurface = Color(0xFFABB2BF),
                onPrimary = Color(0xFF21252B)
            ),
            CustomThemeVariant(
                id = "one_dark_deep",
                name = "Deep Amber",
                primary = Color(0xFFE5C07B),
                background = Color(0xFF1E2227),
                surface = Color(0xFF181A1F),
                surfaceContainer = Color(0xFF282C34),
                surfaceContainerHigh = Color(0xFF3E4451),
                onSurface = Color(0xFFABB2BF),
                onPrimary = Color(0xFF181A1F)
            )
        )
    ),
    CustomThemePreset(
        id = "cyberpunk",
        name = "Cyberpunk 2077",
        variants = listOf(
            CustomThemeVariant(
                id = "cyberpunk_yellow",
                name = "Neon Yellow",
                primary = Color(0xFFFCEE0A),
                background = Color(0xFF121212),
                surface = Color(0xFF080808),
                surfaceContainer = Color(0xFF1F1F1F),
                surfaceContainerHigh = Color(0xFF2E2E2E),
                onSurface = Color(0xFFFDFDFD),
                onPrimary = Color(0xFF000000)
            ),
            CustomThemeVariant(
                id = "cyberpunk_magenta",
                name = "Night City Magenta",
                primary = Color(0xFFFF007F),
                background = Color(0xFF0D0221),
                surface = Color(0xFF080114),
                surfaceContainer = Color(0xFF1B0C3B),
                surfaceContainerHigh = Color(0xFF2D1460),
                onSurface = Color(0xFFE0F7FA),
                onPrimary = Color(0xFF0D0221)
            )
        )
    )
)

fun getVariantById(variantId: String): CustomThemeVariant {
    for (preset in PRESET_CUSTOM_THEMES) {
        preset.variants.find { it.id == variantId }?.let { return it }
    }

    for (preset in PRESET_CUSTOM_THEMES) {
        preset.variants.find { variantId.startsWith(it.id.substringBefore("_")) }?.let { return it }
    }

    return PRESET_CUSTOM_THEMES.first().variants.first()
}
