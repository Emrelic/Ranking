package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.LeagueSettings
import com.example.ranking.data.RankingDatabase
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LeagueSettingsViewModel(application: Application) : AndroidViewModel(application) {
    
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

    data class LeagueSettingsUiState(
        val useScores: Boolean = false,
        val winPoints: Int = 3,
        val drawPoints: Int = 1,
        val allowDraws: Boolean = true,
        val doubleRoundRobin: Boolean = false,
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(LeagueSettingsUiState())
    val uiState: StateFlow<LeagueSettingsUiState> = _uiState.asStateFlow()

    private var currentListId: Long = 0
    private var currentMethod: String = ""

    fun initializeSettings(listId: Long, method: String) {
        currentListId = listId
        currentMethod = method
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val existingSettings = repository.getLeagueSettings(listId, method)
                
                if (existingSettings != null) {
                    _uiState.value = _uiState.value.copy(
                        useScores = existingSettings.useScores,
                        winPoints = existingSettings.winPoints,
                        drawPoints = existingSettings.drawPoints,
                        allowDraws = existingSettings.allowDraws,
                        doubleRoundRobin = existingSettings.doubleRoundRobin,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }

    fun updateUseScores(useScores: Boolean) {
        _uiState.value = _uiState.value.copy(useScores = useScores)
    }

    fun updateWinPoints(points: Int) {
        if (points >= 0) {
            _uiState.value = _uiState.value.copy(winPoints = points)
        }
    }

    fun updateDrawPoints(points: Int) {
        if (points >= 0) {
            _uiState.value = _uiState.value.copy(drawPoints = points)
        }
    }

    fun updateAllowDraws(allowDraws: Boolean) {
        _uiState.value = _uiState.value.copy(allowDraws = allowDraws)
    }

    fun updateDoubleRoundRobin(doubleRoundRobin: Boolean) {
        _uiState.value = _uiState.value.copy(doubleRoundRobin = doubleRoundRobin)
    }

    fun saveSettings() {
        viewModelScope.launch {
            try {
                val settings = LeagueSettings(
                    listId = currentListId,
                    rankingMethod = currentMethod,
                    useScores = _uiState.value.useScores,
                    winPoints = _uiState.value.winPoints,
                    drawPoints = _uiState.value.drawPoints,
                    losePoints = 0, // Always 0 for loss
                    allowDraws = _uiState.value.allowDraws,
                    doubleRoundRobin = _uiState.value.doubleRoundRobin
                )
                
                repository.saveLeagueSettings(settings)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

}