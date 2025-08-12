package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE listId = :listId ORDER BY name ASC")
    fun getSongsByListId(listId: Long): Flow<List<Song>>
    
    @Query("SELECT * FROM songs WHERE listId = :listId ORDER BY name ASC")
    suspend fun getSongsByListIdSync(listId: Long): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Insert
    suspend fun insertSong(song: Song): Long

    @Insert
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("DELETE FROM songs WHERE listId = :listId")
    suspend fun deleteSongsByListId(listId: Long)

    @Query("SELECT COUNT(*) FROM songs WHERE listId = :listId")
    suspend fun getSongCountByListId(listId: Long): Int
}