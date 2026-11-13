package com.example.ui.common.components

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.core.data.mediastore.tags.SaveMetadataResult
import com.example.core.data.mediastore.tags.SongMetadata
import com.example.core.data.mediastore.tags.TaglibHelper
import com.example.core.model.Song
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoBottomSheet(
    song: Song,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val isFullscreen = sheetState.currentValue == SheetValue.Expanded

    var metadata by remember(song.id) {
        mutableStateOf(
            SongMetadata(
                title = song.title,
                artist = song.artist,
                album = song.album,
                year = song.year?.toString() ?: "",
                trackNumber = song.trackNumber?.toString() ?: "",
                genre = song.genre ?: "",
                durationMs = song.duration,
                fileSizeBytes = song.size ?: 0L
            )
        )
    }

    var isLoadingMetadata by remember(song.id) { mutableStateOf(true) }

    var isEditing by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }

    var editTitle by remember { mutableStateOf("") }

    var editArtist by remember { mutableStateOf("") }

    var editAlbum by remember { mutableStateOf("") }

    var editYear by remember { mutableStateOf("") }

    var editTrackNumber by remember { mutableStateOf("") }

    var editDiscNumber by remember { mutableStateOf("") }

    var editGenre by remember { mutableStateOf("") }

    var editAlbumArtist by remember { mutableStateOf("") }

    var editComposer by remember { mutableStateOf("") }

    var editCopyright by remember { mutableStateOf("") }

    var editLyric by remember { mutableStateOf("") }

    var pendingArtworkBytes by remember(song.id) { mutableStateOf<ByteArray?>(null) }
    var pendingArtworkMimeType by remember(song.id) { mutableStateOf<String?>(null) }
    var pendingArtworkBitmap by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }
    var pendingArtworkWidth by remember(song.id) { mutableIntStateOf(0) }
    var pendingArtworkHeight by remember(song.id) { mutableIntStateOf(0) }

    fun syncEditFields(m: SongMetadata) {
        editTitle = m.title
        editArtist = m.artist
        editAlbum = m.album
        editYear = m.year
        editTrackNumber = m.trackNumber
        editDiscNumber = m.discNumber
        editGenre = m.genre
        editAlbumArtist = m.albumArtist
        editComposer = m.composer
        editCopyright = m.copyright
        editLyric = m.lyrics
    }

    fun clearPendingArtwork() {
        pendingArtworkBytes = null
        pendingArtworkMimeType = null
        pendingArtworkBitmap = null
        pendingArtworkWidth = 0
        pendingArtworkHeight = 0
    }

    var retrySaveAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            retrySaveAfterPermission?.invoke()
        } else {
            isSaving = false
            Toast.makeText(context, "Write permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun performSave() {
        if (isSaving) return
        isSaving = true

        val hasNewArt = pendingArtworkBytes != null
        val newMeta = metadata.copy(
            title = editTitle.trim(),
            artist = editArtist.trim(),
            album = editAlbum.trim(),
            year = editYear.trim(),
            trackNumber = editTrackNumber.trim(),
            discNumber = editDiscNumber.trim(),
            genre = editGenre.trim(),
            albumArtist = editAlbumArtist.trim(),
            composer = editComposer.trim(),
            copyright = editCopyright.trim(),
            lyrics = editLyric.trim(),
            newArtworkBytes = pendingArtworkBytes,
            newArtworkMimeType = pendingArtworkMimeType
        )

        coroutineScope.launch {
            when (val res = TaglibHelper.saveSongMetadataWithResult(context, song, newMeta)) {
                is SaveMetadataResult.Success -> {
                    if (hasNewArt) {
                        invalidateArtworkCache(
                            dataPath = song.dataPath,
                            contentUri = song.contentUri,
                            artworkUri = song.artworkUri
                        )
                    }

                    val refreshed = withContext(Dispatchers.IO) {
                        TaglibHelper.getSongMetadata(context, song)
                    }
                    metadata = refreshed
                    clearPendingArtwork()
                    isSaving = false
                    isEditing = false

                    Toast.makeText(context, "Metadata saved", Toast.LENGTH_SHORT).show()
                }
                is SaveMetadataResult.NeedsWritePermission -> {
                    isSaving = false
                    retrySaveAfterPermission = { performSave() }
                    runCatching {
                        writePermissionLauncher.launch(
                            IntentSenderRequest.Builder(res.intentSender).build()
                        )
                    }.onFailure {
                        Toast.makeText(context, "Failed to request write permission", Toast.LENGTH_SHORT).show()
                    }
                }
                is SaveMetadataResult.Failed -> {
                    isSaving = false
                    Toast.makeText(context, "Failed to save metadata", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val loadedArt = withContext(Dispatchers.IO) {
                    runCatching {
                        val rawMime = context.contentResolver.getType(uri)?.lowercase(Locale.US) ?: "image/jpeg"
                        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@runCatching null
                        if (rawBytes.isEmpty()) return@runCatching null

                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
                        val origW = boundsOpts.outWidth
                        val origH = boundsOpts.outHeight
                        if (origW <= 0 || origH <= 0) return@runCatching null

                        val decodedBmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                            ?: return@runCatching null

                        val (finalBytes, finalMime) = if (rawMime == "image/jpeg" || rawMime == "image/jpg" || rawMime == "image/png") {
                            rawBytes to (if (rawMime == "image/jpg") "image/jpeg" else rawMime)
                        } else {
                            val out = ByteArrayOutputStream()
                            decodedBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
                            out.toByteArray() to "image/jpeg"
                        }

                        val previewMaxPx = 512
                        val previewBmp = if (origW > previewMaxPx || origH > previewMaxPx) {
                            val scale = previewMaxPx.toFloat() / maxOf(origW, origH).toFloat()
                            val pw = (origW * scale).toInt().coerceAtLeast(1)
                            val ph = (origH * scale).toInt().coerceAtLeast(1)
                            android.graphics.Bitmap.createScaledBitmap(decodedBmp, pw, ph, true)
                        } else {
                            decodedBmp
                        }

                        Triple(finalBytes to finalMime, origW to origH, previewBmp.asImageBitmap())
                    }.getOrNull()
                }

                if (loadedArt != null) {
                    val (bytesAndMime, dims, imgBitmap) = loadedArt
                    pendingArtworkBytes = bytesAndMime.first
                    pendingArtworkMimeType = bytesAndMime.second
                    pendingArtworkWidth = dims.first
                    pendingArtworkHeight = dims.second
                    pendingArtworkBitmap = imgBitmap
                } else {
                    Toast.makeText(context, "Failed to load selected image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(song.id) {
        isLoadingMetadata = true

        val loaded = withContext(Dispatchers.IO) {
            TaglibHelper.getSongMetadata(context, song)
        }

        metadata = loaded

        syncEditFields(loaded)

        isLoadingMetadata = false
    }

    BackHandler(enabled = isFullscreen) {
        coroutineScope.launch {
            sheetState.partialExpand()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
        },
        modifier = Modifier.testTag("song_info_bottom_sheet")
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

        @OptIn(ExperimentalFoundationApi::class)
        CompositionLocalProvider(
            LocalOverscrollConfiguration provides null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isEditing) "Edit Metadata" else "Song Info",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!isEditing) {
                        FilledTonalButton(
                            onClick = {
                                syncEditFields(metadata)
                                isEditing = true
                            },
                            modifier = Modifier.testTag("song_info_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text("Edit")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    clearPendingArtwork()
                                    syncEditFields(metadata)
                                    isEditing = false
                                },
                                enabled = !isSaving,
                                modifier = Modifier.testTag("song_info_cancel_edit_button")
                            ) {
                                Text("Cancel")
                            }

                            FilledTonalButton(
                                onClick = { performSave() },
                                enabled = !isSaving,
                                modifier = Modifier.testTag("song_info_header_save_button")
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Text("Save")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(128.dp)
                    ) {
                        val artworkShape = RoundedCornerShape(16.dp)
                        Box(
                            modifier = Modifier
                                .size(128.dp)
                                .clip(artworkShape)
                                .then(
                                    if (isEditing) {
                                        Modifier.clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("song_info_artwork"),
                            contentAlignment = Alignment.Center
                        ) {
                            val previewBmp = pendingArtworkBitmap
                            if (previewBmp != null) {
                                Image(
                                    bitmap = previewBmp,
                                    contentDescription = "${song.title} new artwork",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                ArtworkCard(
                                    artworkUri = song.artworkUri,
                                    contentUri = song.contentUri,
                                    dataPath = song.dataPath,
                                    size = 128.dp,
                                    cornerRadius = 16.dp,
                                    title = song.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            if (isEditing) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Artwork",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val displayArtInfo = remember(
                            metadata.formattedDimensions,
                            metadata.formattedArtworkSize,
                            pendingArtworkBytes,
                            pendingArtworkWidth,
                            pendingArtworkHeight
                        ) {
                            val pBytes = pendingArtworkBytes
                            if (pBytes != null && pendingArtworkWidth > 0 && pendingArtworkHeight > 0) {
                                val sizeStr = if (pBytes.size < 1024 * 1024) {
                                    String.format(Locale.US, "%.1f KB", pBytes.size / 1024.0)
                                } else {
                                    String.format(Locale.US, "%.2f MB", pBytes.size / (1024.0 * 1024.0))
                                }
                                "$pendingArtworkWidth × $pendingArtworkHeight · $sizeStr"
                            } else {
                                "${metadata.formattedDimensions} · ${metadata.formattedArtworkSize}"
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = displayArtInfo,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 10.5.sp
                                ),
                                color = if (pendingArtworkBytes != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            AudioTechInfoRow(
                                label = "Format",
                                value = metadata.format.ifBlank { "—" }
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            AudioTechInfoRow(
                                label = "Duration",
                                value = metadata.formattedDuration
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            AudioTechInfoRow(
                                label = "Bitrate",
                                value = metadata.formattedBitrate
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            AudioTechInfoRow(
                                label = "File Size",
                                value = metadata.formattedFileSize
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            AudioTechInfoRow(
                                label = "Stream Size",
                                value = metadata.formattedStreamSize
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isEditing) {
                    InfoDisplayItem(label = "Title", value = metadata.title)

                    InfoDisplayItem(label = "Artist", value = metadata.artist)

                    InfoDisplayItem(label = "Album", value = metadata.album)

                    InfoDisplayItem(label = "Year", value = metadata.year)

                    InfoDisplayItem(label = "Track Number", value = metadata.trackNumber)

                    InfoDisplayItem(label = "Disc Number", value = metadata.discNumber)

                    InfoDisplayItem(label = "Genre", value = metadata.genre)

                    InfoDisplayItem(label = "Album Artist", value = metadata.albumArtist)

                    InfoDisplayItem(label = "Composer", value = metadata.composer)

                    InfoDisplayItem(label = "Copyright", value = metadata.copyright)

                    InfoDisplayItem(label = "Lyric", value = metadata.lyrics, isMultiline = true)
                } else {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_title")
                    )

                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_artist")
                    )

                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        label = { Text("Album") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_album")
                    )

                    OutlinedTextField(
                        value = editYear,
                        onValueChange = { editYear = it },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_year")
                    )

                    OutlinedTextField(
                        value = editTrackNumber,
                        onValueChange = { editTrackNumber = it },
                        label = { Text("Track Number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_track_number")
                    )

                    OutlinedTextField(
                        value = editDiscNumber,
                        onValueChange = { editDiscNumber = it },
                        label = { Text("Disc Number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_disc_number")
                    )

                    OutlinedTextField(
                        value = editGenre,
                        onValueChange = { editGenre = it },
                        label = { Text("Genre") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_genre")
                    )

                    OutlinedTextField(
                        value = editAlbumArtist,
                        onValueChange = { editAlbumArtist = it },
                        label = { Text("Album Artist") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_album_artist")
                    )

                    OutlinedTextField(
                        value = editComposer,
                        onValueChange = { editComposer = it },
                        label = { Text("Composer") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_composer")
                    )

                    OutlinedTextField(
                        value = editCopyright,
                        onValueChange = { editCopyright = it },
                        label = { Text("Copyright") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_copyright")
                    )

                    OutlinedTextField(
                        value = editLyric,
                        onValueChange = { editLyric = it },
                        label = { Text("Lyric") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_field_lyric")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { performSave() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("song_info_save_button"),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text("Saving...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioTechInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoDisplayItem(
    label: String,
    value: String,
    isMultiline: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = if (value.isNotBlank()) value else "—",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = if (isMultiline) 20 else 2
            )
        }
    }
}
