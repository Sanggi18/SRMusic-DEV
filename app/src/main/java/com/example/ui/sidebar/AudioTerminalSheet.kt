package com.example.ui.sidebar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.core.audio.AudioTerminalLogger
import com.example.ui.player.components.rememberAudioOutputInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTerminalSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val audioOutputInfo = rememberAudioOutputInfo()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        AudioTerminalLogger.init(context)
    }

    val logs by AudioTerminalLogger.logs.collectAsStateWithLifecycle()
    val diagLogs by AudioTerminalLogger.diagLogs.collectAsStateWithLifecycle()

    val terminalListState = rememberLazyListState()
    val diagListState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            terminalListState.animateScrollToItem(logs.size - 1)
        }
    }

    LaunchedEffect(diagLogs.size) {
        if (diagLogs.isNotEmpty()) {
            diagListState.animateScrollToItem(diagLogs.size - 1)
        }
    }

    val containerBg = MaterialTheme.colorScheme.surfaceContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val pagerState = rememberPagerState(initialPage = 0) { 2 }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val logDump = buildString {
                        appendLine("SRMusic Audio Subsystem Diagnostic Log")
                        val deviceName = Build.MODEL
                        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                        val armInfo = primaryAbi.replace("-", "")
                        appendLine("Device Name: $deviceName")
                        appendLine("Arm: $armInfo")
                        appendLine("Android Version: ${Build.VERSION.RELEASE}")
                        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                        appendLine("Output Route: ${audioOutputInfo.displayName} [${audioOutputInfo.type}]")
                        appendLine()
                        appendLine("--- Terminal Audio ---")
                        logs.forEach { appendLine(it) }
                        appendLine()
                        appendLine("--- Log Audio ---")
                        diagLogs.forEach { appendLine(it.replace("SRMusicAudioDiag: ", ": ").replace("SRMusicAudioDiag ", "")) }
                    }
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(logDump.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Log successfully saved to srmusic-audio.log", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save log: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val onCopyClick: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val textToCopy = if (pagerState.currentPage == 0) {
            logs.joinToString(separator = "\n")
        } else {
            diagLogs.joinToString(separator = "\n") {
                it.replace("SRMusicAudioDiag: ", ": ").replace("SRMusicAudioDiag ", "")
            }
        }
        if (textToCopy.isNotEmpty()) {
            val clip = ClipData.newPlainText("SRMusic Audio Log", textToCopy)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Terminal logs copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Logs are empty", Toast.LENGTH_SHORT).show()
        }
    }

    val onSaveClick: () -> Unit = {
        saveLauncher.launch("srmusic-audio.log")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
        modifier = Modifier.testTag("audio_terminal_sheet")
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
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            var showHeaderMenu by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = primaryColor,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = primaryColor
                        )
                    },
                    divider = {},
                    modifier = Modifier.weight(1f)
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = {
                            Text(
                                text = "Terminal Audio",
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                maxLines = 1
                            )
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = {
                            Text(
                                text = "Log Audio",
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                maxLines = 1
                            )
                        }
                    )
                }

                Box(modifier = Modifier.padding(start = 6.dp)) {
                    IconButton(
                        onClick = { showHeaderMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = onSurfaceColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false },
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showHeaderMenu = false
                                onCopyClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.SaveAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showHeaderMenu = false
                                onSaveClick()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = containerBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                ) { page ->
                    if (page == 0) {
                        TerminalLogList(
                            list = logs,
                            state = terminalListState,
                            isTerminal1 = true,
                            onSurfaceColor = onSurfaceColor
                        )
                    } else {
                        TerminalLogList(
                            list = diagLogs,
                            state = diagListState,
                            isTerminal1 = false,
                            onSurfaceColor = onSurfaceColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLogList(
    list: List<String>,
    state: LazyListState,
    isTerminal1: Boolean,
    onSurfaceColor: Color
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(list) { rawLine ->
            val line = if (!isTerminal1) {
                rawLine.replace("SRMusicAudioDiag: ", ": ").replace("SRMusicAudioDiag ", "")
            } else {
                rawLine
            }
            val keyword = if (line.contains("Bit-Perfect State : Active")) {
                "Bit-Perfect State : Active"
            } else if (line.contains("Bit-Perfect Route: ACTIVE")) {
                "Bit-Perfect Route: ACTIVE"
            } else {
                null
            }
            if (isTerminal1 && keyword != null) {
                val idx = line.indexOf(keyword)
                val annotated = buildAnnotatedString {
                    append(line.substring(0, idx))
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF2196F3),
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(keyword)
                    }
                    append(line.substring(idx + keyword.length))
                }
                Text(
                    text = annotated,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 14.sp
                    ),
                    color = onSurfaceColor
                )
            } else {
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 14.sp
                    ),
                    color = onSurfaceColor
                )
            }
        }
    }
}
