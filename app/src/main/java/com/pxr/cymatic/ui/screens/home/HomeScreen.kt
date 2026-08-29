package com.pxr.cymatic.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.R
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.data.launcher.SystemShadeHelper
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.theme.PixelFontFamily
import kotlinx.coroutines.withTimeoutOrNull
import com.pxr.cymatic.ui.components.common.verticalFadingEdge
import com.pxr.cymatic.ui.components.launcher.FolderDialog
import com.pxr.cymatic.ui.components.launcher.IdleGreeting
import com.pxr.cymatic.ui.components.launcher.InteractivePinnedGrid
import com.pxr.cymatic.ui.components.launcher.OverviewBottomActions
import com.pxr.cymatic.ui.components.launcher.OverviewMenuLevel
import com.pxr.cymatic.ui.components.launcher.WallpaperBackdrop
import com.pxr.cymatic.ui.components.launcher.rememberWallpaperBitmap
import com.pxr.cymatic.ui.components.player.HomePlayer
import com.pxr.cymatic.ui.components.player.MaximizedPlayer
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.rememberPlaybackState
import kotlinx.coroutines.launch

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
    val vPagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    val wallpaperDarkenOpacity by LauncherStore.wallpaperDarkenOpacityFlow.collectAsState(initial = 0.0f)
    val wallpaperGradientEnabled by LauncherStore.wallpaperGradientEnabledFlow.collectAsState(initial = false)
    val wallpaperBlurRadius by LauncherStore.wallpaperBlurRadiusFlow.collectAsState(initial = 0.0f)
    val useSongWallpaper by LauncherStore.useSongWallpaperFlow.collectAsState(initial = false)
    val wallpaperBitmap = rememberWallpaperBitmap()

    val currentArtworkUri = remember(playbackState.currentMediaId, mediaController) {
        mediaController?.currentMediaItem?.mediaMetadata?.artworkUri
    }
    val isSongWallpaperActive = useSongWallpaper && currentArtworkUri != null

    LaunchedEffect(hasPlayback) {
        if (!hasPlayback && hPagerState.currentPage == 1) {
            hPagerState.animateScrollToPage(0)
        }
    }

    val vOffset = (vPagerState.currentPage + vPagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
    val hOffset = (hPagerState.currentPage + hPagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
    val nonHomeOffset = maxOf(vOffset, hOffset)

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackdrop(
            isSongWallpaperActive = isSongWallpaperActive,
            currentArtworkUri = currentArtworkUri,
            nonHomeOffset = nonHomeOffset,
            wallpaperBlurRadius = wallpaperBlurRadius,
            wallpaperDarkenOpacity = wallpaperDarkenOpacity,
            wallpaperGradientEnabled = wallpaperGradientEnabled,
            wallpaperBitmap = wallpaperBitmap
        )

        HorizontalPager(
            state = hPagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { hPage ->
            when (hPage) {
                0 -> HomeVerticalPager(
                    vPagerState = vPagerState,
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
}

@Composable
private fun HomeVerticalPager(
    vPagerState: PagerState,
    onPlayerClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    BackHandler(enabled = vPagerState.currentPage == 1) {
        scope.launch { vPagerState.animateScrollToPage(0) }
    }

    VerticalPager(
        state = vPagerState,
        modifier = Modifier
            .fillMaxSize()
            .verticalFadingEdge(top = 48.dp, bottom = 48.dp),
        beyondViewportPageCount = 1
    ) { vPage ->
        when (vPage) {
            0 -> HomeContent(onPlayerClick = onPlayerClick)
            1 -> AllAppsScreen(vPagerState = vPagerState)
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
    val showFolderLabels by LauncherStore.showFolderLabelsFlow.collectAsState(initial = true)
    val appIconScale by LauncherStore.appIconScaleFlow.collectAsState(initial = 1.0f)
    val allApps by appsViewModel.allApps.collectAsState()

    val showClock by LauncherStore.showClockFlow.collectAsState(initial = true)
    val showDay by LauncherStore.showDayFlow.collectAsState(initial = true)
    val showDate by LauncherStore.showDateFlow.collectAsState(initial = true)

    val wallpaperDarkenOpacity by LauncherStore.wallpaperDarkenOpacityFlow.collectAsState(initial = 0.0f)
    val wallpaperGradientEnabled by LauncherStore.wallpaperGradientEnabledFlow.collectAsState(initial = false)
    val wallpaperBlurRadius by LauncherStore.wallpaperBlurRadiusFlow.collectAsState(initial = 0.0f)
    val useSongWallpaper by LauncherStore.useSongWallpaperFlow.collectAsState(initial = false)

    var isOverviewMode by remember { mutableStateOf(false) }
    var overviewLevel by remember { mutableStateOf(OverviewMenuLevel.ROOT) }
    var activeFolderForDialog by remember { mutableStateOf<LauncherAppsViewModel.PinnedGridEntry.Folder?>(null) }

    val libraryEntries = remember {
        listOf(
            LibraryEntry("TRACKS", R.drawable.ic_pixel_songs, Screen.AllSongs.createRoute()),
            LibraryEntry("ALBUMS", R.drawable.ic_pixel_albums, Screen.Albums.route),
            LibraryEntry("ARTISTS", R.drawable.ic_pixel_artists, Screen.Artists.route),
            LibraryEntry("PLAYLISTS", R.drawable.ic_pixel_playlists, Screen.Playlists.route)
        )
    }

    BackHandler(enabled = isOverviewMode) {
        if (overviewLevel != OverviewMenuLevel.ROOT) {
            overviewLevel = OverviewMenuLevel.ROOT
        } else {
            isOverviewMode = false
        }
    }

    val contentScale by animateFloatAsState(
        targetValue = if (isOverviewMode) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "overview_scale"
    )

    val contentOffsetY by animateDpAsState(
        targetValue = if (isOverviewMode) (-40).dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "overview_offset_y"
    )

    val triggerOverview = {
        if (!isOverviewMode) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            isOverviewMode = true
            overviewLevel = OverviewMenuLevel.ROOT
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isOverviewMode) {
                    if (isOverviewMode) return@pointerInput
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        val startX = down.position.x
                        var totalY = 0f
                        var isDraggingDown = false
                        var triggered = false

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val dragY = change.position.y - change.previousPosition.y
                            val dragX = change.position.x - change.previousPosition.x

                            if (!isDraggingDown) {
                                if (dragY > 0 && Math.abs(dragY) > Math.abs(dragX)) {
                                    totalY += dragY
                                    if (totalY > touchSlop) {
                                        isDraggingDown = true
                                        change.consume()
                                    }
                                } else if (dragY < -touchSlop || Math.abs(dragX) > touchSlop) {
                                    break
                                }
                            } else {
                                change.consume()
                                totalY += dragY
                                if (!triggered && totalY > touchSlop) {
                                    triggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (startX < size.width / 2f) {
                                        SystemShadeHelper.expandNotifications(context)
                                    } else {
                                        SystemShadeHelper.expandQuickSettings(context)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = contentOffsetY)
                    .scale(contentScale)
                    .padding(top = innerPadding.calculateTopPadding())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(isOverviewMode) {
                            detectTapGestures(onLongPress = { triggerOverview() })
                        }
                )

                if (showClock || showDay || showDate) {
                    IdleGreeting(
                        showClock = showClock,
                        showDay = showDay,
                        showDate = showDate,
                        onLongPress = triggerOverview
                    )
                }

                if (hasPlayback) {
                    HomePlayer(
                        onMaximize = onPlayerClick,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(isOverviewMode) {
                            detectTapGestures(onLongPress = { triggerOverview() })
                        }
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
                                        if (!isOverviewMode) {
                                            navController.navigate(entry.route)
                                        }
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
                        .pointerInput(isOverviewMode) {
                            detectTapGestures(onLongPress = { triggerOverview() })
                        }
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
                        onEmptySpaceLongPress = triggerOverview,
                        onAppClick = { app, sourceBounds ->
                            if (!isOverviewMode) {
                                LauncherAppsLoader.launch(context, app.packageName, sourceBounds)
                            }
                        },
                        onFolderClick = { folder ->
                            if (!isOverviewMode) {
                                activeFolderForDialog = folder
                            }
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
                        },
                        iconScale = appIconScale
                    )
                }
            }

            if (isOverviewMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                isOverviewMode = false
                                overviewLevel = OverviewMenuLevel.ROOT
                            }
                        )
                )
            }

            OverviewBottomActions(
                isOverviewMode = isOverviewMode,
                overviewLevel = overviewLevel,
                onLevelChange = { overviewLevel = it },
                onCloseOverview = {
                    isOverviewMode = false
                    overviewLevel = OverviewMenuLevel.ROOT
                },
                context = context,
                haptic = haptic,
                navController = navController,
                useSongWallpaper = useSongWallpaper,
                wallpaperDarkenOpacity = wallpaperDarkenOpacity,
                wallpaperGradientEnabled = wallpaperGradientEnabled,
                wallpaperBlurRadius = wallpaperBlurRadius,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            activeFolderForDialog?.let { folder ->
                val currentFolder = homeApps.entries
                    .filterIsInstance<LauncherAppsViewModel.PinnedGridEntry.Folder>()
                    .firstOrNull { it.id == folder.id } ?: folder

                val folderIndex = homeApps.entries.indexOfFirst {
                    it is LauncherAppsViewModel.PinnedGridEntry.Folder && it.id == folder.id
                }
                val totalPinnedRows = if (homeApps.entries.isNotEmpty()) (homeApps.entries.size + 3) / 4 else 1
                val folderRowIndex = if (folderIndex >= 0) folderIndex / 4 else 0
                val rowsFromBottom = (totalPinnedRows - 1 - folderRowIndex).coerceAtLeast(0)

                FolderDialog(
                    folder = currentFolder,
                    allApps = allApps,
                    showFolderLabels = showFolderLabels,
                    showPinnedLabels = showPinnedLabels,
                    appIconScale = appIconScale,
                    rowsFromBottom = rowsFromBottom,
                    onDismiss = { activeFolderForDialog = null },
                    onAppClick = { app, sourceBounds ->
                        LauncherAppsLoader.launch(context, app.packageName, sourceBounds)
                        activeFolderForDialog = null
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
        }
    }
}

@Composable
private fun MusicCell(
    entry: LibraryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(52.dp)
            .border(0.dp, Color.Transparent)
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
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
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

