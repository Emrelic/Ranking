package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.YouTubePlaylist
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubePlaylistDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: YouTubePlaylist): Long
    
    @Update
    suspend fun updatePlaylist(playlist: YouTubePlaylist)
    
    @Delete
    suspend fun deletePlaylist(playlist: YouTubePlaylist)
    
    @Query("SELECT * FROM youtube_playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): YouTubePlaylist?
    
    @Query("SELECT * FROM youtube_playlists WHERE artist = :artist ORDER BY createdAt DESC")
    suspend fun getPlaylistsByArtist(artist: String): List<YouTubePlaylist>
    
    @Query("SELECT * FROM youtube_playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylists(): List<YouTubePlaylist>
    
    @Query("SELECT * FROM youtube_playlists ORDER BY createdAt DESC")
    fun getAllPlaylistsFlow(): Flow<List<YouTubePlaylist>>
    
    @Query("SELECT * FROM youtube_playlists WHERE name LIKE '%' || :searchTerm || '%' OR artist LIKE '%' || :searchTerm || '%' ORDER BY createdAt DESC")
    suspend fun searchPlaylists(searchTerm: String): List<YouTubePlaylist>
    
    @Query("DELETE FROM youtube_playlists WHERE artist = :artist")
    suspend fun deletePlaylistsByArtist(artist: String)
    
    @Query("UPDATE youtube_playlists SET trackCount = :trackCount, totalViews = :totalViews, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistStats(playlistId: Long, trackCount: Int, totalViews: Long, updatedAt: Long)
}