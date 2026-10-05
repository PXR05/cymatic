package com.pxr.cymatic.audio.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.media3.common.util.UnstableApi
import java.io.Closeable

@UnstableApi
internal class UsbConnectionManager(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onWaiting: (String) -> Unit,
) : Closeable {
    private val manager = context.getSystemService(UsbManager::class.java)
    private val permissionAction = "${context.packageName}.USB_PLAYBACK_PERMISSION"
    private var started = false
    private var enabled = false
    private var readyDevice: Int? = null
    private var requestedDevice: Int? = null
    private var deniedDevice: Int? = null
    private var permissionIntent: PendingIntent? = null
    val ready
        get() = enabled && readyDevice != null

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device =
                    IntentCompat.getParcelableExtra(
                        intent,
                        UsbManager.EXTRA_DEVICE,
                        UsbDevice::class.java,
                    )
                when (intent.action) {
                    permissionAction -> {
                        if (
                            device == null ||
                                device.deviceId != requestedDevice ||
                                requestedDevice == null
                        )
                            return
                        val granted =
                            manager.hasPermission(device) &&
                                intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED,
                                    false,
                                )
                        clearRequest()
                        if (!granted) deniedDevice = device.deviceId
                        refresh()
                    }

                    UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                        if (device != null && isUsbAudioDevice(device)) refresh()

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        if (device == null || !isUsbAudioDevice(device)) return
                        if (device.deviceId == requestedDevice) clearRequest()
                        if (device.deviceId == deniedDevice) deniedDevice = null
                        if (device.deviceId == readyDevice)
                            waitForDevice("Waiting for USB DAC · device disconnected")
                        refresh()
                    }

                    ACTION_REFRESH -> refresh()
                }
            }
        }

    fun start(enabled: Boolean) {
        if (!started) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter().apply {
                    addAction(permissionAction)
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                    addAction(ACTION_REFRESH)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            started = true
        }
        setEnabled(enabled)
    }

    fun setEnabled(value: Boolean) {
        if (enabled != value) deniedDevice = null
        enabled = value
        if (!value) {
            clearRequest()
            readyDevice = null
            UsbDsdSupport.clear()
            UsbPlaybackState.deviceReady = false
            return
        }
        refresh()
    }

    fun refresh() {
        if (!started || !enabled) return
        val devices = manager.deviceList.values.filter(::isUsbAudioDevice)
        val device = devices.singleOrNull()
        if (device == null) {
            clearRequest()
            waitForDevice(
                if (devices.isEmpty()) "Waiting for USB DAC" else "Connect exactly one USB DAC"
            )
            return
        }
        if (manager.hasPermission(device)) {
            clearRequest()
            deniedDevice = null
            if (readyDevice != device.deviceId) {
                readyDevice = device.deviceId
                UsbDsdSupport.refresh(context, device)
                UsbPlaybackState.deviceReady = true
                UsbPlaybackState.update("USB DAC connected · direct output ready")
                onReady()
            }
            return
        }
        waitForDevice(
            if (deniedDevice == device.deviceId) "USB access denied · open USB audio or reconnect"
            else "Waiting for USB access"
        )
        if (requestedDevice == device.deviceId || deniedDevice == device.deviceId) return
        clearRequest()
        try {
            requestedDevice = device.deviceId
            permissionIntent =
                PendingIntent.getBroadcast(
                    context,
                    device.deviceId,
                    Intent(permissionAction).setPackage(context.packageName),
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            manager.requestPermission(device, permissionIntent)
        } catch (e: RuntimeException) {
            deniedDevice = device.deviceId
            clearRequest()
            waitForDevice("Could not request USB access: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun waitForDevice(message: String) {
        readyDevice = null
        UsbDsdSupport.clear()
        UsbPlaybackState.deviceReady = false
        onWaiting(message)
    }

    private fun clearRequest() {
        permissionIntent?.cancel()
        permissionIntent = null
        requestedDevice = null
    }

    override fun close() {
        clearRequest()
        if (started) context.unregisterReceiver(receiver)
        started = false
        readyDevice = null
        UsbDsdSupport.clear()
        UsbPlaybackState.deviceReady = false
    }

    fun refreshDsdOutput() {
        val device = manager.deviceList.values.singleOrNull { it.deviceId == readyDevice }
        if (device != null) UsbDsdSupport.refresh(context, device) else UsbDsdSupport.clear()
    }

    companion object {
        private const val ACTION_REFRESH = "com.pxr.cymatic.USB_PLAYBACK_REFRESH"

        fun refresh(context: Context) {
            context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
        }
    }
}
