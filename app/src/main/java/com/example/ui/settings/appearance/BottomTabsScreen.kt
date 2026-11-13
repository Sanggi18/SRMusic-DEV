package com.example.ui.settings.appearance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.navigation.components.TabItem
import com.example.ui.navigation.components.getTabIcon
import com.example.ui.settings.components.SettingsCard
import com.example.ui.settings.components.SettingsDivider
import com.example.ui.settings.components.SettingsSwitchRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BottomTabsScreen(
    enabledTabs: List<TabItem>,
    onEnabledTabsChange: (List<TabItem>) -> Unit,
    showIconBackground: Boolean = true,
    onShowIconBackgroundChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val availableTabs = TabItem.entries.filter { !enabledTabs.contains(it) }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isSettling by remember { mutableStateOf(false) }
    val settleAnimatable = remember { Animatable(0f) }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var itemHeightPx by remember { mutableFloatStateOf(with(density) { 58.dp.toPx() }) }
    val draggedItemShape = remember { RoundedCornerShape(14.dp) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Icon Background Bottom Tabs [Switch]
        SettingsCard {
            SettingsSwitchRow(
                title = "Icon Background Bottom Tabs",
                subtitle = "Display a pill-shaped highlight behind the active navigation tab icon",
                icon = Icons.Rounded.Apps,
                checked = showIconBackground,
                onCheckedChange = onShowIconBackgroundChange
            )
        }

        // Card 2: Active Navigation Tabs (Unified Header + Reorderable List)
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Active Navigation Tabs (${enabledTabs.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
                )

                SettingsDivider()

                enabledTabs.forEachIndexed { index, tab ->
                    key(tab.id) {
                        val isBeingDragged = draggingIndex == index
                        val isAnyItemDragging = draggingIndex != -1

                        val targetShift = if (isAnyItemDragging && !isBeingDragged) {
                            when {
                                draggingIndex < targetIndex && index in (draggingIndex + 1)..targetIndex -> -itemHeightPx
                                draggingIndex > targetIndex && index in targetIndex until draggingIndex -> itemHeightPx
                                else -> 0f
                            }
                        } else {
                            0f
                        }

                        val animatedShift by animateFloatAsState(
                            targetValue = targetShift,
                            animationSpec = if (isAnyItemDragging) {
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            } else {
                                snap()
                            },
                            label = "tab_shift_${tab.id}"
                        )

                        val itemOffset = when {
                            isBeingDragged && isSettling -> settleAnimatable.value
                            isBeingDragged -> dragOffsetY
                            else -> animatedShift
                        }

                        val currentDisplayPosition = when {
                            isBeingDragged -> (targetIndex + 1).coerceIn(1, enabledTabs.size)
                            isAnyItemDragging -> {
                                when {
                                    draggingIndex < targetIndex && index in (draggingIndex + 1)..targetIndex -> index
                                    draggingIndex > targetIndex && index in targetIndex until draggingIndex -> index + 2
                                    else -> index + 1
                                }
                            }
                            else -> index + 1
                        }

                        val elevation by animateFloatAsState(
                            targetValue = if (isBeingDragged && !isSettling) 10f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "elevation_${tab.id}"
                        )

                        val dragScale by animateFloatAsState(
                            targetValue = if (isBeingDragged && !isSettling) 1.02f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "scale_${tab.id}"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isBeingDragged) 10f else 1f)
                                .graphicsLayer {
                                    translationY = itemOffset
                                    scaleX = dragScale
                                    scaleY = dragScale
                                    shadowElevation = elevation
                                    shape = draggedItemShape
                                    clip = isBeingDragged
                                }
                                .background(
                                    if (isBeingDragged) {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .onGloballyPositioned { coordinates ->
                                    if (coordinates.size.height > 0 && !isAnyItemDragging) {
                                        itemHeightPx = coordinates.size.height.toFloat()
                                    }
                                }
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = getTabIcon(tab),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "Position $currentDisplayPosition",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .pointerInput(enabledTabs, index) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                if (!isSettling && draggingIndex == -1) {
                                                    draggingIndex = index
                                                    targetIndex = index
                                                    dragOffsetY = 0f
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggingIndex != -1 && !isSettling) {
                                                    val fromIdx = draggingIndex
                                                    val toIdx = targetIndex
                                                    val targetSettleOffset = (toIdx - fromIdx) * itemHeightPx

                                                    isSettling = true
                                                    coroutineScope.launch {
                                                        settleAnimatable.snapTo(dragOffsetY)
                                                        settleAnimatable.animateTo(
                                                            targetValue = targetSettleOffset,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                                stiffness = 650f
                                                            )
                                                        )
                                                        if (fromIdx != toIdx &&
                                                            fromIdx in 0..enabledTabs.lastIndex &&
                                                            toIdx in 0..enabledTabs.lastIndex
                                                        ) {
                                                            val mutableList = enabledTabs.toMutableList()
                                                            val item = mutableList.removeAt(fromIdx)
                                                            mutableList.add(toIdx, item)
                                                            onEnabledTabsChange(mutableList)
                                                        }
                                                        draggingIndex = -1
                                                        targetIndex = -1
                                                        dragOffsetY = 0f
                                                        isSettling = false
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                if (draggingIndex != -1) {
                                                    draggingIndex = -1
                                                    targetIndex = -1
                                                    dragOffsetY = 0f
                                                    isSettling = false
                                                }
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                if (draggingIndex != -1 && !isSettling) {
                                                    val minOffset = -draggingIndex * itemHeightPx
                                                    val maxOffset = (enabledTabs.lastIndex - draggingIndex) * itemHeightPx
                                                    val newOffset = (dragOffsetY + dragAmount).coerceIn(minOffset, maxOffset)
                                                    dragOffsetY = newOffset

                                                    val computedTarget = (draggingIndex + (newOffset / itemHeightPx).roundToInt())
                                                        .coerceIn(0, enabledTabs.lastIndex)

                                                    if (computedTarget != targetIndex) {
                                                        targetIndex = computedTarget
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder ${tab.label}",
                                    tint = if (isBeingDragged) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (enabledTabs.size > 2 && !isAnyItemDragging) {
                                        onEnabledTabsChange(enabledTabs.filter { it != tab })
                                    }
                                },
                                enabled = enabledTabs.size > 2 && !isAnyItemDragging
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove ${tab.label} from bottom bar",
                                    tint = if (enabledTabs.size > 2) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                            }
                        }

                        if (index < enabledTabs.size - 1) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }

        // Card 3: Inactive Navigation Tabs (Unified Header + Available List)
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Inactive Navigation Tabs (${availableTabs.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
                )

                SettingsDivider()

                if (availableTabs.isEmpty()) {
                    Text(
                        text = "All available tabs are currently active in the bottom navigation bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                } else {
                    availableTabs.forEachIndexed { index, tab ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = getTabIcon(tab),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    onEnabledTabsChange(enabledTabs + tab)
                                }
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Add ${tab.label} to bottom bar",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (index < availableTabs.size - 1) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}
