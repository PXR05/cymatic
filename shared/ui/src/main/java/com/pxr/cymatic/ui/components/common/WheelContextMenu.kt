package com.pxr.cymatic.ui.components.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen

@Composable
fun WheelContextMenu(title: String, items: List<NavigationItem>, onDismiss: () -> Unit) {
    val wheel = LocalWheelNavigation.current ?: return
    val currentTitle by rememberUpdatedState(title)
    val currentItems by rememberUpdatedState(items)
    val dismiss by rememberUpdatedState(onDismiss)
    val overlay = remember(wheel) {
        WheelOverlay(
            content = {
                BackHandler { dismiss() }
                BaseScreen(title = currentTitle, onBackClick = { dismiss() }) {
                    NavigationList(currentItems + NavigationItem("Cancel", onClick = { dismiss() }))
                }
            },
            onDismiss = { dismiss() }
        )
    }
    DisposableEffect(wheel, overlay) {
        val previous = wheel.overlay
        wheel.overlay = overlay
        onDispose { if (wheel.overlay === overlay) wheel.overlay = previous }
    }
}
