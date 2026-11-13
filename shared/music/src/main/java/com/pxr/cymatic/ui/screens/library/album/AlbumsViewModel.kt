package com.pxr.cymatic.ui.screens.library.album

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.albumDisplayName
import com.pxr.cymatic.data.library.filterByAlbum
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.screens.library.SearchableLibraryCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)
    private val collection =
        SearchableLibraryCollection(
            scope = viewModelScope,
            cachedItems = repository.getCachedAudio()?.let(::groupNames),
            changes = libraryRefreshEvents(),
            failureMessage = "Failed to load albums",
            loadItems = { groupNames(repository.getAllAudio()) },
            matches = { name, text -> name.contains(text, ignoreCase = true) },
        )

    val searchQuery
        get() = collection.search.value.query

    val isSearchActive
        get() = collection.search.value.active

    val uiState = collection.state

    fun loadAlbums() = collection.refresh()

    fun onSearchQueryChange(query: String) = collection.search.setQuery(query)

    fun onSearchActiveChange(active: Boolean) = collection.search.setActive(active)

    fun getSongsForAlbum(albumName: String, onResult: (List<AudioFile>) -> Unit) {
        viewModelScope.launch {
            val songs =
                withContext(Dispatchers.IO) {
                    filterByAlbum(LibraryFilterStore.audio(repository.getAllAudio()), albumName)
                }
            onResult(songs)
        }
    }

    private fun groupNames(songs: List<AudioFile>): List<String> =
        LibraryFilterStore.audio(songs).map(::albumDisplayName).distinct().sortedBy {
            it.lowercase()
        }
}
