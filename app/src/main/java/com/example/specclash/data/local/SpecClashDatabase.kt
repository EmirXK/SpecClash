package com.example.specclash.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedPhoneEntity::class,
        ComparisonHistoryEntity::class,
        SavedMatchupEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SpecClashDatabase : RoomDatabase() {
    abstract fun cachedPhoneDao(): CachedPhoneDao
    abstract fun comparisonHistoryDao(): ComparisonHistoryDao
    abstract fun savedMatchupDao(): SavedMatchupDao

    companion object {
        @Volatile
        private var instance: SpecClashDatabase? = null

        fun get(context: Context): SpecClashDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SpecClashDatabase::class.java,
                "specclash.db"
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
