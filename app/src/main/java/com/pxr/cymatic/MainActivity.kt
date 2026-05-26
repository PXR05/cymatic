package com.pxr.cymatic

import android.Manifest
import android.content.ComponentName
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.common.StatusBar
import com.pxr.cymatic.ui.components.player.PlayerBar
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.screens.directory.DirectoriesScreen
import com.pxr.cymatic.ui.screens.directory.DirectoryScreen
import com.pxr.cymatic.ui.screens.home.HomeScreen
import com.pxr.cymatic.ui.screens.library.AlbumSongsScreen
import com.pxr.cymatic.ui.screens.library.AlbumsScreen
import com.pxr.cymatic.ui.screens.library.AllSongsScreen
import com.pxr.cymatic.ui.screens.library.ArtistSongsScreen
import com.pxr.cymatic.ui.screens.library.ArtistsScreen
import com.pxr.cymatic.ui.screens.library.PlaylistSongsScreen
import com.pxr.cymatic.ui.screens.library.PlaylistsScreen
import com.pxr.cymatic.ui.screens.library.UnknownAlbum
import com.pxr.cymatic.ui.screens.library.UnknownArtist
import com.pxr.cymatic.ui.screens.settings.EQSettingsScreen
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
                    Manifest.permission.READ_MEDIA_AUDIO
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
                "home" to { HomeScreen() },
                "all_songs?scrollId={scrollId}" to { entry ->
                    val scrollId = entry.arguments?.getString("scrollId")
                    AllSongsScreen(
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                "artists" to { ArtistsScreen() },
                "artist/{artistName}?scrollId={scrollId}" to { entry ->
                    val rawName = entry.arguments?.getString("artistName")
                    val artistName = rawName?.let(Uri::decode) ?: UnknownArtist
                    val scrollId = entry.arguments?.getString("scrollId")
                    ArtistSongsScreen(
                        artistName = artistName,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                "albums" to { AlbumsScreen() },
                "album/{albumName}?scrollId={scrollId}" to { entry ->
                    val rawName = entry.arguments?.getString("albumName")
                    val albumName = rawName?.let(Uri::decode) ?: UnknownAlbum
                    val scrollId = entry.arguments?.getString("scrollId")
                    AlbumSongsScreen(
                        albumName = albumName,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                "playlists" to { PlaylistsScreen() },
                "playlist/{playlistId}?scrollId={scrollId}" to { entry ->
                    val playlistId = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: return@to
                    val scrollId = entry.arguments?.getString("scrollId")
                    PlaylistSongsScreen(
                        playlistId = playlistId,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                "settings" to { SettingsScreen() },
                "setting/eq" to { EQSettingsScreen() },
                "setting/playback" to { PlaybackSettingsScreen() },
                "setting/storage" to { StorageSettingsScreen() },
                "setting/version" to { VersionSettingsScreen() },
                "directories" to { DirectoriesScreen() },
                "directory/{directory}?scrollId={scrollId}" to { entry ->
                    val rawDir = entry.arguments?.getString("directory")
                    val directory = rawDir?.let(Uri::decode) ?: ""
                    val scrollId = entry.arguments?.getString("scrollId")
                    DirectoryScreen(
                        directory = directory,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                }
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
                    val isPlayerExpanded = locked || isDocked

                    LaunchedEffect(isPlayerExpanded) {
                        val windowInsetsController =
                            WindowCompat.getInsetsController(window, window.decorView)
                        if (isPlayerExpanded) {
                            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                            windowInsetsController.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        } else {
                            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                            windowInsetsController.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                        }
                    }

                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column {
                            if (!isPlayerExpanded) {
                                NavHost(
                                    navController = navController,
                                    startDestination = "home",
                                    enterTransition = { EnterTransition.None },
                                    exitTransition = { ExitTransition.None },
                                    popEnterTransition = { EnterTransition.None },
                                    popExitTransition = { ExitTransition.None },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    routes.forEach { (route, composable) ->
                                        composable(route) { backStackEntry ->
                                            composable(backStackEntry)
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.padding(
                                    bottom = WindowInsets.systemBars.asPaddingValues()
                                        .calculateBottomPadding()
                                )
                            ) {
                                if (playbackState.currentMediaId != null && playbackState.totalTracks > 0) {
                                    if (!isPlayerExpanded) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(MaterialTheme.colorScheme.secondary)
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))
                                    }

                                    PlayerBar(
                                        modifier = if (isPlayerExpanded) Modifier.weight(1f) else Modifier,
                                        isDocked = isDocked
                                    )
                                }

                                if (!isPlayerExpanded) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    StatusBar(modifier = Modifier.padding(horizontal = 24.dp))
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
