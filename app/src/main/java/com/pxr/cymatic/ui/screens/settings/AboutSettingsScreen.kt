package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.list.NavigationList
import com.pxr.cymatic.ui.components.pixelIcon
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
                    NavigationItem("Version", icon = pixelIcon(R.drawable.ic_pixel_tag)) {
                        nav.navigate(Screen.VersionSettings.route)
                    },
                    NavigationItem("Licenses", icon = pixelIcon(R.drawable.ic_pixel_document)) {
                        nav.navigate(Screen.LicensesSettings.route)
                    },
                    NavigationItem("Report bug", icon = pixelIcon(R.drawable.ic_pixel_bug)) {
                        nav.navigate(Screen.ReportBug.route)
                    },
                ),
        )
    }
}
