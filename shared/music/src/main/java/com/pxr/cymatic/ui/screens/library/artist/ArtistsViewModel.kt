package com.pxr.cymatic.ui.screens.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.artistDisplayName
import com.pxr.cymatic.data.library.filterByArtist
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.screens.library.SearchableLibraryCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtistsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)
    private val collection =
        SearchableLibraryCollection(
            scope = viewModelScope,
            cachedItems = repository.getCachedAudio()?.let(::groupNames),
            changes = libraryRefreshEvents(),
            failureMessage = "Failed to load artists",
            loadItems = { groupNames(repository.getAllAudio()) },
            matches = { name, text -> name.contains(text, ignoreCase = true) },
        )

    val searchQuery
        get() = collection.search.value.query

    val isSearchActive
        get() = collection.search.value.active

    val uiState = collection.state

    fun loadArtists() = collection.refresh()

    fun onSearchQueryChange(query: String) = collection.search.setQuery(query)

    fun onSearchActiveChange(active: Boolean) = collection.search.setActive(active)

    fun getSongsForArtist(artistName: String, onResult: (List<AudioFile>) -> Unit) {
        viewModelScope.launch {
            val songs =
                withContext(Dispatchers.IO) {
                    filterByArtist(LibraryFilterStore.audio(repository.getAllAudio()), artistName)
                }
            onResult(songs)
        }
    }

    private fun groupNames(songs: List<AudioFile>): List<String> =
        LibraryFilterStore.audio(songs).map(::artistDisplayName).distinct().sortedBy {
            it.lowercase()
        }
}
