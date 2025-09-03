package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.CriterionList
import kotlinx.coroutines.flow.Flow

@Dao
interface CriterionListDao {
    @Query("SELECT * FROM criterion_lists WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllActiveCriterionLists(): Flow<List<CriterionList>>
    
    @Query("SELECT * FROM criterion_lists ORDER BY createdAt DESC")
    fun getAllCriterionLists(): Flow<List<CriterionList>>
    
    @Query("SELECT * FROM criterion_lists WHERE id = :id")
    suspend fun getCriterionListById(id: Long): CriterionList?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCriterionList(criterionList: CriterionList): Long
    
    @Update
    suspend fun updateCriterionList(criterionList: CriterionList)
    
    @Delete
    suspend fun deleteCriterionList(criterionList: CriterionList)
    
    // Validation: Check if criterion list is used by active tournaments
    @Query("SELECT COUNT(*) FROM tournaments WHERE criterionListId = :criterionListId AND isCompleted = 0")
    suspend fun countActiveTournamentsUsingCriterionList(criterionListId: Long): Int
    
    // Safe deletion - only if not used by active tournaments
    @Query("UPDATE criterion_lists SET isActive = 0 WHERE id = :id")
    suspend fun deactivateCriterionList(id: Long)
}