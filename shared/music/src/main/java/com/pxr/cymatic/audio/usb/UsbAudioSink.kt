package com.pxr.cymatic.audio.usb

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.pxr.cymatic.usb.UsbPcmStream
import java.nio.ByteBuffer
import java.time.Instant

@UnstableApi
internal class UsbAudioSink(private val context: Context, private val normal: AudioSink) :
    AudioSink by normal {
    private var direct = false
    private var listener: AudioSink.Listener? = null
    private var format: Format? = null
    private var pendingFormat: Format? = null
    private var device: UsbPcmDevice? = null
    private var stream: UsbPcmStream? = null
    private var packer: UsbPcmPacker? = null
    private var trimmer: UsbPcmTrimmer? = null
    private var playing = false
    private var ended = false
    private var baseTimeUs = C.TIME_UNSET
    private var outputStreamOffsetUs = 0L
    private var writtenFrames = 0L
    private var completedFrames = 0L
    private var discontinuity = false
    private var advancing = false
    private var volume = 1f

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        normal.setListener(listener)
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int {
        if (!UsbPlaybackState.routeToUsb) return normal.getFormatSupport(format)
        return if (format.usbPcmBits() != 0) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else AudioSink.SINK_FORMAT_UNSUPPORTED
    }

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        if (direct && UsbPlaybackState.routeToUsb) {
            validateDirectFormat(inputFormat, outputChannels)
            if (
                device != null &&
                format?.sampleRate == inputFormat.sampleRate &&
                format?.usbPcmLayout() == inputFormat.usbPcmLayout() &&
                !ended
            ) {
                format = inputFormat
                configureTrimmer(inputFormat)
                return
            }
            if (stream != null) {
                pendingFormat = inputFormat
                stream?.end()
                ended = true
                return
            }
        }
        closeDevice()
        direct = UsbPlaybackState.routeToUsb
        format = inputFormat
        resetTimeline()
        if (!direct) {
            normal.configure(inputFormat, specifiedBufferSize, outputChannels)
            normal.setVolume(volume)
            if (playing) normal.play()
            return
        }
        normal.reset()
        validateDirectFormat(inputFormat, outputChannels)
        if (UsbPlaybackState.deviceReady) {
            UsbPlaybackState.update(
                "Ready · ${inputFormat.sampleRate} Hz · ${inputFormat.usbPcmLayout()?.description}"
            )
        }
        configureTrimmer(inputFormat)
    }

    private fun validateDirectFormat(input: Format, outputChannels: IntArray?) {
        val channels = IntArray(input.channelCount.coerceIn(0, 2)) { it }
        if (
            input.usbPcmLayout() == null ||
            (outputChannels != null && !outputChannels.contentEquals(channels))
        ) {
            throw AudioSink.ConfigurationException(
                "Direct USB requires precision-preserving mono/stereo integer PCM",
                input,
            )
        }
    }

    private fun configureTrimmer(input: Format) {
        try {
            trimmer = UsbPcmTrimmer(input)
        } catch (e: IllegalArgumentException) {
            throw AudioSink.ConfigurationException(e, input)
        }
    }

    private fun initialize() {
        if (stream != null) return
        val input = checkNotNull(format)
        try {
            val source = checkNotNull(input.usbPcmLayout())
            val output = UsbPcmDevice.open(context, input.sampleRate, sourceLayout = source)
            device = output
            packer = UsbPcmPacker(source, output.layout)
            stream = output.continuousStream().also { it.play(playing) }
            UsbPlaybackState.setActive(true)
            UsbPlaybackState.update("Direct USB · ${input.sampleRate} Hz · ${source.description}")
        } catch (e: Exception) {
            closeDevice()
            UsbPlaybackState.update("Stopped: ${e.message ?: e.javaClass.simpleName}")
            throw AudioSink.InitializationException("Direct USB: ${e.message}", 0, input, false, e)
        } catch (e: LinkageError) {
            closeDevice()
            UsbPlaybackState.update("Native USB transport unavailable")
            throw AudioSink.InitializationException(
                "Native USB transport unavailable",
                0,
                input,
                false,
                e,
            )
        }
    }

    private fun checkError(): LongArray {
        try {
            device?.checkVolume()
        } catch (e: IllegalStateException) {
            UsbPlaybackState.update("Stopped: ${e.message}")
            throw AudioSink.WriteException(-5, checkNotNull(format), false)
        }
        val stats = stream?.statistics() ?: LongArray(7).also { it[1] = completedFrames }
        if (stats[0] != 0L) {
            val input = checkNotNull(format)
            val message =
                when (stats[0]) {
                    -61L -> "USB clock feedback timed out"
                    -74L -> "USB clock feedback is invalid"
                    else -> "USB transfer error ${stats[0]} · underruns ${stats[5]}"
                }
            UsbPlaybackState.update("Stopped: $message")
            throw AudioSink.WriteException(stats[0].toInt(), input, false)
        }
        return stats
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!direct) return normal.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (!UsbPlaybackState.deviceReady) return false
        if (!buffer.hasRemaining() && trimmer?.output?.hasRemaining() != true) return true
        if (ended || pendingFormat != null) {
            checkError()
            if (hasPendingData()) return false
            val next = pendingFormat ?: format
            closeDevice()
            resetTimeline()
            format = next
            configureTrimmer(checkNotNull(next))
        }
        initialize()
        checkError()
        val trim = checkNotNull(trimmer)
        try {
            if (trim.output?.hasRemaining() != true) {
                trim.queue(buffer, presentationTimeUs - outputStreamOffsetUs)
            }
        } catch (e: IllegalArgumentException) {
            throw AudioSink.WriteException(-22, checkNotNull(format), false)
        }
        val pcm = checkNotNull(trim.output)
        if (!pcm.hasRemaining()) return !buffer.hasRemaining()
        val outputTimeUs = trim.outputTimeUs + outputStreamOffsetUs
        if (baseTimeUs == C.TIME_UNSET) baseTimeUs = outputTimeUs.coerceAtLeast(0)
        val expected = baseTimeUs + writtenFrames * 1_000_000 / checkNotNull(format).sampleRate
        if (discontinuity) {
            baseTimeUs += outputTimeUs - expected
            discontinuity = false
            listener?.onPositionDiscontinuity()
        }
        val consumed =
            try {
                checkNotNull(packer).write(pcm, checkNotNull(stream))
            } catch (e: IllegalArgumentException) {
                UsbPlaybackState.update("Stopped: ${e.message}")
                throw AudioSink.WriteException(-22, checkNotNull(format), false)
            }
        if (consumed < 0) {
            UsbPlaybackState.update("Stopped: USB write error $consumed")
            throw AudioSink.WriteException(consumed, checkNotNull(format), false)
        }
        pcm.position(pcm.position() + consumed)
        writtenFrames += consumed / checkNotNull(checkNotNull(format).usbPcmLayout()).frameBytes
        return !pcm.hasRemaining() && !buffer.hasRemaining()
    }

    override fun play() {
        playing = true
        if (direct) stream?.play(true) else normal.play()
    }

    override fun pause() {
        playing = false
        if (direct) stream?.play(false) else normal.pause()
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!direct) return normal.getCurrentPositionUs(sourceEnded)
        if (baseTimeUs == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val stats = stream?.statistics() ?: LongArray(7).also { it[1] = completedFrames }
        if (stats[1] > 0 && !advancing) {
            advancing = true
            listener?.onPositionAdvancing(System.currentTimeMillis())
        }
        return baseTimeUs + stats[1] * 1_000_000 / checkNotNull(format).sampleRate
    }

    override fun handleDiscontinuity() {
        if (direct) discontinuity = true else normal.handleDiscontinuity()
    }

    override fun playToEndOfStream() {
        if (!direct) {
            normal.playToEndOfStream()
            return
        }
        checkError()
        trimmer?.discardTail()
        stream?.end()
        ended = true
    }

    override fun isEnded(): Boolean {
        if (!direct) return normal.isEnded
        if (!ended || hasPendingData()) return false
        closeDevice()
        return true
    }

    override fun hasPendingData(): Boolean {
        if (!direct) return normal.hasPendingData()
        val stats = stream?.statistics() ?: return false
        return stats[0] != 0L || stats[2] > 0
    }

    override fun flush() {
        if (direct) {
            closeDevice()
            resetTimeline()
            format?.let(::configureTrimmer)
        } else normal.flush()
    }

    override fun reset() {
        closeDevice()
        normal.reset()
        direct = false
        playing = false
        resetTimeline()
        format = null
        trimmer = null
    }

    override fun release() {
        closeDevice()
        normal.release()
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
        if (!direct && !UsbPlaybackState.routeToUsb) normal.setVolume(volume)
    }

    override fun setPlaybackParameters(parameters: PlaybackParameters) {
        normal.setPlaybackParameters(
            if (UsbPlaybackState.routeToUsb || direct) PlaybackParameters.DEFAULT else parameters
        )
    }

    override fun getPlaybackParameters(): PlaybackParameters =
        if (direct || UsbPlaybackState.routeToUsb) PlaybackParameters.DEFAULT
        else normal.playbackParameters

    override fun setSkipSilenceEnabled(enabled: Boolean) {
        normal.setSkipSilenceEnabled(enabled && !direct && !UsbPlaybackState.routeToUsb)
    }

    override fun getSkipSilenceEnabled(): Boolean = !direct && normal.skipSilenceEnabled

    override fun getAudioTrackBufferSizeUs(): Long =
        if (direct) 500_000L else normal.audioTrackBufferSizeUs

    override fun setOutputStreamOffsetUs(offset: Long) {
        outputStreamOffsetUs = offset
        normal.setOutputStreamOffsetUs(offset)
    }

    private fun resetTimeline() {
        baseTimeUs = C.TIME_UNSET
        pendingFormat = null
        writtenFrames = 0
        completedFrames = 0
        ended = false
        advancing = false
        discontinuity = false
    }

    private fun closeDevice() {
        val output = device ?: return
        try {
            stream?.stop()
            val stats = stream?.statistics() ?: LongArray(7)
            completedFrames = stats[1]
            output.close()
            val report =
                output.report
                    .put("capturedAt", Instant.now().toString())
                    .put("scope", "Continuous PCM output; digital equality is unverified")
                    .put("digitalEqualityVerified", false)
                    .putUsbStreamStatistics(stats)
            UsbPlaybackState.record(report)
            if (report.optJSONArray("cleanupWarnings")?.length() != 0) {
                UsbPlaybackState.update("USB cleanup needs attention; export diagnostics")
            }
        } finally {
            UsbPlaybackState.setActive(false)
            device = null
            stream = null
            packer = null
        }
    }
}
