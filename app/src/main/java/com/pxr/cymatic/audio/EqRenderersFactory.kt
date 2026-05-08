package com.pxr.cymatic.audio

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EqRenderersFactory(
    context: Context,
    val eqAudioProcessor: EqAudioProcessor
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true)
            .setAudioProcessors(arrayOf(eqAudioProcessor))
            .build()
    }
}
