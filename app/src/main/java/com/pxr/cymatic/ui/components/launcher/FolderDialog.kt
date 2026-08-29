package com.pxr.cymatic.ui.components.launcher

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.pxr.cymatic.R
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenu
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenuItem
import com.pxr.cymatic.ui.screens.home.LauncherAppsViewModel
import com.pxr.cymatic.ui.theme.PixelFontFamily
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FolderDialog(
    folder: LauncherAppsViewModel.PinnedGridEntry.Folder,
    allApps: List<LauncherAppsLoader.LauncherApp>,
    showFolderLabels: Boolean = true,
    showPinnedLabels: Boolean = false,
    appIconScale: Float = 1.0f,
    rowsFromBottom: Int = 0,
    onDismiss: () -> Unit,
    onAppClick: (LauncherAppsLoader.LauncherApp, android.graphics.Rect?) -> Unit,
    onRenameFolder: (String) -> Unit,
    onRemoveApp: (String) -> Unit,
    onAddApp: (String) -> Unit,
    onReorderApp: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onDeleteFolder: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val window = LocalWindowInfo.current

    val columnCount = if (folder.apps.size <= 4) 2 else 3
    val targetWidthRatio = if (columnCount == 2) 0.68f else 0.88f

    val dialogWidth = with(window) { (containerSize.width * targetWidthRatio) }
    val dialogWidthDp = with(density) { dialogWidth.toDp() }
    val dialogHeight = with(window) { (containerSize.height * 0.8f) }
    val dialogHeightDp = with(density) { dialogHeight.toDp() }

    var folderName by rememberSaveable(folder.id) { mutableStateOf(folder.name) }
    var selectedPackageForAction by rememberSaveable { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var isHeaderMenuOpen by remember { mutableStateOf(false) }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    val cellBounds = remember { mutableStateMapOf<Int, Rect>() }
    val cellCoordinates = remember { mutableStateMapOf<Int, LayoutCoordinates>() }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }

    val dismissWithAnimation: () -> Unit = {
        if (folderName.trim() != folder.name) {
            onRenameFolder(folderName.trim().ifBlank { "Folder" })
        }
        visibleState.targetState = false
    }

    BackHandler(enabled = visibleState.targetState) {
        dismissWithAnimation()
    }

    LaunchedEffect(visibleState.currentState, visibleState.targetState) {
        if (!visibleState.currentState && !visibleState.targetState) {
            onDismiss()
        }
    }

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

    val pinnedCellHeightDp = if (showPinnedLabels) (92 * appIconScale).dp else (72 * appIconScale).dp
    val rowStepDp = pinnedCellHeightDp + 20.dp
    val baseBottomOffset = with(density) { (window.containerSize.height * 0.08f).toDp() }
    val adaptiveBottomOffsetDp = baseBottomOffset + (rowStepDp * rowsFromBottom)

    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissWithAnimation() }
                )
                .padding(bottom = adaptiveBottomOffsetDp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val dialogShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)) +
                                slideInVertically(initialOffsetY = { 50 }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)),
                        exit = scaleOut(targetScale = 0.90f, animationSpec = tween(160)) +
                               slideOutVertically(targetOffsetY = { 40 }, animationSpec = tween(160))
                    )
                    .width(dialogWidthDp)
                    .heightIn(max = dialogHeightDp)
                    .clip(dialogShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), dialogShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(20.dp)
            ) {
                // Header: Folder title with PixelFontFamily + ellipsis menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (folderName.isEmpty()) {
                        Text(
                            text = "FOLDER",
                            fontFamily = PixelFontFamily,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                    }
                    BasicTextField(
                        value = folderName,
                        onValueChange = {
                            folderName = it
                            onRenameFolder(it.trim().ifBlank { "Folder" })
                        },
                        textStyle = TextStyle(
                            fontFamily = PixelFontFamily,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                onClick = { isHeaderMenuOpen = true },
                                indication = null,
                                interactionSource = null
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pixel_more),
                            contentDescription = "Folder Options",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }

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
                                dismissWithAnimation()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Adaptive Grid: 2x2 or 3xN with Hold & Drag reordering
            val chunkedRows = folder.apps.indices.chunked(columnCount)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { rootCoordinates = it }
                    .pointerInput(folder.apps) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downOffset = down.position

                            val rootCoords = rootCoordinates
                            val pressedIndex = cellCoordinates.entries.firstOrNull { (key, coords) ->
                                if (coords.isAttached && rootCoords != null && rootCoords.isAttached) {
                                    val localOffset = coords.localPositionOf(rootCoords, downOffset)
                                    localOffset.x >= 0f && localOffset.x <= coords.size.width.toFloat() &&
                                        localOffset.y >= 0f && localOffset.y <= coords.size.height.toFloat()
                                } else {
                                    val rect = cellBounds[key]
                                    rect != null && rect.contains(downOffset)
                                }
                            }?.key

                            if (pressedIndex == null || pressedIndex !in folder.apps.indices) {
                                return@awaitEachGesture
                            }

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
                                        gestureAction = "SWIPE"
                                        break
                                    }
                                }
                            }

                            if (gestureAction == "TAP") {
                                val app = folder.apps[pressedIndex]
                                val coords = cellCoordinates[pressedIndex]
                                val sourceBounds = if (coords != null && coords.isAttached) {
                                    val pos = coords.positionInWindow()
                                    val sz = coords.size
                                    android.graphics.Rect(
                                        pos.x.toInt(),
                                        pos.y.toInt(),
                                        (pos.x + sz.width).toInt(),
                                        (pos.y + sz.height).toInt()
                                    )
                                } else null
                                onAppClick(app, sourceBounds)
                                dismissWithAnimation()
                            } else if (gestureAction == null && totalDrag.getDistance() <= touchSlop) {
                                // Long press -> lift app for dragging
                                down.consume()
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
                                        if (dragDelta.getDistance() > touchSlop) {
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
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    chunkedRows.forEach { rowIndices ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowIndices.forEach { slotIdx ->
                                val app = folder.apps[slotIdx]
                                val isBeingDragged = draggedIndex == slotIdx
                                val isMenuOpen = selectedPackageForAction == app.packageName

                                val itemScale by animateFloatAsState(
                                    targetValue = if (isBeingDragged) 1.15f else 1.0f,
                                    animationSpec = spring(),
                                    label = "folder_scale"
                                )

                                val iconSize = (52 * appIconScale).dp
                                val cellHeight = if (showFolderLabels) (92 * appIconScale).dp else (72 * appIconScale).dp
                                val fallbackIconSize = (28 * appIconScale).dp

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(cellHeight)
                                        .onGloballyPositioned { coords ->
                                            cellCoordinates[slotIdx] = coords
                                            val root = rootCoordinates
                                            if (root != null && root.isAttached && coords.isAttached) {
                                                val topLeft = root.localPositionOf(coords, Offset.Zero)
                                                cellBounds[slotIdx] = Rect(
                                                    offset = topLeft,
                                                    size = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                                                )
                                            }
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
                                                filterQuality = FilterQuality.High,
                                                modifier = Modifier.size(iconSize)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(iconSize)
                                                    .background(Color.Transparent),
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

                                        if (showFolderLabels) {
                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = app.label,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
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
                            }

                            if (rowIndices.size < columnCount) {
                                repeat(columnCount - rowIndices.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
