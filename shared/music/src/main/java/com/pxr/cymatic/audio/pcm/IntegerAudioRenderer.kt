package com.pxr.cymatic.audio.pcm

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DecoderAudioRenderer

@UnstableApi
internal class IntegerAudioRenderer(
    private val rendererName: String,
    handler: Handler,
    listener: AudioRendererEventListener,
    sink: AudioSink,
    private val outputFormat: (Format) -> Format?,
    private val decoderConfiguration: (Format) -> IntegerDecoderConfiguration,
) : DecoderAudioRenderer<IntegerPcmDecoder>(handler, listener, sink) {
    override fun getName(): String = rendererName

    override fun supportsFormatInternal(format: Format): Int {
        val pcm = outputFormat(format) ?: return C.FORMAT_UNSUPPORTED_SUBTYPE
        return if (sinkSupportsFormat(pcm)) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE
    }

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): IntegerPcmDecoder =
        IntegerPcmDecoder(decoderConfiguration(format))

    override fun getOutputFormat(decoder: IntegerPcmDecoder): Format = decoder.outputFormat
}
