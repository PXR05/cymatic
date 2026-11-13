// Modified for Cymatic: Explanatory comments removed.
package io.github.chronosauros.contour.core.devices

internal object PendingRecords {

    val ECHO_A_FREQ_HZ: ClosedFloatingPointRange<Double> = 20.0..20_000.0

    const val ALLEGRO_RECONNECT_AFTER_SAVE: Boolean = true

    val DAWN_EQ_INDEX_RANGE: IntRange = 0..15
}
