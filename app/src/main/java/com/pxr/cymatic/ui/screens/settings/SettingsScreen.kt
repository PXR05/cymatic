package com.pxr.cymatic.ui.screens.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
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
                    onClick = {
                        /* TODO */
                        Toast.makeText(
                            context,
                            "WIP",
                            Toast.LENGTH_SHORT
                        ).show()
                    }),
                NavigationItem(
                    label = "Storage",
                    subLabel = "Status, scan, directories",
                    onClick = { navController.navigate("setting/storage") }
                ),
                NavigationItem(
                    label = "App",
                    subLabel = "Theme, source, controls",
                    onClick = {
                        /* TODO */
                        Toast.makeText(
                            context,
                            "WIP",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            )
        )
    }
}


