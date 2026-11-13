package com.example.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.common.theme.ExpressiveMotion
import com.example.ui.common.theme.LocalSpacing
import com.example.ui.navigation.components.TabItem
import com.example.ui.navigation.components.getTabIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.ui.sidebar.core.CollectionAction
import com.example.ui.sidebar.core.CollectionUiState
import com.example.ui.home.core.HomeAction
import com.example.ui.home.core.HomeChildScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.core.HomeUiState
import com.example.ui.library.core.LibraryAction
import com.example.ui.library.core.LibraryUiState
import com.example.ui.library.core.SongAction
import com.example.ui.library.album.AlbumScreen
import com.example.ui.library.artist.ArtistScreen
import com.example.ui.library.components.CollectionDetailScreen
import com.example.ui.library.components.albumDetailConfig
import com.example.ui.library.components.artistDetailConfig
import com.example.ui.library.folder.FolderScreen
import com.example.ui.library.song.SongScreen
import com.example.ui.player.core.PlayerUiState
import com.example.core.model.Song

@Composable
private fun RowScope.NavPillItem(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            icon()
            label()
        }
    }
}

@Composable
fun MainBottomNavigationBar(
    enabledTabs: List<TabItem>,
    selectedTab: TabItem,
    pagerState: PagerState? = null,
    expansionProgress: Float = 0f,
    expansionProgressProvider: (() -> Float)? = null,
    isEqualizerOpen: Boolean,
    showIconBackground: Boolean = true,
    onTabSelected: (TabItem) -> Unit
) {
    val isHiddenByExpansion by remember(expansionProgressProvider, expansionProgress) {
        androidx.compose.runtime.derivedStateOf {
            val progress = expansionProgressProvider?.invoke() ?: expansionProgress
            progress >= 0.5f
        }
    }

    if (isEqualizerOpen || isHiddenByExpansion) return

    val spacing = LocalSpacing.current
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarTotalHeight = spacing.navigationBarHeight + navBarBottomInset + 16.dp
    val navBarTotalHeightPx = with(LocalDensity.current) { navBarTotalHeight.toPx() }
    val pillShape = remember { RoundedCornerShape(28.dp) }

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = navBarBottomInset + 8.dp)
            .graphicsLayer {
                val progress = expansionProgressProvider?.invoke() ?: expansionProgress
                translationY = progress * navBarTotalHeightPx
                alpha = (1f - progress * 2.0f).coerceIn(0f, 1f)
            }
            .clip(pillShape)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = pillShape
            ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        val tabCount = enabledTabs.size.coerceAtLeast(1)
        val maxIndex = (tabCount - 1).coerceAtLeast(0)
        val selectedIndex = enabledTabs.indexOf(selectedTab).coerceIn(0, maxIndex)

        var previousSelectedIndex by remember { mutableIntStateOf(selectedIndex) }
        var jumpStartPos by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
        var isJumpAnimating by remember { mutableStateOf(false) }
        val pillAnimatable = remember { Animatable(selectedIndex.toFloat()) }

        if (selectedIndex != previousSelectedIndex) {
            val diff = kotlin.math.abs(selectedIndex - previousSelectedIndex)
            if (diff > 1 && pagerState?.isScrollInProgress != true) {
                jumpStartPos = previousSelectedIndex.toFloat()
                isJumpAnimating = true
            } else {
                isJumpAnimating = false
            }
            previousSelectedIndex = selectedIndex
        }

        LaunchedEffect(selectedIndex, isJumpAnimating) {
            if (isJumpAnimating) {
                pillAnimatable.snapTo(jumpStartPos)
                try {
                    pillAnimatable.animateTo(
                        targetValue = selectedIndex.toFloat(),
                        animationSpec = tween(
                            durationMillis = 220,
                            easing = FastOutSlowInEasing
                        )
                    )
                } finally {
                    isJumpAnimating = false
                }
            } else {
                pillAnimatable.snapTo(selectedIndex.toFloat())
            }
        }

        val activeTabIndex by remember(pagerState, selectedIndex, maxIndex) {
            derivedStateOf {
                if (pagerState == null) {
                    selectedIndex
                } else if (selectedIndex != pagerState.settledPage) {
                    selectedIndex
                } else {
                    (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        .roundToInt()
                        .coerceIn(0, maxIndex)
                }
            }
        }
        val activeTab = enabledTabs.getOrNull(activeTabIndex) ?: selectedTab

        var parentWidthPx by remember { mutableFloatStateOf(0f) }
        val activePillShape = remember { RoundedCornerShape(20.dp) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .onGloballyPositioned { parentWidthPx = it.size.width.toFloat() }
        ) {
            val itemWidthPx = if (tabCount > 0) parentWidthPx / tabCount else 0f

            if (showIconBackground) {
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .offset {
                                val pillPos = if (pagerState != null && (!isJumpAnimating || pagerState.isScrollInProgress)) {
                                    (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                        .coerceIn(0f, maxIndex.toFloat())
                                } else {
                                    pillAnimatable.value
                                }
                                IntOffset((itemWidthPx * pillPos).roundToInt(), 0)
                            }
                            .fillMaxWidth(1f / tabCount)
                            .padding(horizontal = 8.dp)
                            .fillMaxHeight()
                            .padding(vertical = 2.dp)
                            .clip(activePillShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                enabledTabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = ExpressiveMotion.SnappySpring,
                        label = "tab_icon_scale"
                    )

                    NavPillItem(
                        onClick = { onTabSelected(tab) },
                        icon = {
                            Icon(
                                imageVector = getTabIcon(tab),
                                contentDescription = tab.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainTabContent(
    pagerState: PagerState,
    enabledTabs: List<TabItem>,
    selectedTab: TabItem = enabledTabs.firstOrNull() ?: TabItem.HOME,
    hasDetailOpen: Boolean,
    isEqualizerOpen: Boolean,
    innerPadding: PaddingValues,
    homeUiState: HomeUiState,
    onHomeAction: (HomeAction) -> Unit,
    libraryUiState: LibraryUiState,
    onLibraryAction: (LibraryAction) -> Unit,
    collectionUiState: CollectionUiState,
    onCollectionAction: (CollectionAction) -> Unit,
    playerUiState: PlayerUiState,
    onSongAction: (SongAction, Song) -> Unit,
    onSongClick: (Song, List<Song>?) -> Unit,
    onArtistSelect: (String) -> Unit,
    onAlbumSelect: (String) -> Unit,
    onMenuClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onBackFromDetail: () -> Unit,
    artistListState: LazyListState,
    artistGridState: LazyGridState,
    albumListState: LazyListState,
    albumGridState: LazyGridState,
    activeHomeChildScreen: HomeChildScreen = HomeChildScreen.NONE,
    onHomeChildScreenChange: (HomeChildScreen) -> Unit = {}
) {
    val favoriteSongIds = remember(collectionUiState.favoriteSongs) {
        collectionUiState.favoriteSongs.map { it.id }.toSet()
    }

    val currentPlayingSongId = playerUiState.playbackInfo.currentSong?.id
    val isPlaying = playerUiState.playbackInfo.isPlaying

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (enabledTabs.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !hasDetailOpen &&
                    !isEqualizerOpen &&
                    activeHomeChildScreen == HomeChildScreen.NONE,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = 0.22f,
                    snapAnimationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 700f
                    )
                ),
                key = { page -> enabledTabs.getOrNull(page)?.id ?: page }
            ) { page ->

                val currentTab = enabledTabs.getOrNull(page)

                when (currentTab) {
                    TabItem.HOME -> {
                        val adjustedHomeUiState = homeUiState.copy(isLoading = libraryUiState.isLoading)
                        HomeScreen(
                            uiState = adjustedHomeUiState,
                            currentPlayingSongId = currentPlayingSongId,
                            isPlaying = isPlaying,
                            onArtistClick = onArtistSelect,
                            onAlbumClick = onAlbumSelect,
                            onMenuClick = onMenuClick,
                            onAction = onHomeAction,
                            onSongAction = onSongAction,
                            activeChildScreen = activeHomeChildScreen,
                            onChildScreenChange = onHomeChildScreenChange
                        )
                    }

                    TabItem.SONG -> {
                        SongScreen(
                            uiState = libraryUiState,
                            currentPlayingSongId = currentPlayingSongId,
                            isPlaying = isPlaying,
                            onSongClick = onSongClick,
                            onSongAction = onSongAction,
                            onMenuClick = onMenuClick,
                            searchQuery = libraryUiState.searchQuery,
                            onSearchQueryChange = { onLibraryAction(LibraryAction.Search(it)) },
                            onSearchAction = { onLibraryAction(LibraryAction.SearchImmediate(it)) },
                            sortCriteria = libraryUiState.songSortCriteria,
                            onSortCriteriaChange = { onLibraryAction(LibraryAction.UpdateSongSort(it, libraryUiState.songIsAscending)) },
                            isAscending = libraryUiState.songIsAscending,
                            onAscendingChange = { onLibraryAction(LibraryAction.UpdateSongSort(libraryUiState.songSortCriteria, it)) },
                            favoriteSongIds = favoriteSongIds,
                            onToggleFavorite = { song -> onCollectionAction(CollectionAction.ToggleFavorite(song.id)) },
                            onToggleShowCodec = { onLibraryAction(LibraryAction.SetSongShowCodec(it)) },
                            onToggleShowFavoritesToggle = { onLibraryAction(LibraryAction.SetSongShowFavoritesToggle(it)) },
                            onToggleShowFileName = { onLibraryAction(LibraryAction.SetSongShowFileName(it)) },
                            onToggleShowSize = { onLibraryAction(LibraryAction.SetSongShowSize(it)) }
                        )
                    }

                    TabItem.FOLDER -> {
                        FolderScreen(
                            uiState = libraryUiState,
                            currentPlayingSongId = currentPlayingSongId,
                            onSongClick = onSongClick,
                            onMenuClick = onMenuClick,
                            searchQuery = libraryUiState.searchQuery,
                            onSearchQueryChange = { onLibraryAction(LibraryAction.Search(it)) },
                            onSearchAction = { onLibraryAction(LibraryAction.SearchImmediate(it)) },
                            rootFolderDisplay = libraryUiState.rootFolderDisplay,
                            currentRelativePath = libraryUiState.currentFolderRelativePath,
                            onRelativePathChange = { onLibraryAction(LibraryAction.SelectFolder(it)) },
                            onCurrentRelativePathChange = { onLibraryAction(LibraryAction.SelectFolder(it)) },
                            folderStyle = libraryUiState.folderStyle,
                            onFolderStyleChange = { onLibraryAction(LibraryAction.ChangeFolderStyle(it)) },
                            showFileName = libraryUiState.showFolderFileName,
                            onShowFileNameChange = { onLibraryAction(LibraryAction.ChangeFolderShowFileName(it)) },
                            onPlayNextBatch = { songs -> songs.forEach { onSongAction(SongAction.PLAY_NEXT, it) } },
                            onAddToQueueBatch = { songs -> songs.forEach { onSongAction(SongAction.ADD_TO_QUEUE, it) } },
                            onSongAction = onSongAction,
                            favoriteSongIds = favoriteSongIds,
                            onToggleFavorite = { song -> onCollectionAction(CollectionAction.ToggleFavorite(song.id)) }
                        )
                    }

                    TabItem.ARTIST -> {
                        ArtistScreen(
                            uiState = libraryUiState,
                            onMenuClick = onMenuClick,
                            searchQuery = libraryUiState.searchQuery,
                            onSearchQueryChange = { onLibraryAction(LibraryAction.Search(it)) },
                            onSearchAction = { onLibraryAction(LibraryAction.SearchImmediate(it)) },
                            selectedArtistName = libraryUiState.selectedArtistName,
                            onSelectedArtistChange = { onLibraryAction(LibraryAction.SelectArtist(it)) },
                            sortCriteria = libraryUiState.artistSortCriteria,
                            onSortCriteriaChange = { onLibraryAction(LibraryAction.UpdateArtistSort(it, libraryUiState.artistIsAscending)) },
                            isAscending = libraryUiState.artistIsAscending,
                            onAscendingChange = { onLibraryAction(LibraryAction.UpdateArtistSort(libraryUiState.artistSortCriteria, it)) },
                            gridMode = libraryUiState.artistGridMode,
                            onGridModeChange = { onLibraryAction(LibraryAction.UpdateArtistGridMode(it)) },
                            listState = artistListState,
                            gridState = artistGridState
                        )
                    }

                    TabItem.ALBUM -> {
                        AlbumScreen(
                            uiState = libraryUiState,
                            onMenuClick = onMenuClick,
                            searchQuery = libraryUiState.searchQuery,
                            onSearchQueryChange = { onLibraryAction(LibraryAction.Search(it)) },
                            onSearchAction = { onLibraryAction(LibraryAction.SearchImmediate(it)) },
                            selectedAlbumName = libraryUiState.selectedAlbumName,
                            onSelectedAlbumChange = { onLibraryAction(LibraryAction.SelectAlbum(it)) },
                            sortCriteria = libraryUiState.albumSortCriteria,
                            onSortCriteriaChange = { onLibraryAction(LibraryAction.UpdateAlbumSort(it, libraryUiState.albumIsAscending)) },
                            isAscending = libraryUiState.albumIsAscending,
                            onAscendingChange = { onLibraryAction(LibraryAction.UpdateAlbumSort(libraryUiState.albumSortCriteria, it)) },
                            gridMode = libraryUiState.albumGridMode,
                            onGridModeChange = { onLibraryAction(LibraryAction.UpdateAlbumGridMode(it)) },
                            listState = albumListState,
                            gridState = albumGridState
                        )
                    }

                    null -> {}
                }
            }
        }

        var displayedArtistName by remember { mutableStateOf<String?>(null) }
        if (libraryUiState.selectedArtistName != null) {
            displayedArtistName = libraryUiState.selectedArtistName
        }

        AnimatedVisibility(
            visible = libraryUiState.selectedArtistName != null,
            enter = com.example.ui.common.theme.ExpressiveMotion.ScreenEnterTransition,
            exit = com.example.ui.common.theme.ExpressiveMotion.ScreenExitTransition
        ) {
            displayedArtistName?.let { artistName ->
                CollectionDetailScreen(
                    title = artistName,
                    config = artistDetailConfig(artistName),
                    songs = libraryUiState.songs,
                    currentPlayingSongId = currentPlayingSongId,
                    onSongClick = onSongClick,
                    onBackClick = onBackFromDetail,
                    onSongAction = onSongAction,
                    favoriteSongIds = favoriteSongIds,
                    onToggleFavorite = { song -> onCollectionAction(CollectionAction.ToggleFavorite(song.id)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }

        var displayedAlbumName by remember { mutableStateOf<String?>(null) }
        if (libraryUiState.selectedAlbumName != null) {
            displayedAlbumName = libraryUiState.selectedAlbumName
        }

        AnimatedVisibility(
            visible = libraryUiState.selectedAlbumName != null,
            enter = com.example.ui.common.theme.ExpressiveMotion.ScreenEnterTransition,
            exit = com.example.ui.common.theme.ExpressiveMotion.ScreenExitTransition
        ) {
            displayedAlbumName?.let { albumName ->
                CollectionDetailScreen(
                    title = albumName,
                    config = albumDetailConfig(albumName),
                    songs = libraryUiState.songs,
                    currentPlayingSongId = currentPlayingSongId,
                    onSongClick = onSongClick,
                    onBackClick = onBackFromDetail,
                    onSongAction = onSongAction,
                    favoriteSongIds = favoriteSongIds,
                    onToggleFavorite = { song -> onCollectionAction(CollectionAction.ToggleFavorite(song.id)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
    }
}
