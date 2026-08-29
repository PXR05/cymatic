package com.pxr.cymatic.ui.components.launcher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.data.store.LauncherStore
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun IdleGreeting(
    showClock: Boolean,
    showDay: Boolean,
    showDate: Boolean,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val use24Hour by LauncherStore.use24HourFlow.collectAsState(initial = true)

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            now = LocalDateTime.now()
        }
    }
    val time = remember(now, use24Hour) {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    val weekday = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault()))
            .uppercase(Locale.getDefault())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() }
                )
            }
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        if (showClock) {
            Text(
                text = time,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 40.sp,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (showDay) {
            Text(
                text = weekday,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 16.sp,
                letterSpacing = 6.sp
            )
        }
        if (showDate) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = date,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                letterSpacing = 6.sp
            )
        }
    }
}
