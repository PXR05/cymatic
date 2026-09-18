package com.pxr.cymatic

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.theme.CymaticTheme

abstract class MusicActivity : ComponentActivity() {
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

            CymaticTheme {
                CompositionLocalProvider(
                    LocalMediaController provides mediaController,
                    LocalNavController provides navController
                ) {
                    AppContent()
                }
            }
        }
    }

    @Composable
    protected abstract fun AppContent()

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
