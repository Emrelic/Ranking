package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.YouTubeTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeTrackDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: YouTubeTrack): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<YouTubeTrack>): List<Long>
    
    @Update
    suspend fun updateTrack(track: YouTubeTrack)
    
    @Delete
    suspend fun deleteTrack(track: YouTubeTrack)
    
    @Query("SELECT * FROM youtube_tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): YouTubeTrack?
    
    @Query("SELECT * FROM youtube_tracks WHERE videoId = :videoId")
    suspend fun getTrackByVideoId(videoId: String): YouTubeTrack?
    
    @Query("SELECT * FROM youtube_tracks WHERE artist = :artist ORDER BY viewCount DESC")
    suspend fun getTracksByArtist(artist: String): List<YouTubeTrack>
    
    @Query("SELECT * FROM youtube_tracks WHERE artist = :artist ORDER BY viewCount DESC")
    fun getTracksByArtistFlow(artist: String): Flow<List<YouTubeTrack>>
    
    @Query("SELECT * FROM youtube_tracks ORDER BY viewCount DESC LIMIT :limit")
    suspend fun getTopTracks(limit: Int): List<YouTubeTrack>
    
    @Query("SELECT * FROM youtube_tracks ORDER BY createdAt DESC")
    suspend fun getAllTracks(): List<YouTubeTrack>
    
    @Query("SELECT * FROM youtube_tracks ORDER BY createdAt DESC")
    fun getAllTracksFlow(): Flow<List<YouTubeTrack>>
    
    @Query("DELETE FROM youtube_tracks WHERE artist = :artist")
    suspend fun deleteTracksByArtist(artist: String)
    
    @Query("SELECT DISTINCT artist FROM youtube_tracks ORDER BY artist")
    suspend fun getAllArtists(): List<String>
    
    @Query("SELECT COUNT(*) FROM youtube_tracks WHERE artist = :artist")
    suspend fun getTrackCountByArtist(artist: String): Int
    
    @Query("SELECT SUM(viewCount) FROM youtube_tracks WHERE artist = :artist")
    suspend fun getTotalViewsByArtist(artist: String): Long?
}