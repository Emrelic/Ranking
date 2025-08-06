package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.*
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {
    
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
    
    data class ArchiveUiState(
        val isLoading: Boolean = true,
        val archives: List<Archive> = emptyList(),
        val selectedArchive: Archive? = null,
        val archiveResults: List<ArchiveResult> = emptyList(),
        val archiveLeagueTable: List<LeagueTableEntry> = emptyList(),
        val archiveMatches: List<ArchiveMatch> = emptyList(),
        val archiveSettings: ArchiveLeagueSettings? = null,
        val errorMessage: String? = null
    )
    
    data class ArchiveResult(
        val songId: Long,
        val songName: String,
        val artist: String,
        val album: String,
        val score: Double,
        val position: Int
    )
    
    data class LeagueTableEntry(
        val teamName: String,
        val played: Int,
        val won: Int,
        val drawn: Int,
        val lost: Int,
        val goalsFor: Int,
        val goalsAgainst: Int,
        val goalDifference: Int,
        val points: Int
    )
    
    data class ArchiveMatch(
        val team1Id: Long,
        val team1Name: String,
        val team2Id: Long,
        val team2Name: String,
        val score1: Int?,
        val score2: Int?,
        val winnerId: Long?,
        val isCompleted: Boolean,
        val round: Int
    )
    
    data class ArchiveLeagueSettings(
        val useScores: Boolean,
        val winPoints: Int,
        val drawPoints: Int,
        val allowDraws: Boolean
    )
    
    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()
    
    fun loadArchives() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                repository.getAllArchives().collect { archives ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        archives = archives
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Arşivler yüklenirken hata oluştu: ${e.message}"
                )
            }
        }
    }
    
    fun selectArchive(archive: Archive) {
        viewModelScope.launch {
            try {
                val gson = Gson()
                
                // Parse final results
                val resultsType = object : TypeToken<List<ArchiveResult>>() {}.type
                val archiveResults = gson.fromJson<List<ArchiveResult>>(archive.finalResults, resultsType)
                
                // Parse league table if available
                val leagueTable = archive.leagueTable?.let { leagueTableJson ->
                    val leagueTableType = object : TypeToken<List<LeagueTableEntry>>() {}.type
                    gson.fromJson<List<LeagueTableEntry>>(leagueTableJson, leagueTableType)
                } ?: emptyList()
                
                // Parse matches
                val matchesType = object : TypeToken<List<ArchiveMatch>>() {}.type
                val archiveMatches = gson.fromJson<List<ArchiveMatch>>(archive.matchResults, matchesType)
                
                // Parse league settings if available
                val settings = archive.leagueSettings?.let { settingsJson ->
                    val settingsType = object : TypeToken<ArchiveLeagueSettings>() {}.type
                    gson.fromJson<ArchiveLeagueSettings>(settingsJson, settingsType)
                }
                
                _uiState.value = _uiState.value.copy(
                    selectedArchive = archive,
                    archiveResults = archiveResults,
                    archiveLeagueTable = leagueTable,
                    archiveMatches = archiveMatches,
                    archiveSettings = settings
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Arşiv verileri ayrıştırılırken hata oluştu: ${e.message}"
                )
            }
        }
    }
    
    fun closeArchive() {
        _uiState.value = _uiState.value.copy(
            selectedArchive = null,
            archiveResults = emptyList(),
            archiveLeagueTable = emptyList(),
            archiveMatches = emptyList(),
            archiveSettings = null
        )
    }
    
    fun deleteArchive(archive: Archive) {
        viewModelScope.launch {
            try {
                repository.deleteArchive(archive)
                // Reload archives to update the list
                loadArchives()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Arşiv silinirken hata oluştu: ${e.message}"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}