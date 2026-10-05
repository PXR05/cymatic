package com.pxr.cymatic.ui.components.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
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
    val checked: Boolean? = null,
    val showIndicator: Boolean = true,
    val onLongClick: () -> Unit = {},
    val onClick: () -> Unit = {},
)

@Composable
fun NavigationList(
    items: List<NavigationItem>,
    modifier: Modifier = Modifier,
    separator: (@Composable () -> Unit)? = null,
) {
    val wheel = LocalWheelNavigation.current
    val listState = rememberLazyListState()
    val selectedIndex =
        rememberWheelSelection(
            items.size,
            listState,
            onSelect = { if (items[it].enabled) items[it].onClick() },
            selectableIndices = items.indices.filter { items[it].enabled },
            onContext = { if (items[it].enabled) items[it].onLongClick() },
        )
    LazyColumn(state = listState, modifier = modifier) {
        items(
            count = items.size,
            key = { i -> items[i].key ?: items[i].label },
        ) { index ->
            val item = items[index]
            ListItem(
                label = item.label,
                subLabel = item.subLabel,
                subLabelMaxLines = 2,
                icon = item.icon,
                trailing =
                    if (item.enabled && item.showIndicator && item.checked == null) ">" else null,
                trailingContent =
                    if (item.enabled && item.showIndicator && item.checked != null) {
                        { color ->
                            Canvas(Modifier.size(20.dp)) {
                                val pixels = if (item.checked) {
                                    listOf(
                                        "00111100",
                                        "01111110",
                                        "11111111",
                                        "11111111",
                                        "11111111",
                                        "11111111",
                                        "01111110",
                                        "00111100",
                                    )
                                } else {
                                    listOf(
                                        "00111100",
                                        "01000010",
                                        "10000001",
                                        "10000001",
                                        "10000001",
                                        "10000001",
                                        "01000010",
                                        "00111100",
                                    )
                                }

                                val pixel = 1.5.dp.toPx().toInt().coerceAtLeast(1).toFloat()
                                val left = (size.width - 8 * pixel) / 2
                                val top = (size.height - 8 * pixel) / 2

                                pixels.forEachIndexed { y, row ->
                                    row.forEachIndexed { x, value ->
                                        if (value == '1') {
                                            drawRect(
                                                color = color,
                                                topLeft = Offset(left + x * pixel, top + y * pixel),
                                                size = Size(pixel, pixel),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else null,
                trailingStyle =
                    TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                    ),
                onClick = { if (item.enabled) item.onClick() },
                onLongClick = { if (item.enabled) item.onLongClick() },
                isActive = item.enabled && index == selectedIndex,
                enabled = item.enabled,
                modifier =
                    Modifier.semantics {
                            if (item.checked != null) {
                                role = Role.Checkbox
                                toggleableState =
                                    if (item.checked) ToggleableState.On else ToggleableState.Off
                            }
                        }
                        .heightIn(
                            min =
                                if (wheel != null) {
                                    if (item.subLabel != null) 64.dp else 52.dp
                                } else if (item.subLabel != null) 76.dp else 64.dp
                        ),
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
    val items =
        listOf(
            NavigationItem("All Songs") {},
            NavigationItem("恋を唄う") {},
            NavigationItem("Artists", subLabel = "artist") {},
            NavigationItem("ささやくように", subLabel = "恋を唄う") {},
            NavigationItem("Label", subLabel = "恋を唄う") {},
            NavigationItem("ささやくように", subLabel = "Sub Label") {},
        )
    NavigationList(
        items = items,
        separator = {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface)
            )
        },
    )
}
