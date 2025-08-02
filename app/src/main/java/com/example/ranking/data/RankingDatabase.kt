package com.example.ranking.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.ranking.data.dao.*

@Database(
    entities = [Song::class, SongList::class, RankingResult::class, Match::class],
    version = 2,
    exportSchema = false
)
abstract class RankingDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun songListDao(): SongListDao
    abstract fun rankingResultDao(): RankingResultDao
    abstract fun matchDao(): MatchDao

    companion object {
        @Volatile
        private var INSTANCE: RankingDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE songs ADD COLUMN trackNumber INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): RankingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RankingDatabase::class.java,
                    "ranking_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}