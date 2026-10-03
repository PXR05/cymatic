package com.pxr.cymatic.audio.usb

import org.json.JSONObject

internal data class UsbPcmLayout(val channels: Int, val containerBytes: Int, val validBits: Int) {
    init {
        require(channels in 1..2 && containerBytes in 2..4 && validBits in 1..containerBytes * 8)
    }

    val frameBytes: Int
        get() = channels * containerBytes

    val channelMask: Long
        get() = if (channels == 1) 4L else 3L

    val description: String
        get() = "$validBits-bit ${if (channels == 1) "mono" else "stereo"}"

    fun json() =
        JSONObject()
            .put("channels", channels)
            .put("containerBytes", containerBytes)
            .put("validBits", validBits)
            .put("alignment", "SIGNED_LITTLE_ENDIAN_MSB")
}
