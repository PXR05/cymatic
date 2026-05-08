package com.pxr.cymatic.audio

import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.pxr.cymatic.data.model.EqBand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class EqAudioProcessor : AudioProcessor {

    private data class FilterState(
        val coeffs: List<BiquadCoefficients>,
        val preampLinear: Float
    )

    private val pendingUpdate = AtomicReference<FilterState?>(null)

    private var activeCoeffs: List<BiquadCoefficients> = emptyList()

    private var preampLinear: Float = 1f

    private var x1 = Array(0) { FloatArray(0) }
    private var x2 = Array(0) { FloatArray(0) }
    private var y1 = Array(0) { FloatArray(0) }
    private var y2 = Array(0) { FloatArray(0) }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var isActive = false

    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    fun updateBands(preampDb: Float, bands: List<EqBand>, sampleRate: Int) {
        val sampleRateSafe = if (sampleRate > 0) sampleRate else 44100
        val coeffs = bands.map { BiquadCoefficients.from(it, sampleRateSafe) }
        val preampLinear = Math.pow(10.0, preampDb / 20.0).toFloat()
        pendingUpdate.set(FilterState(coeffs, preampLinear))
    }

    fun disable() {
        pendingUpdate.set(FilterState(emptyList(), 1f))
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        return if (inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT || 
                   inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT) {
            inputFormat = inputAudioFormat
            outputFormat = inputAudioFormat
            isActive = true
            outputFormat
        } else {
            inputFormat = AudioFormat.NOT_SET
            outputFormat = AudioFormat.NOT_SET
            isActive = false
            AudioFormat.NOT_SET
        }
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        pendingUpdate.getAndSet(null)?.let { update ->
            Log.d("EqAudioProcessor", "Applying new coefficients! Preamp=${update.preampLinear}, bands=${update.coeffs.size}")
            activeCoeffs = update.coeffs
            preampLinear = update.preampLinear
            resetDelayLines()
        }

        if (!inputBuffer.hasRemaining()) return

        val channelCount = inputFormat.channelCount
        val remainingBytes = inputBuffer.remaining()
        
        val is16Bit = inputFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        val bytesPerSample = if (is16Bit) 2 else 4
        val sampleCount = remainingBytes / bytesPerSample

        if (outputBuffer.capacity() < remainingBytes) {
            outputBuffer = ByteBuffer.allocateDirect(remainingBytes).order(ByteOrder.nativeOrder())
        }
        outputBuffer.clear()

        var sampleIndex = 0
        
        if (is16Bit) {
            val inputShorts = inputBuffer.asShortBuffer()
            val outputShorts = outputBuffer.asShortBuffer()
            
            while (sampleIndex < sampleCount) {
                val channelIndex = sampleIndex % channelCount
                var sample = inputShorts.get().toFloat() / 32768f

                sample *= preampLinear

                for (bandIndex in activeCoeffs.indices) {
                    val c = activeCoeffs[bandIndex]
                    val xn = sample.toDouble()
                    val yn = (c.b0 * xn
                            + c.b1 * x1[bandIndex][channelIndex]
                            + c.b2 * x2[bandIndex][channelIndex]
                            - c.a1 * y1[bandIndex][channelIndex]
                            - c.a2 * y2[bandIndex][channelIndex])

                    x2[bandIndex][channelIndex] = x1[bandIndex][channelIndex]
                    x1[bandIndex][channelIndex] = xn.toFloat()
                    y2[bandIndex][channelIndex] = y1[bandIndex][channelIndex]
                    y1[bandIndex][channelIndex] = yn.toFloat()
                    sample = yn.toFloat()
                }

                sample = sample.coerceIn(-1f, 1f)
                outputShorts.put((sample * 32767f).toInt().toShort())
                sampleIndex++
            }
        } else {
            val inputFloats = inputBuffer.asFloatBuffer()
            val outputFloats = outputBuffer.asFloatBuffer()

            while (sampleIndex < sampleCount) {
                val channelIndex = sampleIndex % channelCount
                var sample = inputFloats.get()

                sample *= preampLinear

                for (bandIndex in activeCoeffs.indices) {
                    val c = activeCoeffs[bandIndex]
                    val xn = sample.toDouble()
                    val yn = (c.b0 * xn
                            + c.b1 * x1[bandIndex][channelIndex]
                            + c.b2 * x2[bandIndex][channelIndex]
                            - c.a1 * y1[bandIndex][channelIndex]
                            - c.a2 * y2[bandIndex][channelIndex])

                    x2[bandIndex][channelIndex] = x1[bandIndex][channelIndex]
                    x1[bandIndex][channelIndex] = xn.toFloat()
                    y2[bandIndex][channelIndex] = y1[bandIndex][channelIndex]
                    y1[bandIndex][channelIndex] = yn.toFloat()
                    sample = yn.toFloat()
                }

                sample = sample.coerceIn(-1f, 1f)
                outputFloats.put(sample)
                sampleIndex++
            }
        }

        inputBuffer.position(inputBuffer.limit())

        outputBuffer.limit(remainingBytes)
        outputBuffer.rewind()
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    @Suppress("OVERRIDE_DEPRECATION")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        resetDelayLines()
    }

    override fun reset() {
        flush()
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        isActive = false
        activeCoeffs = emptyList()
        preampLinear = 1f
        x1 = Array(0) { FloatArray(0) }
        x2 = Array(0) { FloatArray(0) }
        y1 = Array(0) { FloatArray(0) }
        y2 = Array(0) { FloatArray(0) }
    }

    private fun resetDelayLines() {
        val bands = activeCoeffs.size
        val channels = if (inputFormat != AudioFormat.NOT_SET) inputFormat.channelCount else 2
        if (x1.size != bands || (bands > 0 && x1[0].size != channels)) {
            x1 = Array(bands) { FloatArray(channels) }
            x2 = Array(bands) { FloatArray(channels) }
            y1 = Array(bands) { FloatArray(channels) }
            y2 = Array(bands) { FloatArray(channels) }
        } else {
            for (i in 0 until bands) {
                x1[i].fill(0f)
                x2[i].fill(0f)
                y1[i].fill(0f)
                y2[i].fill(0f)
            }
        }
    }
}
