package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current

    BaseScreen(
        title = "Settings",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        NavigationList(
            items = listOf(
                NavigationItem(
                    label = "Equalizer",
                    onClick = { navController.navigate(Screen.EQSettings.route) }
                ),
                NavigationItem(
                    label = "Playback",
                    onClick = { navController.navigate(Screen.PlaybackSettings.route) }
                ),
                NavigationItem(
                    label = "Storage",
                    onClick = { navController.navigate(Screen.StorageSettings.route) }
                ),
                NavigationItem(
                    label = "Permissions",
                    onClick = { navController.navigate(Screen.Permissions.route) }
                ),
                NavigationItem(
                    label = "Launcher",
                    onClick = { navController.navigate(Screen.LauncherSettings.route) }
                ),
                NavigationItem(
                    label = "Version",
                    onClick = { navController.navigate(Screen.VersionSettings.route) }
                ),
            )
        )
    }
}


