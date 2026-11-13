// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Dsp {
    const val FS: Double = 96_000.0
    const val FMIN: Double = 20.0
    const val FMAX: Double = 20_000.0
    const val DISPLAY_POINTS: Int = 512

    class FreqGrid(val freqs: DoubleArray) {
        val size: Int
            get() = freqs.size

        internal val cosW = DoubleArray(freqs.size) { cos(2.0 * Math.PI * freqs[it] / FS) }
        internal val cos2W = DoubleArray(freqs.size) { cos(4.0 * Math.PI * freqs[it] / FS) }
    }

    fun logFreqs(n: Int, fMin: Double = FMIN, fMax: Double = FMAX): DoubleArray {
        require(n >= 2)
        val span = ln(fMax / fMin)
        return DoubleArray(n) { fMin * exp(span * it / (n - 1)) }
    }

    val DISPLAY_GRID: FreqGrid by lazy { FreqGrid(logFreqs(DISPLAY_POINTS)) }

    data class Biquad(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double,
    )

    fun biquad(band: Band, fs: Double = FS): Biquad {
        val w0 = 2.0 * Math.PI * band.freqHz / fs
        val c = cos(w0)
        val s = sin(w0)
        val alpha = s / (2.0 * band.q)
        val a = 10.0.pow(band.gainDb / 40.0)
        val shelfAlpha =
            if (band.type.isShelf) {
                val radicand = (a + 1.0 / a) * (1.0 / band.q - 1.0) + 2.0
                require(radicand.isFinite() && radicand >= 0.0) {
                    "Shelf gain/Q has no real response"
                }
                (s / 2.0) * sqrt(radicand)
            } else 0.0
        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double
        when (band.type) {
            FilterType.PEAK -> {
                b0 = 1 + alpha * a
                b1 = -2 * c
                b2 = 1 - alpha * a
                a0 = 1 + alpha / a
                a1 = -2 * c
                a2 = 1 - alpha / a
            }
            FilterType.LOW_SHELF -> {
                val k = 2 * sqrt(a) * shelfAlpha
                b0 = a * ((a + 1) - (a - 1) * c + k)
                b1 = 2 * a * ((a - 1) - (a + 1) * c)
                b2 = a * ((a + 1) - (a - 1) * c - k)
                a0 = (a + 1) + (a - 1) * c + k
                a1 = -2 * ((a - 1) + (a + 1) * c)
                a2 = (a + 1) + (a - 1) * c - k
            }
            FilterType.HIGH_SHELF -> {
                val k = 2 * sqrt(a) * shelfAlpha
                b0 = a * ((a + 1) + (a - 1) * c + k)
                b1 = -2 * a * ((a - 1) + (a + 1) * c)
                b2 = a * ((a + 1) + (a - 1) * c - k)
                a0 = (a + 1) - (a - 1) * c + k
                a1 = 2 * ((a - 1) - (a + 1) * c)
                a2 = (a + 1) - (a - 1) * c - k
            }
            FilterType.LOW_PASS -> {
                b0 = (1 - c) / 2
                b1 = 1 - c
                b2 = (1 - c) / 2
                a0 = 1 + alpha
                a1 = -2 * c
                a2 = 1 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0 = (1 + c) / 2
                b1 = -(1 + c)
                b2 = (1 + c) / 2
                a0 = 1 + alpha
                a1 = -2 * c
                a2 = 1 - alpha
            }

            FilterType.BAND_PASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                a0 = 1 + alpha
                a1 = -2 * c
                a2 = 1 - alpha
            }

            FilterType.ALL_PASS -> {
                b0 = 1 - alpha
                b1 = -2 * c
                b2 = 1 + alpha
                a0 = 1 + alpha
                a1 = -2 * c
                a2 = 1 - alpha
            }
        }
        val result = Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        require(
            result.b0.isFinite() &&
                result.b1.isFinite() &&
                result.b2.isFinite() &&
                result.a1.isFinite() &&
                result.a2.isFinite()
        ) {
            "Band has no finite response"
        }
        return result
    }

    fun addBandDb(band: Band, grid: FreqGrid, out: DoubleArray) {
        val q = biquad(band)
        val nk = q.b0 * q.b0 + q.b1 * q.b1 + q.b2 * q.b2
        val n1 = 2 * (q.b0 * q.b1 + q.b1 * q.b2)
        val n2 = 2 * q.b0 * q.b2
        val dk = 1 + q.a1 * q.a1 + q.a2 * q.a2
        val d1 = 2 * (q.a1 + q.a1 * q.a2)
        val d2 = 2 * q.a2
        val cw = grid.cosW
        val c2w = grid.cos2W
        for (i in out.indices) {
            val num = nk + n1 * cw[i] + n2 * c2w[i]
            val den = dk + d1 * cw[i] + d2 * c2w[i]
            out[i] += 10.0 * log10(num / den)
        }
    }

    fun responseDb(bands: List<Band>, grid: FreqGrid, out: DoubleArray) {
        require(out.size == grid.size)
        out.fill(0.0)
        for (b in bands) if (b.enabled) addBandDb(b, grid, out)
        require(out.all { it.isFinite() }) { "Profile has no finite response" }
    }

    fun responseDb(bands: List<Band>, grid: FreqGrid = DISPLAY_GRID): DoubleArray =
        DoubleArray(grid.size).also { responseDb(bands, grid, it) }

    fun bandDb(band: Band, grid: FreqGrid = DISPLAY_GRID): DoubleArray =
        DoubleArray(grid.size).also { addBandDb(band, grid, it) }
}

object Preamp {

    const val FLOOR_DB = -30.0

    fun floorTo(x: Double, step: Double = 0.1): Double {

        if (!x.isFinite() || kotlin.math.abs(x) >= 1e9) return x
        return if (step == 0.1) kotlin.math.floor(x * 10 + 1e-6) / 10.0
        else kotlin.math.floor(x / step + 1e-6) * step
    }

    private const val SCAN_POINTS = 1024
    private val SCAN_GRID: Dsp.FreqGrid by lazy { Dsp.FreqGrid(Dsp.logFreqs(SCAN_POINTS)) }

    fun auto(bands: List<Band>): Int {
        val active = bands.filter { it.enabled }
        if (active.isEmpty()) return 0
        answers
            .firstOrNull { it.bands == active }
            ?.let {
                return it.db
            }
        val centres = Dsp.FreqGrid(DoubleArray(active.size) { active[it].freqHz })
        val max =
            maxOf(Dsp.responseDb(active, SCAN_GRID).max(), Dsp.responseDb(active, centres).max())
        require(max.isFinite()) { "AUTO preamp needs a finite response" }
        val db = if (max > 0) -kotlin.math.ceil(max - 1e-6).toInt() else 0
        answers = (listOf(Answer(active, db)) + answers).take(KEPT)
        return db
    }

    private class Answer(val bands: List<Band>, val db: Int)

    private const val KEPT = 4
    @Volatile private var answers: List<Answer> = emptyList()
}
