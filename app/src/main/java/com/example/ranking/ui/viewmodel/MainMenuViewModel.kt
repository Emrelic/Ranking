package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainMenuViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    
    private val _activeTournamentsCount = MutableStateFlow(0)
    val activeTournamentsCount: StateFlow<Int> = _activeTournamentsCount.asStateFlow()
    
    private val _completedTournamentsCount = MutableStateFlow(0)
    val completedTournamentsCount: StateFlow<Int> = _completedTournamentsCount.asStateFlow()
    
    private val _songListsCount = MutableStateFlow(0)
    val songListsCount: StateFlow<Int> = _songListsCount.asStateFlow()
    
    private val _criterionListsCount = MutableStateFlow(0)
    val criterionListsCount: StateFlow<Int> = _criterionListsCount.asStateFlow()
    
    init {
        loadCounts()
    }
    
    fun loadCounts() {
        viewModelScope.launch {
            try {
                // Count active tournaments
                database.tournamentDao().getActiveTournaments().collect { tournaments ->
                    _activeTournamentsCount.value = tournaments.size
                }
            } catch (e: Exception) {
                _activeTournamentsCount.value = 0
            }
        }
        
        viewModelScope.launch {
            try {
                // Count completed tournaments
                database.tournamentDao().getCompletedTournaments().collect { tournaments ->
                    _completedTournamentsCount.value = tournaments.size
                }
            } catch (e: Exception) {
                _completedTournamentsCount.value = 0
            }
        }
        
        viewModelScope.launch {
            try {
                // Count song lists
                database.songListDao().getAllSongLists().collect { lists ->
                    _songListsCount.value = lists.size
                }
            } catch (e: Exception) {
                _songListsCount.value = 0
            }
        }
        
        viewModelScope.launch {
            try {
                // Count criterion lists
                database.criterionListDao().getAllActiveCriterionLists().collect { lists ->
                    _criterionListsCount.value = lists.size
                }
            } catch (e: Exception) {
                _criterionListsCount.value = 0
            }
        }
    }
}