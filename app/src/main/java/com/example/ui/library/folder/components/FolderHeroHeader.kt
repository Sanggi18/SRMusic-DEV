package com.example.ui.library.folder.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.AppFolderStyle
import com.example.core.model.Song
import com.example.ui.common.ArtworkPlaceholderType
import com.example.ui.common.ArtworkShape
import com.example.ui.common.components.ArtworkCard

@Composable
fun FolderHeroHeader(
    headerArtworkSong: Song?,
    effectiveRelativePath: String,
    canGoBack: Boolean,
    onBackClick: () -> Unit,
    hasPlayableSongs: Boolean,
    onPlayAllClick: () -> Unit,
    onShuffleAllClick: () -> Unit,
    onSortClick: () -> Unit,
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    isSearchActive: Boolean,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSearchSubmit: () -> Unit,
    onClearSearch: () -> Unit,
    folderStyle: AppFolderStyle,
    onFolderStyleChange: ((AppFolderStyle) -> Unit)?,
    showFileName: Boolean,
    onShowFileNameChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    onNavigateToPath: ((String) -> Unit)? = null
) {
    val headerCardShape = RoundedCornerShape(24.dp)
    val overlayPillColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
    val accentColor = MaterialTheme.colorScheme.primary
    val onOverlayColor = MaterialTheme.colorScheme.onSurface

    val heroHeight = 200.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(heroHeight)
            .clip(headerCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .testTag("folder_header_card")
    ) {
        // 1. Full-bleed Artwork background
        Box(modifier = Modifier.fillMaxSize()) {
            ArtworkCard(
                artworkUri = headerArtworkSong?.artworkUri,
                contentUri = headerArtworkSong?.contentUri,
                dataPath = headerArtworkSong?.dataPath,
                title = "",
                size = heroHeight,
                decodeSizePx = 1024,
                shape = ArtworkShape.ROUNDED_SQUARE,
                cornerRadius = 24.dp,
                placeholderType = ArtworkPlaceholderType.FOLDER,
                topCrop = false,
                modifier = Modifier.fillMaxSize()
            )

                // Top scrim for back button contrast
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.50f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Bottom scrim for action buttons contrast
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f)
                                )
                            )
                        )
                )
            }

            // 2. Single Chevron Back Button at Top-Left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(overlayPillColor)
                    .clickable(
                        enabled = canGoBack,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackClick
                    )
                    .testTag("folder_header_back"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = "Back",
                    tint = if (canGoBack) accentColor else onOverlayColor.copy(alpha = 0.38f),
                    modifier = Modifier
                        .size(17.dp)
                        .padding(end = 1.dp)
                )
            }

            // 3. Bottom Action Bar: Play, Shuffle, Sort, Search Pill, More Options
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(overlayPillColor)
                        .clickable(
                            enabled = hasPlayableSongs,
                            onClick = onPlayAllClick
                        )
                        .testTag("folder_header_play"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play folder",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(overlayPillColor)
                        .clickable(
                            enabled = hasPlayableSongs,
                            onClick = onShuffleAllClick
                        )
                        .testTag("folder_header_shuffle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle folder",
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(overlayPillColor)
                        .clickable(onClick = onSortClick)
                        .testTag("folder_header_sort"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Sort folder",
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(overlayPillColor)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search folders",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = "Search...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = onOverlayColor.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = onTextFieldValueChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = onOverlayColor,
                                fontSize = 12.5.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { onSearchSubmit() }
                            ),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { onSearchFocusChanged(it.isFocused) }
                                .testTag("search_input_field")
                        )
                    }

                    if (isSearchActive) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onClearSearch)
                                .testTag("search_clear_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = onOverlayColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                var showOptionsMenu by remember { mutableStateOf(false) }

                Box {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(overlayPillColor)
                            .clickable { showOptionsMenu = true }
                            .testTag("folder_header_more"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Folder display options",
                            tint = accentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "Folder Style",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        DropdownMenuItem(
                            text = { Text("Icon", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (folderStyle == AppFolderStyle.ICON) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onFolderStyleChange?.invoke(AppFolderStyle.ICON)
                                showOptionsMenu = false
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Thumbnail", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                if (folderStyle == AppFolderStyle.THUMBNAIL) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onFolderStyleChange?.invoke(AppFolderStyle.THUMBNAIL)
                                showOptionsMenu = false
                            }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = { Text("File name", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
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
                                onShowFileNameChange?.invoke(!showFileName)
                                showOptionsMenu = false
                            }
                        )
                    }
                }
        }
    }
}
