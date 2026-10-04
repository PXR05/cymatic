package com.pxr.cymatic.ui.components.wheel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.audio.usb.UsbPlaybackState
import com.pxr.cymatic.design.R
import com.pxr.cymatic.ui.components.trackTextHeight

@Composable
internal fun WheelUsbIndicator(boxed: Boolean = false) {
    val active by UsbPlaybackState.active.collectAsState()
    if (active) {
        val modifier =
            if (boxed) {
                val textHeight =
                    trackTextHeight(
                        12.sp,
                        MaterialTheme.typography.bodyLarge.lineHeight,
                        1,
                        FontWeight.SemiBold,
                    )
                Modifier.height(textHeight + 4.dp)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            } else Modifier
        Box(
            modifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_pixel_diamond),
                "Direct USB output active",
                Modifier.size(16.dp),
                tint =
                    if (boxed) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
