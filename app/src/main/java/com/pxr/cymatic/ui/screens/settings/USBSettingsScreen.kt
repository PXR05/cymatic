package com.pxr.cymatic.ui.screens.settings

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pxr.cymatic.R
import com.pxr.cymatic.audio.resolveActiveOutput
import com.pxr.cymatic.ui.components.common.BaseScreen
import com.pxr.cymatic.ui.locals.LocalNavController

@Preview
@Composable
fun USBSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: EqViewModel = viewModel()
) {
    val navController = LocalNavController.current
    val context = LocalContext.current
    val fontFamily = FontFamily(Font(R.font.pixel))
    val state by viewModel.uiState.collectAsState()
    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    var activeDevice by remember {
        mutableStateOf(resolveActiveOutput(audioManager))
    }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                activeDevice = resolveActiveOutput(audioManager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                activeDevice = resolveActiveOutput(audioManager)
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        activeDevice = resolveActiveOutput(audioManager)

        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    BaseScreen(
        title = "USB Mode",
        onBackClick = { navController.popBackStack() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp, 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Exclusive Mode",
                    fontFamily = fontFamily,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (state.usbExclusiveEnabled) "I" else "O",
                    fontFamily = fontFamily,
                    fontSize = 14.sp,
                    color = if (state.usbExclusiveEnabled) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.secondary)
                        .background(
                            if (state.usbExclusiveEnabled) MaterialTheme.colorScheme.onBackground
                            else Color.Transparent
                        )
                        .padding(24.dp, 16.dp)
                        .clickable(
                            onClick = { viewModel.setUsbExclusive(!state.usbExclusiveEnabled) },
                            indication = null,
                            interactionSource = null
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Exclusive mode prioritizes the USB DAC and may improve performance, some devices may not support it.",
                fontFamily = fontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Connected device",
                fontFamily = fontFamily,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            val deviceText = if (activeDevice.type == "USB") {
                "USB DAC: ${activeDevice.label}"
            } else {
                "No USB DAC detected"
            }
            Text(
                text = deviceText,
                fontFamily = fontFamily,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
