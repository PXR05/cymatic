package com.pxr.cymatic.ui.screen

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.ui.components.BaseScreen
import com.pxr.cymatic.ui.components.NavigationItem
import com.pxr.cymatic.ui.components.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun ArtistsScreen(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current

    val artists = audioFiles
        .map(::artistDisplayName)
        .distinct()
        .sortedBy { it.lowercase() }

    val items = artists.map { artistName ->
        NavigationItem(artistName) {
            navController.navigate("artist/${Uri.encode(artistName)}")
        }
    }

    BaseScreen(
        title = "Artists",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        NavigationList(
            items = items,
            modifier = Modifier
        )
    }
}
