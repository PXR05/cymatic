package com.pxr.cymatic.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.pxr.cymatic.R
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController

@Composable
fun SourceSettingsScreen(
    modifier: Modifier = Modifier
) {
    val navController = LocalNavController.current
    val fontFamily = FontFamily(Font(R.font.pixel))

    BaseScreen(
        title = "Source",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {

    }
}