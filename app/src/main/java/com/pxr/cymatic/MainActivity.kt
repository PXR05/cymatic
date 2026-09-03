package com.pxr.cymatic

import android.Manifest
import android.content.ComponentName
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pxr.cymatic.data.store.LauncherStore
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.launcher.LibraryWallpaperBackdrop
import com.pxr.cymatic.ui.components.player.PlayerBar
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.navigation.Screen
import com.pxr.cymatic.ui.screens.home.AllAppsScreen
import com.pxr.cymatic.ui.screens.home.HomeScreen
import com.pxr.cymatic.ui.screens.library.AllSongsScreen
import com.pxr.cymatic.ui.screens.library.QueueScreen
import com.pxr.cymatic.ui.screens.library.UnknownAlbum
import com.pxr.cymatic.ui.screens.library.UnknownArtist
import com.pxr.cymatic.ui.screens.library.album.AlbumSongsScreen
import com.pxr.cymatic.ui.screens.library.album.AlbumsScreen
import com.pxr.cymatic.ui.screens.library.artist.ArtistSongsScreen
import com.pxr.cymatic.ui.screens.library.artist.ArtistsScreen
import com.pxr.cymatic.ui.screens.library.playlist.PlaylistSongsScreen
import com.pxr.cymatic.ui.screens.library.playlist.PlaylistsScreen
import com.pxr.cymatic.ui.screens.settings.EQSettingsScreen
import com.pxr.cymatic.ui.screens.settings.LauncherSettingsScreen
import com.pxr.cymatic.ui.screens.settings.PermissionsScreen
import com.pxr.cymatic.ui.screens.settings.PlaybackSettingsScreen
import com.pxr.cymatic.ui.screens.settings.SettingsScreen
import com.pxr.cymatic.ui.screens.settings.StorageSettingsScreen
import com.pxr.cymatic.ui.screens.settings.VersionSettingsScreen
import com.pxr.cymatic.ui.state.rememberPlaybackState
import com.pxr.cymatic.ui.theme.CymaticTheme

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isReady by mutableStateOf(false)

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !isReady }

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                1000
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                1000
            )
        }

        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )
        val audioAttributionContext =
            createAttributionContext("audioPlayback")
        controllerFuture =
            MediaController.Builder(audioAttributionContext, sessionToken).buildAsync()

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isReadyState by mainViewModel.isReady.collectAsState()

            LaunchedEffect(isReadyState) {
                isReady = isReadyState
            }

            val navController = rememberNavController()
            var mediaController by remember { mutableStateOf<MediaController?>(null) }
            val locked by SettingsStore.lockedFlow.collectAsState(initial = SettingsStore.currentLocked)

            LaunchedEffect(Unit) {
                mainViewModel.performInitialScan()
            }

            DisposableEffect(Unit) {
                controllerFuture?.let { future ->
                    future.addListener({
                        try {
                            mediaController = future.get()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, MoreExecutors.directExecutor())
                }
                onDispose { }
            }

            val routes: Map<String, @Composable (NavBackStackEntry) -> Unit> = mapOf(
                Screen.Home.route to { HomeScreen() },
                Screen.AllSongs.route to { entry ->
                    val scrollId = entry.arguments?.getString("scrollId")
                    AllSongsScreen(
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                Screen.Artists.route to { ArtistsScreen() },
                Screen.ArtistSongs.route to { entry ->
                    val rawName = entry.arguments?.getString("artistName")
                    val artistName = rawName?.let(Uri::decode) ?: UnknownArtist
                    val scrollId = entry.arguments?.getString("scrollId")
                    ArtistSongsScreen(
                        artistName = artistName,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                Screen.Albums.route to { AlbumsScreen() },
                Screen.AlbumSongs.route to { entry ->
                    val rawName = entry.arguments?.getString("albumName")
                    val albumName = rawName?.let(Uri::decode) ?: UnknownAlbum
                    val scrollId = entry.arguments?.getString("scrollId")
                    AlbumSongsScreen(
                        albumName = albumName,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                Screen.Playlists.route to { PlaylistsScreen() },
                Screen.AllApps.route to {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LibraryWallpaperBackdrop()
                        AllAppsScreen()
                    }
                },
                Screen.PlaylistSongs.route to { entry ->
                    val playlistId = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: return@to
                    val scrollId = entry.arguments?.getString("scrollId")
                    PlaylistSongsScreen(
                        playlistId = playlistId,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                Screen.Settings.route to { SettingsScreen() },
                Screen.Permissions.route to { PermissionsScreen() },
                Screen.LauncherSettings.route to { LauncherSettingsScreen() },
                Screen.EQSettings.route to { EQSettingsScreen() },
                Screen.PlaybackSettings.route to { PlaybackSettingsScreen() },
                Screen.StorageSettings.route to { StorageSettingsScreen() },
                Screen.VersionSettings.route to { VersionSettingsScreen() },
                Screen.Queue.route to { QueueScreen() },
            )

            CymaticTheme {
                CompositionLocalProvider(
                    LocalMediaController provides mediaController,
                    LocalNavController provides navController
                ) {
                    val playbackState = rememberPlaybackState(mediaController)
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val hasPlayback = playbackState.currentMediaId != null && playbackState.totalTracks > 0
                    val isDocked = isLandscape && hasPlayback
                    val hideSystemBars = locked || isDocked
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

                    LaunchedEffect(hideSystemBars) {
                        val windowInsetsController =
                            WindowCompat.getInsetsController(window, window.decorView)
                        if (hideSystemBars) {
                            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                            windowInsetsController.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                            windowInsetsController.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                        }
                    }

                    val currentBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route
                    val isHomeRoute = currentRoute == null || currentRoute == Screen.Home.route
                    val isLibraryRoute = currentRoute?.let { route ->
                        route.startsWith("all_songs") ||
                        route.startsWith("artists") ||
                        route.startsWith("artist/") ||
                        route.startsWith("albums") ||
                        route.startsWith("album/") ||
                        route.startsWith("playlists") ||
                        route.startsWith("playlist/") ||
                        route.startsWith("queue")
                    } ?: false
                    val isAllAppsRoute = currentRoute == Screen.AllApps.route
                    val isTransparentRoute = isHomeRoute || isLibraryRoute || isAllAppsRoute

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (isTransparentRoute) Color.Transparent else MaterialTheme.colorScheme.background
                    ) {
                        when {
                            hideSystemBars -> {
                                Column(
                                    modifier = Modifier.padding(
                                        bottom = WindowInsets.systemBars.asPaddingValues()
                                            .calculateBottomPadding()
                                    )
                                ) {
                                    PlayerBar(
                                        modifier = Modifier.weight(1f),
                                        isDocked = isDocked
                                    )
                                }
                            }

                            else -> {
                                NavHost(
                                    navController = navController,
                                    startDestination = Screen.Home.route,
                                    enterTransition = {
                                        slideInHorizontally(
                                            initialOffsetX = { fullWidth -> fullWidth },
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)
                                        )
                                    },
                                    exitTransition = {
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)
                                        )
                                    },
                                    popEnterTransition = {
                                        slideInHorizontally(
                                            initialOffsetX = { fullWidth -> -(fullWidth * 0.25f).toInt() },
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)
                                        )
                                    },
                                    popExitTransition = {
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> fullWidth },
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    routes.forEach { (route, composable) ->
                                        composable(route) { backStackEntry ->
                                            composable(backStackEntry)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
