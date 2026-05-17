package com.pxr.cymatic.usb

import android.hardware.usb.UsbDeviceConnection

object UsbConnectionStore {
    @Volatile
    private var connection: UsbDeviceConnection? = null

    fun setConnection(newConnection: UsbDeviceConnection?) {
        connection = newConnection
    }

    fun getConnection(): UsbDeviceConnection? = connection
}

