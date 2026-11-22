package com.pxr.cymatic.ui.screens.library.album

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.UnknownAlbum
import com.pxr.cymatic.data.library.filterByAlbum
import com.pxr.cymatic.data.library.filterByArtistAndAlbum
import com.pxr.cymatic.data.library.libraryRefreshEvents
import com.pxr.cymatic.data.media.AudioRepository
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.LibraryFilterStore
import com.pxr.cymatic.ui.screens.library.LibraryCollectionLoader
import com.pxr.cymatic.ui.screens.library.SongListState

class AlbumSongsViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val repository = AudioRepository.getInstance(application)

    private var albumName =
        savedStateHandle.get<String>("albumName")?.takeIf(String::isNotBlank) ?: UnknownAlbum
    private var artistName = savedStateHandle.get<String>("artistName")?.takeIf(String::isNotBlank)
    private var sourceKey = savedStateHandle.get<String>("sourceKey")?.takeIf(String::isNotBlank)

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

    internal val listState = SongListState()

    fun loadAlbumSongs(
        albumName: String,
        artistName: String? = this.artistName,
        sourceKey: String? = this.sourceKey,
        force: Boolean = false,
    ) {
        if (
            !force &&
            this.albumName == albumName &&
            this.artistName == artistName &&
            this.sourceKey == sourceKey
        ) return
        this.albumName = albumName
        this.artistName = artistName
        this.sourceKey = sourceKey
        loader.updateCached(repository.getCachedAudio()?.let(::filterSongs))
        loader.refresh()
    }

    private fun filterSongs(songs: List<AudioFile>): List<AudioFile> {
        val filtered =
            LibraryFilterStore.audio(songs).let { list ->
                when (val key = sourceKey) {
                    null -> list
                    "local" -> list.filter { it.sourceId == null }
                    else -> list.filter { it.sourceId == key }
                }
            }
        val artist = artistName
        return if (artist == null) filterByAlbum(filtered, albumName)
        else filterByArtistAndAlbum(filtered, artist, albumName)
    }
}
