package com.example.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Usb
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.audio.AudioOutputType
import com.example.core.util.formatBytes
import com.example.ui.common.theme.springPress
import com.example.ui.player.components.rememberAudioOutputInfo

private val HeaderCardShape = RoundedCornerShape(22.dp)
private val FooterCardShape = RoundedCornerShape(20.dp)
private val MenuItemCardShape = RoundedCornerShape(16.dp)
private val MenuItemIconShape = RoundedCornerShape(12.dp)

@Composable
fun SidebarDrawer(
    onScanMedia: () -> Unit,
    onOpenSettings: () -> Unit,
    totalSongs: Int,
    totalSizeBytes: Long,
    modifier: Modifier = Modifier,
    onOpenPlaylists: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onDonateClick: () -> Unit = {},
    isPlaying: Boolean = false,
    hasCurrentSong: Boolean = false,
    isBitPerfectEnabled: Boolean = false
) {
    val totalStorageText = remember(totalSizeBytes) { formatBytes(totalSizeBytes) }

    SidebarDrawerPanel(
        totalSongsCount = totalSongs,
        totalStorageText = totalStorageText,
        onScanMedia = onScanMedia,
        onPlaylistClick = onOpenPlaylists,
        onFavoritesClick = onOpenFavorites,
        onHistoryClick = onOpenHistory,
        onSettingsClick = onOpenSettings,
        onDonateClick = onDonateClick,
        isPlaying = isPlaying,
        hasCurrentSong = hasCurrentSong,
        isBitPerfectEnabled = isBitPerfectEnabled,
        modifier = modifier
    )
}

@Composable
fun SidebarDrawerPanel(
    totalSongsCount: Int,
    totalStorageText: String,
    onScanMedia: () -> Unit,
    onPlaylistClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDonateClick: () -> Unit,
    isPlaying: Boolean = false,
    hasCurrentSong: Boolean = false,
    isBitPerfectEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showTerminalSheet by remember { mutableStateOf(false) }

    if (showTerminalSheet) {
        AudioTerminalSheet(
            onDismiss = { showTerminalSheet = false }
        )
    }

    val isCompact = LocalConfiguration.current.screenHeightDp < 580
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(if (isCompact) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = if (isCompact) Arrangement.spacedBy(16.dp) else Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, HeaderCardShape),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, HeaderCardShape),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.ui.common.components.SRMusicGlyphIcon(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f).padding(end = 16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "SRMusic",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val formattedSongCount = remember(totalSongsCount) {
                                java.text.DecimalFormat("#,###", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(totalSongsCount)
                            }
                            val songsAnnotated = remember(formattedSongCount) {
                                androidx.compose.ui.text.buildAnnotatedString {
                                    withStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    ) {
                                        append(formattedSongCount)
                                    }
                                    withStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            fontFamily = FontFamily.Default,
                                            fontWeight = FontWeight.Normal
                                        )
                                    ) {
                                        append(" Songs")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = songsAnnotated,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (totalStorageText.isNotBlank()) {
                                val storageParts = remember(totalStorageText) {
                                    totalStorageText.trim().split(" ")
                                }
                                val storageAnnotated = remember(storageParts) {
                                    androidx.compose.ui.text.buildAnnotatedString {
                                        if (storageParts.size == 2) {
                                            withStyle(
                                                androidx.compose.ui.text.SpanStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            ) {
                                                append(storageParts[0])
                                            }
                                            withStyle(
                                                androidx.compose.ui.text.SpanStyle(
                                                    fontFamily = FontFamily.Default,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            ) {
                                                append(" ${storageParts[1]}")
                                            }
                                        } else {
                                            append(totalStorageText)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = storageAnnotated,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }

                Spacer(modifier = Modifier.height(2.dp))

                SidebarMenuItemCard(
                    icon = Icons.Rounded.Refresh,
                    title = "Scan Library",
                    onClick = onScanMedia
                )

                SidebarMenuItemCard(
                    icon = Icons.Rounded.QueueMusic,
                    title = "Playlist",
                    onClick = onPlaylistClick
                )

                SidebarMenuItemCard(
                    icon = Icons.Rounded.Favorite,
                    title = "Favorites",
                    onClick = onFavoritesClick
                )

                SidebarMenuItemCard(
                    icon = Icons.Rounded.History,
                    title = "History",
                    onClick = onHistoryClick
                )

                SidebarMenuItemCard(
                    icon = Icons.Rounded.Terminal,
                    title = "Audio Terminal",
                    onClick = { showTerminalSheet = true }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val audioOutputInfo = rememberAudioOutputInfo()

                val outputIcon = when (audioOutputInfo.type) {
                    AudioOutputType.USB -> Icons.Rounded.Usb
                    AudioOutputType.BLUETOOTH -> Icons.Rounded.Bluetooth
                    AudioOutputType.WIRED -> Icons.Rounded.Headphones
                    AudioOutputType.SPEAKER -> Icons.Rounded.Speaker
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, FooterCardShape)
                        .padding(horizontal = 16.dp)
                ) {
                        Icon(
                            imageVector = outputIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        val isDac = audioOutputInfo.type == AudioOutputType.USB
                        val isBitPerfectActive = isDac && isBitPerfectEnabled && isPlaying

                        Column(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (isDac) "USB Audio" else if (audioOutputInfo.type == AudioOutputType.BLUETOOTH && !audioOutputInfo.bluetoothCodec.isNullOrBlank()) "Bluetooth (${audioOutputInfo.bluetoothCodec})" else "Audio Output",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )

                                val dotColor = when {
                                    isBitPerfectActive -> Color(0xFF2196F3)
                                    isPlaying -> Color(0xFF4CAF50)
                                    hasCurrentSong -> Color(0xFF9E9E9E)
                                    else -> Color(0xFFF44336)
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }

                            val outputDisplayName = if (isDac) {
                                audioOutputInfo.displayName.removePrefix("USB Audio : ").removePrefix("USB Audio: ").trim()
                            } else {
                                audioOutputInfo.displayName
                            }

                            Text(
                                text = outputDisplayName,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = onDonateClick,
                        shape = FooterCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Donate",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        onClick = onSettingsClick,
                        shape = FooterCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Text(
                    text = "© SRMusic App",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SidebarMenuItemCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .springPress(interactionSource, pressedScale = 0.98f)
            .clip(MenuItemCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MenuItemIconShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}
