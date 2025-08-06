package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.*
import com.example.ranking.data.RankingDatabase
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FixtureViewModel(application: Application) : AndroidViewModel(application) {
    
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
    
    data class FixtureUiState(
        val isLoading: Boolean = true,
        val matches: List<Match> = emptyList(),
        val songs: List<Song> = emptyList(),
        val completedMatches: Int = 0,
        val totalMatches: Int = 0,
        val leagueSettings: LeagueSettings? = null,
        val editingMatch: Match? = null,
        val errorMessage: String? = null
    )
    
    private val _uiState = MutableStateFlow(FixtureUiState())
    val uiState: StateFlow<FixtureUiState> = _uiState.asStateFlow()
    
    fun loadFixture(listId: Long, method: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Load songs
                repository.getSongsByListId(listId).collect { songs ->
                    // Load league settings if applicable
                    val settings = if (method == "LEAGUE") {
                        repository.getLeagueSettings(listId, method)
                    } else null
                    
                    // Load matches
                    val matches = repository.getMatchesByListAndMethodSync(listId, method)
                    val (completed, total) = repository.getMatchProgress(listId, method)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        matches = matches,
                        songs = songs,
                        completedMatches = completed,
                        totalMatches = total,
                        leagueSettings = settings
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    fun selectMatchForEdit(match: Match) {
        _uiState.value = _uiState.value.copy(editingMatch = match)
    }
    
    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingMatch = null)
    }
    
    fun saveMatchEdit(updatedMatch: Match) {
        viewModelScope.launch {
            try {
                repository.updateMatch(updatedMatch)
                
                // Update local state
                val updatedMatches = _uiState.value.matches.map { match ->
                    if (match.id == updatedMatch.id) updatedMatch else match
                }
                
                _uiState.value = _uiState.value.copy(
                    matches = updatedMatches,
                    editingMatch = null
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message,
                    editingMatch = null
                )
            }
        }
    }
}