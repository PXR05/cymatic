package com.pxr.cymatic.ui.components.common

class WheelSelectionNavigator {
    private var canWrap = false

    fun beginMovement() {
        canWrap = true
    }

    fun next(currentIndex: Int, steps: Int, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        val last = itemCount - 1
        val current = currentIndex.coerceIn(0, last)
        if (steps == 0) return current
        val next = when {
            canWrap && steps > 0 && current == last -> (steps - 1).coerceIn(0, last)
            canWrap && steps < 0 && current == 0 -> (last + steps + 1).coerceIn(0, last)
            else -> (current.toLong() + steps).coerceIn(0L, last.toLong()).toInt()
        }
        canWrap = false
        return next
    }
}
