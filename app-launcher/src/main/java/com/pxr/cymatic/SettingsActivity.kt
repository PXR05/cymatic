package com.pxr.cymatic

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.MusicNavHost
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.screens.home.HomeScreen
import com.pxr.cymatic.ui.screens.settings.HiddenAppsScreen
import com.pxr.cymatic.ui.screens.settings.LauncherSettingsScreen
import com.pxr.cymatic.ui.screens.settings.PermissionsScreen
import com.pxr.cymatic.ui.screens.settings.ReleaseProduct

class SettingsActivity : TaskActivity() {
    @Composable
    override fun AppContent() {
        val navController = LocalNavController.current
        val startDestination = intent.getStringExtra(EXTRA_START_DESTINATION)
            ?: Screen.Settings.route
        MusicNavHost(
            releaseProduct = ReleaseProduct.LAUNCHER,
            home = { HomeScreen() },
            modifier = Modifier.fillMaxSize(),
            animate = true,
            startDestination = startDestination,
            settingsIsTaskRoot = true,
            settingsItems = listOf(
                NavigationItem("Permissions", onClick = { navController.navigate(LauncherRoutes.Permissions) }),
                NavigationItem("Launcher", onClick = { navController.navigate(LauncherRoutes.Settings) }),
            ),
            additionalRoutes = mapOf(
                LauncherRoutes.Permissions to { PermissionsScreen() },
                LauncherRoutes.Settings to { LauncherSettingsScreen() },
                LauncherRoutes.HiddenApps to { HiddenAppsScreen() },
            ),
        )
    }

    companion object {
        const val EXTRA_START_DESTINATION = "start_destination"

        fun launch(context: Context, startDestination: String? = null) {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                if (startDestination != null) {
                    putExtra(EXTRA_START_DESTINATION, startDestination)
                }
            }
            context.startActivity(intent)
        }
    }
}
