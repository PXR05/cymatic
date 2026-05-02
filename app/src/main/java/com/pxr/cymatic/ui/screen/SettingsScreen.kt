package com.pxr.cymatic.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pxr.cymatic.ui.components.BaseScreen
import com.pxr.cymatic.ui.components.NavigationItem
import com.pxr.cymatic.ui.components.NavigationList
import com.pxr.cymatic.ui.locals.LocalNavController

@Preview
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
                    label = "Audio",
                    subLabel = "EQ, effects, output",
                    onClick = { /* TODO */ }),
                NavigationItem(
                    label = "Storage",
                    subLabel = "Status, scan, permissions",
                    onClick = { /* TODO */ }
                ),
                NavigationItem(
                    label = "App",
                    subLabel = "Theme, source, controls",
                    onClick = { /* TODO */ }
                )
            )
        )
    }
}
