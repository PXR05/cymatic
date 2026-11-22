package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.pixelIcon
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    additionalItems: List<NavigationItem> = emptyList(),
) {
    val navController = LocalNavController.current

    BaseScreen(
        title = "Settings",
        onBackClick = { navController.popBackStack() },
        modifier = modifier,
    ) {
        NavigationList(
            items =
                listOf(
                    NavigationItem(
                        label = "Equalizer",
                        icon = pixelIcon(R.drawable.ic_pixel_equalizer),
                        onClick = { navController.navigate(Screen.EQSettings.route) },
                    ),
                    NavigationItem(
                        label = "Playback",
                        icon = pixelIcon(R.drawable.ic_pixel_speaker),
                        onClick = { navController.navigate(Screen.PlaybackSettings.route) },
                    ),
                    NavigationItem(
                        label = "Storage",
                        icon = pixelIcon(R.drawable.ic_pixel_storage),
                        onClick = { navController.navigate(Screen.StorageSettings.route) },
                    ),
                    NavigationItem(
                        label = "Remote library",
                        icon = pixelIcon(R.drawable.ic_pixel_stream),
                        onClick = { navController.navigate(Screen.LibrarySyncSettings.route) },
                    ),
                ) +
                        additionalItems +
                        listOf(
                            NavigationItem(
                                label = "About",
                                icon = pixelIcon(R.drawable.ic_pixel_info),
                                onClick = { navController.navigate(Screen.AboutSettings.route) },
                            ),
                        )
        )
    }
}
