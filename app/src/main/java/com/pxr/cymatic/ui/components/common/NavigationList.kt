package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavigationItem(
    val label: String,
    val subLabel: String? = null,
    val onClick: () -> Unit
)

@Composable
fun NavigationList(
    items: List<NavigationItem>,
    modifier: Modifier = Modifier,
    separator: (@Composable () -> Unit)? = null,
) {
    LazyColumn(modifier = modifier) {
        items(items.size) { index ->
            val item = items[index]
            ListItem(
                label = item.label,
                subLabel = item.subLabel,
                trailing = ">",
                trailingStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp
                ),
                onClick = item.onClick,
                modifier = Modifier.height(
                    if (item.subLabel != null) 76.dp else 64.dp
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



