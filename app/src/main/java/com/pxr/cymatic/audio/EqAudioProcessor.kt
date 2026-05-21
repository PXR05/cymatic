package com.pxr.cymatic.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import com.pxr.cymatic.data.model.EqBand
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

@UnstableApi
class EqAudioProcessor : AudioProcessor {

    private data class FilterState(
        val coeffs: List<BiquadCoefficients>,
        val preampLinear: Float
    )

    private val pendingUpdate = AtomicReference<FilterState?>(null)

    private var activeCoeffs: List<BiquadCoefficients> = emptyList()
    private var activeCoeffsFlat = FloatArray(0)

    private var preampLinear: Float = 1f

    private var x1 = FloatArray(0)
    private var x2 = FloatArray(0)
    private var y1 = FloatArray(0)
    private var y2 = FloatArray(0)

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var isActive = false

    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    fun updateBands(preampDb: Float, bands: List<EqBand>, sampleRate: Int) {
        val sampleRateSafe = if (sampleRate > 0) sampleRate else 44100
        val coeffs = bands.map { BiquadCoefficients.from(it, sampleRateSafe) }
        val preampLinear = 10.0.pow(preampDb / 20.0).toFloat()
        pendingUpdate.set(FilterState(coeffs, preampLinear))
    }

    fun disable() {
        pendingUpdate.set(FilterState(emptyList(), 1f))
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT ||
                   inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
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
        
        val is16Bit = inputFormat.encoding == C.ENCODING_PCM_16BIT
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
                    val coeffOffset = bandIndex * 5
                    val b0 = activeCoeffsFlat[coeffOffset + 0]
                    val b1 = activeCoeffsFlat[coeffOffset + 1]
                    val b2 = activeCoeffsFlat[coeffOffset + 2]
                    val a1 = activeCoeffsFlat[coeffOffset + 3]
                    val a2 = activeCoeffsFlat[coeffOffset + 4]

                    val stateIndex = bandIndex * channelCount + channelIndex
                    val x1Val = x1[stateIndex]
                    val x2Val = x2[stateIndex]
                    val y1Val = y1[stateIndex]
                    val y2Val = y2[stateIndex]

                    val yn = b0 * sample + b1 * x1Val + b2 * x2Val - a1 * y1Val - a2 * y2Val

                    x2[stateIndex] = x1Val
                    x1[stateIndex] = sample
                    y2[stateIndex] = y1Val
                    y1[stateIndex] = yn
                    sample = yn
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
                    val coeffOffset = bandIndex * 5
                    val b0 = activeCoeffsFlat[coeffOffset + 0]
                    val b1 = activeCoeffsFlat[coeffOffset + 1]
                    val b2 = activeCoeffsFlat[coeffOffset + 2]
                    val a1 = activeCoeffsFlat[coeffOffset + 3]
                    val a2 = activeCoeffsFlat[coeffOffset + 4]

                    val stateIndex = bandIndex * channelCount + channelIndex
                    val x1Val = x1[stateIndex]
                    val x2Val = x2[stateIndex]
                    val y1Val = y1[stateIndex]
                    val y2Val = y2[stateIndex]

                    val yn = b0 * sample + b1 * x1Val + b2 * x2Val - a1 * y1Val - a2 * y2Val

                    x2[stateIndex] = x1Val
                    x1[stateIndex] = sample
                    y2[stateIndex] = y1Val
                    y1[stateIndex] = yn
                    sample = yn
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
        activeCoeffsFlat = FloatArray(0)
        preampLinear = 1f
        x1 = FloatArray(0)
        x2 = FloatArray(0)
        y1 = FloatArray(0)
        y2 = FloatArray(0)
    }

    private fun resetDelayLines() {
        val bands = activeCoeffs.size
        val channels = if (inputFormat != AudioFormat.NOT_SET) inputFormat.channelCount else 2
        val size = bands * channels

        if (activeCoeffsFlat.size != bands * 5) {
            activeCoeffsFlat = FloatArray(bands * 5)
        }
        for (i in 0 until bands) {
            val c = activeCoeffs[i]
            activeCoeffsFlat[i * 5 + 0] = c.b0.toFloat()
            activeCoeffsFlat[i * 5 + 1] = c.b1.toFloat()
            activeCoeffsFlat[i * 5 + 2] = c.b2.toFloat()
            activeCoeffsFlat[i * 5 + 3] = c.a1.toFloat()
            activeCoeffsFlat[i * 5 + 4] = c.a2.toFloat()
        }

        if (x1.size != size) {
            x1 = FloatArray(size)
            x2 = FloatArray(size)
            y1 = FloatArray(size)
            y2 = FloatArray(size)
        } else {
            x1.fill(0f)
            x2.fill(0f)
            y1.fill(0f)
            y2.fill(0f)
        }
    }
}
