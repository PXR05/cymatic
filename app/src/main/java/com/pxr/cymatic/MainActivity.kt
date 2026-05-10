package com.pxr.cymatic

import android.Manifest
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pxr.cymatic.data.media.loadCachedAudioFiles
import com.pxr.cymatic.data.media.syncAudioFilesToDb
import com.pxr.cymatic.data.model.AudioFile
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.ui.components.common.StatusBar
import com.pxr.cymatic.ui.components.player.PlayerBar
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.screens.home.HomeScreen
import com.pxr.cymatic.ui.screens.library.AlbumSongsScreen
import com.pxr.cymatic.ui.screens.library.AlbumsScreen
import com.pxr.cymatic.ui.screens.library.AllSongsScreen
import com.pxr.cymatic.ui.screens.library.ArtistSongsScreen
import com.pxr.cymatic.ui.screens.library.ArtistsScreen
import com.pxr.cymatic.ui.screens.library.UnknownAlbum
import com.pxr.cymatic.ui.screens.library.UnknownArtist
import com.pxr.cymatic.ui.screens.settings.EQSettingsScreen
import com.pxr.cymatic.ui.screens.settings.SettingsScreen
import com.pxr.cymatic.ui.screens.settings.SourceSettingsScreen
import com.pxr.cymatic.ui.screens.settings.StorageSettingsScreen
import com.pxr.cymatic.ui.screens.settings.VersionSettingsScreen
import com.pxr.cymatic.ui.state.rememberPlaybackState
import com.pxr.cymatic.ui.theme.CymaticTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isReady by mutableStateOf(false)

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
            val context = LocalContext.current
            val navController = rememberNavController()
            var mediaController by remember { mutableStateOf<MediaController?>(null) }
            var audioFiles by remember { mutableStateOf(emptyList<AudioFile>()) }
            val locked by SettingsStore.lockedFlow.collectAsState(initial = false)
            val lastScanTimeMs by SettingsStore.lastScanTimeMsFlow.collectAsState(initial = 0L)

            LaunchedEffect(Unit) {
                val start = System.currentTimeMillis()
                val scanDirectories = SettingsStore.getScanDirectories()
                val scanAllMedia = SettingsStore.getScanAllMedia()
                audioFiles = withContext(Dispatchers.IO) { loadCachedAudioFiles(context) }
                audioFiles = withContext(Dispatchers.IO) {
                    syncAudioFilesToDb(context, scanDirectories, scanAllMedia)
                }
                val end = System.currentTimeMillis()
                SettingsStore.setLastScanTimeMs(end)
                SettingsStore.setLastScanCount(audioFiles.size.toLong())
                SettingsStore.setLastScanDurationMs(end - start)
                Log.d("MainActivity", "Loaded ${audioFiles.size} audio files in ${end - start} ms")
                isReady = true
            }

            LaunchedEffect(locked) {
                val windowInsetsController =
                    WindowCompat.getInsetsController(window, window.decorView)
                if (locked) {
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
            }

            LaunchedEffect(lastScanTimeMs) {
                if (lastScanTimeMs <= 0L) return@LaunchedEffect
                audioFiles = withContext(Dispatchers.IO) { loadCachedAudioFiles(context) }
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
                        audioFiles,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                "artists" to { ArtistsScreen(audioFiles) },
                "artist/{artistName}?scrollId={scrollId}" to { entry ->
                    val rawName = entry.arguments?.getString("artistName")
                    val artistName = rawName?.let(Uri::decode) ?: UnknownArtist
                    val scrollId = entry.arguments?.getString("scrollId")
                    ArtistSongsScreen(
                        artistName = artistName,
                        audioFiles = audioFiles,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                "albums" to { AlbumsScreen(audioFiles) },
                "album/{albumName}?scrollId={scrollId}" to { entry ->
                    val rawName = entry.arguments?.getString("albumName")
                    val albumName = rawName?.let(Uri::decode) ?: UnknownAlbum
                    val scrollId = entry.arguments?.getString("scrollId")
                    AlbumSongsScreen(
                        albumName = albumName,
                        audioFiles = audioFiles,
                        scrollTargetId = scrollId?.toLongOrNull()
                    )
                },
                "settings" to { SettingsScreen() },
                "setting/eq" to { EQSettingsScreen() },
                "setting/storage" to { StorageSettingsScreen() },
                "setting/source" to { SourceSettingsScreen() },
                "setting/version" to { VersionSettingsScreen() }
            )

            CymaticTheme {
                CompositionLocalProvider(
                    LocalMediaController provides mediaController,
                    LocalNavController provides navController
                ) {
                    val playbackState = rememberPlaybackState(mediaController)

                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column {
                            if (!locked) {
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
                                    if (!locked) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(MaterialTheme.colorScheme.secondary)
                                        )

                                        Spacer(modifier = Modifier.height(24.dp))
                                    }

                                    PlayerBar(
                                        audioFiles = audioFiles,
                                        modifier = if (locked) Modifier.weight(1f) else Modifier
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (!locked) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    StatusBar(modifier = Modifier.padding(horizontal = 24.dp))
                                }

                                Spacer(modifier = Modifier.height(8.dp))
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
