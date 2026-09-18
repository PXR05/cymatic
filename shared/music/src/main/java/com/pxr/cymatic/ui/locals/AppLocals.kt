package com.pxr.cymatic.ui.locals

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.session.MediaController
import androidx.navigation.NavHostController

val LocalMediaController = staticCompositionLocalOf<MediaController?> { null }
val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("NavController not provided")
}

