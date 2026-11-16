package com.pxr.cymatic.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.components.list.NavigationItem

@Composable
fun LibraryContextMenu(
    title: String,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onCreatePlaylist: (() -> Unit)? = null,
    onKeepOffline: (() -> Unit)? = null,
    showLibraryFilter: Boolean = false,
) {
    val wheel = checkNotNull(LocalWheelNavigation.current)
    val owner = LocalLifecycleOwner.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(wheel, owner) {
        onDispose {
            if (wheel.activeOwner != null && wheel.activeOwner !== owner) {
                currentDismiss()
            }
        }
    }
    var choosingFilter by remember { mutableStateOf(false) }
    val filter by LibraryFilterStore.filter.collectAsState()
    if (choosingFilter) {
        LibraryFilterMenu({ choosingFilter = false }, onSelected = onDismiss)
        return
    }
    val items = buildList {
        if (onPlay != null) {
            add(
                NavigationItem(
                    "Play",
                    onClick = {
                        onPlay()
                        wheel.onPlaybackRequested()
                    },
                )
            )
        }
        if (onPlayNext != null) {
            add(
                NavigationItem(
                    "Play Next",
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    },
                )
            )
        }
        if (onAddToQueue != null) {
            add(
                NavigationItem(
                    "Add to Queue",
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    },
                )
            )
        }
        if (onKeepOffline != null)
            add(
                NavigationItem(
                    "Keep offline",
                    onClick = {
                        onKeepOffline()
                        onDismiss()
                    },
                )
            )
        if (showLibraryFilter)
            add(
                NavigationItem(
                    "Show · ${filter.label}",
                    onClick = { choosingFilter = true },
                )
            )
        if (onCreatePlaylist != null) {
            add(
                NavigationItem(
                    "New playlist",
                    onClick = {
                        onCreatePlaylist()
                        onDismiss()
                    },
                )
            )
        }
        if (onSearch != null) {
            add(
                NavigationItem(
                    "Search",
                    onClick = {
                        onSearch()
                        onDismiss()
                    },
                )
            )
        }
    }
    WheelContextMenu(
        title = title,
        items = items,
        onDismiss = onDismiss,
    )
}
