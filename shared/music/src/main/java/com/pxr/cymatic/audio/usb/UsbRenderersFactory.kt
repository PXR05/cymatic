package com.pxr.cymatic.audio.usb

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.pxr.cymatic.audio.EqAudioProcessor
import com.pxr.cymatic.audio.alac.integerAlacDecoderConfiguration
import com.pxr.cymatic.audio.alac.integerAlacOutputFormat
import com.pxr.cymatic.audio.dsd.dsdDecoderConfiguration
import com.pxr.cymatic.audio.flac.integerFlacDecoderConfiguration
import com.pxr.cymatic.audio.flac.integerFlacOutputFormat
import com.pxr.cymatic.audio.pcm.IntegerAudioRenderer

@UnstableApi
internal class UsbRenderersFactory(context: Context, private val equalizer: EqAudioProcessor) :
    DefaultRenderersFactory(context) {
    init {
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableOffload: Boolean,
    ): AudioSink =
        UsbAudioSink(
            context,
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setAudioProcessors(arrayOf(equalizer))
                .build(),
        )

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        out.add(
            IntegerAudioRenderer(
                "DsdAudioRenderer",
                eventHandler,
                eventListener,
                audioSink,
                { UsbDsdSupport.outputFormat(it) },
                { it.dsdDecoderConfiguration(requireNotNull(UsbDsdSupport.outputFormat(it))) },
            )
        )
        out.add(
            IntegerAudioRenderer(
                "IntegerFlacAudioRenderer",
                eventHandler,
                eventListener,
                audioSink,
                { it.integerFlacOutputFormat() },
                { it.integerFlacDecoderConfiguration() },
            )
        )
        out.add(
            IntegerAudioRenderer(
                "IntegerAlacAudioRenderer",
                eventHandler,
                eventListener,
                audioSink,
                { it.integerAlacOutputFormat() },
                { it.integerAlacDecoderConfiguration() },
            )
        )
        out.add(
            UsbMediaCodecAudioRenderer(
                context,
                codecAdapterFactory,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                audioSink,
            )
        )
    }
}
