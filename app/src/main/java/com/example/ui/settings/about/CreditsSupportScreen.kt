package com.example.ui.settings.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.settings.components.OpenLinkConfirmDialog

private data class OpenSourceLibrary(
    val name: String,
    val description: String,
    val license: String,
    val url: String,
    val icon: ImageVector
)

@Composable
fun CreditsSupportScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    var pendingUrl by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        pendingUrl = url
    }

    pendingUrl?.let { url ->
        OpenLinkConfirmDialog(
            url = url,
            onConfirm = {
                pendingUrl = null

                try {
                    uriHandler.openUri(url)
                } catch (_: Exception) {}
            },
            onDismiss = {
                pendingUrl = null
            }
        )
    }

    val coreEngines = listOf(
        OpenSourceLibrary(
            name = "Android Jetpack Compose",
            description = "Modern declarative UI toolkit for Android",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/compose",
            icon = Icons.Rounded.Code
        ),
        OpenSourceLibrary(
            name = "AndroidX Media3 & ExoPlayer",
            description = "High-performance low-latency audio playback engine",
            license = "Apache 2.0",
            url = "https://github.com/androidx/media",
            icon = Icons.Rounded.Code
        ),
        OpenSourceLibrary(
            name = "TagLib Audio Metadata",
            description = "Native metadata engine for reading ID3, FLAC & Vorbis tags",
            license = "LGPL / MPL",
            url = "https://github.com/kyant0/taglib",
            icon = Icons.Rounded.Terminal
        ),
        OpenSourceLibrary(
            name = "Kotlin Coroutines & Flow",
            description = "Asynchronous stream reactive & concurrency primitives",
            license = "Apache 2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
            icon = Icons.Rounded.Code
        ),
        OpenSourceLibrary(
            name = "AndroidX Room Database",
            description = "SQLite abstraction layer for offline local storage",
            license = "Apache 2.0",
            url = "https://developer.android.com/training/data-storage/room",
            icon = Icons.Rounded.Code
        ),
        OpenSourceLibrary(
            name = "Decent Player",
            description = "High-resolution audio player architecture & driver wrapper inspirations",
            license = "GPL-3.0",
            url = "https://github.com/Ma145/decent-player",
            icon = Icons.Rounded.Code
        ),
        OpenSourceLibrary(
            name = "DSD over PCM (DoP)",
            description = "Open standard for bit-perfect DSD encapsulation in PCM frames",
            license = "Open Standard",
            url = "https://dsd-guide.com/dop-open-standard",
            icon = Icons.Rounded.Terminal
        ),
        OpenSourceLibrary(
            name = "Audiophile Music Player",
            description = "Bit-perfect USB DAC bypass architecture and DSD/DoP pipeline reference",
            license = "Apache 2.0",
            url = "https://github.com/androidexpert35/audiophile-music-player",
            icon = Icons.Rounded.Code
        )
    )

    val designInspirations = listOf(
        OpenSourceLibrary(
            name = "AyraMusic",
            description = "Gesture architecture for morphing mini player & now playing",
            license = "GPL-3.0",
            url = "https://github.com/Brosssh/AyraMusic",
            icon = Icons.Rounded.Palette
        ),
        OpenSourceLibrary(
            name = "Catppuccin",
            description = "Soothing pastel color palette for visual comfort",
            license = "MIT",
            url = "https://github.com/catppuccin/catppuccin",
            icon = Icons.Rounded.Palette
        ),
        OpenSourceLibrary(
            name = "Dracula Theme",
            description = "Modern high-contrast dark theme scheme",
            license = "MIT",
            url = "https://github.com/dracula/dracula-theme",
            icon = Icons.Rounded.Palette
        ),
        OpenSourceLibrary(
            name = "Nord Theme",
            description = "Clean and balanced arctic color palette",
            license = "MIT",
            url = "https://github.com/nordtheme/nord",
            icon = Icons.Rounded.Palette
        ),
        OpenSourceLibrary(
            name = "Gruvbox",
            description = "Vintage warm terminal retro palette",
            license = "MIT",
            url = "https://github.com/morhetz/gruvbox",
            icon = Icons.Rounded.Palette
        ),
        OpenSourceLibrary(
            name = "Tokyo Night",
            description = "Elegant Tokyo night neon theme",
            license = "MIT",
            url = "https://github.com/enkia/tokyo-night-vscode-theme",
            icon = Icons.Rounded.Palette
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        CreditsGroupSection(
            title = "CORE FRAMEWORK & AUDIO ENGINE",
            libraries = coreEngines,
            onItemClick = { openUrl(it.url) }
        )

        CreditsGroupSection(
            title = "DESIGN & THEME PALETTES",
            libraries = designInspirations,
            onItemClick = { openUrl(it.url) }
        )

        Text(
            text = "Crafted with dedication for music and open-source communities",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 12.dp)
        )
    }
}

@Composable
private fun CreditsGroupSection(
    title: String,
    libraries: List<OpenSourceLibrary>,
    onItemClick: (OpenSourceLibrary) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            Column {
                libraries.forEachIndexed { index, lib ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(lib) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = lib.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = lib.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(2.dp))

                            Text(
                                text = lib.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = lib.license,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (index < libraries.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
