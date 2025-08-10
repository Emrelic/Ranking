package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.RankingResult
import kotlinx.coroutines.flow.Flow

@Dao
interface RankingResultDao {
    @Query("SELECT * FROM ranking_results WHERE listId = :listId AND rankingMethod = :method ORDER BY position ASC")
    fun getRankingResults(listId: Long, method: String): Flow<List<RankingResult>>
    
    @Query("SELECT * FROM ranking_results WHERE listId = :listId AND rankingMethod = :method ORDER BY position ASC")
    suspend fun getRankingResultsSync(listId: Long, method: String): List<RankingResult>

    @Query("SELECT * FROM ranking_results WHERE listId = :listId AND rankingMethod = :method ORDER BY score DESC")
    suspend fun getRankingResultsByScore(listId: Long, method: String): List<RankingResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRankingResult(result: RankingResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRankingResults(results: List<RankingResult>)

    @Query("DELETE FROM ranking_results WHERE listId = :listId AND rankingMethod = :method")
    suspend fun deleteRankingResults(listId: Long, method: String)

    @Query("DELETE FROM ranking_results WHERE listId = :listId")
    suspend fun deleteAllRankingResults(listId: Long)
}