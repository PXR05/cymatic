package com.pxr.cymatic.ui.screens.library

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun ArtistsScreen(
    modifier: Modifier = Modifier,
    viewModel: ArtistsViewModel = viewModel()
) {
    val navController = LocalNavController.current

    val filteredArtists by viewModel.filteredArtists.collectAsState()

    val items = filteredArtists.map { artistName ->
        NavigationItem(artistName) {
            navController.navigate("artist/${Uri.encode(artistName)}")
        }
    }

    BaseScreen(
        title = "Artists",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
        searchQuery = viewModel.searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        isSearchActive = viewModel.isSearchActive,
        onSearchActiveChange = viewModel::onSearchActiveChange
    ) {
        NavigationList(
            items = items,
            modifier = Modifier
        )
    }
}
