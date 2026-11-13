package com.pxr.cymatic.ui.screens.library.album

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.filterByAlbum
import com.pxr.cymatic.data.library.filterByArtistAndAlbum
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.screens.library.LibraryCollectionLoader

class AlbumSongsViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private var albumName = Uri.decode(savedStateHandle.get<String>("albumName").orEmpty())
    private var artistName =
        savedStateHandle.get<String>("artistName")?.let(Uri::decode)?.takeIf(String::isNotBlank)

    private val loader =
        LibraryCollectionLoader(
            scope = viewModelScope,
            cachedItems = repository.getCachedAudio()?.let(::filterSongs),
            changes = libraryRefreshEvents(),
            failureMessage = "Failed to load album songs",
            loadItems = { filterSongs(repository.getAllAudio()) },
        )
    val songs = loader.items
    val isLoading = loader.isLoading
    val errorMessage = loader.errorMessage

    var contextMenuFile by mutableStateOf<AudioFile?>(null)
    var infoDialogId by mutableStateOf<Long?>(null)

    fun loadAlbumSongs(albumName: String, artistName: String? = this.artistName) {
        this.albumName = albumName
        this.artistName = artistName
        loader.refresh()
    }

    private fun filterSongs(songs: List<AudioFile>): List<AudioFile> {
        val filtered = LibraryFilterStore.audio(songs)
        val artist = artistName
        return if (artist == null) filterByAlbum(filtered, albumName)
        else filterByArtistAndAlbum(filtered, artist, albumName)
    }
}
