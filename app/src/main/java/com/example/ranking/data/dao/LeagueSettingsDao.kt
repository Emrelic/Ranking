package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.LeagueSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface LeagueSettingsDao {
    @Insert
    suspend fun insert(leagueSettings: LeagueSettings): Long
    
    @Update
    suspend fun update(leagueSettings: LeagueSettings)
    
    @Delete
    suspend fun delete(leagueSettings: LeagueSettings)
    
    @Query("SELECT * FROM league_settings WHERE listId = :listId AND rankingMethod = :method LIMIT 1")
    suspend fun getByListAndMethod(listId: Long, method: String): LeagueSettings?
    
    @Query("SELECT * FROM league_settings WHERE listId = :listId AND rankingMethod = :method LIMIT 1")
    fun getByListAndMethodFlow(listId: Long, method: String): Flow<LeagueSettings?>
}