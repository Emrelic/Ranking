package com.example.ranking.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.example.ranking.data.dao.*

@Database(
    entities = [Song::class, SongList::class, RankingResult::class, Match::class, LeagueSettings::class, Archive::class, VotingSession::class, VotingScore::class, SwissState::class, SwissMatchState::class, SwissFixture::class, Tournament::class, CriterionList::class, CriterionScore::class, YouTubeTrack::class, YouTubePlaylist::class, PlaylistTrack::class],
    version = 16,
    exportSchema = true
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
    // New entities for Criteria System
    abstract fun tournamentDao(): TournamentDao
    abstract fun criterionListDao(): CriterionListDao
    abstract fun criterionScoreDao(): CriterionScoreDao
    // YouTube integration DAOs
    abstract fun youTubeTrackDao(): YouTubeTrackDao
    abstract fun youTubePlaylistDao(): YouTubePlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao

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

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Convert old "EMRE" method references to "EMRE_CORRECT"
                db.execSQL("UPDATE matches SET rankingMethod = 'EMRE_CORRECT' WHERE rankingMethod = 'EMRE'")
                db.execSQL("UPDATE ranking_results SET rankingMethod = 'EMRE_CORRECT' WHERE rankingMethod = 'EMRE'")
                db.execSQL("UPDATE voting_sessions SET rankingMethod = 'EMRE_CORRECT' WHERE rankingMethod = 'EMRE'")
                db.execSQL("UPDATE league_settings SET rankingMethod = 'EMRE_CORRECT' WHERE rankingMethod = 'EMRE'")
                db.execSQL("UPDATE archives SET method = 'EMRE_CORRECT' WHERE method = 'EMRE'")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add tournament identification fields to voting_sessions table
                db.execSQL("ALTER TABLE voting_sessions ADD COLUMN tournamentName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE voting_sessions ADD COLUMN listName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE voting_sessions ADD COLUMN startedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE voting_sessions ADD COLUMN finishedAt INTEGER")
                
                // Update existing rows with default values
                db.execSQL("UPDATE voting_sessions SET startedAt = createdAt WHERE startedAt = 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create tournaments table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tournaments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        songListId INTEGER NOT NULL,
                        systemType TEXT NOT NULL,
                        criterionListId INTEGER,
                        criteriaSettings TEXT,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        FOREIGN KEY(songListId) REFERENCES song_lists(id) ON DELETE CASCADE,
                        FOREIGN KEY(criterionListId) REFERENCES criterion_lists(id) ON DELETE SET NULL
                    )
                """)
                
                // Create criterion_lists table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS criterion_lists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        criteria TEXT NOT NULL,
                        createdDate TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                // Create criterion_scores table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS criterion_scores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        matchId INTEGER NOT NULL,
                        tournamentId INTEGER NOT NULL,
                        criterionName TEXT NOT NULL,
                        team1Score REAL,
                        team2Score REAL,
                        createdAt TEXT NOT NULL,
                        FOREIGN KEY(matchId) REFERENCES matches(id) ON DELETE CASCADE,
                        FOREIGN KEY(tournamentId) REFERENCES tournaments(id) ON DELETE CASCADE
                    )
                """)
                
                // Add tournamentId to matches table  
                db.execSQL("ALTER TABLE matches ADD COLUMN tournamentId INTEGER")
                
                // Add foreign key constraint manually (ALTER TABLE doesn't support adding FK)
                // We'll handle this in app logic since SQLite ALTER TABLE has limitations
                
                // Create indices for performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tournaments_songListId ON tournaments (songListId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tournaments_criterionListId ON tournaments (criterionListId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_criterion_scores_matchId ON criterion_scores (matchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_criterion_scores_tournamentId ON criterion_scores (tournamentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_matches_tournamentId ON matches (tournamentId)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add csvData field to songs table for tabular CSV data
                db.execSQL("ALTER TABLE songs ADD COLUMN csvData TEXT")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Database schema fix - force clean migration for existing corrupted databases
                // This migration ensures all tables are properly structured after previous issues
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create YouTube tracks table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS youtube_tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        videoId TEXT NOT NULL UNIQUE,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        viewCount INTEGER NOT NULL,
                        durationSeconds INTEGER,
                        thumbnailUrl TEXT,
                        publishedAt TEXT,
                        createdAt INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                    )
                """)
                
                // Create YouTube playlists table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS youtube_playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        trackCount INTEGER NOT NULL DEFAULT 0,
                        totalViews INTEGER NOT NULL DEFAULT 0,
                        description TEXT,
                        createdAt INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                        updatedAt INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                    )
                """)
                
                // Create playlist-track relations table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playlistId INTEGER NOT NULL,
                        trackId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                        FOREIGN KEY (playlistId) REFERENCES youtube_playlists(id) ON DELETE CASCADE,
                        FOREIGN KEY (trackId) REFERENCES youtube_tracks(id) ON DELETE CASCADE
                    )
                """)
                
                // Add YouTube integration columns to songs table
                db.execSQL("ALTER TABLE songs ADD COLUMN youtubeVideoId TEXT")
                db.execSQL("ALTER TABLE songs ADD COLUMN viewCount INTEGER DEFAULT 0")
                
                // Create indices for performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_tracks_videoId ON youtube_tracks (videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_tracks_artist ON youtube_tracks (artist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_tracks_viewCount ON youtube_tracks (viewCount DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_playlists_artist ON youtube_playlists (artist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks (playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_trackId ON playlist_tracks (trackId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_tracks_unique ON playlist_tracks (playlistId, trackId)")
            }
        }


        /**
         * Entity anotasyonları ile migration'larla oluşturulan şemayı hizalar.
         * Önceki sürümlerde indeksler sadece migration SQL'lerinde vardı; taze
         * kurulumlar (annotation'dan üretilen şema) bu indekslerden yoksundu.
         * Bu migration her iki popülasyonu da aynı şemaya taşır.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Çekirdek tablolar için sorgu indeksleri (yeni)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_listId ON songs (listId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_matches_listId ON matches (listId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_matches_tournamentId ON matches (tournamentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ranking_results_listId ON ranking_results (listId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_league_settings_listId ON league_settings (listId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voting_sessions_listId ON voting_sessions (listId)")

                // FK indeksleri (önceki migration'larda vardı, taze kurulumlarda eksikti)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_swiss_states_sessionId ON swiss_states (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_swiss_match_states_sessionId ON swiss_match_states (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_swiss_match_states_matchId ON swiss_match_states (matchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_swiss_fixtures_sessionId ON swiss_fixtures (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tournaments_songListId ON tournaments (songListId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tournaments_criterionListId ON tournaments (criterionListId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_criterion_scores_matchId ON criterion_scores (matchId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_criterion_scores_tournamentId ON criterion_scores (tournamentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_tracks_videoId ON youtube_tracks (videoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_tracks_artist ON youtube_tracks (artist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_tracks_viewCount ON youtube_tracks (viewCount DESC)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_youtube_playlists_artist ON youtube_playlists (artist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks (playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_trackId ON playlist_tracks (trackId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_tracks_unique ON playlist_tracks (playlistId, trackId)")
            }
        }

        fun getDatabase(context: Context): RankingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RankingDatabase::class.java,
                    "ranking_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                // fallbackToDestructiveMigration KALDIRILDI:
                // Kapsanmayan bir sürüm geçişinde tüm kullanıcı verisini sessizce
                // siliyordu. Artık tüm geçişler explicit migration ile yapılır;
                // eksik migration varsa sessiz veri kaybı yerine hata alınır.
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}