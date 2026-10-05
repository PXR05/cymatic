package com.pxr.cymatic.data.media

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.audio.dsd.DSD_MIME_TYPE
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class AudioTechnicalMetadata(
    val bitRate: Long?,
    val sampleRate: Long?,
    val codec: String?,
)

@UnstableApi
internal fun Format.audioTechnicalMetadata(): AudioTechnicalMetadata {
    val cookieRate =
        if (sampleMimeType == MimeTypes.AUDIO_ALAC) {
            initializationData
                .singleOrNull()
                ?.takeIf { it.size >= 24 }
                ?.let {
                    ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).getInt(16).toLong() and
                        0xffffffffL
                }
                ?.takeIf { it > 0 }
        } else null
    return AudioTechnicalMetadata(
        averageBitrate.toLong().takeIf { it > 0 } ?: cookieRate,
        sampleRate.toLong().takeIf { it > 0 },
        sampleMimeType?.takeIf { it in technicalCodecMimeTypes },
    )
}

private val technicalCodecMimeTypes =
    setOf(
        DSD_MIME_TYPE,
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_FLAC,
        MimeTypes.AUDIO_MPEG,
        MimeTypes.AUDIO_VORBIS,
        MimeTypes.AUDIO_OPUS,
    )
