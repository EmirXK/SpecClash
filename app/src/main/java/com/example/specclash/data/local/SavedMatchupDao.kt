package com.example.specclash.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMatchupDao {

    @Query("SELECT * FROM saved_matchups ORDER BY pinnedAt DESC")
    fun getSavedMatchups(): Flow<List<SavedMatchupEntity>>

    /**
     * Upsert by composite key (slugA, slugB) so re-pinning the same matchup
     * is a no-op rather than creating duplicates.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMatchup(item: SavedMatchupEntity)

    @Query("DELETE FROM saved_matchups WHERE slugA = :slugA AND slugB = :slugB")
    suspend fun deleteMatchup(slugA: String, slugB: String)

    @Query("DELETE FROM saved_matchups WHERE (slugA = :slugA AND slugB = :slugB) OR (slugA = :slugB AND slugB = :slugA)")
    suspend fun deleteMatchupEitherOrder(slugA: String, slugB: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_matchups WHERE (slugA = :slugA AND slugB = :slugB) OR (slugA = :slugB AND slugB = :slugA))")
    fun isMatchupSaved(slugA: String, slugB: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM saved_matchups")
    suspend fun count(): Int
}
