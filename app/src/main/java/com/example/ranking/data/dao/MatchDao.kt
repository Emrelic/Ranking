package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.Match
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE listId = :listId AND rankingMethod = :method ORDER BY round ASC, matchNumber ASC, id ASC")
    fun getMatchesByListAndMethod(listId: Long, method: String): Flow<List<Match>>
    
    @Query("SELECT * FROM matches WHERE listId = :listId AND rankingMethod = :method ORDER BY round ASC, matchNumber ASC, id ASC")
    suspend fun getMatchesByListAndMethodSync(listId: Long, method: String): List<Match>

    @Query("SELECT * FROM matches WHERE listId = :listId AND rankingMethod = :method AND isCompleted = 0 ORDER BY round ASC, matchNumber DESC, id ASC LIMIT 1")
    suspend fun getNextUncompletedMatch(listId: Long, method: String): Match?

    @Query("SELECT * FROM matches WHERE listId = :listId AND rankingMethod = :method AND round = :round")
    suspend fun getMatchesByRound(listId: Long, method: String, round: Int): List<Match>
    
    // Tournament-based queries
    @Query("SELECT * FROM matches WHERE tournamentId = :tournamentId ORDER BY round ASC, matchNumber ASC, id ASC")
    suspend fun getMatchesByTournamentId(tournamentId: Long): List<Match>
    
    @Query("SELECT * FROM matches WHERE id = :matchId")
    suspend fun getMatchById(matchId: Long): Match?

    @Insert
    suspend fun insertMatch(match: Match): Long

    @Insert
    suspend fun insertMatches(matches: List<Match>)

    @Update
    suspend fun updateMatch(match: Match)

    @Query("DELETE FROM matches WHERE listId = :listId AND rankingMethod = :method")
    suspend fun deleteMatches(listId: Long, method: String)

    @Query("DELETE FROM matches WHERE listId = :listId")
    suspend fun deleteAllMatchesByListId(listId: Long)

    @Query("SELECT COUNT(*) FROM matches WHERE listId = :listId AND rankingMethod = :method AND isCompleted = 1")
    suspend fun getCompletedMatchCount(listId: Long, method: String): Int

    @Query("SELECT COUNT(*) FROM matches WHERE listId = :listId AND rankingMethod = :method")
    suspend fun getTotalMatchCount(listId: Long, method: String): Int
    
    @Query("SELECT * FROM matches WHERE listId = :listId AND isCompleted = 1 ORDER BY round ASC, id ASC")
    suspend fun getCompletedMatchesForList(listId: Long): List<Match>
}