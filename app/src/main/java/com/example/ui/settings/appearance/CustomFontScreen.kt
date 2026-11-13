package com.example.ui.settings.appearance

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class CustomFontItem(
    val name: String,
    val displayTitle: String,
    val file: File
)

@Composable
fun CustomFontScreen(
    currentFontPath: String?,
    currentFontName: String?,
    onFontSelected: (path: String?, name: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    var isOperating by remember { mutableStateOf(false) }

    var fontList by remember { mutableStateOf<List<CustomFontItem>>(emptyList()) }

    var isDeleteMode by remember { mutableStateOf(false) }

    var selectedForDelete by remember { mutableStateOf<Set<String>>(emptySet()) }

    BackHandler(enabled = isDeleteMode) {
        isDeleteMode = false

        selectedForDelete = emptySet()
    }

    fun loadFontList() {
        val fontsDir = File(context.filesDir, "custom_fonts")

        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }

        val files = fontsDir.listFiles { file ->
            val ext = file.extension.lowercase()

            file.isFile && (ext == "ttf" || ext == "otf")
        } ?: emptyArray()

        fontList = files.sortedBy { it.name.lowercase() }.map { file ->
            val cleanTitle = file.nameWithoutExtension
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .ifBlank { file.name }

            CustomFontItem(
                name = file.name,
                displayTitle = cleanTitle,
                file = file
            )
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            loadFontList()
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            isOperating = true

            val (savedFile, originalName, errorMsg) = withContext(Dispatchers.IO) {
                importCustomFont(context, uri)
            }

            if (savedFile != null && originalName != null) {
                withContext(Dispatchers.IO) {
                    loadFontList()
                }

                onFontSelected(savedFile.absolutePath, originalName)

                Toast.makeText(context, "Font applied: $originalName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    errorMsg ?: "Failed to import font. Ensure file is valid (.ttf / .otf).",
                    Toast.LENGTH_LONG
                ).show()
            }

            isOperating = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = {
                    if (isDeleteMode) {
                        isDeleteMode = false
                        selectedForDelete = emptySet()
                    } else {
                        openDocumentLauncher.launch(
                            arrayOf(
                                "font/ttf",
                                "font/otf",
                                "application/octet-stream",
                                "font/*",
                                "application/x-font-ttf",
                                "application/x-font-opentype"
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    if (isOperating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isDeleteMode) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Custom Font",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = {
                    if (!isDeleteMode) {
                        if (fontList.isEmpty()) {
                            Toast.makeText(context, "No custom fonts to delete", Toast.LENGTH_SHORT).show()
                            return@Surface
                        }
                        isDeleteMode = true
                        selectedForDelete = emptySet()
                    } else {
                        val targets = selectedForDelete.toList()
                        if (targets.isEmpty()) {
                            isDeleteMode = false
                            return@Surface
                        }
                        scope.launch {
                            isOperating = true
                            val wasCurrentDeleted = currentFontPath != null && targets.contains(currentFontPath)
                            withContext(Dispatchers.IO) {
                                targets.forEach { path -> deleteFontFile(context, path) }
                                loadFontList()
                            }
                            if (wasCurrentDeleted) {
                                onFontSelected(null, null)
                            }
                            selectedForDelete = emptySet()
                            isDeleteMode = false
                            isOperating = false
                            Toast.makeText(context, "${targets.size} fonts deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = if (isDeleteMode && selectedForDelete.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isDeleteMode && selectedForDelete.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDeleteMode && selectedForDelete.isNotEmpty()) "Delete (${selectedForDelete.size})" else "Delete",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isDeleteMode && selectedForDelete.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val isDefaultSelected = currentFontPath.isNullOrBlank()

            FontSelectionCard(
                title = "System Default",
                subtitle = "Default",
                previewFontFamily = FontFamily.Default,
                isSelected = isDefaultSelected,
                isDeleteMode = isDeleteMode,
                isCheckedForDelete = false,
                isSystemDefault = true,
                onClick = {
                    if (isDeleteMode) {
                        Toast.makeText(context, "Cannot delete default font", Toast.LENGTH_SHORT).show()
                    } else {
                        onFontSelected(null, null)
                    }
                },
                onLongClick = null,
                modifier = Modifier.testTag("font_card_default")
            )

            fontList.forEach { item ->
                val path = item.file.absolutePath
                val isSelected = currentFontPath == path
                val isCheckedForDelete = selectedForDelete.contains(path)
                var itemFontFamily by remember(path) { mutableStateOf<FontFamily>(FontFamily.Default) }

                LaunchedEffect(path) {
                    itemFontFamily = withContext(Dispatchers.IO) {
                        runCatching {
                            FontFamily(Typeface.createFromFile(item.file))
                        }.getOrDefault(FontFamily.Default)
                    }
                }

                FontSelectionCard(
                    title = item.displayTitle,
                    subtitle = item.name,
                    previewFontFamily = itemFontFamily,
                    isSelected = isSelected,
                    isDeleteMode = isDeleteMode,
                    isCheckedForDelete = isCheckedForDelete,
                    isSystemDefault = false,
                    onClick = {
                        if (isDeleteMode) {
                            selectedForDelete = if (isCheckedForDelete) {
                                selectedForDelete - path
                            } else {
                                selectedForDelete + path
                            }
                        } else {
                            onFontSelected(path, item.displayTitle)
                        }
                    },
                    onLongClick = {
                        if (!isDeleteMode) {
                            isDeleteMode = true
                            selectedForDelete = setOf(path)
                        }
                    },
                    modifier = Modifier.testTag("font_card_${item.name}")
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FontSelectionCard(
    title: String,
    subtitle: String,
    previewFontFamily: FontFamily,
    isSelected: Boolean,
    isDeleteMode: Boolean,
    isCheckedForDelete: Boolean,
    isSystemDefault: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val highlightCard = if (isDeleteMode) isCheckedForDelete else isSelected

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (highlightCard) {
                if (isDeleteMode) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                }
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = if (highlightCard) {
            BorderStroke(
                width = 1.5.dp,
                color = if (isDeleteMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        } else {
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = previewFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                color = if (isSelected && !isDeleteMode) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isDeleteMode) {
                if (isSystemDefault) {
                    RadioButton(
                        selected = false,
                        onClick = onClick,
                        enabled = false
                    )
                } else {
                    Checkbox(
                        checked = isCheckedForDelete,
                        onCheckedChange = { onClick() }
                    )
                }
            } else {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick
                )
            }
        }
    }
}

private fun importCustomFont(context: Context, uri: Uri): Triple<File?, String?, String?> {
    try {
        var displayName: String? = null

        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

                    if (idx >= 0) {
                        displayName = cursor.getString(idx)
                    }
                }
            }
        }

        if (displayName == null) {
            displayName = uri.lastPathSegment ?: "font.ttf"
        }

        val rawName = displayName

        val ext = rawName.substringAfterLast('.', "").lowercase()

        if (ext != "ttf" && ext != "otf") {
            return Triple(null, null, "Unsupported file format. Please select a .ttf or .otf file.")
        }

        val fontsDir = File(context.filesDir, "custom_fonts")

        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }

        val baseName = rawName.substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "font" }

        var candidateFile = File(fontsDir, "$baseName.$ext")

        var counter = 1

        while (candidateFile.exists()) {
            candidateFile = File(fontsDir, "${baseName}_$counter.$ext")

            counter++
        }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(candidateFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: return Triple(null, null, "Failed to read font stream.")

        val typeface = try {
            Typeface.createFromFile(candidateFile)
        } catch (_: Exception) {
            null
        }

        if (typeface == null) {
            candidateFile.delete()

            return Triple(null, null, "Invalid or corrupted font file.")
        }

        val cleanTitle = baseName.replace('_', ' ').trim()

        return Triple(candidateFile, cleanTitle, null)
    } catch (e: Exception) {
        return Triple(null, null, "Error importing font: ${e.localizedMessage}")
    }
}

private fun deleteFontFile(context: Context, path: String) {
    try {
        val fontsDir = File(context.filesDir, "custom_fonts").canonicalPath

        val targetFile = File(path)

        if (targetFile.canonicalPath.startsWith(fontsDir) && targetFile.exists() && targetFile.isFile) {
            targetFile.delete()
        }
    } catch (_: Exception) {
    }
}
