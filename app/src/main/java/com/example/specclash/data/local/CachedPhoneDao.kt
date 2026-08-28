package com.example.specclash.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedPhoneDao {

    @Query("SELECT * FROM cached_phones WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): CachedPhoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedPhoneEntity)

    @Query("DELETE FROM cached_phones WHERE slug = :slug")
    suspend fun delete(slug: String)

    @Query("SELECT COUNT(*) FROM cached_phones")
    suspend fun count(): Int

    @Query("SELECT slug, name, image FROM cached_phones ORDER BY slug")
    suspend fun listAll(): List<CachedPhoneSummary>

    @Query("DELETE FROM cached_phones")
    suspend fun clearAll()
}

/**
 * Lightweight projection used for cache listings.
 */
data class CachedPhoneSummary(
    val slug: String,
    val name: String,
    val image: String,
)

