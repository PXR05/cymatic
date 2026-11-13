// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core

object BandStripPlan {
    const val BUTTON = 44.0
    const val INSET = 4.0

    const val CELL = 36.0
    const val MAX_CELL = 42.0

    data class Plan(val scroll: Boolean, val cellDp: Double, val numbersDp: Double)

    fun plan(widthDp: Double, bands: Int, showPlus: Boolean): Plan {
        val pinned = if (showPlus) 2 else 1
        val numbers = (widthDp - 2 * INSET - pinned * BUTTON).coerceAtLeast(0.0)
        val inner = (numbers - 2 * INSET).coerceAtLeast(0.0)
        if (bands * CELL <= inner) return Plan(false, CELL, numbers)
        for (m in (bands - 1) downTo 1) {
            val c = inner / (m + 0.5)
            if (c >= CELL) return Plan(true, minOf(c, MAX_CELL), numbers)
        }
        return Plan(true, CELL, numbers)
    }
}
