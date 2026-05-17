package com.pxr.cymatic.ui.screens.settings
//
//import android.content.Context.AUDIO_SERVICE
//import android.media.AudioDeviceCallback
//import android.media.AudioDeviceInfo
//import android.media.AudioManager
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.verticalScroll
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.DisposableEffect
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.Font
//import androidx.compose.ui.text.font.FontFamily
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.lifecycle.viewmodel.compose.viewModel
//import com.decent.usbaudio.UsbAudioDevice
//import com.pxr.cymatic.R
//import com.pxr.cymatic.audio.resolveActiveOutput
//import com.pxr.cymatic.ui.components.common.BaseScreen
//import com.pxr.cymatic.ui.components.primitives.PixelDialog
//import com.pxr.cymatic.ui.components.primitives.PixelDialogButton
//import com.pxr.cymatic.ui.components.primitives.PixelDialogDivider
//import com.pxr.cymatic.ui.locals.LocalNavController
//import com.pxr.cymatic.usb.UsbConnectionStore
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//
//@Preview
//@Composable
//fun USBSettingsScreen(
//    modifier: Modifier = Modifier,
//    viewModel: EqViewModel = viewModel()
//) {
//    val navController = LocalNavController.current
//    val context = LocalContext.current
//    val fontFamily = FontFamily(Font(R.font.pixel))
//    val state by viewModel.uiState.collectAsState()
//    val audioManager = remember(context) {
//        context.getSystemService(AUDIO_SERVICE) as AudioManager
//    }
//    val usbAudioDevice = remember(context) {
//        UsbAudioDevice.getInstance(context)
//    }
//    val scope = rememberCoroutineScope()
//    var activeDevice by remember {
//        mutableStateOf(resolveActiveOutput(audioManager))
//    }
//    var usbDeviceTitle by remember { mutableStateOf("No USB device detected") }
//    var usbDeviceLines by remember { mutableStateOf<List<String>>(emptyList()) }
//    var showExclusiveDisclaimer by remember { mutableStateOf(false) }
//
//    fun refreshUsbDetails() {
//        scope.launch {
//            val details = withContext(Dispatchers.IO) {
//                val device = usbAudioDevice.findUsbAudioDevice()
//                    ?: return@withContext Pair("No USB device detected", emptyList<String>())
//                val header = device.productName?.takeIf { it.isNotBlank() }
//                    ?: "USB Audio Device"
//                if (!usbAudioDevice.hasPermission(device)) {
//                    val lines = listOf(
//                        "Permission required to read device info",
//                        "Vendor: 0x${device.vendorId.toString(16)}",
//                        "Product: 0x${device.productId.toString(16)}"
//                    )
//                    return@withContext Pair(header, lines)
//                }
//                val lines = mutableListOf<String>()
//                val sampleRate = usbAudioDevice.readSampleRate()
//                val supportedBitDepths = listOf(1, 2, 4, 8, 16, 32)
//                    .map { target ->
//                        usbAudioDevice.findAltSettingForBitDepth(target).second
//                    }
//                    .distinct()
//                    .sorted()
//                lines += "Vendor: 0x${device.vendorId.toString(16)}"
//                lines += "Product: 0x${device.productId.toString(16)}"
//                lines += "Interfaces: ${device.interfaceCount}"
//                lines += "Bit depths: ${supportedBitDepths.joinToString(", ")}"
//                lines += if (sampleRate > 0) {
//                    "Sample rate: $sampleRate Hz"
//                } else {
//                    "Sample rate: unknown"
//                }
//                Pair(header, lines)
//            }
//            usbDeviceTitle = details.first
//            usbDeviceLines = details.second
//        }
//    }
//
//    DisposableEffect(audioManager) {
//        val callback = object : AudioDeviceCallback() {
//            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
//                activeDevice = resolveActiveOutput(audioManager)
//                refreshUsbDetails()
//            }
//
//            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
//                activeDevice = resolveActiveOutput(audioManager)
//                refreshUsbDetails()
//            }
//        }
//
//        audioManager.registerAudioDeviceCallback(callback, null)
//        activeDevice = resolveActiveOutput(audioManager)
//        refreshUsbDetails()
//
//        onDispose {
//            audioManager.unregisterAudioDeviceCallback(callback)
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        refreshUsbDetails()
//    }
//
//    if (showExclusiveDisclaimer) {
//        PixelDialog(
//            title = "Enable Exclusive Mode",
//            onDismissRequest = { showExclusiveDisclaimer = false },
//            content = {
//                Text(
//                    text = "[DISCLAIMER] By enabling this feature, you acknowledge that:",
//                    fontFamily = fontFamily,
//                    fontSize = 14.sp,
//                    color = MaterialTheme.colorScheme.onBackground,
//                    modifier = Modifier.padding(horizontal = 24.dp)
//                )
//                Column(
//                    modifier = Modifier.padding(horizontal = 24.dp),
//                    verticalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    listOf(
//                        "The app will take exclusive control of the USB audio device",
//                        "EQ and other audio processing features are disabled",
//                        "No other apps will be able to play audio through the USB device",
//                        "Your device volume can only be controlled within this app or the DAC's controls",
//                    ).forEach {
//                        Row {
//                            Text(
//                                text = "- ",
//                                fontFamily = fontFamily,
//                                fontSize = 12.sp,
//                                color = MaterialTheme.colorScheme.secondary
//                            )
//                            Text(
//                                text = it,
//                                fontFamily = fontFamily,
//                                fontSize = 12.sp,
//                                color = MaterialTheme.colorScheme.secondary
//                            )
//                        }
//                    }
//                }
//            },
//            buttons = {
//                PixelDialogButton(
//                    text = "Cancel",
//                    onClick = { showExclusiveDisclaimer = false }
//                )
//                PixelDialogDivider(fontFamily = fontFamily)
//                PixelDialogButton(
//                    text = "Enable",
//                    onClick = {
//                        showExclusiveDisclaimer = false
//                        viewModel.setUsbExclusive(true)
//                        val device = usbAudioDevice.findUsbAudioDevice()
//                        if (device != null) {
//                            val info = usbAudioDevice.openDevice(device)
//                            if (info != null) {
//                                UsbConnectionStore.setConnection(info.connection)
//                            }
//                        }
//                    },
//                    color = MaterialTheme.colorScheme.primary
//                )
//            }
//        )
//    }
//
//    BaseScreen(
//        title = "USB Mode",
//        onBackClick = { navController.popBackStack() },
//        modifier = modifier
//    ) {
//        Column(
//            modifier = Modifier
//                .verticalScroll(rememberScrollState())
//                .fillMaxSize()
//                .padding(24.dp, 16.dp)
//        ) {
//            Text(
//                text = "Exclusive Mode",
//                fontFamily = fontFamily,
//                fontSize = 20.sp,
//                color = MaterialTheme.colorScheme.onBackground
//            )
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Text(
//                text = if (state.usbExclusiveEnabled) "ON" else "OFF",
//                fontFamily = fontFamily,
//                fontSize = 14.sp,
//                textAlign = TextAlign.Center,
//                color = if (state.usbExclusiveEnabled) MaterialTheme.colorScheme.background
//                else MaterialTheme.colorScheme.onBackground,
//                modifier = Modifier
//                    .border(1.dp, MaterialTheme.colorScheme.secondary)
//                    .background(
//                        if (state.usbExclusiveEnabled) MaterialTheme.colorScheme.onBackground
//                        else Color.Transparent
//                    )
//                    .fillMaxWidth()
//                    .padding(24.dp, 16.dp)
//                    .clickable(
//                        onClick = {
//                            if (state.usbExclusiveEnabled) {
//                                viewModel.setUsbExclusive(false)
//                                usbAudioDevice.closeDevice()
//                                UsbConnectionStore.setConnection(null)
//                            } else {
//                                showExclusiveDisclaimer = true
//                            }
//                        },
//                        indication = null,
//                        interactionSource = null
//                    )
//            )
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Text(
//                text = "Exclusive USB Audio Access bypassing the operating system's audio mixer for \"bit-perfect\" playback.",
//                fontFamily = fontFamily,
//                fontSize = 14.sp,
//                color = MaterialTheme.colorScheme.secondary
//            )
//
//            Spacer(modifier = Modifier.height(24.dp))
//
//            Text(
//                text = "Connected device",
//                fontFamily = fontFamily,
//                fontSize = 20.sp,
//                color = MaterialTheme.colorScheme.onBackground
//            )
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            val deviceText = if (activeDevice.type == "USB") {
//                "USB DAC: ${activeDevice.label}"
//            } else {
//                "No USB DAC detected"
//            }
//            Text(
//                text = deviceText,
//                fontFamily = fontFamily,
//                fontSize = 14.sp,
//                color = MaterialTheme.colorScheme.onBackground
//            )
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            if (activeDevice.type == "USB") {
//                Column(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .border(1.dp, MaterialTheme.colorScheme.secondary)
//                        .background(MaterialTheme.colorScheme.background)
//                        .padding(16.dp)
//                ) {
//                    Text(
//                        text = usbDeviceTitle,
//                        fontFamily = fontFamily,
//                        fontSize = 16.sp,
//                        color = MaterialTheme.colorScheme.onBackground
//                    )
//                    Spacer(modifier = Modifier.height(8.dp))
//                    usbDeviceLines.forEach { line ->
//                        Text(
//                            text = line,
//                            fontFamily = fontFamily,
//                            fontSize = 12.sp,
//                            color = MaterialTheme.colorScheme.secondary
//                        )
//                    }
//                }
//            }
//        }
//    }
//}
//
