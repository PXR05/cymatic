package com.pxr.cymatic.data.store

import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.model.LibraryFilter
import com.pxr.cymatic.data.model.LibrarySourceFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object LibraryFilterStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean()
    private val mutableFilter = MutableStateFlow(LibraryFilter.ALL)
    private val mutableSourceFilter = MutableStateFlow<LibrarySourceFilter>(LibrarySourceFilter.All)

    val filter = mutableFilter.asStateFlow()
    val sourceFilter = mutableSourceFilter.asStateFlow()
    val changes =
        combine(filter, sourceFilter) { availability, source ->
            "${availability.value}|${source.value}".hashCode().toLong()
        }.distinctUntilChanged()

    val isFiltered: Boolean
        get() = filter.value != LibraryFilter.ALL || sourceFilter.value != LibrarySourceFilter.All

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        scope.launch {
            SettingsStore.libraryFilterFlow.distinctUntilChanged().collect {
                mutableFilter.value = LibraryFilter.from(it)
            }
        }
        scope.launch {
            SettingsStore.librarySourceFilterFlow.distinctUntilChanged().collect {
                mutableSourceFilter.value = LibrarySourceFilter.from(it)
            }
        }
        scope.launch {
            RemoteSourceRegistry.sourcesFlow.collect { sources ->
                val current = mutableSourceFilter.value
                if (
                    current is LibrarySourceFilter.Source &&
                    sources.none { it.id == current.id && it.enabled }
                ) {
                    selectSource(LibrarySourceFilter.All)
                }
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

    fun selectSource(sourceFilter: LibrarySourceFilter) {
        mutableSourceFilter.value = sourceFilter
        scope.launch {
            runCatching { SettingsStore.setLibrarySourceFilter(sourceFilter.value) }
                .onFailure {
                    android.util.Log.w(
                        "LibraryFilter",
                        "Could not save source filter",
                        it,
                    )
                }
        }
    }

    fun clearAll() {
        select(LibraryFilter.ALL)
        selectSource(LibrarySourceFilter.All)
    }

    fun audio(files: List<AudioFile>): List<AudioFile> {
        val availability = filter.value
        val source = sourceFilter.value
        return files.filter {
            availability.matches(it.availability) && source.matches(it.sourceId)
        }
    }

    fun playlists(playlists: List<Playlist>): List<Playlist> {
        val availability = filter.value
        val source = sourceFilter.value
        return playlists.filter {
            availability.matches(it.availability) && source.matches(it.sourceId)
        }
    }
}
