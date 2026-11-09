package com.pxr.cymatic.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OfflineWorkItems(
    val tracks: Set<RemoteLibraryItemKey> = emptySet(),
    val playlists: Set<RemoteLibraryItemKey> = emptySet(),
    val localPlaylists: Set<Long> = emptySet(),
)

internal class OfflineWorkTracker {
    private var nextId = 0L
    private val requests = mutableMapOf<Long, Pair<String, OfflineWorkItems>>()
    private var active = OfflineWorkItems()
    private val mutableState = MutableStateFlow(OfflineWorkItems())
    val state = mutableState.asStateFlow()

    @Synchronized fun enqueue(sourceId: String, items: OfflineWorkItems): Long {
        val id = ++nextId
        requests[id] = sourceId to items
        publish()
        return id
    }

    @Synchronized fun snapshot(sourceId: String): Set<Long> =
        requests.filterValues { it.first == sourceId }.keys.toSet()

    @Synchronized fun finish(ids: Set<Long>) {
        ids.forEach(requests::remove)
        publish()
    }

    @Synchronized fun setActive(items: OfflineWorkItems) {
        active = items
        publish()
    }

    private fun publish() {
        val items = requests.values.map { it.second } + active
        mutableState.value = OfflineWorkItems(
            items.flatMapTo(mutableSetOf()) { it.tracks },
            items.flatMapTo(mutableSetOf()) { it.playlists },
            items.flatMapTo(mutableSetOf()) { it.localPlaylists },
        )
    }
}
