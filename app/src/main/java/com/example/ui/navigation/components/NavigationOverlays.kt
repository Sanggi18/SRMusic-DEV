package com.example.ui.navigation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.core.data.database.playlist.PlaylistEntity
import com.example.core.model.Song
import com.example.ui.settings.core.SettingsAction
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.core.SettingsUiState
import com.example.ui.settings.SettingsDestination
import com.example.ui.sidebar.SidebarDrawer

private val DrawerSheetShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)

@Composable
fun SettingsOverlay(
    isOpen: Boolean,
    onClose: () -> Unit,
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onRefreshLibrary: () -> Unit,
    initialDestination: SettingsDestination = SettingsDestination.Main
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = com.example.ui.common.theme.ExpressiveMotion.ScreenEnterTransition,
        exit = com.example.ui.common.theme.ExpressiveMotion.ScreenExitTransition
    ) {
        SettingsScreen(
            uiState = uiState,
            onAction = onAction,
            onClose = onClose,
            onNavigateToEqualizer = onNavigateToEqualizer,
            onRefreshLibrary = onRefreshLibrary,
            initialDestination = initialDestination
        )
    }
}

@Composable
fun DrawerHost(
    drawerState: DrawerState,
    gesturesEnabled: Boolean,
    onScanMedia: () -> Unit,
    onOpenSettings: () -> Unit,
    totalSongs: Int,
    totalSizeBytes: Long,
    onOpenPlaylists: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    isPlaying: Boolean = false,
    hasCurrentSong: Boolean = false,
    isBitPerfectEnabled: Boolean = false,
    onDonateClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        scrimColor = DrawerDefaults.scrimColor,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = DrawerSheetShape,
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight()
                    .graphicsLayer()
            ) {
                SidebarDrawer(
                    onScanMedia = onScanMedia,
                    onOpenSettings = onOpenSettings,
                    totalSongs = totalSongs,
                    totalSizeBytes = totalSizeBytes,
                    onOpenPlaylists = onOpenPlaylists,
                    onOpenFavorites = onOpenFavorites,
                    onOpenHistory = onOpenHistory,
                    isPlaying = isPlaying,
                    hasCurrentSong = hasCurrentSong,
                    isBitPerfectEnabled = isBitPerfectEnabled,
                    onDonateClick = onDonateClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) {
        content()
    }
}

@Composable
fun AddToPlaylistOverlay(
    song: Song?,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onAddSongToPlaylist: (Long, Long) -> Unit,
    onCreatePlaylist: (String, ((Long) -> Unit)?) -> Unit
) {
    if (song != null) {
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = onDismiss,
            onAddToPlaylist = { playlistId, songId ->
                onAddSongToPlaylist(playlistId, songId)
                onDismiss()
            },
            onCreateAndAdd = { playlistName, songId ->
                onCreatePlaylist(playlistName) { newPlaylistId ->
                    onAddSongToPlaylist(newPlaylistId, songId)
                }
                onDismiss()
            }
        )
    }
}
