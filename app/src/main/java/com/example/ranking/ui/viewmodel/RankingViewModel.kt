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
        leagueSettingsDao = database.leagueSettingsDao(),
        archiveDao = database.archiveDao(),
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
        val leagueSettings: LeagueSettings? = null,
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
                // Load league settings if applicable
                val settings = if (method == "LEAGUE") {
                    repository.getLeagueSettings(listId, method)
                } else null
                
                repository.getSongsByListId(listId).collect { songList ->
                    songs = songList
                    if (songs.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(leagueSettings = settings)
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
        // Reset Emre state variables
        emreFirstConsecutiveWin = false
        emreSecondConsecutiveWin = false
        emreConsecutiveWinCount = 0
        
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
                    // Emre usulünde sabit maksimum tur yok - sadece durma koşuluna bak
                    createNextEmreRound(currentRound)
                    return
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
    
    private var emreFirstConsecutiveWin = false
    private var emreSecondConsecutiveWin = false
    private var emreConsecutiveWinCount = 0
    
    private suspend fun createNextEmreRound(round: Int) {
        try {
            val allMatches = repository.getMatchesByListAndMethodSync(currentListId, currentMethod)
            
            // Check if current round is complete and satisfies stopping condition
            if (RankingEngine.checkEmreCompletion(songs, allMatches, round - 1)) {
                // Bir tur daha tüm birinciler kazandı
                emreConsecutiveWinCount++
                
                if (emreConsecutiveWinCount == 1) {
                    // İlk kez üstüste kazanma - devam et
                    emreFirstConsecutiveWin = true
                    val newMatches = RankingEngine.createEmreMatchesWithOrdering(songs, round, allMatches)
                    if (newMatches.isNotEmpty()) {
                        repository.createMatches(newMatches)
                    }
                    loadNextMatch()
                } else if (emreConsecutiveWinCount >= 2) {
                    // İki kez üstüste kazanma - sıralamayı tamamla
                    emreSecondConsecutiveWin = true
                    completeRanking()
                    return
                }
            } else {
                // Bu turda tüm birinciler kazanmadı - sayacı sıfırla
                emreConsecutiveWinCount = 0
                emreFirstConsecutiveWin = false
                emreSecondConsecutiveWin = false
                
                // Sonraki tur için eşleştirme oluştur
                val newMatches = RankingEngine.createEmreMatchesWithOrdering(songs, round, allMatches)
                if (newMatches.isNotEmpty()) {
                    repository.createMatches(newMatches)
                }
                loadNextMatch()
            }
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
    
    fun submitMatchResultWithScore(@Suppress("UNUSED_PARAMETER") matchId: Long, winnerId: Long?, score1: Int?, score2: Int?) {
        viewModelScope.launch {
            val currentState = _uiState.value
            currentState.currentMatch?.let { match ->
                val updatedMatch = match.copy(
                    winnerId = winnerId,
                    score1 = score1,
                    score2 = score2,
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
        if (completedMatches == 0) return 1
        
        var currentSongCount = songs.size
        var totalMatchesSoFar = 0
        var round = 1
        
        while (totalMatchesSoFar < completedMatches) {
            val matchesInThisRound = currentSongCount / 2
            totalMatchesSoFar += matchesInThisRound
            if (totalMatchesSoFar <= completedMatches) {
                round++
                currentSongCount = matchesInThisRound * 2 // Only winners go to next round
            }
        }
        
        return round
    }
}