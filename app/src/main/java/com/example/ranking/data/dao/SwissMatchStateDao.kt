package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.SwissMatchState
import com.example.ranking.data.SwissFixture
import kotlinx.coroutines.flow.Flow

@Dao
interface SwissMatchStateDao {
    
    // Swiss Match State operations
    @Query("SELECT * FROM swiss_match_states WHERE sessionId = :sessionId AND isMatchInProgress = 1 LIMIT 1")
    suspend fun getCurrentMatchState(sessionId: Long): SwissMatchState?
    
    @Query("SELECT * FROM swiss_match_states WHERE sessionId = :sessionId ORDER BY lastUpdateTime DESC")
    fun getAllMatchStates(sessionId: Long): Flow<List<SwissMatchState>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMatchState(matchState: SwissMatchState): Long
    
    @Update
    suspend fun updateMatchState(matchState: SwissMatchState)
    
    @Query("UPDATE swiss_match_states SET isMatchInProgress = 0 WHERE sessionId = :sessionId")
    suspend fun markAllMatchesComplete(sessionId: Long)
    
    @Delete
    suspend fun deleteMatchState(matchState: SwissMatchState)
    
    @Query("DELETE FROM swiss_match_states WHERE sessionId = :sessionId")
    suspend fun deleteAllMatchStates(sessionId: Long)
    
    // Swiss Fixture operations
    @Query("SELECT * FROM swiss_fixtures WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getFixture(sessionId: Long): SwissFixture?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateFixture(fixture: SwissFixture): Long
    
    @Update
    suspend fun updateFixture(fixture: SwissFixture)
    
    @Query("DELETE FROM swiss_fixtures WHERE sessionId = :sessionId")
    suspend fun deleteFixture(sessionId: Long)
}