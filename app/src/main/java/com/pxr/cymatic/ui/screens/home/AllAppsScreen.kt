package com.pxr.cymatic.ui.screens.home

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.data.launcher.LauncherAppsLoader
import com.pxr.cymatic.ui.components.list.AppListRow
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun AllAppsScreen(
    modifier: Modifier = Modifier,
    viewModel: LauncherAppsViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val allApps by viewModel.allApps.collectAsState()
    val homeApps by viewModel.homeApps.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var selectedPackage by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    BaseScreen(
        title = "All Apps",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        isSearchActive = isSearchActive,
        onSearchActiveChange = { isSearchActive = it }
    ) {
        if (filteredApps.isEmpty()) {
            Text(
                text = if (isSearchActive && searchQuery.isNotEmpty()) "NO MATCH" else "NO APPS FOUND",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                items(
                    items = filteredApps,
                    key = { it.packageName }
                ) { app ->
                    val isManuallyPinned = !homeApps.isUsingDefaultPins &&
                        homeApps.entries.any {
                            it is LauncherAppsViewModel.PinnedGridEntry.App &&
                                it.app.packageName == app.packageName
                        }
                    AppListRow(
                        label = app.label,
                        subLabel = if (isManuallyPinned) "pinned" else null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                            LauncherAppsLoader.launch(context, app.packageName)
                        },
                        onLongClick = { selectedPackage = app.packageName }
                    ) {
                        val iconBitmap = remember(app.packageName) { app.icon?.asImageBitmap() }
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = app.label,
                                filterQuality = FilterQuality.High,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                }
            }
        }
    }

    selectedPackage?.let { packageName ->
        val app = allApps.firstOrNull { it.packageName == packageName } ?: return@let
        val isPinned = !homeApps.isUsingDefaultPins &&
            homeApps.entries.any {
                it is LauncherAppsViewModel.PinnedGridEntry.App &&
                    it.app.packageName == packageName
            }

        AppContextMenu(
            label = app.label,
            isPinned = isPinned,
            onDismiss = { selectedPackage = null },
            onTogglePin = {
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                if (isPinned) viewModel.unpin(packageName) else viewModel.pin(packageName)
                selectedPackage = null
            },
            onAppInfo = {
                val infoIntent =
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                infoIntent.data =
                    android.net.Uri.fromParts("package", packageName, null)
                infoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(infoIntent)
                selectedPackage = null
            }
        )
    }
}

@Composable
private fun AppContextMenu(
    label: String,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onAppInfo: () -> Unit
) {
    CymaticDialog(
        title = label,
        onDismissRequest = onDismiss,
        maxHeightRatio = 0.7f,
        widthRatio = 0.8f,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                ContextMenuAction(label = if (isPinned) "Unpin" else "Pin", onClick = onTogglePin)
                ContextMenuAction(label = "App Info", onClick = onAppInfo)
            }
        },
        buttons = {
            CymaticDialogButton(
                text = "Cancel",
                onClick = onDismiss,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    )
}

@Composable
private fun ContextMenuAction(
    label: String,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 24.dp)
            .clickable(onClick = onClick, indication = null, interactionSource = null)
    )
}
