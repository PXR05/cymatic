package com.pxr.cymatic.ui.components.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.components.screen.BaseScreen

internal fun wheelContextMenuItems(
    items: List<NavigationItem>,
    onDismiss: () -> Unit,
): List<NavigationItem> =
    items.mapIndexed { index, item ->
        item.copy(key = "wheel-menu:$index:${item.key ?: item.label}")
    } + NavigationItem(
        "Cancel",
        icon = pixelIcon(R.drawable.ic_pixel_close),
        key = "wheel-menu:cancel",
        onClick = onDismiss,
    )

@Composable
fun WheelContextMenu(
    title: String,
    items: List<NavigationItem>,
    onDismiss: () -> Unit,
    onMenuLongClick: (() -> Unit)? = null,
) {
    val wheel = LocalWheelNavigation.current ?: return
    val owner = LocalLifecycleOwner.current
    val currentTitle by rememberUpdatedState(title)
    val currentItems by rememberUpdatedState(items)
    val dismiss by rememberUpdatedState(onDismiss)
    val currentMenuLongClick by rememberUpdatedState(onMenuLongClick)
    val overlay =
        remember(wheel, owner) {
            WheelOverlay(
                content = {
                    BackHandler(enabled = wheel.overlay === LocalWheelOverlay.current) { dismiss() }
                    BaseScreen(
                        title = currentTitle,
                        onBackClick = { dismiss() },
                        onMenuLongClick = currentMenuLongClick,
                    ) {
                        NavigationList(wheelContextMenuItems(currentItems) { dismiss() })
                    }
                },
                onDismiss = { dismiss() },
                owner = owner,
            )
        }
    DisposableEffect(wheel, overlay) {
        wheel.showOverlay(overlay)
        onDispose {
            wheel.removeOverlay(overlay)
            if (wheel.activeOwner != null && wheel.activeOwner !== owner) {
                dismiss()
            }
        }
    }
}

@Composable
fun WheelConfirmationMenu(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WheelContextMenu(
        title,
        listOf(
            NavigationItem(
                "Confirm",
                text,
                icon = pixelIcon(R.drawable.ic_pixel_check),
                subLabelMaxLines = 8,
                onClick = onConfirm,
            )
        ),
        onDismiss,
    )
}
