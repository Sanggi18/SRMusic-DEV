package com.example.ui.library.folder

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.example.core.filter.FolderTreeParser
import com.example.core.filter.SongFilter
import com.example.core.model.AppFolderStyle
import com.example.core.model.AppSortCriteria
import com.example.core.model.Song
import com.example.core.util.TimeUtils
import com.example.ui.common.LocalMiniPlayerVisible
import com.example.ui.common.theme.ExpressiveMotion
import com.example.ui.library.components.FastScrollBar
import com.example.ui.library.core.LibraryUiState
import com.example.ui.library.core.SongAction
import com.example.ui.library.folder.components.FolderHeroHeader
import com.example.ui.library.song.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun FolderScreen(
    uiState: LibraryUiState,
    currentPlayingSongId: Long?,
    onSongClick: (Song, List<Song>) -> Unit,
    onMenuClick: () -> Unit,
    searchQuery: String = uiState.searchQuery,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchAction: ((String) -> Unit)? = null,
    currentRelativePath: String = uiState.currentFolderRelativePath,
    onRelativePathChange: (String) -> Unit = {},
    onCurrentRelativePathChange: (String) -> Unit = onRelativePathChange,
    rootFolderDisplay: String = uiState.rootFolderDisplay,
    onRootFolderDisplayChange: (String) -> Unit = {},
    onPlayNextBatch: ((List<Song>) -> Unit)? = null,
    onAddToQueueBatch: ((List<Song>) -> Unit)? = null,
    onSongAction: ((SongAction, Song) -> Unit)? = null,
    folderStyle: AppFolderStyle = uiState.folderStyle,
    onFolderStyleChange: ((AppFolderStyle) -> Unit)? = null,
    showFileName: Boolean = uiState.showFolderFileName,
    onShowFileNameChange: ((Boolean) -> Unit)? = null,
    favoriteSongIds: Set<Long> = uiState.favoriteIds,
    onToggleFavorite: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val folderScrollStates = remember { mutableMapOf<String, LazyListState>() }

    var folderSortCriteria by remember { mutableStateOf(AppSortCriteria.TITLE) }
    var folderIsAscending by remember { mutableStateOf(true) }
    var showSortSheet by remember { mutableStateOf(false) }

    val effectiveRelativePath = currentRelativePath.trim('/')

    val updateRelativePath: (String) -> Unit = { newPath ->
        onCurrentRelativePathChange(newPath)
    }

    val canGoBack = effectiveRelativePath.isNotEmpty()

    val handleBack: () -> Unit = {
        if (canGoBack) {
            val parentPath = if (effectiveRelativePath.contains('/')) {
                effectiveRelativePath.substringBeforeLast('/')
            } else {
                ""
            }
            updateRelativePath(parentPath)
        }
    }

    if (canGoBack) {
        BackHandler(onBack = handleBack)
    }

    var isSearchFocused by remember { mutableStateOf(false) }
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = searchQuery, selection = TextRange(searchQuery.length)))
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery != textFieldValue.text) {
            if (!isSearchFocused || searchQuery.isEmpty()) {
                textFieldValue = TextFieldValue(
                    text = searchQuery,
                    selection = TextRange(searchQuery.length)
                )
            }
        }
    }

    val isSearchActive = isSearchFocused || textFieldValue.text.isNotEmpty()

    val clearSearch: () -> Unit = {
        textFieldValue = TextFieldValue("", TextRange.Zero)
        onSearchQueryChange("")
        onSearchAction?.invoke("")
        focusManager.clearFocus()
    }

    BackHandler(enabled = isSearchActive, onBack = clearSearch)

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && isSearchFocused && textFieldValue.text.isEmpty()) {
            focusManager.clearFocus()
        }
    }

    val allSongs = uiState.songs

    val headerBaseContent = remember(allSongs, effectiveRelativePath) {
        FolderTreeParser.buildBaseFolderContent(allSongs, effectiveRelativePath)
    }

    val headerFilteredContent = remember(headerBaseContent, uiState.debouncedSearchQuery) {
        FolderTreeParser.filterFolderContent(headerBaseContent, uiState.debouncedSearchQuery)
    }

    val playableSongsInCurrentFolder = remember(
        headerFilteredContent,
        headerBaseContent,
        folderSortCriteria,
        folderIsAscending,
        uiState.playCounts
    ) {
        val rawSongs = headerFilteredContent.second.ifEmpty { headerBaseContent.songsUnderCurrent }
        SongFilter.sortSongs(
            songs = rawSongs,
            sortCriteria = folderSortCriteria,
            isAscending = folderIsAscending,
            playCounts = uiState.playCounts
        )
    }

    val randomFallbackSong = remember(allSongs, effectiveRelativePath, headerBaseContent) {
        val pool = when {
            effectiveRelativePath.isEmpty() -> allSongs
            headerBaseContent.directSongs.isNotEmpty() -> headerBaseContent.directSongs
            headerBaseContent.songsUnderCurrent.isNotEmpty() -> headerBaseContent.songsUnderCurrent
            else -> allSongs
        }
        pool.filter { it.artworkUri != null || !it.dataPath.isNullOrBlank() }.randomOrNull()
            ?: pool.randomOrNull()
            ?: allSongs.randomOrNull()
    }

    val headerArtworkSong = remember(
        allSongs,
        effectiveRelativePath,
        headerBaseContent,
        currentPlayingSongId,
        randomFallbackSong
    ) {
        val playingSong = currentPlayingSongId?.let { id ->
            allSongs.firstOrNull { it.id == id }
        }
        when {
            playingSong != null && (
                effectiveRelativePath.isEmpty() ||
                    headerBaseContent.directSongs.isEmpty() ||
                    headerBaseContent.songsUnderCurrent.any { it.id == playingSong.id }
                ) -> playingSong
            else -> randomFallbackSong
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 6.dp)
            .testTag("folder_screen")
    ) {
        FolderHeroHeader(
            headerArtworkSong = headerArtworkSong,
            effectiveRelativePath = effectiveRelativePath,
            canGoBack = canGoBack,
            onBackClick = handleBack,
            onNavigateToPath = updateRelativePath,
            hasPlayableSongs = playableSongsInCurrentFolder.isNotEmpty(),
            onPlayAllClick = {
                if (playableSongsInCurrentFolder.isNotEmpty()) {
                    onSongClick(playableSongsInCurrentFolder.first(), playableSongsInCurrentFolder)
                }
            },
            onShuffleAllClick = {
                if (playableSongsInCurrentFolder.isNotEmpty()) {
                    val shuffled = playableSongsInCurrentFolder.shuffled()
                    onSongClick(shuffled.first(), shuffled)
                }
            },
            onSortClick = { showSortSheet = true },
            textFieldValue = textFieldValue,
            onTextFieldValueChange = { newValue ->
                val oldText = textFieldValue.text
                textFieldValue = newValue
                if (newValue.text != oldText) {
                    onSearchQueryChange(newValue.text)
                }
            },
            isSearchActive = isSearchActive,
            onSearchFocusChanged = { isSearchFocused = it },
            onSearchSubmit = {
                onSearchAction?.invoke(textFieldValue.text)
                focusManager.clearFocus()
            },
            onClearSearch = clearSearch,
            folderStyle = folderStyle,
            onFolderStyleChange = onFolderStyleChange,
            showFileName = showFileName,
            onShowFileNameChange = onShowFileNameChange,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 208.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            AnimatedContent(
                targetState = effectiveRelativePath,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 140.dp),
                transitionSpec = {
                    val isOpening = if (initialState.isEmpty()) {
                        targetState.isNotEmpty()
                    } else if (targetState.isEmpty()) {
                        false
                    } else {
                        targetState.startsWith(initialState) ||
                            targetState.count { it == '/' } > initialState.count { it == '/' }
                    }

                    ExpressiveMotion.hierarchicalTransition(isForward = isOpening)
                },
                label = "folder_navigation_animation"
            ) { targetPath ->
                val baseFolderContent = remember(allSongs, targetPath) {
                    FolderTreeParser.buildBaseFolderContent(allSongs, targetPath)
                }

                val currentFolderContent = remember(baseFolderContent, uiState.debouncedSearchQuery) {
                    FolderTreeParser.filterFolderContent(baseFolderContent, uiState.debouncedSearchQuery)
                }

                val (rawSubfolders, rawDirectSongs) = currentFolderContent

                val currentSubfolders = remember(rawSubfolders, folderIsAscending) {
                    if (folderIsAscending) rawSubfolders else rawSubfolders.reversed()
                }

                val currentDirectSongs = remember(
                    rawDirectSongs,
                    folderSortCriteria,
                    folderIsAscending,
                    uiState.playCounts
                ) {
                    SongFilter.sortSongs(
                        songs = rawDirectSongs,
                        sortCriteria = folderSortCriteria,
                        isAscending = folderIsAscending,
                        playCounts = uiState.playCounts
                    )
                }

                if (currentSubfolders.isEmpty() && currentDirectSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 68.dp)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No items match \"$searchQuery\"" else "No songs or subfolders in this directory.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val listState = folderScrollStates.getOrPut(targetPath) { LazyListState() }

                    var isFastScrolling by remember { mutableStateOf(false) }
                    var isHighSpeedScrolling by remember { mutableStateOf(false) }

                    val hasMiniPlayer = LocalMiniPlayerVisible.current
                    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val effectiveBottomPadding = if (hasMiniPlayer) 196.dp + navBarBottomInset else 120.dp + navBarBottomInset

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 68.dp,
                                bottom = effectiveBottomPadding
                            )
                        ) {
                            items(currentSubfolders, key = { "folder_${it.path}" }, contentType = { "folder" }) { folder ->
                                FolderItem(
                                    folder = folder,
                                    folderStyle = folderStyle,
                                    onClick = { updateRelativePath(folder.path) },
                                    onActionClick = { action, targetFolder ->
                                        scope.launch {
                                            val folderSongs = withContext(Dispatchers.Default) {
                                                FolderTreeParser.getSongsInFolder(allSongs, targetFolder.path)
                                            }

                                            if (folderSongs.isNotEmpty()) {
                                                when (action) {
                                                    FolderAction.PLAY -> onSongClick(folderSongs.first(), folderSongs)

                                                    FolderAction.SHUFFLE -> {
                                                        val shuffled = folderSongs.shuffled()
                                                        onSongClick(shuffled.first(), shuffled)
                                                    }

                                                    FolderAction.PLAY_NEXT -> {
                                                        onPlayNextBatch?.invoke(folderSongs)
                                                    }

                                                    FolderAction.ADD_TO_QUEUE -> {
                                                        onAddToQueueBatch?.invoke(folderSongs)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            items(currentDirectSongs, key = { "song_${it.id}" }, contentType = { "song" }) { song ->
                                SongItem(
                                    song = song,
                                    isCurrentSong = song.id == currentPlayingSongId,
                                    onSongClick = { s -> onSongClick(s, currentDirectSongs) },
                                    onActionClick = onSongAction,
                                    isFastScrolling = isFastScrolling,
                                    isHighSpeedScrolling = isHighSpeedScrolling,
                                    isFavorite = favoriteSongIds.contains(song.id),
                                    onToggleFavorite = onToggleFavorite,
                                    showFileNameAndSize = showFileName
                                )
                            }
                        }

                        FastScrollBar(
                            listState = listState,
                            itemCount = currentSubfolders.size + currentDirectSongs.size,
                            previewTextProvider = { index ->
                                if (index < currentSubfolders.size) {
                                    currentSubfolders.getOrNull(index)?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                                } else {
                                    val songIndex = index - currentSubfolders.size
                                    val targetSong = currentDirectSongs.getOrNull(songIndex)
                                    val label = if (showFileName && targetSong != null) {
                                        TimeUtils.extractFileName(targetSong)
                                    } else {
                                        targetSong?.title.orEmpty()
                                    }

                                    label.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
                                }
                            },
                            onDraggingChange = { isFastScrolling = it },
                            onHighSpeedChange = { isHighSpeedScrolling = it },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val availableCriteria = remember {
            listOf(
                AppSortCriteria.TITLE,
                AppSortCriteria.ARTIST,
                AppSortCriteria.ALBUM,
                AppSortCriteria.DATE_ADDED,
                AppSortCriteria.DATE_MODIFIED,
                AppSortCriteria.DURATION
            )
        }

        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    width = 36.dp,
                    height = 4.dp
                )
            }
        ) {
            val view = LocalView.current
            val isDark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f
            SideEffect {
                var parent = view.parent
                while (parent != null) {
                    if (parent is DialogWindowProvider) {
                        val window = parent.window
                        window.navigationBarColor = android.graphics.Color.TRANSPARENT
                        window.statusBarColor = android.graphics.Color.TRANSPARENT
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            window.isNavigationBarContrastEnforced = false
                        }
                        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                        insetsController.isAppearanceLightNavigationBars = !isDark
                        break
                    }
                    parent = parent.parent
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (folderIsAscending) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { folderIsAscending = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Ascending",
                                tint = if (folderIsAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ascending",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (folderIsAscending) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (folderIsAscending) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!folderIsAscending) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { folderIsAscending = false }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "Descending",
                                tint = if (!folderIsAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Descending",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (!folderIsAscending) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (!folderIsAscending) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                availableCriteria.forEach { criteria ->
                    val isSelected = criteria == folderSortCriteria

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                folderSortCriteria = criteria
                                showSortSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = criteria.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
