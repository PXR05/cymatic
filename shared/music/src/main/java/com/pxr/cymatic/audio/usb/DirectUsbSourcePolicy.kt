package com.pxr.cymatic.audio.usb

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.audio.alac.integerAlacMetadata
import com.pxr.cymatic.audio.flac.integerFlacMetadata
import java.util.Locale

@UnstableApi
internal object DirectUsbSourcePolicy {
    private val sourceMimeTypes =
        setOf(
            "audio/flac",
            "audio/x-flac",
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/vnd.wave",
            "audio/alac",
            "audio/x-alac",
            "audio/mp4",
            "audio/x-m4a",
            "audio/m4a",
            "audio/mp4a-latm",
            "audio/mpeg",
            "audio/mp3",
            "audio/x-mp3",
            "audio/ogg",
            "application/ogg",
            "audio/vorbis",
            "audio/opus",
            "audio/x-ogg",
        )
    private val platformMimeTypes =
        setOf(MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_VORBIS, MimeTypes.AUDIO_OPUS)

    fun isCandidate(mimeType: String?): Boolean =
        mimeType.isNullOrBlank() || mimeType.lowercase(Locale.ROOT) in sourceMimeTypes

    fun canDecode(format: Format): Boolean =
        format.usbPcmLayout() != null ||
                format.integerFlacMetadata() != null ||
                format.integerAlacMetadata() != null ||
                canDecodeWithPlatform(format)

    fun canDecodeWithPlatform(format: Format): Boolean =
        format.sampleMimeType in platformMimeTypes &&
                format.cryptoType == C.CRYPTO_TYPE_NONE &&
                format.channelCount in 1..2 &&
                format.sampleRate in 8000..384000
}
