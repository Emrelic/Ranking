package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.VotingScore
import kotlinx.coroutines.flow.Flow

@Dao
interface VotingScoreDao {
    @Query("SELECT * FROM voting_scores WHERE sessionId = :sessionId ORDER BY timestamp")
    fun getScoresForSession(sessionId: Long): Flow<List<VotingScore>>
    
    @Query("SELECT * FROM voting_scores WHERE sessionId = :sessionId")
    suspend fun getScoresForSessionSync(sessionId: Long): List<VotingScore>
    
    @Query("SELECT * FROM voting_scores WHERE sessionId = :sessionId AND songId = :songId")
    suspend fun getScore(sessionId: Long, songId: Long): VotingScore?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateScore(score: VotingScore)
    
    @Update
    suspend fun updateScore(score: VotingScore)
    
    @Delete
    suspend fun deleteScore(score: VotingScore)
    
    @Query("DELETE FROM voting_scores WHERE sessionId = :sessionId")
    suspend fun deleteScoresForSession(sessionId: Long)
}