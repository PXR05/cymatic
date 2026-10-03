package com.pxr.cymatic.audio.usb

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal fun Format.usbPcmLayout(): UsbPcmLayout? {
    if (
        sampleMimeType != MimeTypes.AUDIO_RAW ||
        channelCount !in 1..2 ||
        sampleRate !in 8000..384000 ||
        cryptoType != C.CRYPTO_TYPE_NONE
    )
        return null
    val bytes =
        when (pcmEncoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT -> 4
            else -> return null
        }
    val declared = customData as? UsbPcmLayout
    if (declared != null) {
        return declared.takeIf { it.channels == channelCount && it.containerBytes == bytes }
    }
    return UsbPcmLayout(channelCount, bytes, bytes * 8)
}

@UnstableApi
internal fun Format.usbPcmBits(): Int = usbPcmLayout()?.validBits ?: 0
