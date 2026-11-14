package com.pxr.cymatic.ui.screens.library.artist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.filterByArtist
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.screens.library.LibraryCollectionLoader
import com.pxr.cymatic.ui.screens.library.SongListState

class ArtistSongsViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private var artistName = savedStateHandle.get<String>("artistName").orEmpty()

    private val loader =
        LibraryCollectionLoader(
            scope = viewModelScope,
            cachedItems = repository.getCachedAudio()?.let(::filterSongs),
            changes = libraryRefreshEvents(),
            failureMessage = "Failed to load artist songs",
            loadItems = { filterSongs(repository.getAllAudio()) },
        )
    val songs = loader.items
    val isLoading = loader.isLoading
    val errorMessage = loader.errorMessage

    internal val listState = SongListState()

    fun loadArtistSongs(artistName: String) {
        this.artistName = artistName
        loader.refresh()
    }

    private fun filterSongs(songs: List<AudioFile>) =
        filterByArtist(LibraryFilterStore.audio(songs), artistName)
}
