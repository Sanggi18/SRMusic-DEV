package com.example.ui.library.components

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.core.model.AppGridMode
import com.example.core.model.AppSortCriteria

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBar(
    sortCriteria: AppSortCriteria,
    isAscending: Boolean,
    onSortCriteriaChange: (AppSortCriteria) -> Unit,
    modifier: Modifier = Modifier,
    onAscendingChange: ((Boolean) -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
    totalCount: Int = 0,
    countLabel: String = "items",
    gridMode: AppGridMode? = null,
    onGridModeChange: ((AppGridMode) -> Unit)? = null,
    showSongDisplayMenu: Boolean = false,
    showCodec: Boolean = false,
    onToggleCodec: ((Boolean) -> Unit)? = null,
    showFavoritesToggle: Boolean = false,
    onToggleFavorites: ((Boolean) -> Unit)? = null,
    showFileName: Boolean = false,
    onToggleFileName: ((Boolean) -> Unit)? = null,
    showSize: Boolean = false,
    onToggleSize: ((Boolean) -> Unit)? = null,
    availableCriteria: List<AppSortCriteria> = listOf(
        AppSortCriteria.TITLE,
        AppSortCriteria.ARTIST,
        AppSortCriteria.ALBUM,
        AppSortCriteria.DATE_ADDED,
        AppSortCriteria.DATE_MODIFIED
    )
) {
    var showSortSheet by remember { mutableStateOf(false) }

    val isAmoled = MaterialTheme.colorScheme.background == com.example.ui.common.theme.AmoledBg

    val chipContainerColor = if (isAmoled) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = chipContainerColor,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(CircleShape)
                .clickable { showSortSheet = true }
                .testTag("sort_bar_chip")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sort,
                    contentDescription = "Sort options",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                val displayCriteriaLabel = sortCriteria.label

                Text(
                    text = "$displayCriteriaLabel ($totalCount $countLabel)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onShuffleClick != null) {
                Surface(
                    shape = CircleShape,
                    color = chipContainerColor,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(CircleShape)
                        .clickable { onShuffleClick() }
                        .testTag("sort_bar_shuffle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shuffle",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (showSongDisplayMenu) {
                Spacer(modifier = Modifier.width(6.dp))

                var showDisplayMenu by remember { mutableStateOf(false) }

                Box {
                    Surface(
                        shape = CircleShape,
                        color = chipContainerColor,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { showDisplayMenu = true }
                            .testTag("sort_bar_more_options")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Song display options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showDisplayMenu,
                        onDismissRequest = { showDisplayMenu = false },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        // 1. Show codec
                        DropdownMenuItem(
                            text = { Text("Show codec", style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                if (showCodec) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onToggleCodec?.invoke(!showCodec)
                            }
                        )

                        // 2. Show favorites toggle
                        DropdownMenuItem(
                            text = { Text("Show favorites toggle", style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                if (showFavoritesToggle) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onToggleFavorites?.invoke(!showFavoritesToggle)
                            }
                        )

                        // 3. Show file name
                        DropdownMenuItem(
                            text = { Text("Show file name", style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                if (showFileName) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onToggleFileName?.invoke(!showFileName)
                            }
                        )

                        // 4. Show size
                        DropdownMenuItem(
                            text = { Text("Show size", style = MaterialTheme.typography.bodyMedium) },
                            trailingIcon = {
                                if (showSize) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onToggleSize?.invoke(!showSize)
                            }
                        )
                    }
                }
            }

            if (gridMode != null && onGridModeChange != null) {
                Spacer(modifier = Modifier.width(4.dp))

                val nextMode = when (gridMode) {
                    AppGridMode.LIST -> AppGridMode.GRID_2

                    AppGridMode.GRID_2 -> AppGridMode.GRID_3

                    AppGridMode.GRID_3 -> AppGridMode.GRID_4

                    AppGridMode.GRID_4 -> AppGridMode.LIST
                }

                val modeIcon = when (gridMode) {
                    AppGridMode.LIST -> Icons.Rounded.ViewList

                    AppGridMode.GRID_2 -> Icons.Rounded.GridView

                    AppGridMode.GRID_3 -> Icons.Rounded.ViewModule

                    AppGridMode.GRID_4 -> Icons.Rounded.Widgets
                }

                IconButton(
                    onClick = { onGridModeChange(nextMode) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("sort_bar_grid_mode_button")
                ) {
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = "View mode: ${gridMode.label}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showSortSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        color = if (isAscending) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onAscendingChange?.invoke(true)
                            }
                            .testTag("sort_order_asc")
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
                                tint = if (isAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "Ascending",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isAscending) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isAscending) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isAscending) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onAscendingChange?.invoke(false)
                            }
                            .testTag("sort_order_desc")
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
                                tint = if (!isAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "Descending",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (!isAscending) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (!isAscending) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                availableCriteria.forEach { criteria ->
                    val isSelected = criteria == sortCriteria

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSortCriteriaChange(criteria)

                                showSortSheet = false
                            }
                            .testTag("sort_criteria_${criteria.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val itemCriteriaLabel = criteria.label

                            Text(
                                text = itemCriteriaLabel,
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
