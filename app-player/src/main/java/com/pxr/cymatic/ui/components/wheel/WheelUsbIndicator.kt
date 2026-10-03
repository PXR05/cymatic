package com.pxr.cymatic.ui.components.wheel

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.design.R

@Composable
internal fun WheelUsbIndicator() {
    val active by UsbPlaybackState.active.collectAsState()
    if (active) {
        Icon(
            painterResource(R.drawable.ic_pixel_usb),
            "Direct USB output active",
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
