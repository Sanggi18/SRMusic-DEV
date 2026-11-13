package com.example.ui.settings

import com.example.ui.settings.components.*
import com.example.ui.settings.core.*
import com.example.ui.common.components.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.common.theme.ExpressiveMotion
import com.example.ui.common.theme.springPress
import com.example.ui.settings.about.AppSettingsScreen
import com.example.ui.settings.about.ChangelogScreen
import com.example.ui.settings.about.CreditsSupportScreen
import com.example.ui.settings.appearance.AppearanceScreen
import com.example.ui.settings.appearance.AppThemeScreen
import com.example.ui.settings.appearance.BottomTabsScreen
import com.example.ui.settings.appearance.CustomFontScreen
import com.example.ui.settings.appearance.CustomThemesScreen
import com.example.ui.settings.appearance.NowPlayingStyleScreen
import com.example.ui.settings.audio.AudioPlaybackScreen
import com.example.ui.settings.audio.DacConfigScreen
import com.example.ui.settings.library.LibrarySettingsScreen
import com.example.ui.settings.library.MusicFoldersScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onClose: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onRefreshLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    initialDestination: SettingsDestination = SettingsDestination.Main
) {
    var navigationStack by remember { mutableStateOf(listOf(initialDestination)) }

    var isBackNavigation by remember { mutableStateOf(false) }

    val currentDestination = navigationStack.last()

    val navigateTo: (SettingsDestination) -> Unit = { dest ->
        isBackNavigation = false

        navigationStack = navigationStack + dest
    }

    val navigateBack: () -> Unit = {
        if (navigationStack.size > 1) {
            isBackNavigation = true

            navigationStack = navigationStack.dropLast(1)
        } else {
            onClose()
        }
    }

    BackHandler(enabled = true) {
        navigateBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDestination.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    val backInteractionSource = remember { MutableInteractionSource() }

                    IconButton(
                        onClick = navigateBack,
                        interactionSource = backInteractionSource,
                        modifier = Modifier
                            .springPress(backInteractionSource, pressedScale = 0.88f)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    ExpressiveMotion.hierarchicalTransition(isForward = !isBackNavigation)
                },
                label = "settings_destination_anim"
            ) { destination ->
                when (destination) {
                is SettingsDestination.Main -> {
                    MainSettingsScreen(
                        onNavigate = navigateTo
                    )
                }

                is SettingsDestination.Appearance -> {
                    AppearanceScreen(
                        currentThemeMode = uiState.themeMode.toUiThemeMode(),
                        isDynamicColor = uiState.isDynamicColor,
                        enabledTabs = uiState.enabledTabIds.toTabItems(),
                        onNavigate = navigateTo,
                        currentFontName = uiState.customFontName,
                        dpiScalePercent = uiState.dpiScalePercent,
                        onDpiScaleChange = { onAction(SettingsAction.SetDpiScalePercent(it)) }
                    )
                }

                is SettingsDestination.CustomFont -> {
                    CustomFontScreen(
                        currentFontPath = uiState.customFontPath,
                        currentFontName = uiState.customFontName,
                        onFontSelected = { path, name ->
                            onAction(SettingsAction.SetCustomFont(path, name))
                        }
                    )
                }

                is SettingsDestination.AppTheme -> {
                    AppThemeScreen(
                        currentThemeMode = uiState.themeMode.toUiThemeMode(),
                        onThemeModeChange = { onAction(SettingsAction.SetThemeMode(it.toAppThemeMode())) },
                        isDynamicColor = uiState.isDynamicColor,
                        onDynamicColorChange = { onAction(SettingsAction.SetDynamicColor(it)) },
                        isArtworkAccent = uiState.isArtworkAccent,
                        onArtworkAccentChange = { onAction(SettingsAction.SetArtworkAccent(it)) },
                        currentAccentColor = uiState.accentColorArgb.toComposeColor(),
                        onAccentColorChange = { onAction(SettingsAction.SetAccentColor(it.toArgbLong())) },
                        currentBackgroundColor = uiState.customBackgroundColorArgb?.toComposeColor(),
                        onBackgroundColorChange = { onAction(SettingsAction.SetCustomBackgroundColor(it?.toArgbLong())) },
                        currentFontColor = uiState.customFontColorArgb?.toComposeColor(),
                        onFontColorChange = { onAction(SettingsAction.SetCustomFontColor(it?.toArgbLong())) },
                        onNavigate = navigateTo,
                        currentCustomThemePresetId = uiState.customThemePresetId
                    )
                }

                is SettingsDestination.CustomThemes -> {
                    CustomThemesScreen(
                        currentThemeMode = uiState.themeMode.toUiThemeMode(),
                        currentCustomThemePresetId = uiState.customThemePresetId,
                        onCustomThemePresetChange = { onAction(SettingsAction.SetCustomThemePresetId(it)) },
                        onThemeModeChange = { onAction(SettingsAction.SetThemeMode(it.toAppThemeMode())) },
                        onBackgroundColorChange = { onAction(SettingsAction.SetCustomBackgroundColor(it?.toArgbLong())) },
                        onFontColorChange = { onAction(SettingsAction.SetCustomFontColor(it?.toArgbLong())) },
                        onAccentColorChange = { onAction(SettingsAction.SetAccentColor(it.toArgbLong())) },
                        onDynamicColorChange = { onAction(SettingsAction.SetDynamicColor(it)) }
                    )
                }

                is SettingsDestination.NowPlayingStyle -> {
                    NowPlayingStyleScreen(
                        currentProgressStyle = uiState.nowPlayingProgressStyle,
                        onProgressStyleChange = { onAction(SettingsAction.SetNowPlayingProgressStyle(it)) },
                        currentArtworkStyle = uiState.nowPlayingArtworkStyle,
                        onArtworkStyleChange = { onAction(SettingsAction.SetNowPlayingArtworkStyle(it)) },
                        currentTheme = uiState.nowPlayingTheme,
                        onThemeChange = { onAction(SettingsAction.SetNowPlayingTheme(it)) },
                        currentAlignment = uiState.trackInfoAlignment,
                        onAlignmentChange = { onAction(SettingsAction.SetTrackInfoAlignment(it)) }
                    )
                }

                is SettingsDestination.BottomTabs -> {
                    BottomTabsScreen(
                        enabledTabs = uiState.enabledTabIds.toTabItems(),
                        onEnabledTabsChange = { onAction(SettingsAction.SetEnabledTabIds(it.toTabIds())) },
                        showIconBackground = uiState.showBottomTabIconBackground,
                        onShowIconBackgroundChange = { onAction(SettingsAction.SetShowBottomTabIconBackground(it)) }
                    )
                }

                is SettingsDestination.AudioPlayback -> {
                    AudioPlaybackScreen(
                        uiState = uiState,
                        onAction = onAction,
                        onNavigateToEqualizer = onNavigateToEqualizer
                    )
                }

                is SettingsDestination.DacConfig -> {
                    DacConfigScreen(
                        uiState = uiState,
                        onAction = onAction
                    )
                }

                is SettingsDestination.Library -> {
                    LibrarySettingsScreen(
                        uiState = uiState,
                        onAction = onAction,
                        onRefreshLibrary = onRefreshLibrary,
                        onNavigate = navigateTo
                    )
                }

                is SettingsDestination.MusicFolders -> {
                    MusicFoldersScreen(
                        uiState = uiState,
                        onAction = onAction,
                        onRefreshLibrary = onRefreshLibrary
                    )
                }

                is SettingsDestination.App -> {
                    AppSettingsScreen(
                        onNavigate = navigateTo,
                        headerSurface = uiState.aboutHeaderSurface,
                        onHeaderSurfaceChange = { onAction(SettingsAction.SetAboutHeaderSurface(it)) }
                    )
                }

                is SettingsDestination.Changelog -> {
                    ChangelogScreen()
                }

                is SettingsDestination.Credits -> {
                    CreditsSupportScreen()
                }
            }
        }
    }
}
}

@Composable
fun MainSettingsScreen(
    onNavigate: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsCard {
            SettingsNavigationRow(
                title = "Appearance",
                subtitle = "Theme, dynamic color, bottom bar tabs",
                icon = Icons.Rounded.Palette,
                onClick = { onNavigate(SettingsDestination.Appearance) }
            )
        }

        SettingsCard {
            SettingsNavigationRow(
                title = "Audio & Playback",
                subtitle = "Equalizer, gapless playback, 32-bit float audio",
                icon = Icons.Rounded.Tune,
                onClick = { onNavigate(SettingsDestination.AudioPlayback) }
            )
        }

        SettingsCard {
            SettingsNavigationRow(
                title = "DAC Configuration",
                subtitle = "USB DAC volume, DSD, sample rate, buffer size",
                icon = Icons.Rounded.Usb,
                onClick = { onNavigate(SettingsDestination.DacConfig) }
            )
        }

        SettingsCard {
            SettingsNavigationRow(
                title = "Library",
                subtitle = "Scan folders, blacklists, audio duration filters, auto-scan",
                icon = Icons.Rounded.Folder,
                onClick = { onNavigate(SettingsDestination.Library) }
            )
        }

        SettingsCard {
            SettingsNavigationRow(
                title = "About",
                subtitle = "Version, What's New, open-source licenses",
                icon = Icons.Rounded.Info,
                onClick = { onNavigate(SettingsDestination.App) }
            )
        }
    }
}
