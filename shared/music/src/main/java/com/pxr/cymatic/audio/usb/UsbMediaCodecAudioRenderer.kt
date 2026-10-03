package com.pxr.cymatic.audio.usb

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.pxr.cymatic.audio.alac.integerAlacMetadata
import com.pxr.cymatic.audio.flac.integerFlacMetadata

@UnstableApi
internal class UsbMediaCodecAudioRenderer(
    context: Context,
    adapterFactory: MediaCodecAdapter.Factory,
    selector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    handler: Handler,
    listener: AudioRendererEventListener,
    sink: AudioSink,
) :
    MediaCodecAudioRenderer(
        context,
        adapterFactory,
        selector,
        enableDecoderFallback,
        handler,
        listener,
        sink,
    ) {
    override fun supportsFormat(selector: MediaCodecSelector, format: Format): Int {
        if (
            format.integerFlacMetadata() != null ||
            format.integerAlacMetadata() != null ||
            (UsbPlaybackState.routeToUsb && !DirectUsbSourcePolicy.canDecode(format))
        ) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        }
        return super.supportsFormat(selector, format)
    }

    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxInputSize: Int,
        codecOperatingRate: Float,
    ): MediaFormat =
        super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate).apply {
            if (
                UsbPlaybackState.routeToUsb && DirectUsbSourcePolicy.canDecodeWithPlatform(format)
            ) {
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
        }

    override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        if (
            UsbPlaybackState.routeToUsb &&
            DirectUsbSourcePolicy.canDecodeWithPlatform(format) &&
            mediaFormat != null
        ) {
            val sameLayout =
                mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) == format.sampleRate &&
                        mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == format.channelCount
            val encoding =
                mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            if (!sameLayout || encoding != AudioFormat.ENCODING_PCM_16BIT) {
                throw createRendererException(
                    IllegalStateException(
                        "Decoder changed the direct USB rate, channels or PCM encoding"
                    ),
                    format,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                )
            }
        }
        super.onOutputFormatChanged(format, mediaFormat)
    }
}
