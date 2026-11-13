package com.pxr.cymatic.data.library

import com.pxr.cymatic.data.media.LibraryChanges
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.data.store.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

internal fun libraryRefreshEvents(includeScans: Boolean = true): Flow<Unit> {
    val changes = listOf(LibraryChanges.version, LibraryFilterStore.changes)
    return merge(
            *(if (includeScans) changes + SettingsStore.lastScanTimeMsFlow else changes)
                .toTypedArray()
        )
        .map { Unit }
        .conflate()
}
