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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import com.pxr.cymatic.ui.components.StatusBar
import com.pxr.cymatic.ui.components.player.PlayerBar
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.rememberPlaybackState
import com.pxr.cymatic.ui.screen.AlbumSongsScreen
import com.pxr.cymatic.ui.screen.AlbumsScreen
import com.pxr.cymatic.ui.screen.AllSongsScreen
import com.pxr.cymatic.ui.screen.ArtistSongsScreen
import com.pxr.cymatic.ui.screen.ArtistsScreen
import com.pxr.cymatic.ui.screen.HomeScreen
import com.pxr.cymatic.ui.screen.SettingsScreen
import com.pxr.cymatic.ui.screen.UnknownAlbum
import com.pxr.cymatic.ui.screen.UnknownArtist
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
                this, arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 1000
            )
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1000
            )
        }

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val audioAttributionContext =
            createAttributionContext("audioPlayback")
        controllerFuture =
            MediaController.Builder(audioAttributionContext, sessionToken).buildAsync()

        setContent {
            val context = LocalContext.current
            val navController = rememberNavController()
            var mediaController by remember { mutableStateOf<MediaController?>(null) }
            var audioFiles by remember { mutableStateOf(emptyList<AudioFile>()) }

            LaunchedEffect(Unit) {
                val start = System.currentTimeMillis()
                audioFiles = withContext(Dispatchers.IO) { loadCachedAudioFiles(context) }
                audioFiles = withContext(Dispatchers.IO) { syncAudioFilesToDb(context) }
                val end = System.currentTimeMillis()
                Log.d("MainActivity", "Loaded ${audioFiles.size} audio files in ${end - start} ms")
                isReady = true
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
                "all_songs" to { AllSongsScreen(audioFiles) },
                "artists" to { ArtistsScreen(audioFiles) },
                "albums" to { AlbumsScreen(audioFiles) },
                "artist/{artistName}" to { backStackEntry ->
                    val rawName = backStackEntry.arguments?.getString("artistName")
                    val artistName = rawName?.let(Uri::decode) ?: UnknownArtist
                    ArtistSongsScreen(artistName = artistName, audioFiles = audioFiles)
                },
                "album/{albumName}" to { backStackEntry ->
                    val rawName = backStackEntry.arguments?.getString("albumName")
                    val albumName = rawName?.let(Uri::decode) ?: UnknownAlbum
                    AlbumSongsScreen(albumName = albumName, audioFiles = audioFiles)
                },
                "settings" to { SettingsScreen() }
            )

            CymaticTheme {
                CompositionLocalProvider(
                    LocalMediaController provides mediaController,
                    LocalNavController provides navController
                ) {
                    val playbackState = rememberPlaybackState(mediaController)

                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column {
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
                            Column(
                                modifier = Modifier.padding(
                                    bottom = WindowInsets.systemBars.asPaddingValues()
                                        .calculateBottomPadding()
                                )
                            ) {
                                if (playbackState.currentMediaId != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    PlayerBar(audioFiles = audioFiles)

                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.secondary)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                StatusBar()

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
