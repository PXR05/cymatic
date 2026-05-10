package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.components.common.NavigationItem
import com.pxr.cymatic.ui.components.common.NavigationList
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
                    label = "Equalizer",
                    onClick = { navController.navigate("setting/eq") }
                ),
                NavigationItem(
                    label = "Storage",
                    onClick = { navController.navigate("setting/storage") }
                ),
                NavigationItem(
                    label = "Source",
                    onClick = { navController.navigate("setting/source") }
                ),
                NavigationItem(
                    label = "Version",
                    onClick = { navController.navigate("setting/version") }
                ),
            )
        )
    }
}


