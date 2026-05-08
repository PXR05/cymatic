package com.pxr.cymatic.data.model

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class EqPreset(
    val name: String,
    val preamp: Float = 0f,
    val bands: List<EqBand> = emptyList()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put(KEY_NAME, name)
        obj.put(KEY_PREAMP, preamp.toDouble())
        val bandsArray = JSONArray()
        bands.forEach { band ->
            val bandObj = JSONObject()
            bandObj.put(KEY_ID, band.id)
            bandObj.put(KEY_TYPE, band.type.name)
            bandObj.put(KEY_FREQUENCY, band.frequency.toDouble())
            bandObj.put(KEY_GAIN, band.gain.toDouble())
            bandObj.put(KEY_Q, band.q.toDouble())
            bandObj.put(KEY_ENABLED, band.enabled)
            bandsArray.put(bandObj)
        }
        obj.put(KEY_BANDS, bandsArray)
        return obj
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_PREAMP = "preamp"
        private const val KEY_BANDS = "bands"
        private const val KEY_ID = "id"
        private const val KEY_TYPE = "type"
        private const val KEY_FREQUENCY = "frequency"
        private const val KEY_GAIN = "gain"
        private const val KEY_Q = "q"
        private const val KEY_ENABLED = "enabled"
        private const val DEFAULT_Q = 1.0f
        private val DEFAULT_FREQUENCIES = floatArrayOf(
            31f,
            62f,
            125f,
            250f,
            500f,
            1000f,
            2000f,
            4000f,
            8000f,
            16000f
        )

        fun fromJson(jsonStr: String): EqPreset? {
            return try {
                val obj = JSONObject(jsonStr)
                val name = obj.getString(KEY_NAME)
                val preamp = obj.optDouble(KEY_PREAMP, 0.0).toFloat()
                val bandsArray = obj.optJSONArray(KEY_BANDS)
                val bands = mutableListOf<EqBand>()
                if (bandsArray != null) {
                    for (i in 0 until bandsArray.length()) {
                        val bandObj = bandsArray.getJSONObject(i)
                        val typeStr = bandObj.optString(KEY_TYPE, FilterType.PEAKING.name)
                        bands.add(
                            EqBand(
                                id = bandObj.optInt(KEY_ID, i),
                                type = try { FilterType.valueOf(typeStr) } catch (_: Exception) { FilterType.PEAKING },
                                frequency = bandObj.optDouble(KEY_FREQUENCY, 1000.0).toFloat(),
                                gain = bandObj.optDouble(KEY_GAIN, 0.0).toFloat(),
                                q = bandObj.optDouble(KEY_Q, DEFAULT_Q.toDouble()).toFloat(),
                                enabled = bandObj.optBoolean(KEY_ENABLED, true)
                            )
                        )
                    }
                }
                EqPreset(name, preamp, bands)
            } catch (e: Exception) {
                Log.e("EqPreset", "Failed to parse EqPreset from JSON: ${e.message}")
                null
            }
        }
        
        fun defaultPreset(name: String = "Flat"): EqPreset {
            val bands = (0 until 10).map { i ->
                EqBand(
                    id = i,
                    type = if (i == 0) FilterType.LOW_SHELF else if (i == 9) FilterType.HIGH_SHELF else FilterType.PEAKING,
                    frequency = DEFAULT_FREQUENCIES[i],
                    gain = 0f,
                    q = DEFAULT_Q,
                    enabled = true
                )
            }
            return EqPreset(name, 0f, bands)
        }

        /**
         * Serialize this preset to Equalizer APO .txt format.
         * Example output:
         *   Preamp: -3.5 dB
         *   Filter 1: ON PK Fc 1000 Hz Gain 3.0 dB Q 1.0
         */
        fun EqPreset.toApoString(): String {
            val sb = StringBuilder()
            sb.appendLine(String.format(Locale.US, "Preamp: %.1f dB", preamp))
            bands.forEachIndexed { index, band ->
                sb.appendLine(band.toApoString(index + 1))
            }
            return sb.toString().trimEnd()
        }

        /**
         * Parse an Equalizer APO .txt string into an [EqPreset].
         * Lines that cannot be parsed are silently skipped.
         *
         * Supported filter line format:
         *   Filter N: ON|OFF <type> Fc <freq> Hz Gain <gain> dB Q <q>
         *   Filter N: ON|OFF <type> Fc <freq> Hz              (for LP/HP/BP/NO/AP — gain & Q optional)
         */
        fun fromApoString(text: String, name: String): EqPreset? {
            return try {
                var preamp = 0f
                val bands = mutableListOf<EqBand>()
                text.lines().forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.startsWith("Preamp:", ignoreCase = true) -> {
                            val parts = line.split("\\s+".toRegex())
                            preamp = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                        }
                        line.startsWith("Filter", ignoreCase = true) -> {
                            EqBand.fromApoLine(line, bands.size)?.let { bands.add(it) }
                        }
                    }
                }
                EqPreset(name, preamp, bands)
            } catch (e: Exception) {
                Log.e("EqPreset", "Failed to parse APO string: ${e.message}")
                null
            }
        }
    }
}