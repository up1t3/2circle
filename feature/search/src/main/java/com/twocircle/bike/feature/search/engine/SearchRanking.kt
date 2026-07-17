package com.twocircle.bike.feature.search.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Search-result ranking weights. Tuned for bicycle touring: places near the rider matter
 * more than distant same-name matches, but a big city still outranks a hamlet of the
 * same name at any distance (so "Yalta" the city beats "Yalta" a clearing 5 km away).
 *
 * The final score is a weighted product of three sub-scores, each normalised to [0,1]:
 *
 *   score = relevance^W_REL  ×  population^W_POP  ×  proximity^W_PROX
 *
 * Product (not sum) so that a zero in any dimension zeros the whole result — e.g. a
 * place 1000 km away cannot surface just because it has a famous name.
 */
object SearchRanking {
    const val W_RELEVANCE = 1.0
    const val W_POPULATION = 0.55
    const val W_PROXIMITY = 0.85

    /** Distance at which proximity score drops to ~0.5 (km). Tunable. */
    const val HALF_LIFE_KM = 50.0

    /**
     * Compute the final ranking score in [0, 1].
     *
     * @param ftsRank raw FTS5 bm25 rank (negative; closer to 0 = better). Normalised
     *   against [worstFtsRank] so we get a [0,1] relevance.
     * @param population place population; 0 if unknown.
     * @param distanceKm distance from the search anchor; null/∞ if no anchor.
     * @param worstFtsRank the worst (most negative) FTS rank in the result set, used to
     *   normalise. If equal to [ftsRank], relevance is 1 (single best result).
     */
    fun score(
        ftsRank: Double,
        population: Long,
        distanceKm: Double?,
        worstFtsRank: Double = ftsRank,
    ): Double {
        val rel = relevance(ftsRank, worstFtsRank).coerceIn(0.0, 1.0)
        val pop = populationScore(population).coerceIn(0.0, 1.0)
        val prox = proximityScore(distanceKm).coerceIn(0.0, 1.0)
        // Weighted product. Use logarithms to avoid underflow and to make weights additive.
        val logScore = W_RELEVANCE * ln(rel.coerceAtLeast(1e-9)) +
            W_POPULATION * ln(pop.coerceAtLeast(1e-9)) +
            W_PROXIMITY * ln(prox.coerceAtLeast(1e-9))
        return exp(logScore).coerceIn(0.0, 1.0)
    }

    /**
     * Map FTS5 bm25 rank to [0,1] relevance.
     *
     * FTS5 bm25 returns negative values: 0 is a perfect match, more-negative is worse.
     * We divide by the worst rank in the set and subtract from 1, so the best result
     * gets ~1 and the worst gets ~0.
     *
     * Special case: single result (worst == best) → relevance 1.
     */
    fun relevance(ftsRank: Double, worstFtsRank: Double): Double {
        if (worstFtsRank >= ftsRank) return 1.0 // best == worst (or only) result
        // ftsRank is negative or zero; worstFtsRank is more negative.
        val span = -worstFtsRank // positive
        if (span <= 0.0) return 1.0
        return ((-ftsRank) / span).let { 1.0 - it.coerceIn(0.0, 1.0) }
    }

    /**
     * Population score: a saturating curve so a 10k town and a 10M city score similarly
     * (both "real places"), but a hamlet (pop 5) is heavily downweighted.
     *
     * f(pop) = pop / (pop + K). With K = 5000, pop 5k → 0.5, pop 50k → 0.91.
     */
    fun populationScore(population: Long, k: Double = 5_000.0): Double {
        if (population <= 0) return 0.05 // unknown — small but nonzero so it can still surface
        val p = population.toDouble()
        return p / (p + k)
    }

    /**
     * Proximity score: exponential decay from the anchor point.
     *
     * f(d) = 0.5^(d / HALF_LIFE_KM). At 0 km → 1; at HALF_LIFE_KM → 0.5; far away → ~0.
     * Exponential (not linear) so a place 5 km away is dramatically better than 100 km,
     * matching rider intuition.
     */
    fun proximityScore(distanceKm: Double?): Double {
        if (distanceKm == null) return 0.5 // no anchor: neutral
        if (distanceKm < 0.0) return 0.5
        return sqrt(0.5.pow(distanceKm / HALF_LIFE_KM))
    }
}

private fun Double.pow(exp: Double): Double = Math.pow(this, exp)
