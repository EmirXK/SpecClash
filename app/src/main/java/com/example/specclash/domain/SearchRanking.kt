package com.example.specclash.domain

/**
 * Tokenized multi-keyword search ranking for the device picker.
 *
 * A literal substring match (the previous approach) fails whenever the
 * user's words don't appear in the exact order/adjacency used by the
 * official device name — e.g. searching `"samsung s25"` never matches
 * `"Samsung Galaxy S25"` because `"Galaxy"` sits between the two query
 * words. This ranks candidates by how many of the query's individual
 * *tokens* they match instead of requiring the whole query to appear
 * verbatim, so word order and omitted mid-name words no longer break search.
 */
object SearchRanking {

    /**
     * Normalizes [query] (trim, lowercase, strip punctuation) and splits it
     * into individual term tokens, e.g. `"Samsung, S25!"` -> `["samsung",
     * "s25"]`. Returns an empty list for a blank/punctuation-only query.
     */
    fun tokenize(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    /**
     * Scores [candidates] against [queryTokens] (from [tokenize]) and
     * returns them sorted by descending relevance, dropping any candidate
     * that matches zero tokens. [nameOf] extracts the display name to score
     * from each candidate.
     *
     * Ties are broken by an inferred recency signal — the largest
     * model/generation number found in the name. Search results carry no
     * release-date field, but recent phone naming schemes (Galaxy S`xx`,
     * iPhone `xx`, Pixel `x`) reliably increment with each release, so the
     * highest digit run is a reasonable relative "newer" proxy — then by
     * name length (shorter, more exact matches first).
     *
     * Returns [candidates] unchanged, in their original order, when
     * [queryTokens] is empty.
     */
    fun <T> rank(candidates: List<T>, queryTokens: List<String>, nameOf: (T) -> String): List<T> {
        if (queryTokens.isEmpty()) return candidates
        return candidates
            .map { candidate -> candidate to scoreName(nameOf(candidate), queryTokens) }
            .filter { (_, score) -> score.tokenHits > 0 }
            .sortedWith(
                compareByDescending<Pair<T, NameScore>> { it.second.total }
                    .thenByDescending { recencyHint(nameOf(it.first)) }
                    .thenBy { nameOf(it.first).length },
            )
            .map { it.first }
    }

    private data class NameScore(val tokenHits: Int, val total: Double)

    private const val EXACT_TOKEN_POINTS = 10.0
    private const val PREFIX_TOKEN_POINTS = 7.0
    private const val SUBSTRING_TOKEN_POINTS = 4.0
    private const val FULL_MATCH_BONUS = 20.0
    private const val CONSECUTIVE_MATCH_BONUS = 15.0
    private const val PREFIX_MATCH_BONUS = 10.0

    private fun scoreName(name: String, queryTokens: List<String>): NameScore {
        val normalizedName = normalize(name)
        val nameTokens = normalizedName.split(' ').filter { it.isNotEmpty() }

        var hits = 0
        var score = 0.0
        for (token in queryTokens) {
            when {
                nameTokens.any { it == token } -> {
                    hits++
                    score += EXACT_TOKEN_POINTS
                }
                nameTokens.any { it.startsWith(token) } -> {
                    hits++
                    score += PREFIX_TOKEN_POINTS
                }
                normalizedName.contains(token) -> {
                    hits++
                    score += SUBSTRING_TOKEN_POINTS
                }
            }
        }

        // Full-match bonus: every query token matched somewhere in the name.
        if (hits == queryTokens.size) {
            score += FULL_MATCH_BONUS
        }

        // Consecutive/substring bonus: the query, joined back into one
        // phrase, appears verbatim inside the name (e.g. "galaxy s25").
        // An exact-prefix bonus stacks on top when the name starts with it.
        val joinedQuery = queryTokens.joinToString(" ")
        if (joinedQuery.isNotEmpty() && normalizedName.contains(joinedQuery)) {
            score += CONSECUTIVE_MATCH_BONUS
            if (normalizedName.startsWith(joinedQuery)) {
                score += PREFIX_MATCH_BONUS
            }
        }

        return NameScore(hits, score)
    }

    /** Trims, lowercases, strips punctuation, and collapses whitespace. */
    private fun normalize(text: String): String = text
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9+ ]"), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /**
     * Best-effort "newer is bigger" proxy: the largest plausible
     * model/generation number found in [name]. Not a real release year —
     * search results carry no date field — but taking the max of every
     * digit run in the name is robust against noise like a trailing "5G".
     */
    private fun recencyHint(name: String): Int =
        Regex("""\d+""").findAll(name)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..999 }
            .maxOrNull() ?: 0
}
