package com.example.specclash.data

import android.content.Context
import com.example.specclash.data.local.CachedPhoneDao
import com.example.specclash.data.local.CachedPhoneEntity
import com.example.specclash.data.local.ComparisonHistoryDao
import com.example.specclash.data.local.ComparisonHistoryEntity
import com.example.specclash.data.local.SavedMatchupDao
import com.example.specclash.data.local.SavedMatchupEntity
import com.example.specclash.data.local.SpecClashDatabase
import com.example.specclash.data.remote.PhoneDetailData
import com.example.specclash.data.remote.PhoneDetailResponse
import com.example.specclash.data.remote.SearchDeviceItem
import com.example.specclash.data.remote.SpecClashApi
import com.example.specclash.domain.PhoneSpec
import com.example.specclash.domain.SearchDevice
import com.example.specclash.domain.SearchRanking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Single source of truth for the UI layer.
 *
 *  * `searchPhones` always hits the network — the spec contract is small and
 *    dynamic, so we keep results fresh. It also tokenizes multi-word
 *    queries and ranks the merged candidate pool by relevance (see its
 *    kdoc) - callers get back an already-sorted, deduplicated list.
 *  * `getPhoneSpec` writes through the Room cache so a previously viewed
 *    device can be reloaded instantly while offline.
 *  * Comparison history + pinned matchups + cache count are exposed as
 *    cold Flows for the navigation drawer.
 */
class SpecClashRepository(
    private val api: SpecClashApi,
    private val cacheDao: CachedPhoneDao,
    private val historyDao: ComparisonHistoryDao,
    private val savedDao: SavedMatchupDao,
) {
    private val specsSerializer = MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), String.serializer())
    )

    /**
     * Tokenized multi-keyword device search. A literal query like `"samsung
     * s25"` often under-matches upstream (the search endpoint - and any
     * naive client-side substring filter - can miss "Samsung Galaxy S25"
     * since "Galaxy" breaks a literal substring match). To fix this:
     *
     *  1. The query is split into individual term tokens
     *     ([SearchRanking.tokenize]).
     *  2. When there's more than one token, we re-query per token (in
     *     parallel with the full-phrase query) so a real match isn't missed
     *     just because the endpoint under-matches the multi-word phrase.
     *  3. Every query attempt's results are merged and deduplicated by
     *     [SearchDeviceItem.slug].
     *  4. The merged pool is scored and sorted by [SearchRanking.rank],
     *     dropping candidates that match none of the query tokens.
     *
     * Returns `Result.success(emptyList())` — not a failure — when every
     * query attempt succeeds but simply finds no matches, so callers can
     * tell "genuinely zero results" apart from a network/server failure.
     * Only returns `Result.failure` when *every* attempt errors out.
     */
    suspend fun searchPhones(query: String): Result<List<SearchDevice>> = withContext(Dispatchers.IO) {
        val tokens = SearchRanking.tokenize(query)
        if (tokens.isEmpty()) return@withContext Result.success(emptyList())

        val queriesToTry = if (tokens.size > 1) (listOf(query) + tokens).distinct() else listOf(query)

        val outcomes = queriesToTry
            .map { q -> async { runCatching { api.searchPhones(q).data } } }
            .awaitAll()

        val successes = outcomes.mapNotNull { it.getOrNull() }
        if (successes.isEmpty()) {
            // Every attempt failed (e.g. no network) - surface the error
            // rather than silently reporting "zero results."
            val cause = outcomes.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException("Search failed")
            return@withContext Result.failure(cause)
        }

        val merged: List<SearchDeviceItem> = successes.flatten().distinctBy { it.slug }
        val ranked = SearchRanking.rank(merged, tokens) { it.name }
        Result.success(ranked.map { it.toDomain() })
    }

    suspend fun getPhoneSpec(slug: String): Result<PhoneSpec> = withContext(Dispatchers.IO) {
        runCatching {
            val response: PhoneDetailResponse = api.getPhoneDetails(slug)
            val data: PhoneDetailData = response.data
            val phone = PhoneSpec(
                slug = slug,
                name = data.name,
                image = data.image,
                specs = data.specs,
            )
            // Write-through cache
            cacheDao.upsert(phone.toEntity())
            phone
        }.recoverCatching {
            // Network failed — try the cache before rethrowing
            val cached = cacheDao.getBySlug(slug) ?: throw it
            cached.toDomain()
        }
    }

    // ---- Comparison history ---------------------------------------------

    fun recentHistory(limit: Int = 10): Flow<List<ComparisonHistoryEntity>> =
        historyDao.getRecentHistory(limit)

    suspend fun recordComparison(
        slugA: String, nameA: String, imageA: String,
        slugB: String, nameB: String, imageB: String,
    ) = withContext(Dispatchers.IO) {
        historyDao.insertComparison(
            ComparisonHistoryEntity(
                slugA = slugA, nameA = nameA, imageA = imageA,
                slugB = slugB, nameB = nameB, imageB = imageB,
            )
        )
        historyDao.pruneToLatest(ComparisonHistoryDao.MAX_HISTORY_ROWS)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        historyDao.clearHistory()
    }

    // ---- Pinned matchups -------------------------------------------------

    fun savedMatchups(): Flow<List<SavedMatchupEntity>> = savedDao.getSavedMatchups()

    fun isMatchupSaved(slugA: String, slugB: String): Flow<Boolean> =
        savedDao.isMatchupSaved(slugA, slugB)

    suspend fun saveMatchup(
        slugA: String, nameA: String, imageA: String,
        slugB: String, nameB: String, imageB: String,
    ) = withContext(Dispatchers.IO) {
        savedDao.saveMatchup(
            SavedMatchupEntity(
                slugA = slugA, nameA = nameA, imageA = imageA,
                slugB = slugB, nameB = nameB, imageB = imageB,
            )
        )
    }

    suspend fun deleteMatchup(slugA: String, slugB: String) = withContext(Dispatchers.IO) {
        savedDao.deleteMatchupEitherOrder(slugA, slugB)
    }

    // ---- Cache management -----------------------------------------------

    suspend fun cachedPhoneCount(): Int = withContext(Dispatchers.IO) {
        cacheDao.count()
    }

    suspend fun clearOfflineCache() = withContext(Dispatchers.IO) {
        cacheDao.clearAll()
    }

    private fun SearchDeviceItem.toDomain(): SearchDevice = SearchDevice(
        name = name,
        slug = slug,
        thumbnail = thumbnail,
        description = description,
    )

    private fun PhoneSpec.toEntity(): CachedPhoneEntity = CachedPhoneEntity(
        slug = slug,
        name = name,
        image = image,
        specsJson = Json.encodeToString(specsSerializer, specs),
    )

    private fun CachedPhoneEntity.toDomain(): PhoneSpec = PhoneSpec(
        slug = slug,
        name = name,
        image = image,
        specs = runCatching {
            Json.decodeFromString(specsSerializer, specsJson)
        }.getOrDefault(emptyMap()),
    )

    companion object {
        @Volatile
        private var instance: SpecClashRepository? = null

        fun get(context: Context): SpecClashRepository = instance ?: synchronized(this) {
            instance ?: SpecClashRepository(
                api = com.example.specclash.data.remote.SpecClashApiClient.api,
                cacheDao = SpecClashDatabase.get(context).cachedPhoneDao(),
                historyDao = SpecClashDatabase.get(context).comparisonHistoryDao(),
                savedDao = SpecClashDatabase.get(context).savedMatchupDao(),
            ).also { instance = it }
        }
    }
}
