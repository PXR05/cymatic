package com.pxr.cymatic

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pxr.cymatic.data.store.SettingsStore
import com.pxr.cymatic.data.store.interfaceSettingsFlow
import com.pxr.cymatic.ui.components.wheel.WheelPlayer
import kotlinx.coroutines.launch

class MainActivity : MusicActivity() {
    private var standby: StandbyController? = null
    private var wakingKey: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterFullscreen()
        standby = StandbyController(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SettingsStore.interfaceSettingsFlow.collect {
                    standby?.configure(it.standbyTimeoutMs)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
        standby?.focus(hasFocus && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    override fun onResume() {
        super.onResume()
        standby?.focus(hasWindowFocus())
    }

    override fun onPause() {
        standby?.focus(false)
        super.onPause()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        standby?.interact()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (standby?.asleep == true && event.action == KeyEvent.ACTION_DOWN)
            wakingKey = event.keyCode
        standby?.interact()
        if (wakingKey == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) wakingKey = null
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        standby?.close()
        standby = null
        super.onDestroy()
    }

    private fun enterFullscreen() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    @Composable
    override fun AppContent() {
        WheelPlayer()
    }
}
