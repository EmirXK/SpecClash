package com.example.specclash.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted record of a comparison that the user has *successfully viewed*
 * (both specs loaded). Older entries are auto-pruned beyond the most
 * recent 20 by [ComparisonHistoryDao.insertComparison].
 */
@Entity(tableName = "comparison_history")
data class ComparisonHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slugA: String,
    val nameA: String,
    val imageA: String,
    val slugB: String,
    val nameB: String,
    val imageB: String,
    val timestamp: Long = System.currentTimeMillis(),
)
