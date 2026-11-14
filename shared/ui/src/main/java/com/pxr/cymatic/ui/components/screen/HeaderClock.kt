package com.pxr.cymatic.ui.components.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Date

private val LocalClockState = staticCompositionLocalOf<State<String>?> { null }

@Composable
fun HeaderClockProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalClockState provides rememberClockState(), content = content)
}

@Composable
private fun rememberClockState(): State<String> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val timeText = remember {
        mutableStateOf(DateFormat.getTimeFormat(context).format(Date()))
    }

    DisposableEffect(context, lifecycleOwner) {
        fun updateTime() {
            timeText.value = DateFormat.getTimeFormat(context).format(Date())
        }

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    updateTime()
                }
            }

        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateTime()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return timeText
}

@Composable
fun HeaderClock(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    val clock = LocalClockState.current ?: rememberClockState()
    Text(text = clock.value, color = color, fontSize = 12.sp, maxLines = 1, modifier = modifier)
}
