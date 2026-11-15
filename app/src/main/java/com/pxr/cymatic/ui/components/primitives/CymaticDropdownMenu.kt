package com.pxr.cymatic.ui.components.primitives

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.ui.components.common.WheelSettingsOverlay
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList

private val LocalWheelMenuItems =
    staticCompositionLocalOf<SnapshotStateList<NavigationItem>?> { null }

@Composable
fun CymaticDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (expanded) {
        WheelSettingsOverlay("Options", onDismissRequest) {
            val items = remember { mutableStateListOf<NavigationItem>() }
            CompositionLocalProvider(LocalWheelMenuItems provides items) {
                Column(content = content)
            }
            NavigationList(
                items + NavigationItem("Cancel", key = "cancel", onClick = onDismissRequest)
            )
        }
    }
}

@Composable
fun CymaticDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val wheelItems = checkNotNull(LocalWheelMenuItems.current)
    val click by rememberUpdatedState(onClick)
    val icon by rememberUpdatedState(leadingIcon)
    val item =
        remember(text) {
            NavigationItem(text, icon = { icon?.invoke() }, onClick = { click() })
        }
    DisposableEffect(wheelItems, item) {
        wheelItems.add(item)
        onDispose { wheelItems.remove(item) }
    }
}

@Composable
fun CymaticDropdownMenuItem(
    text: String,
    leadingIcon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CymaticDropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}
