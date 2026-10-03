package com.pxr.cymatic.audio.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

internal fun isUsbAudioDevice(device: UsbDevice): Boolean =
    device.deviceClass == UsbConstants.USB_CLASS_AUDIO ||
            (0 until device.configurationCount).any { configuration ->
                val value = device.getConfiguration(configuration)
                (0 until value.interfaceCount).any {
                    value.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO
                }
            }
