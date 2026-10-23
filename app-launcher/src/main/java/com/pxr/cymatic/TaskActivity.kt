package com.pxr.cymatic

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.audio.usb.UsbVolumeState
import com.pxr.cymatic.ui.locals.LocalNavController
import com.pxr.cymatic.ui.theme.CymaticTheme

abstract class TaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            CymaticTheme {
                CompositionLocalProvider(
                    LocalNavController provides navController
                ) {
                    AppContent()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            UsbPlaybackState.volumeActive.value &&
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

    @Composable
    protected abstract fun AppContent()
}
