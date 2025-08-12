package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.SwissState
import kotlinx.coroutines.flow.Flow

@Dao
interface SwissStateDao {
    @Query("SELECT * FROM swiss_states WHERE sessionId = :sessionId")
    suspend fun getSwissStateBySession(sessionId: Long): SwissState?
    
    @Query("SELECT * FROM swiss_states WHERE sessionId = :sessionId")
    fun getSwissStateBySessionFlow(sessionId: Long): Flow<SwissState?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSwissState(swissState: SwissState): Long
    
    @Update
    suspend fun updateSwissState(swissState: SwissState)
    
    @Delete
    suspend fun deleteSwissState(swissState: SwissState)
    
    @Query("DELETE FROM swiss_states WHERE sessionId = :sessionId")
    suspend fun deleteSwissStateBySession(sessionId: Long)
}