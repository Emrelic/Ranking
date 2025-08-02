package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.SongList
import kotlinx.coroutines.flow.Flow

@Dao
interface SongListDao {
    @Query("SELECT * FROM song_lists ORDER BY createdAt DESC")
    fun getAllSongLists(): Flow<List<SongList>>

    @Query("SELECT * FROM song_lists WHERE id = :id")
    suspend fun getSongListById(id: Long): SongList?

    @Insert
    suspend fun insertSongList(songList: SongList): Long

    @Update
    suspend fun updateSongList(songList: SongList)

    @Delete
    suspend fun deleteSongList(songList: SongList)
}