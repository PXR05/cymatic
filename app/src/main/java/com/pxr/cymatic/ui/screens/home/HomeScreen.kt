package com.pxr.cymatic.ui.screens.home

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.R
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.player.FullPlayer
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.components.primitives.CymaticInputDialog
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.rememberPlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private data class LibraryEntry(
    val label: String,
    val iconRes: Int,
    val route: String
)

@Composable
fun HomeScreen() {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appsViewModel: LauncherAppsViewModel = viewModel()
    val homeApps by appsViewModel.homeApps.collectAsState()
    val allApps by appsViewModel.allApps.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appsViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mediaController = LocalMediaController.current
    val playbackState = rememberPlaybackState(mediaController)
    val hasPlayback = mediaController != null &&
        playbackState.currentMediaId != null &&
        playbackState.totalTracks > 0
    val showPinnedLabels by LauncherStore.showPinnedLabelsFlow.collectAsState(initial = false)
    val showClock by LauncherStore.showClockFlow.collectAsState(initial = true)
    val showDay by LauncherStore.showDayFlow.collectAsState(initial = true)
    val showDate by LauncherStore.showDateFlow.collectAsState(initial = true)

    val libraryEntries = listOf(
        LibraryEntry("All Songs", R.drawable.ic_pixel_songs, Screen.AllSongs.createRoute()),
        LibraryEntry("Artists", R.drawable.ic_pixel_artists, Screen.Artists.route),
        LibraryEntry("Albums", R.drawable.ic_pixel_albums, Screen.Albums.route),
        LibraryEntry("Playlists", R.drawable.ic_pixel_playlists, Screen.Playlists.route)
    )

    var openFolderName by remember { mutableStateOf<String?>(null) }
    var appPopupPackage by remember { mutableStateOf<String?>(null) }
    var folderMenuName by remember { mutableStateOf<String?>(null) }
    var renamingFolderName by remember { mutableStateOf<String?>(null) }
    var editingFolderName by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            verticalArrangement = Arrangement.Bottom,
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 24.dp
            )
        ) {
            if (showClock || showDay || showDate) {
                item(key = "hero_greeting") {
                    IdleGreeting(
                        showClock = showClock,
                        showDay = showDay,
                        showDate = showDate
                    )
                }
            }

            if (hasPlayback) {
                item(key = "hero_player") {
                    FullPlayer()
                }
            }

            item(key = "cymatic_label") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 6.dp, start = 4.dp)
                ) {
                    Text(
                        text = "CYMATIC",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Settings >",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    navController.navigate(Screen.Settings.route)
                                },
                                indication = null,
                                interactionSource = null
                            )
                            .padding(4.dp)
                    )
                }
            }

            item(key = "music_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    libraryEntries.chunked(2).forEach { rowEntries ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowEntries.forEach { entry ->
                                MusicCell(
                                    entry = entry,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        navController.navigate(entry.route)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowEntries.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item(key = "apps_label") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 6.dp, start = 4.dp)
                ) {
                    Text(
                        text = "APPS",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "all >",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    navController.navigate(Screen.AllApps.route)
                                },
                                indication = null,
                                interactionSource = null
                            )
                            .padding(4.dp)
                    )
                }
            }

            if (homeApps.entries.isNotEmpty()) {
                item(key = "pinned_grid") {
                    PinnedGrid(
                        entries = homeApps.entries,
                        showLabels = showPinnedLabels,
                        onAppClick = { app ->
                            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            LauncherAppsLoader.launch(context, app.packageName)
                        },
                        onFolderClick = { name ->
                            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            openFolderName = name
                        },
                        onAppLongPress = { app ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            appPopupPackage = app.packageName
                        },
                        onCommitMove = { from, to ->
                            appsViewModel.moveItem(from, to)
                        },
                        onCreateFolder = { draggedPkg, targetPkg ->
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            appsViewModel.createFolder(draggedPkg, targetPkg)
                        },
                        onAddToFolder = { folderName, pkg ->
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            appsViewModel.addToFolder(folderName, pkg)
                        }
                    )
                }
            }
        }
    }

    openFolderName?.let { folderName ->
        val folder = homeApps.entries
            .filterIsInstance<LauncherAppsViewModel.PinnedGridEntry.Folder>()
            .firstOrNull { it.name == folderName }

        if (folder == null) {
            openFolderName = null
        } else {
            FolderDialog(
                folder = folder,
                onDismiss = { openFolderName = null },
                onAppClick = { app ->
                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    LauncherAppsLoader.launch(context, app.packageName)
                    openFolderName = null
                },
                onRemoveApp = { app ->
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    appsViewModel.removeFromFolder(folder.name, app.packageName)
                },
                onMoreClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    folderMenuName = folder.name
                }
            )
        }
    }

    appPopupPackage?.let { pkg ->
        val app = homeApps.entries
            .filterIsInstance<LauncherAppsViewModel.PinnedGridEntry.App>()
            .firstOrNull { it.app.packageName == pkg }?.app
            ?: appsViewModel.allApps.value.firstOrNull { it.packageName == pkg }

        if (app == null) {
            appPopupPackage = null
        } else {
            AppActionsDialog(
                app = app,
                onDismiss = { appPopupPackage = null },
                onLaunchShortcut = { shortcut ->
                    haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    LauncherAppsLoader.startShortcut(context, shortcut)
                    appPopupPackage = null
                },
                onOpenAppInfo = {
                    val intent = Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    )
                    intent.data = Uri.fromParts("package", pkg, null)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    appPopupPackage = null
                },
                onUnpin = {
                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    appsViewModel.unpin(pkg)
                    appPopupPackage = null
                }
            )
        }
    }

    folderMenuName?.let { name ->
        CymaticDialog(
            title = name,
            onDismissRequest = { folderMenuName = null },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    MenuRow("Rename") {
                        renamingFolderName = name
                        folderMenuName = null
                    }
                    MenuRow("Edit Apps") {
                        editingFolderName = name
                        folderMenuName = null
                    }
                    MenuRow("Delete Folder") {
                        appsViewModel.unpinFolder(name)
                        folderMenuName = null
                    }
                }
            },
            buttons = {
                CymaticDialogButton(
                    text = "CLOSE",
                    onClick = { folderMenuName = null },
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        )
    }

    renamingFolderName?.let { name ->
        var newName by remember(name) { mutableStateOf(name) }
        CymaticInputDialog(
            title = "Rename Folder",
            hint = "Folder name",
            value = newName,
            onValueChange = { newName = it },
            onConfirm = {
                appsViewModel.renameFolder(name, newName.trim())
                renamingFolderName = null
            },
            onDismiss = { renamingFolderName = null }
        )
    }

    editingFolderName?.let { name ->
        val folder = homeApps.entries
            .filterIsInstance<LauncherAppsViewModel.PinnedGridEntry.Folder>()
            .firstOrNull { it.name == name }

        if (folder == null) {
            editingFolderName = null
        } else {
            CymaticDialog(
                title = "Edit Apps",
                onDismissRequest = { editingFolderName = null },
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        allApps.forEach { app ->
                            val inFolder = folder.apps.any { it.packageName == app.packageName }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                            if (inFolder) {
                                                appsViewModel.removeFromFolder(name, app.packageName)
                                            } else {
                                                appsViewModel.addToFolder(name, app.packageName)
                                            }
                                        },
                                        indication = null,
                                        interactionSource = null
                                    )
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                            ) {
                                AppIcon(app = app, size = 26.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = app.label,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.onBackground)
                                ) {
                                    if (inFolder) {
                                        Text(
                                            text = "x",
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                buttons = {
                    CymaticDialogButton(
                        text = "CLOSE",
                        onClick = { editingFolderName = null },
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedGrid(
    entries: List<LauncherAppsViewModel.PinnedGridEntry>,
    showLabels: Boolean,
    onAppClick: (LauncherAppsLoader.LauncherApp) -> Unit,
    onFolderClick: (String) -> Unit,
    onAppLongPress: (LauncherAppsLoader.LauncherApp) -> Unit,
    onCommitMove: (Int, Int) -> Unit,
    onCreateFolder: (String, String) -> Unit,
    onAddToFolder: (String, String) -> Unit
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { 4.dp.toPx() }
    val cellHeightPx = with(density) { (if (showLabels) 76.dp else 64.dp).toPx() }

    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragStartCenter by remember { mutableStateOf(Offset.Zero) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    var folderPendingIndex by remember { mutableStateOf<Int?>(null) }
    val cellBounds = remember { mutableStateMapOf<Int, Rect>() }
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                containerOrigin = coordinates.positionInRoot()
                containerWidthPx = coordinates.size.width
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.chunked(4).forEachIndexed { rowIndex, rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowItems.forEachIndexed { colIndex, entry ->
                        val index = rowIndex * 4 + colIndex
                        val isOrigin = dragIndex == index
                        val isHovered = hoveredIndex == index
                        val isFolderTarget = folderPendingIndex == index

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (showLabels) 76.dp else 64.dp)
                                .onGloballyPositioned { cellBounds[index] = it.boundsInRoot() }
                                .combinedClickable(
                                    onClick = {
                                        when (entry) {
                                            is LauncherAppsViewModel.PinnedGridEntry.App ->
                                                onAppClick(entry.app)

                                            is LauncherAppsViewModel.PinnedGridEntry.Folder ->
                                                onFolderClick(entry.name)
                                        }
                                    },
                                    interactionSource = null,
                                    indication = null
                                )
                                .scale(if (isFolderTarget) 0.9f else 1f)
                                .alpha(if (isOrigin) 0.3f else 1f)
                                .border(
                                    1.dp,
                                    when {
                                        isFolderTarget -> MaterialTheme.colorScheme.onBackground
                                        isHovered -> MaterialTheme.colorScheme.secondary
                                        else -> Color.Transparent
                                    }
                                )
                                .background(
                                    if (isFolderTarget) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .pointerInput(entries) {
                                    var folderCandidate: Int? = null
                                    var folderCandidateSince = 0L
                                    var folderPending = false
                                    var didMove = false

                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            dragIndex = index
                                            dragPosition = cellBounds[index]?.center ?: offset
                                            hoveredIndex = null
                                            folderCandidate = null
                                            folderPending = false
                                            folderPendingIndex = null
                                            didMove = false
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragPosition += dragAmount
                                            if (!didMove &&
                                                (dragPosition - dragStartCenter).getDistance() > 12f
                                            ) {
                                                didMove = true
                                            }

                                            val hovered = cellBounds.entries
                                                .firstOrNull { it.value.contains(dragPosition) }
                                                ?.key
                                                ?.takeIf { it != dragIndex }

                                            hoveredIndex = hovered

                                            if (hovered == null) {
                                                folderCandidate = null
                                                folderPending = false
                                                folderPendingIndex = null
                                                return@detectDragGesturesAfterLongPress
                                            }

                                            val draggedEntry =
                                                entries.getOrNull(dragIndex ?: return@detectDragGesturesAfterLongPress)
                                            val targetEntry = entries.getOrNull(hovered)
                                            val isFolderCandidate =
                                                draggedEntry is LauncherAppsViewModel.PinnedGridEntry.App &&
                                                    (targetEntry is LauncherAppsViewModel.PinnedGridEntry.App ||
                                                        targetEntry is LauncherAppsViewModel.PinnedGridEntry.Folder)

                                            if (isFolderCandidate && folderCandidate == hovered) {
                                                if (!folderPending &&
                                                    System.currentTimeMillis() - folderCandidateSince > FOLDER_HOVER_MILLIS
                                                ) {
                                                    folderPending = true
                                                    folderPendingIndex = hovered
                                                }
                                            } else {
                                                folderCandidate =
                                                    if (isFolderCandidate) hovered else null
                                                folderCandidateSince = System.currentTimeMillis()
                                                folderPending = false
                                                folderPendingIndex = null
                                            }
                                        },
                                        onDragEnd = {
                                            val from = dragIndex
                                            val draggedEntry = from?.let { entries.getOrNull(it) }
                                            if (folderPending && folderPendingIndex != null) {
                                                val targetEntry = folderPendingIndex?.let { entries.getOrNull(it) }
                                                val draggedPkg =
                                                    (draggedEntry as? LauncherAppsViewModel.PinnedGridEntry.App)
                                                        ?.app?.packageName
                                                when (targetEntry) {
                                                    is LauncherAppsViewModel.PinnedGridEntry.App -> {
                                                        if (draggedPkg != null) {
                                                            onCreateFolder(
                                                                draggedPkg,
                                                                targetEntry.app.packageName
                                                            )
                                                        }
                                                    }

                                                    is LauncherAppsViewModel.PinnedGridEntry.Folder -> {
                                                        if (draggedPkg != null) {
                                                            onAddToFolder(
                                                                targetEntry.name,
                                                                draggedPkg
                                                            )
                                                        }
                                                    }

                                                    else -> {}
                                                }
                                            } else if (!didMove) {
                                                when (val entry = entries.getOrNull(index)) {
                                                    is LauncherAppsViewModel.PinnedGridEntry.App ->
                                                        onAppLongPress(entry.app)

                                                    is LauncherAppsViewModel.PinnedGridEntry.Folder ->
                                                        onFolderClick(entry.name)

                                                    null -> {}
                                                }
                                            } else if (from != null) {
                                                val to = hoveredIndex
                                                if (to != null && from != to) {
                                                    onCommitMove(from, to)
                                                }
                                            }
                                            dragIndex = null
                                            hoveredIndex = null
                                            folderPendingIndex = null
                                            folderPending = false
                                            folderCandidate = null
                                        },
                                        onDragCancel = {
                                            dragIndex = null
                                            hoveredIndex = null
                                            folderPendingIndex = null
                                            folderPending = false
                                            folderCandidate = null
                                        }
                                    )
                                }
                        ) {
                            PinnedCellContent(
                                entry = entry,
                                showLabels = showLabels
                            )
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

        val draggedEntry = dragIndex?.let { entries.getOrNull(it) }
        if (draggedEntry != null && containerWidthPx > 0) {
            val cellWidthPx = (containerWidthPx - 3 * spacingPx) / 4
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragPosition.x - containerOrigin.x - cellWidthPx / 2).roundToInt(),
                            (dragPosition.y - containerOrigin.y - cellHeightPx / 2).roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { cellWidthPx.toDp() },
                        height = with(density) { cellHeightPx.toDp() }
                    )
                    .zIndex(2f)
                    .border(1.dp, MaterialTheme.colorScheme.onBackground)
            ) {
                PinnedCellContent(
                    entry = draggedEntry,
                    showLabels = showLabels
                )
            }
        }
    }
}

@Composable
private fun PinnedCellContent(
    entry: LauncherAppsViewModel.PinnedGridEntry,
    showLabels: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        when (entry) {
            is LauncherAppsViewModel.PinnedGridEntry.App -> {
                AppIcon(app = entry.app, size = 36.dp)
                if (showLabels) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.app.label,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            is LauncherAppsViewModel.PinnedGridEntry.Folder -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.size(36.dp)
                ) {
                    entry.apps.take(4).chunked(2).forEach { rowApps ->
                        Row {
                            rowApps.forEach { app ->
                                Box(
                                    modifier = Modifier
                                        .padding(1.dp)
                                        .size(16.dp)
                                ) {
                                    AppIcon(app = app, size = 16.dp)
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
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: LauncherAppsLoader.LauncherApp, size: androidx.compose.ui.unit.Dp) {
    val iconBitmap = remember(app.packageName) { app.icon?.asImageBitmap() }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = app.label,
            filterQuality = FilterQuality.High,
            modifier = Modifier.size(size)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .border(1.dp, MaterialTheme.colorScheme.outline)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderDialog(
    folder: LauncherAppsViewModel.PinnedGridEntry.Folder,
    onDismiss: () -> Unit,
    onAppClick: (LauncherAppsLoader.LauncherApp) -> Unit,
    onRemoveApp: (LauncherAppsLoader.LauncherApp) -> Unit,
    onMoreClick: () -> Unit
) {
    CymaticDialog(
        title = folder.name,
        onDismissRequest = onDismiss,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "...",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable(
                            onClick = onMoreClick,
                            indication = null,
                            interactionSource = null
                        )
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                folder.apps.chunked(4).forEach { rowApps ->
                    Row {
                        rowApps.forEach { app ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .combinedClickable(
                                        onClick = { onAppClick(app) },
                                        onLongClick = { onRemoveApp(app) },
                                        interactionSource = null,
                                        indication = null
                                    )
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AppIcon(app = app, size = 36.dp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = app.label,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (rowApps.size < 4) {
                            repeat(4 - rowApps.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        buttons = {
            CymaticDialogButton(
                text = "CLOSE",
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    )
}

@Composable
private fun AppActionsDialog(
    app: LauncherAppsLoader.LauncherApp,
    onDismiss: () -> Unit,
    onLaunchShortcut: (ShortcutInfo) -> Unit,
    onOpenAppInfo: () -> Unit,
    onUnpin: () -> Unit
) {
    val context = LocalContext.current
    var shortcuts by remember(app.packageName) {
        mutableStateOf<List<ShortcutInfo>?>(null)
    }

    LaunchedEffect(app.packageName) {
        shortcuts = withContext(Dispatchers.IO) {
            LauncherAppsLoader.getShortcutsForPackage(context, app.packageName)
        }
    }

    CymaticDialog(
        title = app.label,
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                shortcuts?.forEach { shortcut ->
                    MenuRow(label = shortcut.shortLabel?.toString() ?: "Shortcut") {
                        onLaunchShortcut(shortcut)
                    }
                }
                MenuRow(label = "App Info") {
                    onOpenAppInfo()
                }
                MenuRow(label = "Remove") {
                    onUnpin()
                }
            }
        },
        buttons = {
            CymaticDialogButton(
                text = "CLOSE",
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    )
}

@Composable
private fun MenuRow(
    label: String,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = null
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicCell(
    entry: LibraryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(52.dp)
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                painter = painterResource(entry.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = entry.label,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun IdleGreeting(
    showClock: Boolean,
    showDay: Boolean,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    val use24Hour by LauncherStore.use24HourFlow.collectAsState(initial = true)

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            now = LocalDateTime.now()
        }
    }
    val time = remember(now, use24Hour) {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    val weekday = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        if (showClock) {
            Text(
                text = time,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 40.sp,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (showDay) {
            Text(
                text = weekday,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 16.sp,
                letterSpacing = 6.sp
            )
        }
        if (showDate) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = date,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                letterSpacing = 6.sp
            )
        }
    }
}

private const val FOLDER_HOVER_MILLIS = 400
