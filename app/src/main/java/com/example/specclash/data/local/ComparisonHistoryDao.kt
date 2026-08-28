package com.example.specclash.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComparisonHistoryDao {

    @Query("SELECT * FROM comparison_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 10): Flow<List<ComparisonHistoryEntity>>

    /**
     * Inserts a new entry. After the insert we trim the table to the
     * [MAX_HISTORY_ROWS] most-recent entries so the cache never grows
     * unbounded.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComparison(item: ComparisonHistoryEntity)

    @Query(
        "DELETE FROM comparison_history WHERE id NOT IN " +
            "(SELECT id FROM comparison_history ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun pruneToLatest(keep: Int = MAX_HISTORY_ROWS)

    @Query("DELETE FROM comparison_history")
    suspend fun clearHistory()

    companion object {
        const val MAX_HISTORY_ROWS = 20
    }
}
