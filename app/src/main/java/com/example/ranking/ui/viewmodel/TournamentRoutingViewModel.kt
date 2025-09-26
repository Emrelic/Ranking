package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.Tournament
import kotlinx.coroutines.launch

class TournamentRoutingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    
    fun loadTournament(tournamentId: Long, onResult: (Tournament?) -> Unit) {
        viewModelScope.launch {
            try {
                val tournament = database.tournamentDao().getTournamentById(tournamentId)
                onResult(tournament)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
}