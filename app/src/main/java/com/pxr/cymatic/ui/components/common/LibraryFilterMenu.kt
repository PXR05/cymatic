package com.pxr.cymatic.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.model.LibrarySourceFilter
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.data.store.RemoteSourceRegistry
import com.pxr.cymatic.ui.components.list.NavigationItem

internal fun sourceFilterLabel(
    sourceFilter: LibrarySourceFilter,
    sourceNames: Map<String, String>,
): String =
    when (sourceFilter) {
        LibrarySourceFilter.All -> "All sources"
        LibrarySourceFilter.Local -> "This device"
        is LibrarySourceFilter.Source -> sourceNames[sourceFilter.id] ?: "Remote"
    }

internal fun libraryFilterSummary(
    filter: LibraryFilter,
    sourceFilter: LibrarySourceFilter,
    sourceNames: Map<String, String>,
): String {
    val parts = buildList {
        if (filter != LibraryFilter.ALL) add(filter.label)
        if (sourceFilter != LibrarySourceFilter.All) {
            add(sourceFilterLabel(sourceFilter, sourceNames))
        }
    }
    return if (parts.isEmpty()) LibraryFilter.ALL.label else parts.joinToString(" · ")
}

@Composable
fun LibraryFilterMenu(onDismiss: () -> Unit, onSelected: () -> Unit = onDismiss) {
    val selected by LibraryFilterStore.filter.collectAsState()
    val selectedSource by LibraryFilterStore.sourceFilter.collectAsState()
    val sources by RemoteSourceRegistry.sourcesFlow.collectAsState(
        initial = RemoteSourceRegistry.currentSources
    )
    val enabledSources = remember(sources) { sources.filter { it.enabled } }

    val items = buildList {
        LibraryFilter.entries.forEach { filter ->
            add(
                NavigationItem(
                    label = filter.label,
                    checked = filter == selected,
                    key = "availability_${filter.value}",
                    onClick = {
                        LibraryFilterStore.select(filter)
                        onSelected()
                    },
                )
            )
        }
        if (enabledSources.isNotEmpty()) {
            add(
                NavigationItem(
                    label = "Source",
                    enabled = false,
                    showIndicator = false,
                    key = "source_header",
                )
            )
            add(
                NavigationItem(
                    label = "All sources",
                    checked = selectedSource == LibrarySourceFilter.All,
                    key = "source_all",
                    onClick = {
                        LibraryFilterStore.selectSource(LibrarySourceFilter.All)
                        onSelected()
                    },
                )
            )
            add(
                NavigationItem(
                    label = "This device",
                    checked = selectedSource == LibrarySourceFilter.Local,
                    key = "source_local",
                    onClick = {
                        LibraryFilterStore.selectSource(LibrarySourceFilter.Local)
                        onSelected()
                    },
                )
            )
            enabledSources.forEach { config ->
                val filter = LibrarySourceFilter.Source(config.id)
                add(
                    NavigationItem(
                        label = config.displayName,
                        checked = selectedSource == filter,
                        key = "source_${config.id}",
                        onClick = {
                            LibraryFilterStore.selectSource(filter)
                            onSelected()
                        },
                    )
                )
            }
        }
    }
    WheelContextMenu("Show music", items, onDismiss)
}

@Composable
fun LibraryFilterStatus(filter: LibraryFilter) {
    val sourceFilter by LibraryFilterStore.sourceFilter.collectAsState()
    if (filter == LibraryFilter.ALL && sourceFilter == LibrarySourceFilter.All) return
    val sources by RemoteSourceRegistry.sourcesFlow.collectAsState(
        initial = RemoteSourceRegistry.currentSources
    )
    val sourceNames = remember(sources) { sources.associate { it.id to it.displayName } }
    val summary = libraryFilterSummary(filter, sourceFilter, sourceNames)
    Text(
        "$summary · Show all",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.secondary,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { LibraryFilterStore.clearAll() }
                .padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
fun LibraryFilteredEmptyState() {
    val filter by LibraryFilterStore.filter.collectAsState()
    val sourceFilter by LibraryFilterStore.sourceFilter.collectAsState()
    val sources by RemoteSourceRegistry.sourcesFlow.collectAsState(
        initial = RemoteSourceRegistry.currentSources
    )
    val sourceNames = remember(sources) { sources.associate { it.id to it.displayName } }
    val title =
        when {
            sourceFilter != LibrarySourceFilter.All && filter == LibraryFilter.ALL ->
                "No music in ${sourceFilterLabel(sourceFilter, sourceNames)}"

            filter == LibraryFilter.OFFLINE -> "No offline music"
            filter == LibraryFilter.NOT_OFFLINE -> "No non-offline music"
            else -> "No matching music"
        }
    EmptyState(
        title = title,
        message = "No items match this filter.",
        iconText = "( ? )",
        actionLabel = "SHOW ALL",
        onActionClick = { LibraryFilterStore.clearAll() },
    )
}
