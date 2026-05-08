package com.pxr.cymatic.data.model

import java.util.Locale

enum class FilterType(val apoName: String, val displayName: String) {
    PEAKING("PK", "Peaking"),
    LOW_SHELF("LS", "Low Shelf"),
    HIGH_SHELF("HS", "High Shelf"),
    LOW_PASS("LP", "Low Pass"),
    HIGH_PASS("HP", "High Pass"),
    BAND_PASS("BP", "Band Pass"),
    NOTCH("NO", "Notch"),
    ALL_PASS("AP", "All Pass");

    companion object {
        fun fromApoName(apoName: String): FilterType {
            return entries.firstOrNull { it.apoName.equals(apoName, ignoreCase = true) } ?: PEAKING
        }
    }
}

data class EqBand(
    val id: Int,
    val type: FilterType = FilterType.PEAKING,
    val frequency: Float = 1000f,
    val gain: Float = 0f,
    val q: Float = 1.0f,
    val enabled: Boolean = true
) {
    fun toApoString(filterIndex: Int): String {
        val state = if (enabled) STATE_ON else STATE_OFF
        return String.format(
            Locale.US,
            "Filter %d: %s %s Fc %.0f Hz Gain %.1f dB Q %.2f",
            filterIndex, state, type.apoName, frequency, gain, q
        )
    }

    companion object {
        private const val STATE_ON = "ON"
        private const val STATE_OFF = "OFF"

        /**
         * Parse a single Equalizer APO filter line into an [EqBand].
         * Expected format: "Filter N: ON|OFF <type> Fc <freq> Hz [Gain <gain> dB] [Q <q>]"
         * Returns null if the line cannot be parsed.
         */
        fun fromApoLine(line: String, id: Int): EqBand? {
            return try {
                // Normalize whitespace
                val tokens = line.trim().split("\\s+".toRegex())
                // Minimum: Filter N: ON TYPE Fc FREQ Hz  -> 8 tokens
                if (tokens.size < 8) return null

                val stateToken = tokens[2].uppercase()
                val enabled = stateToken != STATE_OFF

                val typeToken = tokens[3]
                val filterType = FilterType.fromApoName(typeToken)

                // tokens[4] == "Fc", tokens[5] == freq, tokens[6] == "Hz"
                val frequency = tokens[5].toFloatOrNull() ?: return null

                // Optional: "Gain <gain> dB Q <q>"
                var gain = 0f
                var q = 1f
                val gainIndex = tokens.indexOfFirst { it.equals("Gain", ignoreCase = true) }
                if (gainIndex >= 0 && gainIndex + 1 < tokens.size) {
                    gain = tokens[gainIndex + 1].toFloatOrNull() ?: 0f
                }
                val qIndex = tokens.indexOfFirst { it.equals("Q", ignoreCase = true) }
                if (qIndex >= 0 && qIndex + 1 < tokens.size) {
                    q = tokens[qIndex + 1].toFloatOrNull() ?: 1f
                }

                EqBand(
                    id = id,
                    type = filterType,
                    frequency = frequency,
                    gain = gain,
                    q = q,
                    enabled = enabled
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}