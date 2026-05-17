package com.pxr.cymatic.audio

import com.pxr.cymatic.data.model.EqBand
import com.pxr.cymatic.data.model.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double
) {
    fun evaluateMagnitudeDb(frequency: Double, sampleRate: Double): Double {
        val w = 2.0 * PI * frequency / sampleRate
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        
        val num = b0 * b0 + b1 * b1 + b2 * b2 +
                  2.0 * (b0 * b1 + b1 * b2) * cosW +
                  2.0 * b0 * b2 * cos2W
                  
        val den = 1.0 + a1 * a1 + a2 * a2 +
                  2.0 * (a1 + a1 * a2) * cosW +
                  2.0 * a2 * cos2W
                  
        val magnitudeSquared = num / den
        return if (magnitudeSquared > 0) 10.0 * log10(magnitudeSquared) else -100.0
    }

    companion object {
        val IDENTITY = BiquadCoefficients(
            b0 = 1.0, b1 = 0.0, b2 = 0.0,
            a1 = 0.0, a2 = 0.0
        )

        fun from(band: EqBand, sampleRate: Int): BiquadCoefficients {
            if (!band.enabled) return IDENTITY

            val fs = sampleRate.toDouble()
            val f0 = band.frequency.toDouble().coerceIn(1.0, fs / 2.0 - 1.0)
            val gain = band.gain.toDouble()
            val q = band.q.toDouble().coerceAtLeast(0.001)

            val w0 = 2.0 * PI * f0 / fs
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)
            val A = 10.0.pow(gain / 40.0)

            return when (band.type) {
                FilterType.PEAKING -> {
                    val alphaTimesSqrtA = alpha * sqrt(A)
                    val alphaOverSqrtA = alpha / sqrt(A)
                    val b0 = 1.0 + alphaTimesSqrtA
                    val b1 = -2.0 * cosW0
                    val b2 = 1.0 - alphaTimesSqrtA
                    val a0 = 1.0 + alphaOverSqrtA
                    val a1 = -2.0 * cosW0
                    val a2 = 1.0 - alphaOverSqrtA
                    normalized(b0, b1, b2, a0, a1, a2)
                }

                FilterType.LOW_SHELF -> {
                    val sqrtA = sqrt(A)
                    val alphaMul = 2.0 * sqrtA * alpha
                    val b0 = A * ((A + 1) - (A - 1) * cosW0 + alphaMul)
                    val b1 = 2.0 * A * ((A - 1) - (A + 1) * cosW0)
                    val b2 = A * ((A + 1) - (A - 1) * cosW0 - alphaMul)
                    val a0 = (A + 1) + (A - 1) * cosW0 + alphaMul
                    val a1 = -2.0 * ((A - 1) + (A + 1) * cosW0)
                    val a2 = (A + 1) + (A - 1) * cosW0 - alphaMul
                    normalized(b0, b1, b2, a0, a1, a2)
                }

                FilterType.HIGH_SHELF -> {
                    val sqrtA = sqrt(A)
                    val alphaMul = 2.0 * sqrtA * alpha
                    val b0 = A * ((A + 1) + (A - 1) * cosW0 + alphaMul)
                    val b1 = -2.0 * A * ((A - 1) + (A + 1) * cosW0)
                    val b2 = A * ((A + 1) + (A - 1) * cosW0 - alphaMul)
                    val a0 = (A + 1) - (A - 1) * cosW0 + alphaMul
                    val a1 = 2.0 * ((A - 1) - (A + 1) * cosW0)
                    val a2 = (A + 1) - (A - 1) * cosW0 - alphaMul
                    normalized(b0, b1, b2, a0, a1, a2)
                }

                FilterType.LOW_PASS -> {
                    val b0 = (1.0 - cosW0) / 2.0
                    val b1 = 1.0 - cosW0
                    val b2 = (1.0 - cosW0) / 2.0
                    val a0 = 1.0 + alpha
                    val a1 = -2.0 * cosW0
                    val a2 = 1.0 - alpha
                    normalized(b0, b1, b2, a0, a1, a2)
                }

                FilterType.HIGH_PASS -> {
                    val b0 = (1.0 + cosW0) / 2.0
                    val b1 = -(1.0 + cosW0)
                    val b2 = (1.0 + cosW0) / 2.0
                    val a0 = 1.0 + alpha
                    val a1 = -2.0 * cosW0
                    val a2 = 1.0 - alpha
                    normalized(b0, b1, b2, a0, a1, a2)
                }

                FilterType.BAND_PASS -> {
                    val b0 = sinW0 / 2.0
                    val b1 = 0.0
                    val b2 = -sinW0 / 2.0
                    val a0 = 1.0 + alpha
                    val a1 = -2.0 * cosW0
                    val a2 = 1.0 - alpha
                    normalized(b0, b1, b2, a0, a1, a2)
                }

                FilterType.NOTCH -> {
                    val b0 = 1.0
                    val b1 = -2.0 * cosW0
                    val b2 = 1.0
                    val a0 = 1.0 + alpha
                    val a1 = -2.0 * cosW0
                    val a2 = 1.0 - alpha
                    normalized(b0, b1, b2, a0, a1, a2)
                }

                FilterType.ALL_PASS -> {
                    val b0 = 1.0 - alpha
                    val b1 = -2.0 * cosW0
                    val b2 = 1.0 + alpha
                    val a0 = 1.0 + alpha
                    val a1 = -2.0 * cosW0
                    val a2 = 1.0 - alpha
                    normalized(b0, b1, b2, a0, a1, a2)
                }
            }
        }

        private fun normalized(
            b0: Double, b1: Double, b2: Double,
            a0: Double, a1: Double, a2: Double
        ): BiquadCoefficients = BiquadCoefficients(
            b0 = b0 / a0,
            b1 = b1 / a0,
            b2 = b2 / a0,
            a1 = a1 / a0,
            a2 = a2 / a0
        )
    }
}
