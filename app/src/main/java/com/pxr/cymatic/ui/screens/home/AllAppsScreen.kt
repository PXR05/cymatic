package com.pxr.cymatic.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.common.AppActionPopup
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
    val allApps by viewModel.allApps.collectAsState()
    val showAllAppsLabels by LauncherStore.showAllAppsLabelsFlow.collectAsState(initial = true)
    val appIconScale by LauncherStore.appIconScaleFlow.collectAsState(initial = 1.0f)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val isSearching = searchQuery.isNotBlank()

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
            .imePadding()
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

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cellHeight)
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                        LauncherAppsLoader.launch(context, app.packageName)
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
                                        viewModel.pinApp(app.packageName)
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(searchShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), searchShape)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "SEARCH APPS",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
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
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }
}
