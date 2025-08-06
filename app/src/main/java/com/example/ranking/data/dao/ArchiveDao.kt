package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.Archive
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {
    @Insert
    suspend fun insert(archive: Archive): Long
    
    @Update
    suspend fun update(archive: Archive)
    
    @Delete
    suspend fun delete(archive: Archive)
    
    @Query("SELECT * FROM archives ORDER BY archivedAt DESC")
    fun getAllArchives(): Flow<List<Archive>>
    
    @Query("SELECT * FROM archives WHERE id = :id")
    suspend fun getArchiveById(id: Long): Archive?
    
    @Query("SELECT * FROM archives WHERE method = :method ORDER BY archivedAt DESC")
    fun getArchivesByMethod(method: String): Flow<List<Archive>>
    
    @Query("DELETE FROM archives WHERE id = :id")
    suspend fun deleteById(id: Long)
}