package com.pxr.cymatic.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.albumDisplayName
import com.pxr.cymatic.data.library.artistDisplayName
import com.pxr.cymatic.data.library.filterByAlbum
import com.pxr.cymatic.data.library.filterByArtist
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LibraryCategory(
    val title: String,
    val displayName: (AudioFile) -> String,
    val filterSongs: (List<AudioFile>, String) -> List<AudioFile>,
) {
    ARTISTS("Artists", ::artistDisplayName, ::filterByArtist),
    ALBUMS("Albums", ::albumDisplayName, ::filterByAlbum);

    fun browseRoute(name: String): String =
        when (this) {
            ARTISTS -> Screen.ArtistAlbums.createRoute(name)
            ALBUMS -> Screen.AlbumSongs.createRoute(name)
        }

    fun songsRoute(name: String): String =
        when (this) {
            ARTISTS -> Screen.ArtistSongs.createRoute(name)
            ALBUMS -> Screen.AlbumSongs.createRoute(name)
        }

    fun groups(library: LibraryGroups): Map<String, LibraryGroup> =
        when (this) {
            ARTISTS -> library.artists
            ALBUMS -> library.albums
        }
}

internal class LibraryCategoryViewModel(
    application: Application,
    private val category: LibraryCategory,
) : AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)
    private val collection =
        SearchableLibraryCollection(
            scope = viewModelScope,
            cachedItems = repository.getCachedAudio()?.let(::groupNames),
            changes = libraryRefreshEvents(),
            failureMessage = "Failed to load ${category.title.lowercase()}",
            loadItems = { groupNames(repository.getAllAudio()) },
            matches = { name, text -> name.contains(text, ignoreCase = true) },
        )

    val searchQuery
        get() = collection.search.value.query

    val isSearchActive
        get() = collection.search.value.active

    val uiState = collection.state

    fun refresh() = collection.refresh()

    fun onSearchQueryChange(query: String) = collection.search.setQuery(query)

    fun onSearchActiveChange(active: Boolean) = collection.search.setActive(active)

    fun getSongs(name: String, onResult: (List<AudioFile>) -> Unit) {
        viewModelScope.launch {
            val songs =
                withContext(Dispatchers.IO) {
                    category.filterSongs(LibraryFilterStore.audio(repository.getAllAudio()), name)
                }
            onResult(songs)
        }
    }

    private fun groupNames(songs: List<AudioFile>): List<String> =
        LibraryFilterStore.audio(songs).map(category.displayName).distinct().sortedBy {
            it.lowercase()
        }
}
