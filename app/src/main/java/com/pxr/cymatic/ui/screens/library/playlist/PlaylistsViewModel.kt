package com.pxr.cymatic.ui.screens.library.playlist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.Playlist
import com.pxr.cymatic.data.media.PlaylistRepository
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.screens.library.SearchableLibraryCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository.getInstance(application)

    private val collection =
        SearchableLibraryCollection(
            scope = viewModelScope,
            cachedItems = repository.getCachedPlaylists()?.let(LibraryFilterStore::playlists),
            changes = libraryRefreshEvents(includeScans = false),
            failureMessage = "Failed to load playlists",
            loadItems = { LibraryFilterStore.playlists(repository.getPlaylists()) },
            matches = { playlist, text -> playlist.name.contains(text, ignoreCase = true) },
        )
    val searchQuery
        get() = collection.search.value.query

    val isSearchActive
        get() = collection.search.value.active

    val uiState = collection.state

    var showCreateDialog by mutableStateOf(false)
    var selectedPlaylist by mutableStateOf<Playlist?>(null)
    var newPlaylistName by mutableStateOf("")

    fun loadPlaylists() = collection.refresh()

    fun onSearchQueryChange(query: String) = collection.search.setQuery(query)

    fun onSearchActiveChange(active: Boolean) = collection.search.setActive(active)

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.createPlaylist(trimmed) }
            loadPlaylists()
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.renamePlaylist(playlistId, trimmed) }
            loadPlaylists()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deletePlaylist(playlistId) }
            loadPlaylists()
        }
    }

    fun getPlaylistSongs(
        playlistId: Long,
        onResult: (List<com.pxr.cymatic.data.model.AudioFile>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = LibraryFilterStore.audio(repository.getPlaylistAudio(playlistId))
            withContext(Dispatchers.Main) {
                onResult(songs)
            }
        }
    }
}
