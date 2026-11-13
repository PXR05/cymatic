package com.pxr.cymatic.audio.eq

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.chronosauros.contour.usb.DacHandle

internal class DacConnectionMonitor(
    private val context: Context,
    private val onChanged: () -> Unit,
    private val onMessage: (String) -> Unit,
) : AutoCloseable {
    val manager = context.getSystemService(UsbManager::class.java)
    private val permissionAction = "${context.packageName}.HARDWARE_EQ_PERMISSION"
    private var permissionRequest: PendingIntent? = null
    private var requestedDevice: Int? = null
    val permissionPending
        get() = requestedDevice != null

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device =
                    IntentCompat.getParcelableExtra(
                        intent,
                        UsbManager.EXTRA_DEVICE,
                        UsbDevice::class.java,
                    )
                if (intent.action == permissionAction) {
                    if (device == null || device.deviceId != requestedDevice) return
                    clearRequest()
                    onChanged()
                    if (!manager.hasPermission(device)) onMessage("USB access denied")
                } else {
                    if (
                        intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED &&
                            device?.deviceId == requestedDevice
                    ) {
                        clearRequest()
                    }
                    onChanged()
                }
            }
        }

    init {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(permissionAction)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun devices(): List<DacHandle> =
        manager.deviceList.values.mapNotNull(DacHandle::of).sortedBy { it.target.caps.name }

    fun hasPermission(handle: DacHandle?) = handle?.usb?.let(manager::hasPermission) == true

    fun requestAccess(handle: DacHandle) {
        val device = handle.usb ?: return
        if (permissionPending) return
        if (manager.hasPermission(device)) {
            onChanged()
            return
        }
        requestedDevice = device.deviceId
        permissionRequest =
            PendingIntent.getBroadcast(
                context,
                device.deviceId,
                Intent(permissionAction).setPackage(context.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            )
        try {
            manager.requestPermission(device, permissionRequest)
            onMessage("Waiting for USB access")
        } catch (e: Exception) {
            clearRequest()
            onMessage(e.message ?: "USB access request failed")
        }
        onChanged()
    }

    override fun close() {
        clearRequest()
        context.unregisterReceiver(receiver)
    }

    private fun clearRequest() {
        permissionRequest?.cancel()
        permissionRequest = null
        requestedDevice = null
    }
}
