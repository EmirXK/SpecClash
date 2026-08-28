package com.example.specclash.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached full spec payload for a viewed device. The [specsJson] field stores
 * the raw `Map<String, Map<String, String>>` from the API as a serialized blob
 * so we don't need a complex relational schema for a small, write-once cache.
 */
@Entity(tableName = "cached_phones")
data class CachedPhoneEntity(
    @PrimaryKey val slug: String,
    val name: String,
    val image: String,
    val specsJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
