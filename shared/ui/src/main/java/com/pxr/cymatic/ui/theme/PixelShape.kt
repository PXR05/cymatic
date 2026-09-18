package com.pxr.cymatic.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Rounded corners built from discrete pixel steps. */
class PixelCornerShape(
    val cornerRadius: Dp = 24.dp,
    val pixelSize: Dp = 2.dp,
    val topStart: Boolean = true,
    val topEnd: Boolean = true,
    val bottomEnd: Boolean = true,
    val bottomStart: Boolean = true
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val rPx = with(density) { cornerRadius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val pPx = with(density) { pixelSize.toPx() }.coerceAtLeast(1f)

        if (rPx <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        val w = size.width
        val h = size.height
        val steps = (rPx / pPx).roundToInt().coerceAtLeast(1)
        val stepSize = rPx / steps

        // Quantized circular insets from the outer bounding box edge
        // r = 0 is at y = 0 (top/bottom boundary), r = steps - 1 is at y = rPx (flush with straight edge)
        val insets = FloatArray(steps) { r ->
            val yAtBottom = (r + 1) * stepSize
            val radSq = rPx * rPx
            val dy = (rPx - yAtBottom).coerceAtLeast(0f)
            val dySq = dy * dy
            val chord = if (radSq >= dySq) sqrt(radSq - dySq) else 0f
            val rawInset = (rPx - chord).coerceIn(0f, rPx)
            ((rawInset / stepSize).roundToInt() * stepSize).coerceIn(0f, rPx)
        }

        // Ensure insets are strictly non-increasing and the final row meets the straight wall (0)
        insets[steps - 1] = 0f
        for (i in steps - 2 downTo 0) {
            if (insets[i] < insets[i + 1]) {
                insets[i] = insets[i + 1]
            }
        }

        val path = Path()

        val startTopX = if (topStart) rPx else 0f
        val endTopX = if (topEnd) w - rPx else w
        path.moveTo(startTopX, 0f)
        path.lineTo(endTopX, 0f)

        if (topEnd) {
            for (r in 0 until steps) {
                val x = w - insets[r]
                val yTop = r * stepSize
                val yBottom = (r + 1) * stepSize
                path.lineTo(x, yTop)
                path.lineTo(x, yBottom)
            }
        } else {
            path.lineTo(w, 0f)
            path.lineTo(w, if (bottomEnd) h - rPx else h)
        }

        val endRightY = if (bottomEnd) h - rPx else h
        path.lineTo(w, endRightY)

        if (bottomEnd) {
            for (r in (steps - 1) downTo 0) {
                val x = w - insets[r]
                val nextX = if (r > 0) w - insets[r - 1] else w - rPx
                val yBottom = h - r * stepSize
                path.lineTo(x, yBottom)
                path.lineTo(nextX, yBottom)
            }
        } else {
            path.lineTo(w, h)
            path.lineTo(if (bottomStart) rPx else 0f, h)
        }

        val endBottomX = if (bottomStart) rPx else 0f
        path.lineTo(endBottomX, h)

        if (bottomStart) {
            for (r in 0 until steps) {
                val x = insets[r]
                val yBottom = h - r * stepSize
                val yTop = h - (r + 1) * stepSize
                path.lineTo(x, yBottom)
                path.lineTo(x, yTop)
            }
        } else {
            path.lineTo(0f, h)
            path.lineTo(0f, if (topStart) rPx else 0f)
        }

        val endLeftY = if (topStart) rPx else 0f
        path.lineTo(0f, endLeftY)

        if (topStart) {
            for (r in (steps - 1) downTo 0) {
                val x = insets[r]
                val nextX = if (r > 0) insets[r - 1] else rPx
                val yTop = r * stepSize
                path.lineTo(x, yTop)
                path.lineTo(nextX, yTop)
            }
        } else {
            path.lineTo(0f, 0f)
        }

        path.close()
        return Outline.Generic(path)
    }

    override fun toString(): String {
        return "PixelCornerShape(cornerRadius=$cornerRadius, pixelSize=$pixelSize)"
    }
}
