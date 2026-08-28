package com.example.specclash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {

    // -----------------------------------------------------------------
    //  tokenize
    // -----------------------------------------------------------------

    @Test
    fun `tokenize splits on whitespace and lowercases`() {
        assertEquals(listOf("samsung", "s25"), SearchRanking.tokenize("Samsung S25"))
    }

    @Test
    fun `tokenize strips punctuation and collapses extra spaces`() {
        assertEquals(listOf("samsung", "s25"), SearchRanking.tokenize("  Samsung,   S25!  "))
    }

    @Test
    fun `tokenize returns empty list for blank or punctuation-only input`() {
        assertEquals(emptyList<String>(), SearchRanking.tokenize("   "))
        assertEquals(emptyList<String>(), SearchRanking.tokenize("!!!"))
    }

    // -----------------------------------------------------------------
    //  rank - the required "samsung s25" ranking scenario
    // -----------------------------------------------------------------

    @Test
    fun `rank - samsung s25 ranks the S25 family above generic Samsung phones`() {
        val candidates = listOf(
            "Samsung Galaxy A54",
            "Samsung Galaxy S25 Ultra",
            "Samsung Galaxy Z Fold6",
            "Samsung Galaxy S25",
            "Samsung Galaxy S25 FE",
            "Samsung Galaxy S24",
        )
        val tokens = SearchRanking.tokenize("samsung s25")

        val ranked = SearchRanking.rank(candidates, tokens) { it }

        val s25FamilyIndices = listOf(
            ranked.indexOf("Samsung Galaxy S25"),
            ranked.indexOf("Samsung Galaxy S25 Ultra"),
            ranked.indexOf("Samsung Galaxy S25 FE"),
        )
        val genericIndices = listOf(
            ranked.indexOf("Samsung Galaxy A54"),
            ranked.indexOf("Samsung Galaxy Z Fold6"),
            ranked.indexOf("Samsung Galaxy S24"),
        )

        assertTrue("All S25-family devices must be present", s25FamilyIndices.all { it >= 0 })
        assertTrue("All generic Samsung devices must be present", genericIndices.all { it >= 0 })
        assertTrue(
            "Every S25-family match must rank above every generic Samsung match",
            s25FamilyIndices.max() < genericIndices.min(),
        )
    }

    @Test
    fun `rank - the plain Samsung Galaxy S25 match sorts first among the tied S25 family`() {
        val candidates = listOf("Samsung Galaxy S25 Ultra", "Samsung Galaxy S25 FE", "Samsung Galaxy S25")
        val tokens = SearchRanking.tokenize("samsung s25")

        val ranked = SearchRanking.rank(candidates, tokens) { it }

        assertEquals("Samsung Galaxy S25", ranked.first())
    }

    // -----------------------------------------------------------------
    //  rank - scoring rules
    // -----------------------------------------------------------------

    @Test
    fun `rank - candidates matching zero tokens are filtered out entirely`() {
        val candidates = listOf("Samsung Galaxy S25", "Apple iPhone 16", "Google Pixel 9")
        val tokens = SearchRanking.tokenize("samsung s25")

        val ranked = SearchRanking.rank(candidates, tokens) { it }

        assertEquals(listOf("Samsung Galaxy S25"), ranked)
    }

    @Test
    fun `rank - matching both tokens outranks matching only one`() {
        val candidates = listOf("Samsung Galaxy A54", "Samsung Galaxy S25")
        val tokens = SearchRanking.tokenize("samsung s25")

        val ranked = SearchRanking.rank(candidates, tokens) { it }

        assertEquals(listOf("Samsung Galaxy S25", "Samsung Galaxy A54"), ranked)
    }

    @Test
    fun `rank - consecutive substring match outranks a non-consecutive match with the same token hit count`() {
        // Both candidates match all tokens of "galaxy s25", but only the first
        // contains "galaxy s25" as a contiguous phrase.
        val candidates = listOf("Samsung Galaxy S25", "Samsung S25 Galaxy Edition")
        val tokens = SearchRanking.tokenize("galaxy s25")

        val ranked = SearchRanking.rank(candidates, tokens) { it }

        assertEquals("Samsung Galaxy S25", ranked.first())
    }

    @Test
    fun `rank - empty query tokens returns candidates unchanged`() {
        val candidates = listOf("Samsung Galaxy S25", "Apple iPhone 16")
        assertEquals(candidates, SearchRanking.rank(candidates, emptyList()) { it })
    }

    @Test
    fun `rank - recency tie-breaker prefers the higher model number when relevance is equal`() {
        // Both match only the "galaxy" token equally - same score - so the
        // higher inferred generation number should sort first.
        val candidates = listOf("Samsung Galaxy S22", "Samsung Galaxy S25")
        val tokens = SearchRanking.tokenize("galaxy")

        val ranked = SearchRanking.rank(candidates, tokens) { it }

        assertEquals("Samsung Galaxy S25", ranked.first())
    }

    @Test
    fun `rank - is case and punctuation insensitive`() {
        val candidates = listOf("samsung galaxy s25")
        val tokens = SearchRanking.tokenize("SAMSUNG, S25!")

        val ranked = SearchRanking.rank(candidates, tokens) { it }

        assertEquals(listOf("samsung galaxy s25"), ranked)
    }
}
