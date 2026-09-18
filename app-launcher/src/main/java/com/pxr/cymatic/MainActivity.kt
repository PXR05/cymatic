package com.pxr.cymatic

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.ui.components.launcher.LibraryWallpaperBackdrop
import com.pxr.cymatic.ui.components.list.NavigationItem
import com.pxr.cymatic.ui.components.player.PlayerBar
import com.pxr.cymatic.ui.components.screen.LocalScreenBackdrop
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.MusicNavHost
import com.pxr.cymatic.ui.screens.home.AllAppsScreen
import com.pxr.cymatic.ui.screens.home.HomeScreen
import com.pxr.cymatic.ui.screens.settings.LauncherSettingsScreen
import com.pxr.cymatic.ui.screens.settings.PermissionsScreen
import com.pxr.cymatic.ui.state.rememberMusicWindowState

class MainActivity : MusicActivity() {
    @Composable
    override fun AppContent() {
        val navController = LocalNavController.current
        val state = rememberMusicWindowState(window)
        val wallpaperBlurRadius by LauncherStore.wallpaperBlurRadiusFlow.collectAsState(initial = 0f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LaunchedEffect(wallpaperBlurRadius) {
                val blurPx = (wallpaperBlurRadius * resources.displayMetrics.density).toInt()
                try {
                    if (blurPx > 0) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.attributes.blurBehindRadius = blurPx
                        window.setBackgroundBlurRadius(blurPx)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.attributes.blurBehindRadius = 0
                        window.setBackgroundBlurRadius(0)
                    }
                    window.attributes = window.attributes
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }


        CompositionLocalProvider(LocalScreenBackdrop provides { LibraryWallpaperBackdrop() }) {
            // Destinations own their backgrounds. Keep the host stable during transitions.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent
            ) {
                when {
                    state.isExpanded -> {
                        Column(
                            modifier = Modifier.padding(
                                bottom = WindowInsets.systemBars.asPaddingValues()
                                    .calculateBottomPadding()
                            )
                        ) {
                            PlayerBar(
                                modifier = Modifier.weight(1f),
                                isDocked = state.isDocked
                            )
                        }
                    }

                    else -> {
                        MusicNavHost(
                            home = { HomeScreen() },
                            modifier = Modifier.fillMaxSize(),
                            animate = true,
                            settingsItems = listOf(
                                NavigationItem("Permissions", onClick = { navController.navigate(LauncherRoutes.Permissions) }),
                                NavigationItem("Launcher", onClick = { navController.navigate(LauncherRoutes.Settings) }),
                            ),
                            additionalRoutes = mapOf(
                                LauncherRoutes.AllApps to {
                                    Box(Modifier.fillMaxSize()) {
                                        LibraryWallpaperBackdrop()
                                        AllAppsScreen()
                                    }
                                },
                                LauncherRoutes.Permissions to { PermissionsScreen() },
                                LauncherRoutes.Settings to { LauncherSettingsScreen() },
                            ),
                        )
                    }
                }
            }
        }
    }
}
