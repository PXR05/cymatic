package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.screen.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen

@Composable
internal fun AboutSettingsScreen() {
    val nav = LocalNavController.current

    BaseScreen(title = "About", onBackClick = { nav.popBackStack() }) {
        NavigationList(
            items =
                listOf(
                    NavigationItem("Version") { nav.navigate(Screen.VersionSettings.route) },
                    NavigationItem("Licenses") { nav.navigate(Screen.LicensesSettings.route) },
                ),
        )
    }
}
