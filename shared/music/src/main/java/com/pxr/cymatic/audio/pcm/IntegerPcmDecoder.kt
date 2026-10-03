package com.pxr.cymatic.audio.pcm

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer

@UnstableApi
internal class IntegerPcmDecoder(private val configuration: IntegerDecoderConfiguration) :
    SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, DecoderException>(
        arrayOfNulls(4),
        arrayOfNulls(4),
    ) {
    val outputFormat: Format
        get() = configuration.outputFormat

    private val native: IntegerFrameDecoder

    init {
        try {
            setInitialInputBufferSize(configuration.initialInputBytes)
            native = configuration.open()
        } catch (e: Exception) {
            super.release()
            throw DecoderException("${configuration.name} initialization failed", e)
        } catch (e: LinkageError) {
            super.release()
            throw DecoderException("${configuration.name} unavailable", e)
        }
    }

    override fun getName(): String = configuration.name

    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): SimpleDecoderOutputBuffer =
        SimpleDecoderOutputBuffer(::releaseOutputBuffer)

    override fun createUnexpectedDecodeException(error: Throwable): DecoderException =
        DecoderException("${configuration.name} decoding failed", error)

    override fun decode(
        input: DecoderInputBuffer,
        output: SimpleDecoderOutputBuffer,
        reset: Boolean,
    ): DecoderException? =
        try {
            val pcm = output.init(input.timeUs, configuration.maxOutputBytes)
            val bytes = native.decode(requireNotNull(input.data), pcm, reset)
            check(
                bytes > 0 &&
                        bytes <= configuration.maxOutputBytes &&
                        bytes % configuration.frameBytes == 0
            )
            pcm.limit(bytes)
            val frames = bytes / configuration.frameBytes
            val skip = framesBeforeStart(input.timeUs, frames)
            output.shouldBeSkipped = skip == frames
            pcm.position(skip * configuration.frameBytes)
            output.timeUs = sampleTimeUs(input.timeUs, skip)
            null
        } catch (e: Exception) {
            DecoderException("${configuration.name} rejected a packet: ${e.message}", e)
        }

    private fun framesBeforeStart(timeUs: Long, frames: Int): Int {
        if (isAtLeastOutputStartTimeUs(timeUs)) return 0
        var lower = 0
        var upper = frames
        while (lower < upper) {
            val middle = lower + (upper - lower) / 2
            if (isAtLeastOutputStartTimeUs(sampleTimeUs(timeUs, middle))) upper = middle
            else lower = middle + 1
        }
        return lower
    }

    private fun sampleTimeUs(timeUs: Long, frame: Int): Long =
        timeUs + frame.toLong() * 1_000_000 / outputFormat.sampleRate

    override fun release() {
        super.release()
        native.close()
    }
}
