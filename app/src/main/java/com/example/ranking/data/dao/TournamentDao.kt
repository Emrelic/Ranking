package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.Tournament
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    fun getAllTournaments(): Flow<List<Tournament>>
    
    @Query("SELECT * FROM tournaments WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun getActiveTournaments(): Flow<List<Tournament>>
    
    @Query("SELECT * FROM tournaments WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedTournaments(): Flow<List<Tournament>>
    
    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getTournamentById(id: Long): Tournament?

    @Query("SELECT * FROM tournaments WHERE songListId = :listId AND systemType = :systemType AND isCompleted = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveTournamentForList(listId: Long, systemType: String): Tournament?
    
    @Query("SELECT * FROM tournaments WHERE criterionListId = :criterionListId")
    suspend fun getTournamentsByCriterionList(criterionListId: Long): List<Tournament>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(tournament: Tournament): Long
    
    @Update
    suspend fun updateTournament(tournament: Tournament)
    
    @Delete
    suspend fun deleteTournament(tournament: Tournament)
    
    @Query("UPDATE tournaments SET isCompleted = 1, completedAt = :completedAt WHERE id = :tournamentId")
    suspend fun completeTournament(tournamentId: Long, completedAt: Long = System.currentTimeMillis())
}