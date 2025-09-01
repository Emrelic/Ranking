package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.VotingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface VotingSessionDao {
    @Query("SELECT * FROM voting_sessions WHERE listId = :listId AND rankingMethod = :method ORDER BY lastModified DESC")
    fun getSessionsForList(listId: Long, method: String): Flow<List<VotingSession>>
    
    @Query("SELECT * FROM voting_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): VotingSession?
    
    @Query("SELECT * FROM voting_sessions WHERE listId = :listId AND rankingMethod = :method AND isCompleted = 0 ORDER BY lastModified DESC LIMIT 1")
    suspend fun getActiveSession(listId: Long, method: String): VotingSession?
    
    @Insert
    suspend fun createSession(session: VotingSession): Long
    
    @Update
    suspend fun updateSession(session: VotingSession)
    
    @Delete
    suspend fun deleteSession(session: VotingSession)
    
    @Query("DELETE FROM voting_sessions WHERE listId = :listId AND rankingMethod = :method")
    suspend fun deleteSessionsForList(listId: Long, method: String)
    
    // Tournament management queries
    @Query("SELECT * FROM voting_sessions WHERE isCompleted = 0 ORDER BY startedAt DESC")
    suspend fun getAllActiveTournaments(): List<VotingSession>
    
    @Query("SELECT * FROM voting_sessions WHERE isCompleted = 1 ORDER BY finishedAt DESC")
    suspend fun getAllCompletedTournaments(): List<VotingSession>
    
    @Query("UPDATE voting_sessions SET tournamentName = :name, listName = :listName WHERE id = :sessionId")
    suspend fun updateTournamentInfo(sessionId: Long, name: String, listName: String)
    
    @Query("UPDATE voting_sessions SET finishedAt = :finishedAt, isCompleted = 1 WHERE id = :sessionId")
    suspend fun completeTournament(sessionId: Long, finishedAt: Long)
}