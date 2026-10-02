package com.pxr.cymatic.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.ui.components.common.LocalWheelNavigation
import com.pxr.cymatic.ui.components.common.rememberWheelSelection

data class NavigationItem(
    val label: String,
    val subLabel: String? = null,
    val icon: @Composable (() -> Unit)? = null,
    val enabled: Boolean = true,
    val key: Any? = null,
    val onLongClick: () -> Unit = { },
    val onClick: () -> Unit = { },
)

@Composable
fun NavigationList(
    items: List<NavigationItem>,
    modifier: Modifier = Modifier,
    separator: (@Composable () -> Unit)? = null,
) {
    val wheel = LocalWheelNavigation.current
    val listState = rememberLazyListState()
    val selectedIndex = rememberWheelSelection(
        items.size, listState,
        onSelect = { if (items[it].enabled) items[it].onClick() },
        onContext = { if (items[it].enabled) items[it].onLongClick() }
    )
    LazyColumn(state = listState, modifier = modifier) {
        items(
            count = items.size,
            key = { i -> items[i].key ?: items[i].label }
        ) { index ->
            val item = items[index]
            ListItem(
                label = item.label,
                subLabel = item.subLabel,
                icon = item.icon,
                trailing = if (item.enabled) ">" else "",
                trailingStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp
                ),
                onClick = { if (item.enabled) item.onClick() },
                onLongClick = { if (item.enabled) item.onLongClick() },
                isActive = index == selectedIndex,
                modifier = Modifier.height(
                    if (wheel != null) {
                        if (item.subLabel != null) 64.dp else 52.dp
                    } else if (item.subLabel != null) 76.dp else 64.dp
                )
            )
            if (separator != null && index < items.size - 1) {
                separator()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NavigationListPreview() {
    val items = listOf(
        NavigationItem("All Songs") {},
        NavigationItem("恋を唄う") {},
        NavigationItem("Artists", subLabel = "artist") {},
        NavigationItem("ささやくように", subLabel = "恋を唄う") {},
        NavigationItem("Label", subLabel = "恋を唄う") {},
        NavigationItem("ささやくように", subLabel = "Sub Label") {},
    )
    NavigationList(items = items, separator = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface)
        )
    })
}
