package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.primitives.CymaticDialog
import com.pxr.cymatic.ui.components.primitives.CymaticDialogButton
import com.pxr.cymatic.ui.screens.library.LibraryFilterStore

@Composable
fun LibraryFilterMenu(onDismiss: () -> Unit, onSelected: () -> Unit = onDismiss) {
    val selected by LibraryFilterStore.filter.collectAsState()
    val items = LibraryFilter.entries.map { filter ->
        NavigationItem(filter.label, checked = filter == selected, key = filter.value, onClick = {
            LibraryFilterStore.select(filter)
            onSelected()
        })
    }
    if (LocalWheelNavigation.current != null) {
        WheelContextMenu("Show music", items, onDismiss)
    } else {
        CymaticDialog("Show music", onDismiss,
            content = { NavigationList(items) },
            buttons = { CymaticDialogButton("Close", onDismiss) })
    }
}

@Composable
fun LibraryFilterStatus(filter: LibraryFilter) {
    if (filter == LibraryFilter.ALL) return
    Text("${filter.label} · Show all", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.fillMaxWidth().clickable { LibraryFilterStore.select(LibraryFilter.ALL) }
            .padding(horizontal = 24.dp, vertical = 6.dp))
}

@Composable
fun LibraryFilteredEmptyState() {
    val filter by LibraryFilterStore.filter.collectAsState()
    EmptyState(
        title = if (filter == LibraryFilter.OFFLINE) "No offline music" else "No non-offline music",
        message = "No items match this filter.",
        iconText = "( ? )",
        actionLabel = "SHOW ALL",
        onActionClick = { LibraryFilterStore.select(LibraryFilter.ALL) },
    )
}
