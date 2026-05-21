package com.pxr.cymatic.ui.screens.library

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun ArtistsScreen(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val artists = remember(audioFiles) {
        audioFiles
            .map(::artistDisplayName)
            .distinct()
            .sortedBy { it.lowercase() }
    }

    val filteredArtists = remember(artists, searchQuery, isSearchActive) {
        if (isSearchActive && searchQuery.isNotEmpty()) {
            artists.filter { it.contains(searchQuery, ignoreCase = true) }
        } else {
            artists
        }
    }

    val items = filteredArtists.map { artistName ->
        NavigationItem(artistName) {
            navController.navigate("artist/${Uri.encode(artistName)}")
        }
    }

    BaseScreen(
        title = "Artists",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        isSearchActive = isSearchActive,
        onSearchActiveChange = { isSearchActive = it }
    ) {
        NavigationList(
            items = items,
            modifier = Modifier
        )
    }
}


