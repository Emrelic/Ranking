package com.example.ranking.data.dao

import androidx.room.*
import com.example.ranking.data.PlaylistTrack
import com.example.ranking.data.YouTubeTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistTrackDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrack): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(playlistTracks: List<PlaylistTrack>): List<Long>
    
    @Update
    suspend fun updatePlaylistTrack(playlistTrack: PlaylistTrack)
    
    @Delete
    suspend fun deletePlaylistTrack(playlistTrack: PlaylistTrack)
    
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getPlaylistTracks(playlistId: Long): List<PlaylistTrack>
    
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun getPlaylistTracksFlow(playlistId: Long): Flow<List<PlaylistTrack>>
    
    @Query("""
        SELECT yt.* FROM youtube_tracks yt 
        INNER JOIN playlist_tracks pt ON yt.id = pt.trackId 
        WHERE pt.playlistId = :playlistId 
        ORDER BY pt.position
    """)
    suspend fun getTracksInPlaylist(playlistId: Long): List<YouTubeTrack>
    
    @Query("""
        SELECT yt.* FROM youtube_tracks yt 
        INNER JOIN playlist_tracks pt ON yt.id = pt.trackId 
        WHERE pt.playlistId = :playlistId 
        ORDER BY pt.position
    """)
    fun getTracksInPlaylistFlow(playlistId: Long): Flow<List<YouTubeTrack>>
    
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteAllTracksFromPlaylist(playlistId: Long)
    
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)
    
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getTrackCountInPlaylist(playlistId: Long): Int
    
    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPositionInPlaylist(playlistId: Long): Int?
    
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: Long): Int
    
    @Query("UPDATE playlist_tracks SET position = :newPosition WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updateTrackPosition(playlistId: Long, trackId: Long, newPosition: Int)
}