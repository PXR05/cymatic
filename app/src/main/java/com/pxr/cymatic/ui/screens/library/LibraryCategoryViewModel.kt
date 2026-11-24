package com.pxr.cymatic.ui.screens.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pxr.cymatic.data.library.albumDisplayName
import com.pxr.cymatic.data.library.artistDisplayNames
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

internal data class LibraryCategoryItem(
    val name: String,
    val sourceId: String? = null,
) {
    val sourceKey: String
        get() = sourceId ?: "local"

    val key: String
        get() = "$sourceKey:$name"
}

internal enum class LibraryCategory(
    val title: String,
    val displayNames: (AudioFile) -> List<String>,
    val splitBySource: Boolean,
) {
    ARTISTS("Artists", ::artistDisplayNames, splitBySource = false),
    ALBUMS("Albums", { listOf(albumDisplayName(it)) }, splitBySource = true);

    fun browseRoute(item: LibraryCategoryItem): String =
        when (this) {
            ARTISTS -> Screen.ArtistAlbums.createRoute(item.name)
            ALBUMS -> Screen.AlbumSongs.createRoute(item.name, sourceKey = item.sourceKey)
        }

    fun songsRoute(item: LibraryCategoryItem): String =
        when (this) {
            ARTISTS -> Screen.ArtistSongs.createRoute(item.name)
            ALBUMS -> Screen.AlbumSongs.createRoute(item.name, sourceKey = item.sourceKey)
        }

    fun filterSongs(songs: List<AudioFile>, item: LibraryCategoryItem): List<AudioFile> =
        when (this) {
            ARTISTS -> filterByArtist(songs, item.name)
            ALBUMS ->
                filterByAlbum(
                    songs.filter { it.sourceId == item.sourceId },
                    item.name,
                )
        }

    fun group(library: LibraryGroups, item: LibraryCategoryItem): LibraryGroup? =
        when (this) {
            ARTISTS -> library.artists[item.name]
            ALBUMS -> library.albumsBySource[item.name to item.sourceId]
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
            cachedItems = repository.getCachedAudio()?.let(::groupItems),
            changes = libraryRefreshEvents(),
            failureMessage = "Failed to load ${category.title.lowercase()}",
            loadItems = { groupItems(repository.getAllAudio()) },
            matches = { item, text -> item.name.contains(text, ignoreCase = true) },
        )

    val searchQuery
        get() = collection.search.value.query

    val isSearchActive
        get() = collection.search.value.active

    val uiState = collection.state

    fun refresh() = collection.refresh()

    fun onSearchQueryChange(query: String) = collection.search.setQuery(query)

    fun onSearchActiveChange(active: Boolean) = collection.search.setActive(active)

    fun getSongs(item: LibraryCategoryItem, onResult: (List<AudioFile>) -> Unit) {
        viewModelScope.launch {
            val songs =
                withContext(Dispatchers.IO) {
                    category.filterSongs(LibraryFilterStore.audio(repository.getAllAudio()), item)
                }
            onResult(songs)
        }
    }

    private fun groupItems(songs: List<AudioFile>): List<LibraryCategoryItem> {
        val filtered = LibraryFilterStore.audio(songs)
        return if (category.splitBySource) {
            filtered
                .flatMap { song ->
                    category.displayNames(song).map { LibraryCategoryItem(it, song.sourceId) }
                }
                .distinct()
                .sortedWith(
                    compareBy<LibraryCategoryItem>(
                        { it.name.lowercase() },
                        { it.sourceId.orEmpty() },
                    )
                )
        } else {
            filtered
                .flatMap { song -> category.displayNames(song).map { LibraryCategoryItem(it) } }
                .distinct()
                .sortedBy { it.name.lowercase() }
        }
    }
}
