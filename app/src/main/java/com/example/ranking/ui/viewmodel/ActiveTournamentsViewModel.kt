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
    
    fun deleteTournament(tournamentId: Long) {
        viewModelScope.launch {
            try {
                // First get the tournament object, then delete it
                val tournament = database.tournamentDao().getTournamentById(tournamentId)
                if (tournament != null) {
                    database.tournamentDao().deleteTournament(tournament)
                }
                // List will automatically update through the collect above
            } catch (e: Exception) {
                // Handle error if needed
            }
        }
    }
}