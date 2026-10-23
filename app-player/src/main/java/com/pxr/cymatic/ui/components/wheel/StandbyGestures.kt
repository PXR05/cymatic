package com.pxr.cymatic.ui.components.wheel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal object StandbyHitTest {
    fun isTouchOnScreen(pos: Offset, screenBounds: Rect?): Boolean =
        screenBounds?.contains(pos) == true

    fun isTouchOnWheel(pos: Offset, wheelCenter: Offset?, wheelRadius: Float?): Boolean {
        if (wheelCenter == null || wheelRadius == null || wheelRadius <= 0f) return false
        return (pos - wheelCenter).getDistance() <= wheelRadius
    }

    fun isTouchOnScreenOrWheel(
        pos: Offset,
        screenBounds: Rect?,
        wheelCenter: Offset?,
        wheelRadius: Float?,
    ): Boolean = isTouchOnScreen(pos, screenBounds) || isTouchOnWheel(pos, wheelCenter, wheelRadius)
}
