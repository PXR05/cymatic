package com.pxr.cymatic.ui.components.common

import android.media.AudioDeviceInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pxr.cymatic.R

@Composable
fun OutputSwitcherDialog(
    modifier: Modifier = Modifier,
    showDialog: Boolean = false,
    devices: List<AudioDeviceInfo>,
    defaultDevice: AudioDeviceInfo? = null,
    onDismissRequest: () -> Unit,
    onConfirmation: (AudioDeviceInfo?) -> Unit,
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    val fontFamily = FontFamily(Font(R.font.pixel))
    val dialogWidth =
        with(window) { (containerSize.width * 0.8f) }
    val dialogWidthDp = with(density) { dialogWidth.toDp() }
    val dialogHeight = with(window) { (containerSize.height * 0.5f) }
    val dialogHeightDp = with(density) { dialogHeight.toDp() }
    var selectedDevice: AudioDeviceInfo? by remember(defaultDevice) { mutableStateOf(defaultDevice) }

    if (!showDialog) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = modifier
                .border(1.dp, MaterialTheme.colorScheme.secondary)
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp)
                .width(dialogWidthDp)
                .heightIn(max = dialogHeightDp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Output Device",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontFamily = fontFamily,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                items(devices.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (devices[index] == selectedDevice)
                                    MaterialTheme.colorScheme.onBackground
                                else
                                    MaterialTheme.colorScheme.background
                            )
                            .clickable(
                                onClick = { selectedDevice = devices[index] },
                                indication = null,
                                interactionSource = null
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[${mapDeviceToType(devices[index])}] ",
                            color = if (devices[index] == selectedDevice)
                                MaterialTheme.colorScheme.background
                            else
                                MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontFamily = fontFamily,
                        )


                        Text(
                            text = devices[index].productName.toString(),
                            color = if (devices[index] == selectedDevice)
                                MaterialTheme.colorScheme.background
                            else
                                MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontFamily = fontFamily,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Close",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            onClick = onDismissRequest,
                            indication = null,
                            interactionSource = null
                        )
                )

                Text(
                    text = "|",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontFamily = fontFamily,
                )

                Text(
                    text = "Done",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            onClick = {
                                onConfirmation(selectedDevice)
                                onDismissRequest()
                            },
                            indication = null,
                            interactionSource = null
                        )
                )
            }
        }
    }
}

private fun mapDeviceToType(device: AudioDeviceInfo): String {
    return when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT"

        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "AUX"

        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"

        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE"

        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EAR"

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPK"

        else -> "UNK"
    }
}

