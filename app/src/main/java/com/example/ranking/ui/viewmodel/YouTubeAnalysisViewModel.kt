package com.example.ranking.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.*
import com.example.ranking.data.dao.*
import com.example.ranking.youtube.YouTubeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeAnalysisViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "YouTubeAnalysisVM"
    }
    
    private val database = RankingDatabase.getDatabase(application)
    private val youTubeService = YouTubeService(application)
    
    private val youTubeTrackDao = database.youTubeTrackDao()
    private val youTubePlaylistDao = database.youTubePlaylistDao()
    private val playlistTrackDao = database.playlistTrackDao()
    private val songDao = database.songDao()
    private val songListDao = database.songListDao()
    
    private val _uiState = MutableStateFlow(YouTubeAnalysisUiState())
    val uiState: StateFlow<YouTubeAnalysisUiState> = _uiState.asStateFlow()
    
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
    
    fun updateMaxResults(maxResults: Int) {
        _uiState.value = _uiState.value.copy(maxResults = maxResults)
    }
    
    fun searchArtist() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    hasSearched = true
                )
                
                Log.d(TAG, "Starting search for artist: $query")
                
                // Search for artist tracks
                val result = youTubeService.searchArtistTopTracks(
                    artistName = query,
                    maxResults = _uiState.value.maxResults
                )
                
                result.fold(
                    onSuccess = { tracks ->
                        Log.d(TAG, "Found ${tracks.size} tracks")
                        
                        // Get detailed statistics for the tracks
                        val videoIds = tracks.map { it.videoId }
                        val statsResult = youTubeService.getVideoStatistics(videoIds)
                        
                        statsResult.fold(
                            onSuccess = { statsMap ->
                                // Update tracks with statistics
                                val updatedTracks = tracks.map { track ->
                                    val stats = statsMap[track.videoId]
                                    track.copy(
                                        viewCount = stats?.viewCount ?: 0,
                                        durationSeconds = stats?.durationSeconds,
                                        title = stats?.title?.takeIf { it.isNotBlank() } ?: track.title
                                    )
                                }.sortedByDescending { it.viewCount } // Sort by view count
                                
                                // Save to database
                                saveTracksToDatabase(updatedTracks)
                                
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    tracks = updatedTracks,
                                    error = null
                                )
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Failed to get video statistics", error)
                                // Use tracks without detailed stats
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    tracks = tracks.sortedByDescending { it.viewCount },
                                    error = null
                                )
                            }
                        )
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Search failed", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Arama başarısız: ${error.message}",
                            tracks = emptyList()
                        )
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during search", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Beklenmeyen hata: ${e.message}",
                    tracks = emptyList()
                )
            }
        }
    }
    
    fun createPlaylist() {
        val tracks = _uiState.value.tracks
        if (tracks.isEmpty()) return
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isCreatingPlaylist = true)
                
                val artistName = _uiState.value.searchQuery.trim()
                val totalViews = tracks.sumOf { it.viewCount }
                
                // Create playlist
                val playlist = YouTubePlaylist(
                    name = "$artistName - En Popüler Şarkılar",
                    artist = artistName,
                    trackCount = tracks.size,
                    totalViews = totalViews,
                    description = "YouTube view count bazında ${tracks.size} en popüler şarkı"
                )
                
                val playlistId = youTubePlaylistDao.insertPlaylist(playlist)
                
                // Add tracks to playlist
                val playlistTracks = tracks.mapIndexed { index, track ->
                    PlaylistTrack(
                        playlistId = playlistId,
                        trackId = track.id,
                        position = index + 1
                    )
                }
                
                playlistTrackDao.insertPlaylistTracks(playlistTracks)
                
                Log.d(TAG, "Created playlist with ${tracks.size} tracks")
                
                _uiState.value = _uiState.value.copy(isCreatingPlaylist = false)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist", e)
                _uiState.value = _uiState.value.copy(
                    isCreatingPlaylist = false,
                    error = "Oynatma listesi oluşturulamadı: ${e.message}"
                )
            }
        }
    }
    
    fun createRankingList(tracks: List<YouTubeTrack>): Long {
        if (tracks.isEmpty()) return -1
        
        var listId = -1L
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isCreatingRanking = true)
                
                val artistName = _uiState.value.searchQuery.trim()
                
                // Create song list
                val songList = SongList(
                    name = "$artistName - YouTube Analizi",
                    createdDate = System.currentTimeMillis()
                )
                
                listId = withContext(Dispatchers.IO) {
                    songListDao.insertSongList(songList)
                }
                
                // Convert YouTube tracks to Songs
                val songs = tracks.map { track ->
                    Song(
                        name = track.title,
                        artist = track.artist,
                        album = "YouTube",
                        listId = listId,
                        youtubeVideoId = track.videoId,
                        viewCount = track.viewCount
                    )
                }
                
                withContext(Dispatchers.IO) {
                    songDao.insertSongs(songs)
                }
                
                Log.d(TAG, "Created ranking list with ${songs.size} songs")
                
                _uiState.value = _uiState.value.copy(isCreatingRanking = false)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create ranking list", e)
                _uiState.value = _uiState.value.copy(
                    isCreatingRanking = false,
                    error = "Ranking listesi oluşturulamadı: ${e.message}"
                )
                listId = -1
            }
        }
        
        return listId
    }
    
    private suspend fun saveTracksToDatabase(tracks: List<YouTubeTrack>) {
        try {
            withContext(Dispatchers.IO) {
                youTubeTrackDao.insertTracks(tracks)
            }
            Log.d(TAG, "Saved ${tracks.size} tracks to database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tracks to database", e)
        }
    }
}

data class YouTubeAnalysisUiState(
    val searchQuery: String = "",
    val maxResults: Int = 25,
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val tracks: List<YouTubeTrack> = emptyList(),
    val error: String? = null,
    val isCreatingPlaylist: Boolean = false,
    val isCreatingRanking: Boolean = false
)