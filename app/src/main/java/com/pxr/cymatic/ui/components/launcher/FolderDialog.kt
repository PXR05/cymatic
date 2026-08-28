package com.pxr.cymatic.ui.components.launcher

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pxr.cymatic.R
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenu
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenuItem
import com.pxr.cymatic.ui.screens.home.LauncherAppsViewModel
import kotlin.math.roundToInt

@Composable
fun FolderDialog(
    folder: LauncherAppsViewModel.PinnedGridEntry.Folder,
    allApps: List<LauncherAppsLoader.LauncherApp>,
    onDismiss: () -> Unit,
    onAppClick: (LauncherAppsLoader.LauncherApp) -> Unit,
    onRenameFolder: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onAddApp: (String) -> Unit,
    onReorderApp: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onDeleteFolder: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var folderName by rememberSaveable(folder.id) { mutableStateOf(folder.name) }
    var selectedPackageForAction by rememberSaveable { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var isHeaderMenuOpen by remember { mutableStateOf(false) }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    val cellBounds = remember { mutableStateMapOf<Int, Rect>() }

    if (showAppPicker) {
        AppPickerDialog(
            title = "Add to ${folder.name}",
            apps = allApps,
            alreadySelectedPackages = folder.apps.map { it.packageName },
            onDismiss = { showAppPicker = false },
            onAppSelected = { app ->
                onAddApp(app.packageName)
            }
        )
    }

    CymaticDialog(
        title = "",
        onDismissRequest = {
            if (folderName.trim() != folder.name) {
                onRenameFolder(folderName.trim().ifBlank { "Folder" })
            }
            onDismiss()
        },
        maxHeightRatio = 0.8f,
        widthRatio = 0.88f,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header: Name TextField + Ellipsis Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = folderName,
                        onValueChange = {
                            folderName = it
                            onRenameFolder(it.trim().ifBlank { "Folder" })
                        },
                        placeholder = {
                            Text(
                                text = "Folder Name",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 16.sp
                            )
                        },
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = MaterialTheme.colorScheme.onBackground,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { isHeaderMenuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pixel_more),
                            contentDescription = "Folder Options",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )

                        CymaticDropdownMenu(
                            expanded = isHeaderMenuOpen,
                            onDismissRequest = { isHeaderMenuOpen = false }
                        ) {
                            CymaticDropdownMenuItem(
                                text = "Add App",
                                leadingIcon = R.drawable.ic_pixel_plus,
                                onClick = {
                                    isHeaderMenuOpen = false
                                    showAppPicker = true
                                }
                            )
                            CymaticDropdownMenuItem(
                                text = "Delete Folder",
                                leadingIcon = R.drawable.ic_pixel_trash,
                                onClick = {
                                    isHeaderMenuOpen = false
                                    onDeleteFolder()
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Interactive Grid of Apps inside folder with Hold & Drag reordering
                val allGridSlots = folder.apps.size + 1 // apps + add button
                val chunkedRows = (0 until allGridSlots).chunked(4)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(folder.apps) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downOffset = down.position

                                val pressedIndex = cellBounds.entries.firstOrNull { (_, rect) ->
                                    rect.contains(downOffset)
                                }?.key

                                if (pressedIndex == null || pressedIndex !in folder.apps.indices) {
                                    return@awaitEachGesture
                                }

                                val longPress = awaitLongPressOrCancellation(down.id)
                                if (longPress == null) {
                                    // Tap
                                    val app = folder.apps[pressedIndex]
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    onAppClick(app)
                                    onDismiss()
                                } else {
                                    // Long press -> lift app for dragging
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggedIndex = pressedIndex
                                    dragDelta = Offset.Zero
                                    hoveredIndex = pressedIndex
                                    var hasMoved = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                        if (change.pressed) {
                                            val dragAmount = change.position - change.previousPosition
                                            dragDelta += dragAmount
                                            if (dragDelta.getDistance() > viewConfiguration.touchSlop) {
                                                hasMoved = true
                                            }
                                            change.consume()

                                            val originalBounds = cellBounds[pressedIndex]
                                            if (originalBounds != null) {
                                                val currentCenter = originalBounds.center + dragDelta

                                                var closestIdx: Int? = null
                                                var closestDist = Float.MAX_VALUE

                                                for ((idx, rect) in cellBounds) {
                                                    if (idx in folder.apps.indices) {
                                                        val dist = (rect.center - currentCenter).getDistance()
                                                        if (dist < closestDist) {
                                                            closestDist = dist
                                                            closestIdx = idx
                                                        }
                                                    }
                                                }

                                                hoveredIndex = closestIdx
                                            }
                                        } else {
                                            // Pointer released
                                            change.consume()
                                            val targetIdx = hoveredIndex

                                            if (hasMoved && targetIdx != null && targetIdx in folder.apps.indices && targetIdx != pressedIndex) {
                                                haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                                onReorderApp(pressedIndex, targetIdx)
                                            } else if (!hasMoved) {
                                                // Long-pressed without moving -> show context popup
                                                selectedPackageForAction = folder.apps[pressedIndex].packageName
                                            }
                                            break
                                        }
                                    }

                                    draggedIndex = null
                                    dragDelta = Offset.Zero
                                    hoveredIndex = null
                                }
                            }
                        }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        chunkedRows.forEach { rowSlotIndices ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowSlotIndices.forEach { slotIdx ->
                                    if (slotIdx < folder.apps.size) {
                                        val app = folder.apps[slotIdx]
                                        val isBeingDragged = draggedIndex == slotIdx
                                        val isMenuOpen = selectedPackageForAction == app.packageName

                                        val itemScale by animateFloatAsState(
                                            targetValue = if (isBeingDragged) 1.15f else 1.0f,
                                            animationSpec = spring(),
                                            label = "folder_scale"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(84.dp)
                                                .onGloballyPositioned { coords ->
                                                    cellBounds[slotIdx] = coords.boundsInParent()
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
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                if (app.icon != null) {
                                                    Image(
                                                        bitmap = app.icon.asImageBitmap(),
                                                        contentDescription = null,
                                                        filterQuality = FilterQuality.None,
                                                        modifier = Modifier.size(44.dp)
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.ic_pixel_apps),
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = app.label,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 2.dp)
                                                )
                                            }

                                            CymaticDropdownMenu(
                                                expanded = isMenuOpen,
                                                onDismissRequest = { selectedPackageForAction = null }
                                            ) {
                                                CymaticDropdownMenuItem(
                                                    text = "Remove from Folder",
                                                    leadingIcon = R.drawable.ic_pixel_trash,
                                                    onClick = {
                                                        selectedPackageForAction = null
                                                        onRemoveApp(app.packageName)
                                                    }
                                                )
                                                CymaticDropdownMenuItem(
                                                    text = "App Info",
                                                    leadingIcon = R.drawable.ic_pixel_info,
                                                    onClick = {
                                                        selectedPackageForAction = null
                                                        val infoIntent =
                                                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                data = Uri.fromParts("package", app.packageName, null)
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                        context.startActivity(infoIntent)
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        // The "Add App" slot
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(84.dp)
                                                .clickable { showAppPicker = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_pixel_plus),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onBackground,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = "Add",
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                if (rowSlotIndices.size < 4) {
                                    repeat(4 - rowSlotIndices.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        buttons = {}
    )
}
