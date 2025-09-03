package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.CriterionScore
import kotlinx.coroutines.flow.Flow

@Dao
interface CriterionScoreDao {
    @Query("SELECT * FROM criterion_scores WHERE matchId = :matchId ORDER BY criterionName")
    suspend fun getCriterionScoresByMatch(matchId: Long): List<CriterionScore>
    
    @Query("SELECT * FROM criterion_scores WHERE tournamentId = :tournamentId ORDER BY matchId, criterionName")
    suspend fun getCriterionScoresByTournament(tournamentId: Long): List<CriterionScore>
    
    @Query("SELECT * FROM criterion_scores WHERE matchId = :matchId AND criterionName = :criterionName")
    suspend fun getCriterionScore(matchId: Long, criterionName: String): CriterionScore?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCriterionScore(criterionScore: CriterionScore): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCriterionScores(criterionScores: List<CriterionScore>)
    
    @Update
    suspend fun updateCriterionScore(criterionScore: CriterionScore)
    
    @Delete
    suspend fun deleteCriterionScore(criterionScore: CriterionScore)
    
    @Query("DELETE FROM criterion_scores WHERE matchId = :matchId")
    suspend fun deleteCriterionScoresByMatch(matchId: Long)
    
    @Query("DELETE FROM criterion_scores WHERE tournamentId = :tournamentId")
    suspend fun deleteCriterionScoresByTournament(tournamentId: Long)
    
    // Real-time updates for UI
    @Query("SELECT * FROM criterion_scores WHERE matchId = :matchId ORDER BY criterionName")
    fun getCriterionScoresByMatchFlow(matchId: Long): Flow<List<CriterionScore>>
}