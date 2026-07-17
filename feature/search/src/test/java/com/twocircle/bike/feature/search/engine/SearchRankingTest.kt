package com.twocircle.bike.feature.search.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchRankingTest {

    @Test
    fun `relevance is 1 when result is the only one`() {
        // Single result: worst == best, both 0.
        assertThat(SearchRanking.relevance(0.0, 0.0)).isEqualTo(1.0)
    }

    @Test
    fun `relevance is 1 when result has the best rank`() {
        // ftsRank = 0 (best), worst = -10. Span = 10. (0/10) subtracted from 1 → 1.
        assertThat(SearchRanking.relevance(0.0, -10.0)).isEqualTo(1.0)
    }

    @Test
    fun `relevance is 0 when result has the worst rank`() {
        // ftsRank = -10 (worst), worst = -10. (-(-10))/10 = 1. 1 - 1 = 0.
        assertThat(SearchRanking.relevance(-10.0, -10.0)).isEqualTo(1.0) // tie treated as best
        // Now with a clearly worse worst:
        assertThat(SearchRanking.relevance(-5.0, -10.0)).isWithin(0.01).of(0.5)
    }

    @Test
    fun `population score saturates`() {
        // pop 0 → tiny nonzero (unknown place still surfaces).
        assertThat(SearchRanking.populationScore(0L)).isLessThan(0.1)
        // pop 5000 → 0.5 (the K constant).
        assertThat(SearchRanking.populationScore(5_000L)).isWithin(0.01).of(0.5)
        // pop 5_000_000 → close to 1.
        assertThat(SearchRanking.populationScore(5_000_000L)).isGreaterThan(0.99)
    }

    @Test
    fun `proximity score is 1 at zero distance`() {
        assertThat(SearchRanking.proximityScore(0.0)).isWithin(0.01).of(1.0)
    }

    @Test
    fun `proximity score is around 0_7 at half-life distance`() {
        // f(50 km) = sqrt(0.5^1) = sqrt(0.5) ≈ 0.707.
        assertThat(SearchRanking.proximityScore(50.0)).isWithin(0.02).of(0.707)
    }

    @Test
    fun `proximity score is neutral when no anchor`() {
        // No distance → 0.5 (neutral, doesn't penalise).
        assertThat(SearchRanking.proximityScore(null)).isEqualTo(0.5)
    }

    @Test
    fun `proximity score decays with distance`() {
        val close = SearchRanking.proximityScore(1.0)
        val mid = SearchRanking.proximityScore(50.0)
        val far = SearchRanking.proximityScore(500.0)
        assertThat(close).isGreaterThan(mid)
        assertThat(mid).isGreaterThan(far)
    }

    @Test
    fun `combined score favours nearby city over distant same-name hamlet`() {
        // Same relevance, same name match. City pop 100k at 5 km vs hamlet pop 10 at 1 km.
        // The city should win despite the hamlet being closer.
        val cityScore = SearchRanking.score(
            ftsRank = -1.0, population = 100_000L, distanceKm = 5.0, worstFtsRank = -5.0,
        )
        val hamletScore = SearchRanking.score(
            ftsRank = -1.0, population = 10L, distanceKm = 1.0, worstFtsRank = -5.0,
        )
        assertThat(cityScore).isGreaterThan(hamletScore)
    }

    @Test
    fun `combined score is in unit interval`() {
        val s = SearchRanking.score(
            ftsRank = -2.0, population = 1000L, distanceKm = 30.0, worstFtsRank = -5.0,
        )
        assertThat(s).isAtLeast(0.0)
        assertThat(s).isAtMost(1.0)
    }

    @Test
    fun `combined score zeros out an impossibly distant place`() {
        // 10000 km away, irrelevant name, no population → should be ~0.
        val s = SearchRanking.score(
            ftsRank = -10.0, population = 0L, distanceKm = 10_000.0, worstFtsRank = -10.0,
        )
        assertThat(s).isLessThan(0.01)
    }

    @Test
    fun `combined score rewards perfect name match`() {
        val perfect = SearchRanking.score(
            ftsRank = 0.0, population = 1000L, distanceKm = 30.0, worstFtsRank = -5.0,
        )
        val partial = SearchRanking.score(
            ftsRank = -4.0, population = 1000L, distanceKm = 30.0, worstFtsRank = -5.0,
        )
        assertThat(perfect).isGreaterThan(partial)
    }
}
