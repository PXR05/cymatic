package com.pxr.cymatic.ui.screens.directory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController
import java.io.File
import java.net.URLEncoder

@Composable
fun DirectoriesScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current

    val directories by SettingsStore.scanDirectoriesFlow.collectAsState(emptyList())

    val items = directories.map { directory ->
        val dirName = File(directory).name.substringAfterLast("%3A").ifEmpty { "/" }
        val encodedPath = URLEncoder.encode(directory, "UTF-8")
        NavigationItem(dirName) {
            navController.navigate("directory/${encodedPath}")
        }
    }

    BaseScreen(
        title = "Directories",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        NavigationList(
            items = items,
            modifier = Modifier
        )
    }
}