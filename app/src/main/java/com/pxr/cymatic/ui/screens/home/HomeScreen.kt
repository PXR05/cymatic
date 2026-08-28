package com.pxr.cymatic.ui.screens.home

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pxr.cymatic.R
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.launcher.FolderDialog
import com.pxr.cymatic.ui.components.launcher.InteractivePinnedGrid
import com.pxr.cymatic.ui.components.player.HomePlayer
import com.pxr.cymatic.ui.components.player.MaximizedPlayer
import com.pxr.cymatic.ui.components.primitives.CymaticSlider
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.state.rememberPlaybackState
import com.pxr.cymatic.ui.theme.PixelFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
private fun rememberWallpaperBitmap(): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun loadBitmap() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val drawable = wallpaperManager.drawable ?: wallpaperManager.fastDrawable
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                bitmap = drawable.bitmap
            } else if (drawable != null) {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap = bmp
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadBitmap()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        loadBitmap()
    }

    return bitmap
}

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

    // Offset calculations:
    // vOffset: 0f on Home, 1f on AllApps
    // hOffset: 0f on Home, 1f on MaximizedPlayer
    val vOffset = (vPagerState.currentPage + vPagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
    val hOffset = (hPagerState.currentPage + hPagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
    val nonHomeOffset = maxOf(vOffset, hOffset)

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSongWallpaperActive) {
            // 1. Base Ambient Extended Backdrop (Deep 70dp Blur)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentArtworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.50f
                        scaleY = 1.50f
                    }
                    .blur(70.dp)
            )

            // 2. Diffuse Bloom Layer (28dp Blur for rich, non-grainy color depth)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentArtworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.20f
                        scaleY = 1.20f
                        alpha = 0.55f
                    }
                    .blur(28.dp)
            )

            // 3. Hero Cover Art at Top with Bottom-Only Seamless Melting Mask (Fades to 0 in Apps List & Maximized Player)
            val heroAlpha = (1f - nonHomeOffset * 1.5f).coerceIn(0f, 1f)
            if (heroAlpha > 0.001f) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentArtworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.58f)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            alpha = heroAlpha
                        }
                        .drawWithContent {
                            drawContent()
                            // Smooth vertical falloff on bottom edge only
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.00f to Color.Black,
                                    0.35f to Color.Black,
                                    0.50f to Color.Black.copy(alpha = 0.95f),
                                    0.65f to Color.Black.copy(alpha = 0.78f),
                                    0.78f to Color.Black.copy(alpha = 0.50f),
                                    0.88f to Color.Black.copy(alpha = 0.22f),
                                    0.96f to Color.Black.copy(alpha = 0.06f),
                                    1.00f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            }

            // 4. Pre-applied Balanced Readability Darkening Gradient (Edge to Edge)
            // On Home: 0.05f top -> 0.65f bottom
            // On Apps list & MaximizedPlayer: 0.68f top -> 0.74f bottom
            val homeTopAlpha = 0.05f
            val nonHomeTopAlpha = 0.68f
            val topAlpha = homeTopAlpha + (nonHomeTopAlpha - homeTopAlpha) * nonHomeOffset
            val homeBottomAlpha = 0.65f
            val nonHomeBottomAlpha = 0.74f
            val bottomAlpha = homeBottomAlpha + (nonHomeBottomAlpha - homeBottomAlpha) * nonHomeOffset

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = topAlpha),
                            0.40f to Color.Black.copy(alpha = topAlpha + (bottomAlpha - topAlpha) * 0.35f),
                            0.75f to Color.Black.copy(alpha = bottomAlpha * 0.90f),
                            1.00f to Color.Black.copy(alpha = bottomAlpha)
                        )
                    )
            )
        } else {
            // Standard system wallpaper blur layer fallback
            if (wallpaperBlurRadius > 0.001f && wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(wallpaperBlurRadius.dp)
                )
            }

            // Fixed Wallpaper Darken / Gradient Overlay for Home, All Apps, and Maximized Player
            if (wallpaperDarkenOpacity > 0.001f) {
                val topAlpha = if (wallpaperGradientEnabled) {
                    wallpaperDarkenOpacity * nonHomeOffset
                } else {
                    wallpaperDarkenOpacity
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = topAlpha),
                                0.35f to Color.Black.copy(alpha = topAlpha),
                                1.0f to Color.Black.copy(alpha = wallpaperDarkenOpacity)
                            )
                        )
                )
            }
        }

        // Horizontal Pager: Page 0 = Home & Apps Drawer, Page 1 = Maximized Player
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
    vPagerState: androidx.compose.foundation.pager.PagerState,
    onPlayerClick: () -> Unit
) {
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
    val showFolderLabels by LauncherStore.showFolderLabelsFlow.collectAsState(initial = true)
    val showClock by LauncherStore.showClockFlow.collectAsState(initial = true)
    val showDay by LauncherStore.showDayFlow.collectAsState(initial = true)
    val showDate by LauncherStore.showDateFlow.collectAsState(initial = true)
    val appIconScale by LauncherStore.appIconScaleFlow.collectAsState(initial = 1.0f)
    val wallpaperDarkenOpacity by LauncherStore.wallpaperDarkenOpacityFlow.collectAsState(initial = 0.0f)
    val wallpaperGradientEnabled by LauncherStore.wallpaperGradientEnabledFlow.collectAsState(initial = false)
    val wallpaperBlurRadius by LauncherStore.wallpaperBlurRadiusFlow.collectAsState(initial = 0.0f)
    val useSongWallpaper by LauncherStore.useSongWallpaperFlow.collectAsState(initial = false)

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

    val libraryEntries = listOf(
        LibraryEntry("All Songs", R.drawable.ic_pixel_songs, Screen.AllSongs.createRoute()),
        LibraryEntry("Artists", R.drawable.ic_pixel_artists, Screen.Artists.route),
        LibraryEntry("Albums", R.drawable.ic_pixel_albums, Screen.Albums.route),
        LibraryEntry("Playlists", R.drawable.ic_pixel_playlists, Screen.Playlists.route)
    )

    var isOverviewMode by remember { mutableStateOf(false) }
    var overviewLevel by remember { mutableStateOf(OverviewMenuLevel.ROOT) }

    BackHandler(enabled = isOverviewMode) {
        when (overviewLevel) {
            OverviewMenuLevel.DARKEN_OPTION,
            OverviewMenuLevel.BLUR_OPTION -> {
                overviewLevel = OverviewMenuLevel.EFFECTS
            }
            OverviewMenuLevel.EFFECTS -> {
                overviewLevel = OverviewMenuLevel.ROOT
            }
            OverviewMenuLevel.ROOT -> {
                isOverviewMode = false
            }
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isOverviewMode) {
                    detectTapGestures(
                        onLongPress = {
                            if (!isOverviewMode) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isOverviewMode = true
                                overviewLevel = OverviewMenuLevel.ROOT
                            }
                        },
                        onTap = {
                            if (isOverviewMode) {
                                isOverviewMode = false
                                overviewLevel = OverviewMenuLevel.ROOT
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = contentOffsetY)
                    .scale(contentScale)
                    .padding(top = innerPadding.calculateTopPadding())
                    .navigationBarsPadding()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = 20.dp
                    )
                    .then(
                        if (isOverviewMode) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    isOverviewMode = false
                                    overviewLevel = OverviewMenuLevel.ROOT
                                }
                            )
                        } else Modifier
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
                    HomePlayer(
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
                                        if (!isOverviewMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
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
                            if (!isOverviewMode) {
                                LauncherAppsLoader.launch(context, app.packageName)
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

            // Overview mode bottom action buttons
            AnimatedVisibility(
                visible = isOverviewMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
            ) {
                AnimatedContent(
                    targetState = overviewLevel,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "overview_actions_nav"
                ) { level ->
                    when (level) {
                        OverviewMenuLevel.ROOT -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OverviewActionCell(
                                    label = "WALLPAPER",
                                    iconRes = R.drawable.ic_pixel_edit,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        try {
                                            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                                            context.startActivity(Intent.createChooser(intent, "Set Wallpaper"))
                                        } catch (e: Exception) {
                                            try {
                                                val displayIntent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                                                context.startActivity(displayIntent)
                                            } catch (e2: Exception) {
                                                e2.printStackTrace()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                OverviewActionCell(
                                    label = "EFFECTS",
                                    iconRes = R.drawable.ic_pixel_effects,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        overviewLevel = OverviewMenuLevel.EFFECTS
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                OverviewActionCell(
                                    label = "SETTINGS",
                                    iconRes = R.drawable.ic_pixel_settings,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        isOverviewMode = false
                                        overviewLevel = OverviewMenuLevel.ROOT
                                        navController.navigate(Screen.LauncherSettings.route)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        OverviewMenuLevel.EFFECTS -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OverviewActionCell(
                                    label = "BACK",
                                    iconRes = R.drawable.ic_pixel_arrow_left,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        overviewLevel = OverviewMenuLevel.ROOT
                                    },
                                    modifier = Modifier.weight(0.8f)
                                )

                                OverviewActionCell(
                                    label = "SONG ART",
                                    badge = if (useSongWallpaper) "ON" else "OFF",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        LauncherStore.setUseSongWallpaper(!useSongWallpaper)
                                    },
                                    modifier = Modifier.weight(1.05f)
                                )

                                OverviewActionCell(
                                    label = "DARKEN",
                                    badge = if (useSongWallpaper) "AUTO" else "${(wallpaperDarkenOpacity * 100).roundToInt()}%",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        overviewLevel = OverviewMenuLevel.DARKEN_OPTION
                                    },
                                    modifier = Modifier.weight(1.0f)
                                )

                                OverviewActionCell(
                                    label = "GRADIENT",
                                    badge = if (useSongWallpaper) "AUTO" else (if (wallpaperGradientEnabled) "ON" else "OFF"),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        if (!useSongWallpaper) {
                                            LauncherStore.setWallpaperGradientEnabled(!wallpaperGradientEnabled)
                                        }
                                    },
                                    modifier = Modifier.weight(1.05f)
                                )

                                OverviewActionCell(
                                    label = "BLUR",
                                    badge = if (useSongWallpaper) "AUTO" else "${wallpaperBlurRadius.roundToInt()}px",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        overviewLevel = OverviewMenuLevel.BLUR_OPTION
                                    },
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                        }
                        OverviewMenuLevel.DARKEN_OPTION -> {
                            val cardShape = RoundedCornerShape(12.dp)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                OverviewActionCell(
                                    label = "BACK",
                                    iconRes = R.drawable.ic_pixel_arrow_left,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        overviewLevel = OverviewMenuLevel.EFFECTS
                                    },
                                    modifier = Modifier.width(68.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(cardShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), cardShape)
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "DARKEN",
                                                fontFamily = PixelFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = "${(wallpaperDarkenOpacity * 100).roundToInt()}%",
                                                fontFamily = PixelFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        CymaticSlider(
                                            value = wallpaperDarkenOpacity,
                                            onValueChange = { LauncherStore.setWallpaperDarkenOpacity(it) },
                                            valueRange = 0.0f..1.0f
                                        )
                                    }
                                }
                            }
                        }
                        OverviewMenuLevel.BLUR_OPTION -> {
                            val cardShape = RoundedCornerShape(12.dp)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                OverviewActionCell(
                                    label = "BACK",
                                    iconRes = R.drawable.ic_pixel_arrow_left,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        overviewLevel = OverviewMenuLevel.EFFECTS
                                    },
                                    modifier = Modifier.width(68.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(cardShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), cardShape)
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "BLUR",
                                                fontFamily = PixelFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = "${wallpaperBlurRadius.roundToInt()} px",
                                                fontFamily = PixelFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        CymaticSlider(
                                            value = wallpaperBlurRadius,
                                            onValueChange = { LauncherStore.setWallpaperBlurRadius(it) },
                                            valueRange = 0.0f..30.0f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
        }
    }
}

private enum class OverviewMenuLevel {
    ROOT,
    EFFECTS,
    DARKEN_OPTION,
    BLUR_OPTION
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OverviewActionCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    badge: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cellShape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(cellShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), cellShape)
            .background(
                if (pressed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background.copy(alpha = 0.90f)
            )
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
            .padding(horizontal = 4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = PixelFontFamily,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = badge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontFamily = PixelFontFamily,
                    fontSize = 9.sp,
                    maxLines = 1
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
