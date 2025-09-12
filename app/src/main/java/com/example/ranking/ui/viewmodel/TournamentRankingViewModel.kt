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
        // Integration point with existing Swiss/Emre system
        // This would call the existing match generation logic
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