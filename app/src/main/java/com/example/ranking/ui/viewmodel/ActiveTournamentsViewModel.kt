package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.Tournament
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActiveTournamentsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    
    private val _activeTournaments = MutableStateFlow<List<Tournament>>(emptyList())
    val activeTournaments: StateFlow<List<Tournament>> = _activeTournaments.asStateFlow()
    
    init {
        loadActiveTournaments()
    }
    
    private fun loadActiveTournaments() {
        viewModelScope.launch {
            database.tournamentDao().getActiveTournaments().collect { tournaments ->
                _activeTournaments.value = tournaments
            }
        }
    }
    
    fun deleteTournament(tournament: Tournament) {
        viewModelScope.launch {
            try {
                // Turnuva ile ilişkili tüm verileri sil
                deleteTournamentRelatedData(tournament.id)
                
                // Turnuvayı sil
                database.tournamentDao().deleteTournament(tournament)
                
                android.util.Log.d("ActiveTournamentsViewModel", "Tournament deleted: ${tournament.name}")
            } catch (e: Exception) {
                android.util.Log.e("ActiveTournamentsViewModel", "Error deleting tournament: ${e.message}", e)
            }
        }
    }
    
    private suspend fun deleteTournamentRelatedData(tournamentId: Long) {
        try {
            // Turnuva ile ilişkili maçları sil
            val matches = database.matchDao().getMatchesByTournamentId(tournamentId)
            matches.forEach { match ->
                database.matchDao().updateMatch(match.copy(tournamentId = null))
            }
            
            // Kriter skorlarını sil
            val criterionScores = database.criterionScoreDao().getCriterionScoresByTournament(tournamentId)
            criterionScores.forEach { score ->
                database.criterionScoreDao().deleteCriterionScore(score)
            }
            
            android.util.Log.d("ActiveTournamentsViewModel", "Deleted related data for tournament: $tournamentId")
        } catch (e: Exception) {
            android.util.Log.e("ActiveTournamentsViewModel", "Error deleting tournament related data: ${e.message}", e)
        }
    }
}