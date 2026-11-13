// Modified for Cymatic: Serialization annotations removed; ImportedEq retained from TextCodecs.kt.
// Upstream: Chronosauros/contour 95d592df (Apache-2.0); see README.md and bundled notices.
package io.github.chronosauros.contour.core

enum class FilterType {
    PEAK,
    LOW_SHELF,
    HIGH_SHELF,
    LOW_PASS,
    HIGH_PASS,
    BAND_PASS,
    ALL_PASS,
}

val FilterType.isPass: Boolean
    get() =
        this == FilterType.LOW_PASS ||
            this == FilterType.HIGH_PASS ||
            this == FilterType.BAND_PASS ||
            this == FilterType.ALL_PASS

val FilterType.isShelf: Boolean
    get() = this == FilterType.LOW_SHELF || this == FilterType.HIGH_SHELF

data class Band(
    val id: String,
    val type: FilterType,
    val freqHz: Double,
    val gainDb: Double,
    val q: Double,
    val enabled: Boolean = true,
)

data class ImportedEq(
    val bands: List<Band>,
    val preampDb: Double?,
    val notes: List<String> = emptyList(),
)

data class Profile(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val icon: String = "",
    val bands: List<Band>,
    val preampDb: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
) {

    fun effectivePreampDb(): Double = preampDb ?: Preamp.auto(bands).toDouble()
}
