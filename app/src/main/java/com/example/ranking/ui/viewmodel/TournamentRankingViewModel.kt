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
                } else {
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
        
        try {
            // Check if matches already exist
            val existingMatches = database.matchDao().getMatchesByTournamentId(tournament.id)
            
            if (existingMatches.isEmpty()) {
                // Generate initial matches based on tournament system
                generateTournamentMatches(tournament)
            } else {
                // Load current match
                loadCurrentMatch(tournament.id)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Match initialization error: ${e.message}"
            )
        }
    }
    
    private suspend fun generateTournamentMatches(tournament: Tournament) {
        
        try {
            // TEMPORARY FIX: Redirect to existing working RankingScreen system
            // This will be properly integrated later
            
            when (tournament.systemType) {
                "EMRE_CORRECT" -> {
                    
                    // For now, set error to indicate need for redirect
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "REDIRECT_TO_RANKING_SCREEN:${tournament.songListId}:${tournament.systemType}"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "System type ${tournament.systemType} not yet implemented in tournament mode"
                    )
                }
            }
            
        } catch (e: Exception) {
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
                val criteriaSettings = _uiState.value.criteriaSettings
                
                // Mecburi kriter kontrolü
                val mandatoryCriteria = criteriaSettings?.get("mandatoryCriteria") as? Boolean ?: false
                if (mandatoryCriteria) {
                    val validationError = validateMandatoryCriteria(scores)
                    if (validationError != null) {
                        _uiState.value = _uiState.value.copy(errorMessage = validationError)
                        return@launch
                    }
                }
                
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
                
                // Auto-determine winner if enabled
                val autoWinnerFromCriteria = criteriaSettings?.get("autoWinnerFromCriteria") as? Boolean ?: false
                
                if (autoWinnerFromCriteria) {
                    val result = processCriteriaResults(scores, criteriaSettings)
                    if (result != null) {
                        recordMatchResultFromCriteria(matchId, result)
                        closeCriteriaDialog()
                    }
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Kriter skorları kaydedilemedi: ${e.message}")
            }
        }
    }
    
    /**
     * Mecburi kriter kontrolü
     * @param scores Kriter skorları
     * @return Hata mesajı veya null (geçerli ise)
     */
    private fun validateMandatoryCriteria(scores: Map<String, Pair<Double?, Double?>>): String? {
        val incompleteCount = scores.values.count { 
            it.first == null || it.second == null 
        }
        
        return if (incompleteCount > 0) {
            "Kriter oylaması mecburi tutulduğu için tüm kriterler doldurulmalıdır. $incompleteCount kriter eksik."
        } else null
    }
    
    /**
     * Kriter puanlarına göre maç sonucunu belirle
     * @param scores Map<CriterionName, Pair<Team1Score?, Team2Score?>>
     * @param criteriaSettings Turnuva kriter ayarları
     * @return 1 = Team1 wins, 2 = Team2 wins, 0 = Draw, null = Not enough data
     */
    private fun processCriteriaResults(
        scores: Map<String, Pair<Double?, Double?>>, 
        criteriaSettings: Map<String, Any>?
    ): Int? {
        if (criteriaSettings == null) return null
        
        val drawThresholdPercent = criteriaSettings["drawThresholdPercent"] as? List<*>
        val minThreshold = (drawThresholdPercent?.getOrNull(0) as? Double)?.toInt() ?: 51
        
        // Null olmayan skorları filtrele
        val validScores = scores.values.filter { 
            it.first != null && it.second != null 
        }.map { 
            it.first!! to it.second!! 
        }
        
        if (validScores.isEmpty()) return null
        
        // Toplam puanları hesapla
        val team1Total = validScores.sumOf { it.first }
        val team2Total = validScores.sumOf { it.second }
        val grandTotal = team1Total + team2Total
        
        if (grandTotal == 0.0) return null
        
        // Yüzdelik hesapla
        val team1Percentage = (team1Total / grandTotal) * 100
        
        // Sonucu belirle
        return when {
            team1Percentage >= minThreshold -> 1 // Team1 galip
            team1Percentage <= (100 - minThreshold) -> 2 // Team2 galip
            else -> 0 // Beraberlik
        }
    }
}