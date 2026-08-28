package com.pxr.cymatic.ui.components.launcher

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pxr.cymatic.R
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenu
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenuItem
import com.pxr.cymatic.ui.screens.home.LauncherAppsViewModel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
fun InteractivePinnedGrid(
    entries: List<LauncherAppsViewModel.PinnedGridEntry>,
    showLabels: Boolean,
    onAppClick: (LauncherAppsLoader.LauncherApp) -> Unit,
    onFolderClick: (LauncherAppsViewModel.PinnedGridEntry.Folder) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onMergeFolder: (sourceIndex: Int, targetIndex: Int) -> Unit,
    onUnpinItem: (Int) -> Unit,
    onRenameFolderRequest: (LauncherAppsViewModel.PinnedGridEntry.Folder) -> Unit,
    onEmptySpaceLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
    iconScale: Float = 1.0f
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    var isHoveringMergeTarget by remember { mutableStateOf(false) }

    val cellBounds = remember { mutableStateMapOf<Int, Rect>() }
    var selectedItemIndexForMenu by rememberSaveable { mutableStateOf<Int?>(null) }

    val cellHeightDp = if (showLabels) (92 * iconScale).dp else (72 * iconScale).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(entries) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downOffset = down.position

                    // Find which item was pressed
                    val pressedIndex = cellBounds.entries.firstOrNull { (_, rect) ->
                        rect.contains(downOffset)
                    }?.key

                    if (pressedIndex == null || pressedIndex !in entries.indices) {
                        // Empty space in grid -> check for long press to open wallpaper overview
                        val longPressOnEmpty = awaitLongPressOrCancellation(down.id)
                        if (longPressOnEmpty != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEmptySpaceLongPress()
                        }
                        return@awaitEachGesture
                    }

                    // A pinned item was pressed.
                    // Wait for:
                    // 1. Pointer released (finger lifted UP) before timeout and within touchSlop -> TAP on press up!
                    // 2. Pointer moved past touchSlop -> SWIPE gesture for parent VerticalPager!
                    // 3. Timeout reached without moving -> LONG PRESS / DRAG!
                    val touchSlop = viewConfiguration.touchSlop
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    var totalDrag = Offset.Zero
                    var gestureAction: String? = null

                    withTimeoutOrNull(longPressTimeout) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                if (totalDrag.getDistance() <= touchSlop && !change.isConsumed) {
                                    change.consume()
                                    gestureAction = "TAP"
                                }
                                break
                            }

                            val dragAmount = change.position - change.previousPosition
                            totalDrag += dragAmount
                            if (totalDrag.getDistance() > touchSlop) {
                                // Finger moved past touchSlop -> user is swiping up to app drawer or scrolling.
                                // Do NOT consume, so parent VerticalPager receives the gesture.
                                gestureAction = "SWIPE"
                                break
                            }
                        }
                    }

                    if (gestureAction == "TAP") {
                        val entry = entries[pressedIndex]
                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                        when (entry) {
                            is LauncherAppsViewModel.PinnedGridEntry.App -> onAppClick(entry.app)
                            is LauncherAppsViewModel.PinnedGridEntry.Folder -> onFolderClick(entry)
                        }
                    } else if (gestureAction == null && totalDrag.getDistance() <= touchSlop) {
                        // Long-press triggered! Lift the item with haptic feedback
                        down.consume()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        draggedIndex = pressedIndex
                        dragDelta = Offset.Zero
                        hoveredIndex = pressedIndex
                        isHoveringMergeTarget = false
                        var hasMoved = false

                        // Follow subsequent drag gestures
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.pressed) {
                                val dragAmount = change.position - change.previousPosition
                                dragDelta += dragAmount
                                if (dragDelta.getDistance() > touchSlop) {
                                    hasMoved = true
                                }
                                change.consume()

                                val originalBounds = cellBounds[pressedIndex]
                                if (originalBounds != null) {
                                    val currentCenter = originalBounds.center + dragDelta

                                    var closestIdx: Int? = null
                                    var closestDist = Float.MAX_VALUE
                                    var isMergeCandidate = false

                                    for ((idx, rect) in cellBounds) {
                                        if (idx in entries.indices) {
                                            val dist = (rect.center - currentCenter).getDistance()
                                            if (dist < closestDist) {
                                                closestDist = dist
                                                closestIdx = idx
                                            }
                                        }
                                    }

                                    if (closestIdx != null && closestIdx in entries.indices) {
                                        val targetRect = cellBounds[closestIdx]
                                        if (targetRect != null && closestIdx != pressedIndex) {
                                            val distToCenter = (targetRect.center - currentCenter).getDistance()
                                            val mergeThresholdPx = with(density) { 36.dp.toPx() }
                                            isMergeCandidate = distToCenter < mergeThresholdPx
                                        }
                                        hoveredIndex = closestIdx
                                        isHoveringMergeTarget = isMergeCandidate
                                    }
                                }
                            } else {
                                // Pointer released
                                change.consume()
                                val targetIdx = hoveredIndex

                                if (hasMoved && targetIdx != null && targetIdx in entries.indices && targetIdx != pressedIndex) {
                                    if (isHoveringMergeTarget) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMergeFolder(pressedIndex, targetIdx)
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        onReorder(pressedIndex, targetIdx)
                                    }
                                } else if (!hasMoved) {
                                    // User held down and released in place -> open popup menu
                                    selectedItemIndexForMenu = pressedIndex
                                }
                                break
                            }
                        }

                        draggedIndex = null
                        dragDelta = Offset.Zero
                        hoveredIndex = null
                        isHoveringMergeTarget = false
                    }
                }
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val chunked = entries.chunked(4)
            chunked.forEachIndexed { rowIndex, rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems.forEachIndexed { colIndex, entry ->
                        val itemIndex = rowIndex * 4 + colIndex
                        val isBeingDragged = draggedIndex == itemIndex
                        val isHoverTarget = hoveredIndex == itemIndex && draggedIndex != null && draggedIndex != itemIndex && isHoveringMergeTarget
                        val isMenuOpen = selectedItemIndexForMenu == itemIndex

                        val itemScale by animateFloatAsState(
                            targetValue = if (isBeingDragged) 1.15f else if (isHoverTarget) 1.08f else 1.0f,
                            animationSpec = spring(),
                            label = "scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeightDp)
                                .onGloballyPositioned { coordinates ->
                                    cellBounds[itemIndex] = coordinates.boundsInParent()
                                }
                                .zIndex(if (isBeingDragged) 10f else 1f)
                                .offset {
                                    if (isBeingDragged) {
                                        IntOffset(dragDelta.x.roundToInt(), dragDelta.y.roundToInt())
                                    } else {
                                        IntOffset.Zero
                                    }
                                }
                                .scale(itemScale)
                                .alpha(if (isBeingDragged) 0.88f else 1.0f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isHoverTarget) {
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.onBackground,
                                                RoundedCornerShape(14.dp)
                                            )
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                PinnedCell(
                                    entry = entry,
                                    showLabels = showLabels,
                                    iconScale = iconScale
                                )
                            }

                            CymaticDropdownMenu(
                                expanded = isMenuOpen,
                                onDismissRequest = { selectedItemIndexForMenu = null }
                            ) {
                                when (entry) {
                                    is LauncherAppsViewModel.PinnedGridEntry.App -> {
                                        CymaticDropdownMenuItem(
                                            text = "Unpin from Home",
                                            leadingIcon = R.drawable.ic_pixel_trash,
                                            onClick = {
                                                selectedItemIndexForMenu = null
                                                onUnpinItem(itemIndex)
                                            }
                                        )
                                        CymaticDropdownMenuItem(
                                            text = "App Info",
                                            leadingIcon = R.drawable.ic_pixel_info,
                                            onClick = {
                                                selectedItemIndexForMenu = null
                                                val infoIntent =
                                                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = Uri.fromParts("package", entry.app.packageName, null)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                context.startActivity(infoIntent)
                                            }
                                        )
                                    }

                                    is LauncherAppsViewModel.PinnedGridEntry.Folder -> {
                                        CymaticDropdownMenuItem(
                                            text = "Rename Folder",
                                            leadingIcon = R.drawable.ic_pixel_edit,
                                            onClick = {
                                                selectedItemIndexForMenu = null
                                                onRenameFolderRequest(entry)
                                            }
                                        )
                                        CymaticDropdownMenuItem(
                                            text = "Unpin Folder",
                                            leadingIcon = R.drawable.ic_pixel_trash,
                                            onClick = {
                                                selectedItemIndexForMenu = null
                                                onUnpinItem(itemIndex)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (rowItems.size < 4) {
                        repeat(4 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedCell(
    entry: LauncherAppsViewModel.PinnedGridEntry,
    showLabels: Boolean,
    iconScale: Float = 1.0f
) {
    val iconSize = (52 * iconScale).dp
    val fallbackIconSize = (28 * iconScale).dp
    val miniIconSize = (18 * iconScale).dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        when (entry) {
            is LauncherAppsViewModel.PinnedGridEntry.App -> {
                if (entry.app.icon != null) {
                    Image(
                        bitmap = entry.app.icon.asImageBitmap(),
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(iconSize)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pixel_apps),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(fallbackIconSize)
                        )
                    }
                }

                if (showLabels) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.app.label,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            is LauncherAppsViewModel.PinnedGridEntry.Folder -> {
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(3.dp)
                    ) {
                        entry.apps.take(4).chunked(2).forEach { rowApps ->
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowApps.forEach { app ->
                                    Box(
                                        modifier = Modifier
                                            .padding(1.dp)
                                            .size(miniIconSize),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (app.icon != null) {
                                            Image(
                                                bitmap = app.icon.asImageBitmap(),
                                                contentDescription = null,
                                                filterQuality = FilterQuality.None,
                                                modifier = Modifier.size(miniIconSize)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(miniIconSize)
                                                    .background(MaterialTheme.colorScheme.surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_pixel_apps),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(fallbackIconSize / 2)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showLabels) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
