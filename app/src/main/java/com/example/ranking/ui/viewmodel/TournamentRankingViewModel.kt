package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.*
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TournamentRankingUiState(
    val tournament: Tournament? = null,
    val currentMatch: Match? = null,
    val standings: List<Any> = emptyList(), // Will be properly typed
    val criteriaSettings: Map<String, Any>? = null,
    val currentMatchCriteriaScores: List<CriterionScore> = emptyList(),
    val isLoading: Boolean = true,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
    val emreState: EmreSystemCorrect.EmreState? = null,
    val allSongs: List<Song> = emptyList(),
    val showInitialRanking: Boolean = false,
    val showMatchingsList: Boolean = false,
    val matchingsList: List<Match> = emptyList()
)

class TournamentRankingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    private val gson = Gson()
    
    private val repository = RankingRepository(
        songDao = database.songDao(),
        songListDao = database.songListDao(),
        rankingResultDao = database.rankingResultDao(),
        matchDao = database.matchDao(),
        leagueSettingsDao = database.leagueSettingsDao(),
        archiveDao = database.archiveDao(),
        csvReader = CsvReader(),
        swissStateDao = database.swissStateDao(),
        swissMatchStateDao = database.swissMatchStateDao()
    )
    
    private var emreState: EmreSystemCorrect.EmreState? = null
    private var songs: List<Song> = emptyList()
    
    private val _uiState = MutableStateFlow(TournamentRankingUiState())
    val uiState: StateFlow<TournamentRankingUiState> = _uiState.asStateFlow()
    
    private val _showCriteriaDialog = MutableStateFlow(false)
    val showCriteriaDialog: StateFlow<Boolean> = _showCriteriaDialog.asStateFlow()
    
    fun initializeTournament(tournamentId: Long) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                val tournament = database.tournamentDao().getTournamentById(tournamentId)
                if (tournament == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Turnuva bulunamadı"
                    )
                    return@launch
                }
                
                // Parse criteria settings
                val criteriaSettings = tournament.criteriaSettings?.let { json ->
                    try {
                        gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    tournament = tournament,
                    criteriaSettings = criteriaSettings,
                    isLoading = false,
                    isCompleted = tournament.isCompleted
                )
                
                // Initialize tournament matches if not completed
                if (!tournament.isCompleted) {
                    initializeTournamentMatches(tournament)
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Turnuva yüklenemedi: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun initializeTournamentMatches(tournament: Tournament) {
        // This would integrate with existing Swiss/Emre system logic
        // For now, placeholder implementation
        
        // Check if matches already exist
        val existingMatches = database.matchDao().getMatchesByTournamentId(tournament.id)
        
        if (existingMatches.isEmpty()) {
            // Generate initial matches based on tournament system
            generateTournamentMatches(tournament)
        } else {
            // Load current match
            loadCurrentMatch(tournament.id)
        }
    }
    
    private suspend fun generateTournamentMatches(tournament: Tournament) {
        try {
            // Load songs for the tournament
            repository.getSongsByListId(tournament.songListId).collect { songList ->
                songs = songList
                
                if (songs.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Şarkı listesi boş!"
                    )
                    return@collect
                }
                
                android.util.Log.d("TournamentRankingViewModel", "Tournament system: ${tournament.systemType}")
                android.util.Log.d("TournamentRankingViewModel", "Songs count: ${songs.size}")
                
                when (tournament.systemType) {
                    "EMRE_CORRECT" -> initializeEmreTournament()
                    "SWISS" -> initializeSwissTournament()
                    "LEAGUE" -> initializeLeagueTournament()
                    "ELIMINATION" -> initializeEliminationTournament()
                    "FULL_ELIMINATION" -> initializeFullEliminationTournament()
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Desteklenmeyen turnuva sistemi: ${tournament.systemType}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TournamentRankingViewModel", "generateTournamentMatches error: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Turnuva başlatma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun loadCurrentMatch(tournamentId: Long) {
        // Load the next unplayed match
        val matches = database.matchDao().getMatchesByTournamentId(tournamentId)
        val currentMatch = matches.firstOrNull { !it.isCompleted }
        
        _uiState.value = _uiState.value.copy(currentMatch = currentMatch)
        
        // Load criteria scores for current match if exists
        if (currentMatch != null) {
            loadCurrentMatchCriteriaScores(currentMatch.id)
        }
    }
    
    private suspend fun loadCurrentMatchCriteriaScores(matchId: Long) {
        val scores = database.criterionScoreDao().getCriterionScoresByMatch(matchId)
        _uiState.value = _uiState.value.copy(currentMatchCriteriaScores = scores)
    }
    
    fun recordMatchResult(matchId: Long, result: Int) {
        viewModelScope.launch {
            try {
                val match = database.matchDao().getMatchById(matchId)
                if (match != null) {
                    val updatedMatch = match.copy(
                        winnerId = when (result) {
                            1 -> match.songId1
                            2 -> match.songId2
                            else -> null // draw
                        },
                        isCompleted = true
                    )
                    
                    database.matchDao().updateMatch(updatedMatch)
                    
                    // Move to next match
                    val tournamentId = _uiState.value.tournament?.id ?: return@launch
                    loadCurrentMatch(tournamentId)
                    
                    // Check if tournament is completed
                    checkTournamentCompletion(tournamentId)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Maç sonucu kaydedilemedi: ${e.message}")
            }
        }
    }
    
    fun recordMatchResultFromCriteria(matchId: Long, result: Int) {
        recordMatchResult(matchId, result)
    }
    
    private suspend fun checkTournamentCompletion(tournamentId: Long) {
        val matches = database.matchDao().getMatchesByTournamentId(tournamentId)
        val allCompleted = matches.all { it.isCompleted }
        
        if (allCompleted) {
            database.tournamentDao().completeTournament(tournamentId)
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }
    
    fun openCriteriaDialog() {
        _showCriteriaDialog.value = true
    }
    
    fun closeCriteriaDialog() {
        _showCriteriaDialog.value = false
    }
    
    fun saveCriteriaScores(matchId: Long, scores: Map<String, Pair<Double?, Double?>>) {
        viewModelScope.launch {
            try {
                val tournamentId = _uiState.value.tournament?.id ?: return@launch
                
                scores.forEach { (criterionName, scorePair) ->
                    val criterionScore = CriterionScore(
                        matchId = matchId,
                        tournamentId = tournamentId,
                        criterionName = criterionName,
                        team1Score = scorePair.first,
                        team2Score = scorePair.second
                    )
                    
                    database.criterionScoreDao().insertCriterionScore(criterionScore)
                }
                
                // Reload criteria scores
                loadCurrentMatchCriteriaScores(matchId)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Kriter skorları kaydedilemedi: ${e.message}")
            }
        }
    }
    
    private suspend fun initializeEmreTournament() {
        try {
            android.util.Log.d("TournamentRankingViewModel", "initializeEmreTournament started")
            
            // Initialize Emre system
            emreState = EmreSystemCorrect.initializeEmreTournament(songs)
            android.util.Log.d("TournamentRankingViewModel", "Emre system initialized, teams: ${emreState?.teams?.size}")
            
            // Show initial ranking table first
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                showInitialRanking = true,
                emreState = emreState,
                allSongs = songs,
                currentMatch = null
            )
            android.util.Log.d("TournamentRankingViewModel", "Initial ranking table shown")
            
        } catch (e: Exception) {
            android.util.Log.e("TournamentRankingViewModel", "initializeEmreTournament error: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Emre sistemi başlatma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun initializeSwissTournament() {
        try {
            val matches = RankingEngine.createSwissMatches(songs, 1, emptyList())
            
            // Create matches in database with tournament ID
            val tournament = _uiState.value.tournament
            if (tournament != null) {
                val tournamentMatches = matches.map { match ->
                    match.copy(tournamentId = tournament.id)
                }
                repository.createMatches(tournamentMatches)
            }
            
            loadCurrentMatch(_uiState.value.tournament?.id ?: 0L)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Swiss sistemi başlatma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun initializeLeagueTournament() {
        try {
            val matches = RankingEngine.createLeagueMatches(songs, false)
            
            // Create matches in database with tournament ID
            val tournament = _uiState.value.tournament
            if (tournament != null) {
                val tournamentMatches = matches.map { match ->
                    match.copy(tournamentId = tournament.id)
                }
                repository.createMatches(tournamentMatches)
            }
            
            loadCurrentMatch(_uiState.value.tournament?.id ?: 0L)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Lig sistemi başlatma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun initializeEliminationTournament() {
        try {
            val matches = RankingEngine.createEliminationMatches(songs)
            
            // Create matches in database with tournament ID
            val tournament = _uiState.value.tournament
            if (tournament != null) {
                val tournamentMatches = matches.map { match ->
                    match.copy(tournamentId = tournament.id)
                }
                repository.createMatches(tournamentMatches)
            }
            
            loadCurrentMatch(_uiState.value.tournament?.id ?: 0L)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Eleme sistemi başlatma hatası: ${e.message}"
            )
        }
    }
    
    private suspend fun initializeFullEliminationTournament() {
        try {
            val matches = RankingEngine.createFullEliminationMatches(songs)
            
            // Create matches in database with tournament ID
            val tournament = _uiState.value.tournament
            if (tournament != null) {
                val tournamentMatches = matches.map { match ->
                    match.copy(tournamentId = tournament.id)
                }
                repository.createMatches(tournamentMatches)
            }
            
            loadCurrentMatch(_uiState.value.tournament?.id ?: 0L)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Tam eleme sistemi başlatma hatası: ${e.message}"
            )
        }
    }
    
    fun createFirstRoundMatches() {
        android.util.Log.d("TournamentRankingViewModel", "createFirstRoundMatches called")
        viewModelScope.launch {
            try {
                val currentState = emreState
                if (currentState == null) {
                    android.util.Log.e("TournamentRankingViewModel", "EmreState is null!")
                    _uiState.value = _uiState.value.copy(errorMessage = "EmreState bulunamadı")
                    return@launch
                }
                
                // Create first round pairing
                val pairingResult = EmreSystemCorrect.createHybridPairingSystem(currentState)
                android.util.Log.d("TournamentRankingViewModel", "Pairing result: ${pairingResult.matches.size} matches")
                
                if (pairingResult.matches.isNotEmpty()) {
                    // Add tournament ID to matches
                    val tournament = _uiState.value.tournament
                    if (tournament != null) {
                        val tournamentMatches = pairingResult.matches.map { match ->
                            match.copy(tournamentId = tournament.id)
                        }
                        repository.createMatches(tournamentMatches)
                        android.util.Log.d("TournamentRankingViewModel", "Matches saved to database")
                        
                        // Show matchings list
                        _uiState.value = _uiState.value.copy(
                            showInitialRanking = false,
                            showMatchingsList = true,
                            matchingsList = tournamentMatches.sortedBy { it.matchNumber }
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Eşleştirme oluşturulamadı"
                    )
                }
                
            } catch (e: Exception) {
                android.util.Log.e("TournamentRankingViewModel", "createFirstRoundMatches error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Eşleştirme hatası: ${e.message}"
                )
            }
        }
    }
    
    fun startScoring() {
        android.util.Log.d("TournamentRankingViewModel", "startScoring called")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showMatchingsList = false)
            loadCurrentMatch(_uiState.value.tournament?.id ?: 0L)
        }
    }
}