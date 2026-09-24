package com.pxr.cymatic

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.ui.navigation.MusicNavHost
import com.pxr.cymatic.ui.screens.home.HomeScreen
import com.pxr.cymatic.ui.screens.settings.HiddenAppsScreen
import com.pxr.cymatic.ui.screens.settings.ReleaseProduct

class HiddenAppsActivity : TaskActivity() {
    @Composable
    override fun AppContent() {
        MusicNavHost(
            releaseProduct = ReleaseProduct.LAUNCHER,
            home = { HomeScreen() },
            modifier = Modifier.fillMaxSize(),
            animate = true,
            startDestination = LauncherRoutes.HiddenApps,
            additionalRoutes = mapOf(
                LauncherRoutes.HiddenApps to { HiddenAppsScreen(showBackButton = false) },
            ),
        )
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, HiddenAppsActivity::class.java))
        }
    }
}
