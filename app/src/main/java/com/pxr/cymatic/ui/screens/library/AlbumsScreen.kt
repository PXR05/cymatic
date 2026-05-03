package com.pxr.cymatic.ui.screens.library

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.components.common.NavigationItem
import com.pxr.cymatic.ui.components.common.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun AlbumsScreen(
    audioFiles: List<AudioFile>,
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current

    val albums = audioFiles
        .map(::albumDisplayName)
        .distinct()
        .sortedBy { it.lowercase() }

    val items = albums.map { albumName ->
        NavigationItem(albumName) {
            navController.navigate("album/${Uri.encode(albumName)}")
        }
    }

    BaseScreen(
        title = "Albums",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        NavigationList(
            items = items,
            modifier = Modifier
        )
    }
}


