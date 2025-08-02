package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.*
import com.example.ranking.data.RankingDatabase
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RankingViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(
        songDao = database.songDao(),
        songListDao = database.songListDao(),
        rankingResultDao = database.rankingResultDao(),
        matchDao = database.matchDao(),
        csvReader = CsvReader()
    )
    
    data class RankingUiState(
        val isLoading: Boolean = true,
        val isComplete: Boolean = false,
        val progress: Float = 0f,
        val currentIndex: Int = 0,
        val totalCount: Int = 0,
        val currentSong: Song? = null,
        val currentMatch: Match? = null,
        val song1: Song? = null,
        val song2: Song? = null,
        val completedMatches: Int = 0,
        val totalMatches: Int = 0,
        val currentRound: Int = 1,
        val error: String? = null
    )
    
    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState: StateFlow<RankingUiState> = _uiState.asStateFlow()
    
    private var songs: List<Song> = emptyList()
    private var currentMethod: String = ""
    private var currentListId: Long = 0L
    private var directScores: MutableMap<Long, Double> = mutableMapOf()
    private var currentSongIndex: Int = 0
    
    fun initializeRanking(listId: Long, method: String) {
        currentListId = listId
        currentMethod = method
        
        viewModelScope.launch {
            try {
                repository.getSongsByListId(listId).collect { songList ->
                    songs = songList
                    if (songs.isNotEmpty()) {
                        when (method) {
                            "DIRECT_SCORING" -> initializeDirectScoring()
                            "LEAGUE" -> initializeLeague()
                            "ELIMINATION" -> initializeElimination()
                            "SWISS" -> initializeSwiss()
                            "EMRE" -> initializeEmre()
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Hata: ${e.message}"
                )
            }
        }
    }
    
    private fun initializeDirectScoring() {
        directScores.clear()
        currentSongIndex = 0
        updateDirectScoringUI()
    }
    
    private fun updateDirectScoringUI() {
        if (currentSongIndex >= songs.size) {
            // Complete - calculate results
            completeDirectScoring()
            return
        }
        
        val currentSong = songs[currentSongIndex]
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentSong = currentSong,
            currentIndex = currentSongIndex,
            totalCount = songs.size,
            progress = currentSongIndex.toFloat() / songs.size
        )
    }
    
    private fun completeDirectScoring() {
        viewModelScope.launch {
            val results = RankingEngine.createDirectScoringResults(songs, directScores)
            repository.saveRankingResults(results)
            
            _uiState.value = _uiState.value.copy(
                isComplete = true,
                progress = 1f
            )
        }
    }
    
    private fun initializeLeague() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createLeagueMatches(songs)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeSwiss() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createSwissMatches(songs, 1, emptyList())
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeEmre() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createEmreMatches(songs, 1)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private fun initializeElimination() {
        viewModelScope.launch {
            repository.clearMatches(currentListId, currentMethod)
            val matches = RankingEngine.createEliminationMatches(songs)
            repository.createMatches(matches)
            loadNextMatch()
        }
    }
    
    private suspend fun loadNextMatch() {
        val nextMatch = repository.getNextUncompletedMatch(currentListId, currentMethod)
        val (completed, total) = repository.getMatchProgress(currentListId, currentMethod)
        
        if (nextMatch == null) {
            // Check if we need more rounds (for Swiss or Emre)
            when (currentMethod) {
                "SWISS" -> {
                    val currentRound = getCurrentSwissRound(completed)
                    val maxRounds = RankingEngine.getSwissRoundCount(songs.size)
                    if (currentRound <= maxRounds) {
                        createNextSwissRound(currentRound)
                        return
                    }
                }
                "EMRE" -> {
                    val currentRound = getCurrentEmreRound(completed)
                    val maxRounds = RankingEngine.getEmreRoundCount(songs.size)
                    if (currentRound <= maxRounds) {
                        createNextEmreRound(currentRound)
                        return
                    }
                }
            }
            
            // Complete ranking
            completeRanking()
            return
        }
        
        val song1 = songs.find { it.id == nextMatch.songId1 }
        val song2 = songs.find { it.id == nextMatch.songId2 }
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            currentMatch = nextMatch,
            song1 = song1,
            song2 = song2,
            completedMatches = completed,
            totalMatches = total,
            progress = if (total > 0) completed.toFloat() / total else 0f
        )
    }
    
    private suspend fun createNextSwissRound(round: Int) {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            val completedMatches = allMatches.filter { it.isCompleted }
            val newMatches = RankingEngine.createSwissMatches(songs, round, completedMatches)
            
            if (newMatches.isNotEmpty()) {
                repository.createMatches(newMatches)
            }
            
            loadNextMatch()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Swiss round oluşturma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun createNextEmreRound(round: Int) {
        try {
            // Recalculate song order based on previous round results
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            val completedMatches = allMatches.filter { it.isCompleted }
            val results = RankingEngine.calculateEmreResults(songs, completedMatches)
            val reorderedSongs = results.sortedBy { it.position }.mapNotNull { result ->
                songs.find { it.id == result.songId }
            }
            
            val newMatches = RankingEngine.createEmreMatches(reorderedSongs, round)
            if (newMatches.isNotEmpty()) {
                repository.createMatches(newMatches)
            }
            
            loadNextMatch()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Emre round oluşturma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun completeRanking() {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            val results = when (currentMethod) {
                "LEAGUE" -> RankingEngine.calculateLeagueResults(songs, allMatches)
                "SWISS" -> RankingEngine.calculateSwissResults(songs, allMatches)
                "EMRE" -> RankingEngine.calculateEmreResults(songs, allMatches)
                else -> emptyList()
            }
            
            repository.saveRankingResults(results)
            
            _uiState.value = _uiState.value.copy(
                isComplete = true,
                progress = 1f
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Sıralama tamamlama hatası: ${e.message}"
            )
        }
    }
    
    fun submitDirectScore(songId: Long, score: Double) {
        directScores[songId] = score
        currentSongIndex++
        updateDirectScoringUI()
    }
    
    fun submitMatchResult(@Suppress("UNUSED_PARAMETER") matchId: Long, winnerId: Long?) {
        viewModelScope.launch {
            val currentState = _uiState.value
            currentState.currentMatch?.let { match ->
                val updatedMatch = match.copy(
                    winnerId = winnerId,
                    isCompleted = true
                )
                repository.updateMatch(updatedMatch)
                
                loadNextMatch()
            }
        }
    }
    
    private fun getCurrentSwissRound(completedMatches: Int): Int {
        val matchesPerRound = songs.size / 2
        return (completedMatches / matchesPerRound) + 1
    }
    
    private fun getCurrentEmreRound(completedMatches: Int): Int {
        val matchesPerRound = songs.size / 2
        return (completedMatches / matchesPerRound) + 1
    }
}