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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.LauncherRoutes
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.data.launcher.LauncherHomePressBus
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.design.R
import com.pxr.cymatic.ui.components.common.AppActionPopup
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenu
import com.pxr.cymatic.ui.components.primitives.CymaticDropdownMenuItem
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.theme.PixelFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsScreen(
    modifier: Modifier = Modifier,
    vPagerState: androidx.compose.foundation.pager.PagerState? = null,
    viewModel: LauncherAppsViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val navController = LocalNavController.current
    val allApps by viewModel.visibleApps.collectAsState()
    val showAllAppsLabels by LauncherStore.showAllAppsLabelsFlow.collectAsState(initial = true)
    val appIconScale by LauncherStore.appIconScaleFlow.collectAsState(initial = 1.0f)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var isOptionsMenuOpen by rememberSaveable { mutableStateOf(false) }
    val isSearching = searchQuery.isNotBlank()

    BackHandler(enabled = isSearching) {
        searchQuery = ""
        selectedPackage = null
        keyboardController?.hide()
    }
    BackHandler(enabled = !isSearching && selectedPackage != null) {
        selectedPackage = null
    }

    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) {
            allApps
        } else {
            val query = searchQuery.trim()
            allApps
                .filter {
                    it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                }
                .sortedWith(
                    compareBy<LauncherAppsLoader.LauncherApp> { app ->
                        val label = app.label
                        when {
                            label.equals(query, ignoreCase = true) -> 0
                            label.startsWith(query, ignoreCase = true) -> 1
                            label.split(" ", "-", "_", ".").any { it.startsWith(query, ignoreCase = true) } -> 2
                            label.contains(query, ignoreCase = true) -> 3
                            app.packageName.startsWith(query, ignoreCase = true) -> 4
                            else -> 5
                        }
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
                )
        }
    }

    val gridState = rememberLazyGridState()

    LaunchedEffect(isSearching) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(Unit) {
        LauncherHomePressBus.events.collect {
            searchQuery = ""
            selectedPackage = null
            isOptionsMenuOpen = false
            keyboardController?.hide()
            try {
                gridState.scrollToItem(0)
            } catch (_: Exception) {
            }
        }
    }

    val nestedScrollConnection = remember(vPagerState, gridState, isSearching) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val isAtTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                if (available.y > 0 && isAtTop && !isSearching && vPagerState != null) {
                    val consumed = vPagerState.dispatchRawDelta(-available.y)
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val isAtTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                if (available.y > 0 && isAtTop && !isSearching && vPagerState != null) {
                    vPagerState.animateScrollToPage(0)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .safeDrawingPadding()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(vPagerState) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 12f && vPagerState != null) {
                                    scope.launch { vPagerState.animateScrollToPage(0) }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "NO MATCH" else "NO APPS FOUND",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = gridState,
                    reverseLayout = isSearching,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = if (isSearching) Arrangement.spacedBy(20.dp, Alignment.Bottom) else Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        val isMenuOpen = selectedPackage == app.packageName

                        val iconSize = (52 * appIconScale).dp
                        val cellHeight = if (showAllAppsLabels) (92 * appIconScale).dp else (72 * appIconScale).dp
                        var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .onGloballyPositioned { itemCoordinates = it }
                                .combinedClickable(
                                    onClick = {
                                        val coords = itemCoordinates
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
                                        LauncherAppsLoader.launch(context, app.packageName, sourceBounds)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedPackage = app.packageName
                                    },
                                    indication = null,
                                    interactionSource = null
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val iconBitmap = remember(app.packageName) { app.icon?.asImageBitmap() }
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = app.label,
                                        filterQuality = FilterQuality.High,
                                        modifier = Modifier.size(iconSize)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(iconSize)
                                            .border(1.dp, MaterialTheme.colorScheme.outline)
                                    )
                                }
                                if (showAllAppsLabels) {
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

                            val isPinned = viewModel.isPinned(app.packageName)

                            AppActionPopup(
                                expanded = isMenuOpen,
                                onDismissRequest = { selectedPackage = null },
                                onPin = if (!isPinned) {
                                    {
                                        viewModel.pinApp(app.packageName, context)
                                    }
                                } else null,
                                onUnpin = if (isPinned) {
                                    {
                                        viewModel.unpinApp(app.packageName)
                                    }
                                } else null,
                                onAppInfo = {
                                    val infoIntent =
                                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", app.packageName, null)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    context.startActivity(infoIntent)
                                },
                                onHide = {
                                    viewModel.hideApp(app.packageName)
                                },
                                onUninstall = if (app.canUninstall) {
                                    {
                                        LauncherAppsLoader.uninstall(context, app.packageName)
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        val searchShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .clip(searchShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), searchShape)
                .padding(start = 4.dp, top = 2.dp, bottom = 2.dp, end = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "SEARCH APPS",
                            color = MaterialTheme.colorScheme.secondary,
                            fontFamily = PixelFontFamily,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.onBackground,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = PixelFontFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                )

                Box {
                    Icon(
                        painter = painterResource(R.drawable.ic_pixel_more),
                        contentDescription = "App list options",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clickable(
                                onClick = { isOptionsMenuOpen = true },
                                indication = null,
                                interactionSource = null
                            )
                            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                            .size(20.dp)
                    )
                    CymaticDropdownMenu(
                        expanded = isOptionsMenuOpen,
                        onDismissRequest = { isOptionsMenuOpen = false }
                    ) {
                        CymaticDropdownMenuItem(
                            text = "Settings",
                            leadingIcon = R.drawable.ic_pixel_settings,
                            onClick = {
                                isOptionsMenuOpen = false
                                navController.navigate(Screen.Settings.route)
                            }
                        )
                        CymaticDropdownMenuItem(
                            text = "Launcher settings",
                            leadingIcon = R.drawable.ic_pixel_apps,
                            onClick = {
                                isOptionsMenuOpen = false
                                navController.navigate(LauncherRoutes.Settings)
                            }
                        )
                        CymaticDropdownMenuItem(
                            text = "Hidden apps",
                            leadingIcon = R.drawable.ic_pixel_eye,
                            onClick = {
                                isOptionsMenuOpen = false
                                navController.navigate(LauncherRoutes.HiddenApps)
                            }
                        )
                    }
                }
            }
        }

        BackHandler(enabled = isOptionsMenuOpen) {
            isOptionsMenuOpen = false
        }
    }
}
