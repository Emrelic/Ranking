package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.*
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
    val errorMessage: String? = null
)

class TournamentRankingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    private val gson = Gson()
    
    private val _uiState = MutableStateFlow(TournamentRankingUiState())
    val uiState: StateFlow<TournamentRankingUiState> = _uiState.asStateFlow()
    
    private val _showCriteriaDialog = MutableStateFlow(false)
    val showCriteriaDialog: StateFlow<Boolean> = _showCriteriaDialog.asStateFlow()
    
    fun initializeTournament(tournamentId: Long) {
        android.util.Log.d("TournamentRankingViewModel", "initializeTournament called with ID: $tournamentId")
        viewModelScope.launch {
            try {
                android.util.Log.d("TournamentRankingViewModel", "Setting isLoading = true")
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                
                android.util.Log.d("TournamentRankingViewModel", "Querying database for tournament $tournamentId")
                val tournament = database.tournamentDao().getTournamentById(tournamentId)
                android.util.Log.d("TournamentRankingViewModel", "Database query result: ${tournament?.name ?: "NULL"}")
                
                if (tournament == null) {
                    android.util.Log.e("TournamentRankingViewModel", "Tournament not found!")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Turnuva bulunamadı"
                    )
                    return@launch
                }
                
                android.util.Log.d("TournamentRankingViewModel", "Parsing criteria settings...")
                
                // Parse criteria settings
                val criteriaSettings = tournament.criteriaSettings?.let { json ->
                    try {
                        gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
                    } catch (e: Exception) {
                        android.util.Log.e("TournamentRankingViewModel", "Failed to parse criteria: ${e.message}")
                        null
                    }
                }
                
                android.util.Log.d("TournamentRankingViewModel", "Updating UI state...")
                _uiState.value = _uiState.value.copy(
                    tournament = tournament,
                    criteriaSettings = criteriaSettings,
                    isLoading = false,
                    isCompleted = tournament.isCompleted
                )
                
                android.util.Log.d("TournamentRankingViewModel", "Tournament completed: ${tournament.isCompleted}")
                
                // Initialize tournament matches if not completed
                if (!tournament.isCompleted) {
                    android.util.Log.d("TournamentRankingViewModel", "Initializing tournament matches...")
                    initializeTournamentMatches(tournament)
                } else {
                    android.util.Log.d("TournamentRankingViewModel", "Tournament already completed, skipping initialization")
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
        android.util.Log.d("TournamentRankingViewModel", "initializeTournamentMatches called for tournament ${tournament.id}")
        
        try {
            // Check if matches already exist
            android.util.Log.d("TournamentRankingViewModel", "Checking existing matches...")
            val existingMatches = database.matchDao().getMatchesByTournamentId(tournament.id)
            android.util.Log.d("TournamentRankingViewModel", "Found ${existingMatches.size} existing matches")
            
            if (existingMatches.isEmpty()) {
                android.util.Log.d("TournamentRankingViewModel", "No existing matches, generating new ones...")
                // Generate initial matches based on tournament system
                generateTournamentMatches(tournament)
            } else {
                android.util.Log.d("TournamentRankingViewModel", "Loading current match from existing matches...")
                // Load current match
                loadCurrentMatch(tournament.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("TournamentRankingViewModel", "Error in initializeTournamentMatches: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                errorMessage = "Match initialization error: ${e.message}"
            )
        }
    }
    
    private suspend fun generateTournamentMatches(tournament: Tournament) {
        android.util.Log.d("TournamentRankingViewModel", "generateTournamentMatches called for ${tournament.systemType}")
        
        try {
            // TEMPORARY FIX: Redirect to existing working RankingScreen system
            // This will be properly integrated later
            
            when (tournament.systemType) {
                "EMRE_CORRECT" -> {
                    android.util.Log.d("TournamentRankingViewModel", "EMRE_CORRECT system - redirecting to working implementation")
                    
                    // For now, set error to indicate need for redirect
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "REDIRECT_TO_RANKING_SCREEN:${tournament.songListId}:${tournament.systemType}"
                    )
                }
                else -> {
                    android.util.Log.d("TournamentRankingViewModel", "Other system type: ${tournament.systemType}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "System type ${tournament.systemType} not yet implemented in tournament mode"
                    )
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("TournamentRankingViewModel", "Error generating matches: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Match generation failed: ${e.message}"
            )
        }
        // For now, placeholder
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
}