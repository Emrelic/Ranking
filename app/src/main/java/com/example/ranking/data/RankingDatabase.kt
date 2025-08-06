package com.example.ranking.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.ranking.data.dao.*

@Database(
    entities = [Song::class, SongList::class, RankingResult::class, Match::class, LeagueSettings::class, Archive::class],
    version = 5,
    exportSchema = false
)
abstract class RankingDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun songListDao(): SongListDao
    abstract fun rankingResultDao(): RankingResultDao
    abstract fun matchDao(): MatchDao
    abstract fun leagueSettingsDao(): LeagueSettingsDao
    abstract fun archiveDao(): ArchiveDao

    companion object {
        @Volatile
        private var INSTANCE: RankingDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE songs ADD COLUMN trackNumber INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add score columns to matches table
                db.execSQL("ALTER TABLE matches ADD COLUMN score1 INTEGER")
                db.execSQL("ALTER TABLE matches ADD COLUMN score2 INTEGER")
                
                // Create league settings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS league_settings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        listId INTEGER NOT NULL,
                        rankingMethod TEXT NOT NULL,
                        useScores INTEGER NOT NULL DEFAULT 0,
                        winPoints INTEGER NOT NULL DEFAULT 3,
                        drawPoints INTEGER NOT NULL DEFAULT 1,
                        losePoints INTEGER NOT NULL DEFAULT 0,
                        allowDraws INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create archives table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS archives (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        listId INTEGER NOT NULL,
                        listName TEXT NOT NULL,
                        method TEXT NOT NULL,
                        totalSongs INTEGER NOT NULL,
                        totalMatches INTEGER NOT NULL,
                        completedMatches INTEGER NOT NULL,
                        finalResults TEXT NOT NULL,
                        leagueTable TEXT,
                        matchResults TEXT NOT NULL,
                        leagueSettings TEXT,
                        archivedAt INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL
                    )
                """)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add doubleRoundRobin column to league_settings table
                db.execSQL("ALTER TABLE league_settings ADD COLUMN doubleRoundRobin INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): RankingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RankingDatabase::class.java,
                    "ranking_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}