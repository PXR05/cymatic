package com.pxr.cymatic.ui.components.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pxr.cymatic.audio.BiquadCoefficients
import com.pxr.cymatic.data.model.EqPreset
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun EqBodePlot(
    preset: EqPreset,
    modifier: Modifier = Modifier
) {
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val labelTextStyle = MaterialTheme.typography.bodySmall

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val minFreq = 20.0
    val maxFreq = 20000.0
    val minLog = log10(minFreq)
    val maxLog = log10(maxFreq)
    val logRange = maxLog - minLog

    val minDb = -20.0
    val maxDb = 20.0
    val dbRange = maxDb - minDb

    val xTicks = listOf(
        20.0, 30.0, 40.0, 50.0, 60.0, 80.0, 
        100.0, 150.0, 200.0, 300.0, 400.0, 500.0, 600.0, 800.0, 
        1000.0, 1500.0, 2000.0, 3000.0, 4000.0, 5000.0, 6000.0, 8000.0, 
        10000.0, 15000.0, 20000.0
    )

    val labeledXTicks = mapOf(
        50.0 to "50",
        100.0 to "100",
        200.0 to "200",
        500.0 to "500",
        1000.0 to "1k",
        2000.0 to "2k",
        5000.0 to "5k",
        10000.0 to "10k",
        15000.0 to "15k"
    )

    val yTicks = listOf(-20.0, -15.0, -10.0, -5.0, 0.0, 5.0, 10.0, 15.0, 20.0)
    val labeledYTicks = mapOf(
        15.0 to "15",
        10.0 to "10",
        5.0 to "5",
        0.0 to "0",
        -5.0 to "-5",
        -10.0 to "-10",
        -15.0 to "-15",
    )

    val biquads = remember(preset.bands) {
        preset.bands.filter { it.enabled }.map { BiquadCoefficients.from(it, 44100) }
    }

    val steps = 300
    val frequencies = remember {
        DoubleArray(steps + 1) { i ->
            val fraction = i.toDouble() / steps
            10.0.pow(minLog + fraction * logRange)
        }
    }
    val magnitudes = remember(preset.preamp, biquads) {
        FloatArray(steps + 1) { i ->
            val freq = frequencies[i]
            var totalDb = preset.preamp.toDouble()
            for (bq in biquads) {
                totalDb += bq.evaluateMagnitudeDb(freq, 44100.0)
            }
            totalDb.coerceIn(minDb - 10.0, maxDb + 10.0).toFloat()
        }
    }

    Canvas(
        modifier = modifier
            .border(1.dp, secondaryColor)
            .fillMaxSize()
    ) {
        val width = size.width
        val height = size.height

        fun xForFreq(freq: Double): Float {
            return (((log10(freq) - minLog) / logRange) * width).toFloat()
        }

        fun yForDb(db: Double): Float {
            return (((maxDb - db) / dbRange) * height).toFloat()
        }

        for (tick in xTicks) {
            val isLabeled = labeledXTicks.containsKey(tick)
            val x = xForFreq(tick)
            
            drawLine(
                color = secondaryColor.copy(alpha = if (isLabeled) 0.5f else 0.2f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = if (isLabeled) with(density) { 1.dp.toPx() } else with(density) { 0.5.dp.toPx() }
            )

            if (isLabeled) {
                val label = labeledXTicks[tick]!!
                val textLayoutResult = textMeasurer.measure(
                    text = label,
                    style = labelTextStyle.copy(
                        color = secondaryColor, fontSize = 10.sp
                    )
                )
                
                val textWidth = textLayoutResult.size.width
                val startX = when {
                    x - textWidth / 2f < 0f -> 4f
                    x + textWidth / 2f > width -> width - textWidth - 4f
                    else -> x - textWidth / 2f
                }
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(startX, height - textLayoutResult.size.height - 4f)
                )
            }
        }

        for (tick in yTicks) {
            val isLabeled = labeledYTicks.containsKey(tick)
            val y = yForDb(tick)
            val isZero = tick == 0.0

            drawLine(
                color = if (isZero) onBackgroundColor.copy(alpha = 0.5f) else secondaryColor.copy(alpha = if (isLabeled) 0.5f else 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = if (isZero) with(density) { 1.dp.toPx() } else if (isLabeled) with(density) { 1.dp.toPx() } else with(density) { 0.5.dp.toPx() }
            )

            if (isLabeled && tick != -20.0) {
                val label = labeledYTicks[tick]!!
                val textLayoutResult = textMeasurer.measure(
                    text = label,
                    style = labelTextStyle.copy(color = secondaryColor, fontSize = 10.sp)
                )
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(4f, y - textLayoutResult.size.height - 2f)
                )
            }
        }

        val path = Path()
        for (i in 0..steps) {
            val freq = frequencies[i]
            val db = magnitudes[i].toDouble()
            val px = xForFreq(freq)
            val py = yForDb(db)

            if (i == 0) {
                path.moveTo(px, py)
            } else {
                path.lineTo(px, py)
            }
        }

        drawPath(
            path = path,
            color = onBackgroundColor,
            style = Stroke(width = with(density) { 1.dp.toPx() })
        )
    }
}
