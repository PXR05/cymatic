package com.pxr.cymatic.data.store

import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.LibraryFilter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object LibraryFilterStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean()
    private val mutableFilter = MutableStateFlow(LibraryFilter.ALL)
    val filter = mutableFilter.asStateFlow()
    val changes = filter.map { it.ordinal.toLong() }

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch {
            SettingsStore.libraryFilterFlow.distinctUntilChanged().collect {
                mutableFilter.value = LibraryFilter.from(it)
            }
        }
    }

    fun select(filter: LibraryFilter) {
        mutableFilter.value = filter
        scope.launch {
            runCatching { SettingsStore.setLibraryFilter(filter.value) }
                .onFailure {
                    android.util.Log.w(
                        "LibraryFilter",
                        "Could not save library filter",
                        it,
                    )
                }
        }
    }

    fun audio(files: List<AudioFile>): List<AudioFile> = files.filter {
        filter.value.matches(it.availability)
    }

    fun playlists(playlists: List<Playlist>): List<Playlist> = playlists.filter {
        filter.value.matches(it.availability)
    }
}
