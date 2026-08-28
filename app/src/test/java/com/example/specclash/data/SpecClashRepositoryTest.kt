package com.example.specclash.data

import com.example.specclash.data.local.CachedPhoneDao
import com.example.specclash.data.local.CachedPhoneEntity
import com.example.specclash.data.local.CachedPhoneSummary
import com.example.specclash.data.local.ComparisonHistoryDao
import com.example.specclash.data.local.ComparisonHistoryEntity
import com.example.specclash.data.local.SavedMatchupDao
import com.example.specclash.data.local.SavedMatchupEntity
import com.example.specclash.data.remote.PhoneDetailResponse
import com.example.specclash.data.remote.SearchDeviceItem
import com.example.specclash.data.remote.SearchResponse
import com.example.specclash.data.remote.SpecClashApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecClashRepositoryTest {

    /** Records every query it was asked for and answers from a fixed [byQuery] map. */
    private class FakeApi(
        private val byQuery: Map<String, List<SearchDeviceItem>>,
        private val failing: Set<String> = emptySet(),
    ) : SpecClashApi {
        // Repository.searchPhones fires per-token queries concurrently via
        // async/awaitAll, so this must tolerate concurrent writes.
        val queriesReceived = java.util.concurrent.CopyOnWriteArrayList<String>()

        override suspend fun searchPhones(query: String): SearchResponse {
            queriesReceived.add(query)
            if (query in failing) throw java.io.IOException("simulated network failure for \"$query\"")
            val data = byQuery[query].orEmpty()
            return SearchResponse(status = "ok", count = data.size, data = data)
        }

        override suspend fun getPhoneDetails(slug: String): PhoneDetailResponse =
            throw NotImplementedError("not used by these tests")
    }

    private object NoOpCachedPhoneDao : CachedPhoneDao {
        override suspend fun getBySlug(slug: String): CachedPhoneEntity? = null
        override suspend fun upsert(entity: CachedPhoneEntity) = Unit
        override suspend fun delete(slug: String) = Unit
        override suspend fun count(): Int = 0
        override suspend fun listAll(): List<CachedPhoneSummary> = emptyList()
        override suspend fun clearAll() = Unit
    }

    private object NoOpHistoryDao : ComparisonHistoryDao {
        override fun getRecentHistory(limit: Int): Flow<List<ComparisonHistoryEntity>> = flowOf(emptyList())
        override suspend fun insertComparison(item: ComparisonHistoryEntity) = Unit
        override suspend fun pruneToLatest(keep: Int) = Unit
        override suspend fun clearHistory() = Unit
    }

    private object NoOpSavedMatchupDao : SavedMatchupDao {
        override fun getSavedMatchups(): Flow<List<SavedMatchupEntity>> = flowOf(emptyList())
        override suspend fun saveMatchup(item: SavedMatchupEntity) = Unit
        override suspend fun deleteMatchup(slugA: String, slugB: String) = Unit
        override suspend fun deleteMatchupEitherOrder(slugA: String, slugB: String) = Unit
        override fun isMatchupSaved(slugA: String, slugB: String): Flow<Boolean> = flowOf(false)
        override suspend fun count(): Int = 0
    }

    private fun repositoryWith(api: FakeApi): SpecClashRepository =
        SpecClashRepository(api, NoOpCachedPhoneDao, NoOpHistoryDao, NoOpSavedMatchupDao)

    private fun item(name: String, slug: String) = SearchDeviceItem(name = name, slug = slug, thumbnail = "")

    @Test
    fun `searchPhones - multi-word query merges per-token results, dedupes by slug, and ranks the S25 family first`() {
        val api = FakeApi(
            byQuery = mapOf(
                // The full phrase under-matches upstream - only finds the plain S25.
                "samsung s25" to listOf(item("Samsung Galaxy S25", "samsung-galaxy-s25")),
                // The individual tokens each surface the rest of the family.
                "samsung" to listOf(
                    item("Samsung Galaxy S25", "samsung-galaxy-s25"), // duplicate of the phrase-query hit
                    item("Samsung Galaxy S25 Ultra", "samsung-galaxy-s25-ultra"),
                    item("Samsung Galaxy A54", "samsung-galaxy-a54"),
                ),
                "s25" to listOf(
                    item("Samsung Galaxy S25", "samsung-galaxy-s25"),
                    item("Samsung Galaxy S25 Ultra", "samsung-galaxy-s25-ultra"),
                    item("Samsung Galaxy S25 FE", "samsung-galaxy-s25-fe"),
                ),
            ),
        )
        val repository = repositoryWith(api)

        val result = runBlocking { repository.searchPhones("samsung s25") }

        assertTrue(result.isSuccess)
        val names = result.getOrThrow().map { it.name }
        // Deduped: exactly one "Samsung Galaxy S25" entry despite 3 queries returning it.
        assertEquals(1, names.count { it == "Samsung Galaxy S25" })
        // All three S25-family devices are present and rank above the generic one.
        val s25FamilyIndices = listOf("Samsung Galaxy S25", "Samsung Galaxy S25 Ultra", "Samsung Galaxy S25 FE")
            .map { names.indexOf(it) }
        assertTrue(s25FamilyIndices.all { it >= 0 })
        val genericIndex = names.indexOf("Samsung Galaxy A54")
        assertTrue(genericIndex >= 0)
        assertTrue(s25FamilyIndices.max() < genericIndex)
        // All three underlying queries (phrase + both tokens) were actually issued.
        assertEquals(setOf("samsung s25", "samsung", "s25"), api.queriesReceived.toSet())
    }

    @Test
    fun `searchPhones - zero matches from every query returns an empty success list, not a failure`() {
        val api = FakeApi(byQuery = mapOf("xyzzynonexistent" to emptyList()))
        val repository = repositoryWith(api)

        val result = runBlocking { repository.searchPhones("xyzzynonexistent") }

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrThrow())
    }

    @Test
    fun `searchPhones - single-word query issues exactly one network call`() {
        val api = FakeApi(byQuery = mapOf("pixel" to listOf(item("Google Pixel 9", "google-pixel-9"))))
        val repository = repositoryWith(api)

        runBlocking { repository.searchPhones("pixel") }

        assertEquals(listOf("pixel"), api.queriesReceived)
    }

    @Test
    fun `searchPhones - every underlying query failing returns a failure, not an empty success`() {
        val api = FakeApi(byQuery = emptyMap(), failing = setOf("brokenquery"))
        val repository = repositoryWith(api)

        val result = runBlocking { repository.searchPhones("brokenquery") }

        assertTrue(result.isFailure)
    }

    @Test
    fun `searchPhones - one failing sub-query does not sink the whole search when others succeed`() {
        val api = FakeApi(
            byQuery = mapOf("s25" to listOf(item("Samsung Galaxy S25", "samsung-galaxy-s25"))),
            failing = setOf("flaky s25", "flaky"),
        )
        val repository = repositoryWith(api)

        val result = runBlocking { repository.searchPhones("flaky s25") }

        assertTrue(result.isSuccess)
        assertEquals(listOf("Samsung Galaxy S25"), result.getOrThrow().map { it.name })
    }

    @Test
    fun `searchPhones - blank query returns an empty success list without hitting the network`() {
        val api = FakeApi(byQuery = emptyMap())
        val repository = repositoryWith(api)

        val result = runBlocking { repository.searchPhones("   ") }

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrThrow())
        assertTrue(api.queriesReceived.isEmpty())
    }
}
