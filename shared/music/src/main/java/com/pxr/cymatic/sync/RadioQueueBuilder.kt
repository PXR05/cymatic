package com.pxr.cymatic.sync

import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class RadioCandidate(
    val id: String,
    val embedding: QuantizedEmbedding,
    val artist: String?,
    val album: String?,
)

data class RadioQueueOptions(
    val limit: Int = 25,
    val lambda: Float = 0.7f,
    val temperature: Float = 0.08f,
    val topK: Int = 25,
    val artistCooldown: Int = 4,
    val albumCooldown: Int = 3,
    val shortlistLimit: Int = 500,
    val minSeedSimilarity: Float = 0.25f,
    val rngSeed: Long = System.nanoTime(),
    val seedArtist: String? = null,
    val seedAlbum: String? = null,
    val excludeIds: Set<String> = emptySet(),
    val initialRecentArtists: List<String> = emptyList(),
    val initialRecentAlbums: List<String> = emptyList(),
    val anchorBlend: QuantizedEmbedding? = null,
)

object RadioQueueBuilder {
    private const val NOVELTY_WINDOW = 8

    private class IndexedCandidate(
        val id: String,
        val vector: FloatArray,
        val artistNorm: String,
        val albumNorm: String,
        val recentSims: FloatArray = FloatArray(NOVELTY_WINDOW),
    )

    private class ScoredCandidate(
        val poolIndex: Int,
        val mmr: Float,
        val cooldown: Boolean,
    )

    fun build(
        seed: QuantizedEmbedding,
        excludeId: String,
        candidates: List<RadioCandidate>,
        opts: RadioQueueOptions = RadioQueueOptions(),
    ): List<String> {
        if (opts.limit <= 0 || candidates.isEmpty() || seed.vector.isEmpty()) return emptyList()

        val seedDim = seed.vector.size
        val filtered = ArrayList<Pair<RadioCandidate, Float>>()
        for (cand in candidates) {
            if (cand.id == excludeId || cand.id in opts.excludeIds) continue
            if (cand.embedding.vector.size != seedDim) continue
            val sim = seed.dot(cand.embedding)
            if (sim >= opts.minSeedSimilarity) {
                filtered.add(cand to sim)
            }
        }
        if (filtered.isEmpty()) return emptyList()

        val shortlisted =
            if (filtered.size > opts.shortlistLimit) {
                filtered.sortByDescending { it.second }
                filtered.subList(0, opts.shortlistLimit)
            } else {
                filtered
            }

        val seedFloat = l2Normalize(seed.toFloatArray())
        val pool = ArrayList<IndexedCandidate>(shortlisted.size)
        for ((cand, _) in shortlisted) {
            pool.add(
                IndexedCandidate(
                    id = cand.id,
                    vector = l2Normalize(cand.embedding.toFloatArray()),
                    artistNorm = normKey(cand.artist),
                    albumNorm = normKey(cand.album),
                )
            )
        }

        val rng = Random(opts.rngSeed)
        val picked = ArrayList<String>(min(opts.limit, pool.size))
        val recentArtists = ArrayList<String>()
        val recentAlbums = ArrayList<String>()
        if (opts.initialRecentArtists.isNotEmpty() || opts.initialRecentAlbums.isNotEmpty()) {
            opts.initialRecentArtists.forEach { recentArtists.add(normKey(it)) }
            opts.initialRecentAlbums.forEach { recentAlbums.add(normKey(it)) }
        } else if (!opts.seedArtist.isNullOrBlank() || !opts.seedAlbum.isNullOrBlank()) {
            recentArtists.add(normKey(opts.seedArtist))
            recentAlbums.add(normKey(opts.seedAlbum))
        }

        val anchor =
            if (opts.anchorBlend != null && opts.anchorBlend.vector.size == seedFloat.size) {
                val blend = l2Normalize(opts.anchorBlend.toFloatArray())
                val combined = FloatArray(seedFloat.size) { i ->
                    0.7f * seedFloat[i] + 0.3f * blend[i]
                }
                l2Normalize(combined)
            } else {
                seedFloat.copyOf()
            }
        val limit = min(opts.limit, pool.size)
        val scored = ArrayList<ScoredCandidate>(pool.size)
        val eligible = ArrayList<ScoredCandidate>(pool.size)

        while (picked.size < limit && pool.isNotEmpty()) {
            scored.clear()
            eligible.clear()
            val simCount = min(picked.size, NOVELTY_WINDOW)

            for (i in pool.indices) {
                val item = pool[i]
                val sim = dot(anchor, item.vector)
                var novelty = 0f
                for (w in 0 until simCount) {
                    val s = item.recentSims[w]
                    if (s > novelty) novelty = s
                }
                val cooldown = violatesCooldown(
                    item.artistNorm,
                    item.albumNorm,
                    recentArtists,
                    recentAlbums,
                    opts.artistCooldown,
                    opts.albumCooldown,
                )
                val mmr =
                    opts.lambda * sim - (1f - opts.lambda) * novelty - (if (cooldown) 1f else 0f)
                scored.add(ScoredCandidate(i, mmr, cooldown))
            }

            scored.sortByDescending { it.mmr }
            for (s in scored) {
                if (!s.cooldown) eligible.add(s)
            }
            val activeList = if (eligible.isNotEmpty()) eligible else scored
            val effectiveTopK =
                if (activeList.size <= opts.topK) {
                    max(1, (activeList.size + 1) / 2)
                } else {
                    opts.topK
                }
            val k = max(1, min(effectiveTopK, activeList.size))

            val tau = max(1e-6f, opts.temperature).toDouble()
            val maxMmr = activeList[0].mmr.toDouble()
            val weights = DoubleArray(k)
            var totalWeight = 0.0
            for (i in 0 until k) {
                val w = exp((activeList[i].mmr.toDouble() - maxMmr) / tau)
                weights[i] = w
                totalWeight += w
            }

            var r = rng.nextDouble() * (if (totalWeight > 0.0) totalWeight else 1.0)
            var chosenPoolIdx = activeList[0].poolIndex
            for (i in 0 until k) {
                r -= weights[i]
                if (r <= 0.0) {
                    chosenPoolIdx = activeList[i].poolIndex
                    break
                }
            }

            val chosenItem = pool[chosenPoolIdx]
            val ringSlot = picked.size % NOVELTY_WINDOW
            picked.add(chosenItem.id)
            recentArtists.add(chosenItem.artistNorm)
            recentAlbums.add(chosenItem.albumNorm)

            val lastIdx = pool.lastIndex
            if (chosenPoolIdx != lastIdx) {
                pool[chosenPoolIdx] = pool[lastIdx]
            }
            pool.removeAt(lastIdx)

            for (item in pool) {
                item.recentSims[ringSlot] = dot(item.vector, chosenItem.vector)
            }

            var normSq = 0.0
            for (i in anchor.indices) {
                val v = 0.7f * seedFloat[i] + 0.3f * chosenItem.vector[i]
                anchor[i] = v
                normSq += v * v
            }
            val invNorm = if (normSq > 0.0) (1.0 / sqrt(normSq)).toFloat() else 1f
            for (i in anchor.indices) anchor[i] *= invNorm
        }

        return picked
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val n = min(a.size, b.size)
        for (i in 0 until n) sum += a[i] * b[i]
        return sum
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sum = 0.0
        for (v in vec) sum += v * v
        val invNorm = if (sum > 0.0) (1.0 / sqrt(sum)).toFloat() else 1f
        val out = FloatArray(vec.size)
        for (i in vec.indices) out[i] = vec[i] * invNorm
        return out
    }

    private fun normKey(value: String?): String =
        value?.trim()?.lowercase(Locale.ROOT).orEmpty()

    private fun violatesCooldown(
        artistNorm: String,
        albumNorm: String,
        recentArtists: List<String>,
        recentAlbums: List<String>,
        artistCooldown: Int,
        albumCooldown: Int,
    ): Boolean {
        if (artistNorm.isNotEmpty() && artistCooldown > 0) {
            val start = max(0, recentArtists.size - artistCooldown)
            for (i in start until recentArtists.size) {
                if (recentArtists[i] == artistNorm) return true
            }
        }
        if (albumNorm.isNotEmpty() && albumCooldown > 0) {
            val start = max(0, recentAlbums.size - albumCooldown)
            for (i in start until recentAlbums.size) {
                if (recentAlbums[i] == albumNorm) return true
            }
        }
        return false
    }
}
