package com.pxr.cymatic.ui.screens.library

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore

class AllSongsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)
    private val collection =
        SearchableLibraryCollection(
            scope = viewModelScope,
            cachedItems = repository.getCachedAudio()?.let(LibraryFilterStore::audio),
            changes = libraryRefreshEvents(),
            failureMessage = "Failed to load audio files",
            loadItems = { LibraryFilterStore.audio(repository.getAllAudio()) },
            matches = { file, text ->
                with(file.metadata) {
                    listOf(title, artist, album).any {
                        it.orEmpty().contains(text, ignoreCase = true)
                    }
                }
            },
        )
    val searchQuery
        get() = collection.search.value.query

    val isSearchActive
        get() = collection.search.value.active

    var contextMenuFile by mutableStateOf<AudioFile?>(null)
    var infoDialogId by mutableStateOf<Long?>(null)

    val uiState = collection.state

    fun loadAudioFiles() = collection.refresh()

    fun onSearchQueryChange(query: String) = collection.search.setQuery(query)

    fun onSearchActiveChange(active: Boolean) = collection.search.setActive(active)
}
