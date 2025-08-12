package com.example.ranking.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.ranking.data.dao.*

@Database(
    entities = [Song::class, SongList::class, RankingResult::class, Match::class, LeagueSettings::class, Archive::class, VotingSession::class, VotingScore::class, SwissState::class, SwissMatchState::class, SwissFixture::class],
    version = 9,
    exportSchema = false
)
abstract class RankingDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun songListDao(): SongListDao
    abstract fun rankingResultDao(): RankingResultDao
    abstract fun matchDao(): MatchDao
    abstract fun leagueSettingsDao(): LeagueSettingsDao
    abstract fun archiveDao(): ArchiveDao
    abstract fun votingSessionDao(): VotingSessionDao
    abstract fun votingScoreDao(): VotingScoreDao
    abstract fun swissStateDao(): SwissStateDao
    abstract fun swissMatchStateDao(): SwissMatchStateDao

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
        
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create voting_sessions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS voting_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        listId INTEGER NOT NULL,
                        rankingMethod TEXT NOT NULL,
                        sessionName TEXT NOT NULL DEFAULT '',
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        isPaused INTEGER NOT NULL DEFAULT 0,
                        currentIndex INTEGER NOT NULL DEFAULT 0,
                        totalItems INTEGER NOT NULL DEFAULT 0,
                        progress REAL NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        lastModified INTEGER NOT NULL,
                        completedAt INTEGER,
                        currentSongId INTEGER,
                        currentMatchId INTEGER,
                        currentRound INTEGER NOT NULL DEFAULT 1,
                        completedMatches INTEGER NOT NULL DEFAULT 0,
                        totalMatches INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(listId) REFERENCES song_lists(id) ON DELETE CASCADE
                    )
                """)
                
                // Create voting_scores table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS voting_scores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        songId INTEGER NOT NULL,
                        score REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES voting_sessions(id) ON DELETE CASCADE,
                        FOREIGN KEY(songId) REFERENCES songs(id) ON DELETE CASCADE
                    )
                """)
                
                // Create indices for voting_scores
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voting_scores_sessionId ON voting_scores (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voting_scores_songId ON voting_scores (songId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_voting_scores_sessionId_songId ON voting_scores (sessionId, songId)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create swiss_states table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS swiss_states (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        currentRound INTEGER NOT NULL DEFAULT 1,
                        maxRounds INTEGER NOT NULL,
                        standings TEXT NOT NULL,
                        pairingHistory TEXT NOT NULL,
                        roundHistory TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES voting_sessions(id) ON DELETE CASCADE
                    )
                """)
                
                // Create index for swiss_states
                db.execSQL("CREATE INDEX IF NOT EXISTS index_swiss_states_sessionId ON swiss_states (sessionId)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create swiss_match_states table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS swiss_match_states (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        matchId INTEGER NOT NULL,
                        currentRound INTEGER NOT NULL,
                        song1Id INTEGER NOT NULL,
                        song2Id INTEGER NOT NULL,
                        song1Name TEXT NOT NULL,
                        song2Name TEXT NOT NULL,
                        isMatchInProgress INTEGER NOT NULL DEFAULT 1,
                        preliminaryWinnerId INTEGER,
                        preliminaryScore1 INTEGER,
                        preliminaryScore2 INTEGER,
                        matchStartTime INTEGER NOT NULL,
                        lastUpdateTime INTEGER NOT NULL,
                        FOREIGN KEY(matchId) REFERENCES matches(id) ON DELETE CASCADE,
                        FOREIGN KEY(sessionId) REFERENCES voting_sessions(id) ON DELETE CASCADE
                    )
                """)
                
                // Create swiss_fixtures table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS swiss_fixtures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        currentRound INTEGER NOT NULL,
                        totalRounds INTEGER NOT NULL,
                        fixtureData TEXT NOT NULL,
                        currentStandings TEXT NOT NULL,
                        nextMatchIndex INTEGER NOT NULL DEFAULT 0,
                        isRoundComplete INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES voting_sessions(id) ON DELETE CASCADE
                    )
                """)
                
                // Create indices for performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_swiss_match_states_sessionId ON swiss_match_states (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_swiss_match_states_matchId ON swiss_match_states (matchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_swiss_fixtures_sessionId ON swiss_fixtures (sessionId)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // This is a safe migration - just version bump
                // All tables already exist from v8, no schema changes needed
                // This forces database reset for existing users with problematic migrations
            }
        }

        fun getDatabase(context: Context): RankingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RankingDatabase::class.java,
                    "ranking_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}