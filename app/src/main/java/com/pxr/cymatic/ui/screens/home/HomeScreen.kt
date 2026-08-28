package com.pxr.cymatic.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.R
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.common.AppActionPopup
import com.pxr.cymatic.ui.components.launcher.FolderDialog
import com.pxr.cymatic.ui.components.launcher.InteractivePinnedGrid
import com.pxr.cymatic.ui.components.player.FullPlayer
import com.pxr.cymatic.ui.components.player.MaximizedPlayer
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.rememberPlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class LibraryEntry(
    val label: String,
    val iconRes: Int,
    val route: String
)

@Composable
fun HomeScreen() {
    val mediaController = LocalMediaController.current
    val playbackState = rememberPlaybackState(mediaController)
    val hasPlayback = mediaController != null &&
        playbackState.currentMediaId != null &&
        playbackState.totalTracks > 0

    val hPagerState = rememberPagerState(pageCount = { if (hasPlayback) 2 else 1 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(hasPlayback) {
        if (!hasPlayback && hPagerState.currentPage == 1) {
            hPagerState.animateScrollToPage(0)
        }
    }

    HorizontalPager(
        state = hPagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { hPage ->
        when (hPage) {
            0 -> HomeVerticalPager(
                onPlayerClick = {
                    if (hasPlayback) {
                        scope.launch { hPagerState.animateScrollToPage(1) }
                    }
                }
            )

            1 -> {
                if (hasPlayback) {
                    MaximizedPlayer(
                        modifier = Modifier.fillMaxSize(),
                        isActive = hPagerState.currentPage == 1,
                        onClose = {
                            scope.launch { hPagerState.animateScrollToPage(0) }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun HomeVerticalPager(
    onPlayerClick: () -> Unit
) {
    val vPagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    BackHandler(enabled = vPagerState.currentPage == 1) {
        scope.launch { vPagerState.animateScrollToPage(0) }
    }

    VerticalPager(
        state = vPagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { vPage ->
        when (vPage) {
            0 -> HomeContent(onPlayerClick = onPlayerClick)
            1 -> AllAppsScreen()
        }
    }
}

@Composable
private fun HomeContent(
    onPlayerClick: () -> Unit
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appsViewModel: LauncherAppsViewModel = viewModel()
    val homeApps by appsViewModel.homeApps.collectAsState()
    val mediaController = LocalMediaController.current
    val playbackState = rememberPlaybackState(mediaController)
    val hasPlayback = mediaController != null &&
        playbackState.currentMediaId != null &&
        playbackState.totalTracks > 0
    val showPinnedLabels by LauncherStore.showPinnedLabelsFlow.collectAsState(initial = false)
    val showClock by LauncherStore.showClockFlow.collectAsState(initial = true)
    val showDay by LauncherStore.showDayFlow.collectAsState(initial = true)
    val showDate by LauncherStore.showDateFlow.collectAsState(initial = true)

    val allApps by appsViewModel.allApps.collectAsState()
    var activeFolderForDialog by remember { mutableStateOf<LauncherAppsViewModel.PinnedGridEntry.Folder?>(null) }

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

    activeFolderForDialog?.let { folder ->
        val currentFolder = homeApps.entries
            .filterIsInstance<LauncherAppsViewModel.PinnedGridEntry.Folder>()
            .firstOrNull { it.id == folder.id } ?: folder

        FolderDialog(
            folder = currentFolder,
            allApps = allApps,
            onDismiss = { activeFolderForDialog = null },
            onAppClick = { app ->
                LauncherAppsLoader.launch(context, app.packageName)
            },
            onRenameFolder = { newName ->
                appsViewModel.renameFolder(folder.id, newName)
            },
            onRemoveApp = { pkg ->
                appsViewModel.removeAppFromFolder(folder.id, pkg)
            },
            onAddApp = { pkg ->
                appsViewModel.addAppToFolder(folder.id, pkg)
            },
            onReorderApp = { fromIdx, toIdx ->
                appsViewModel.reorderInFolder(folder.id, fromIdx, toIdx)
            },
            onDeleteFolder = {
                appsViewModel.deleteFolder(folder.id)
            }
        )
    }

    val libraryEntries = listOf(
        LibraryEntry("All Songs", R.drawable.ic_pixel_songs, Screen.AllSongs.createRoute()),
        LibraryEntry("Artists", R.drawable.ic_pixel_artists, Screen.Artists.route),
        LibraryEntry("Albums", R.drawable.ic_pixel_albums, Screen.Albums.route),
        LibraryEntry("Playlists", R.drawable.ic_pixel_playlists, Screen.Playlists.route)
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 24.dp
                ),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (showClock || showDay || showDate) {
                IdleGreeting(
                    showClock = showClock,
                    showDay = showDay,
                    showDate = showDate
                )
            }

            if (hasPlayback) {
                FullPlayer(
                    onMaximize = onPlayerClick,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
            ) {
                Text(
                    text = "LIBRARY",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    letterSpacing = 4.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                libraryEntries.chunked(2).forEach { rowEntries ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 6.dp, start = 4.dp)
            ) {
                Text(
                    text = "PINNED",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    letterSpacing = 4.sp
                )
            }

            if (homeApps.entries.isNotEmpty()) {
                InteractivePinnedGrid(
                    entries = homeApps.entries,
                    showLabels = showPinnedLabels,
                    onAppClick = { app ->
                        LauncherAppsLoader.launch(context, app.packageName)
                    },
                    onFolderClick = { folder ->
                        activeFolderForDialog = folder
                    },
                    onReorder = { fromIdx, toIdx ->
                        appsViewModel.reorderPinned(fromIdx, toIdx)
                    },
                    onMergeFolder = { sourceIdx, targetIdx ->
                        appsViewModel.mergeIntoFolder(sourceIdx, targetIdx)
                    },
                    onUnpinItem = { idx ->
                        appsViewModel.unpinItem(idx)
                    },
                    onRenameFolderRequest = { folder ->
                        activeFolderForDialog = folder
                    }
                )
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

@Composable
private fun MusicCell(
    entry: LibraryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(52.dp)
            .border(0.dp, androidx.compose.ui.graphics.Color.Transparent)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = interactionSource
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
