package com.example.specclash.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-pinned comparison pair. Sticks around indefinitely until explicitly
 * deleted by the user from the navigation drawer.
 */
@Entity(tableName = "saved_matchups")
data class SavedMatchupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slugA: String,
    val nameA: String,
    val imageA: String,
    val slugB: String,
    val nameB: String,
    val imageB: String,
    val pinnedAt: Long = System.currentTimeMillis(),
)
