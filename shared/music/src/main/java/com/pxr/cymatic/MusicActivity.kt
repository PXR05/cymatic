package com.pxr.cymatic

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.pxr.cymatic.audio.usb.UsbConnectionManager
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.audio.usb.UsbVolumeState
import com.pxr.cymatic.ui.locals.LocalMediaController
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.theme.CymaticTheme

abstract class MusicActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val mainViewModel: MainViewModel by viewModels()

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val permissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1000)
        }

        val sessionToken =
            SessionToken(
                this,
                ComponentName(this, PlaybackService::class.java),
            )
        val audioAttributionContext = createAttributionContext("audioPlayback")
        controllerFuture =
            MediaController.Builder(audioAttributionContext, sessionToken).buildAsync()

        setContent {
            val navController = rememberNavController()
            var mediaController by remember { mutableStateOf<MediaController?>(null) }

            DisposableEffect(Unit) {
                controllerFuture?.let { future ->
                    future.addListener(
                        {
                            try {
                                mediaController = future.get()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        MoreExecutors.directExecutor(),
                    )
                }
                onDispose {}
            }

            CymaticTheme {
                CompositionLocalProvider(
                    LocalMediaController provides mediaController,
                    LocalNavController provides navController,
                ) {
                    AppContent()
                }
            }
        }
    }

    @Composable protected abstract fun AppContent()

    override fun onResume() {
        super.onResume()
        mainViewModel.performInitialScan()
        UsbConnectionManager.refresh(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000) mainViewModel.performInitialScan()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            UsbPlaybackState.active.value &&
                (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                    event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                UsbVolumeState.adjust(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 2 else -2)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
