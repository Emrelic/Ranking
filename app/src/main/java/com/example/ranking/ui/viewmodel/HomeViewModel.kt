package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.SongList
import com.example.ranking.data.VotingSession
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(
        songDao = database.songDao(),
        songListDao = database.songListDao(),
        rankingResultDao = database.rankingResultDao(),
        matchDao = database.matchDao(),
        leagueSettingsDao = database.leagueSettingsDao(),
        archiveDao = database.archiveDao(),
        csvReader = CsvReader(),
        swissStateDao = database.swissStateDao(),
        swissMatchStateDao = database.swissMatchStateDao()
    )
    
    private val votingSessionDao = database.votingSessionDao()
    
    val songLists: StateFlow<List<SongList>> = repository.getAllSongLists()
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Active tournaments
    private val _activeTournaments = MutableStateFlow<List<VotingSession>>(emptyList())
    val activeTournaments: StateFlow<List<VotingSession>> = _activeTournaments.asStateFlow()
    
    init {
        loadActiveTournaments()
    }
    
    fun loadActiveTournaments() {
        viewModelScope.launch {
            try {
                val tournaments = votingSessionDao.getAllActiveTournaments()
                _activeTournaments.value = tournaments
                android.util.Log.d("HomeViewModel", "Loaded ${tournaments.size} active tournaments")
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error loading active tournaments", e)
                _activeTournaments.value = emptyList()
            }
        }
    }
    
    fun resumeTournament(session: VotingSession): Pair<Long, String> {
        return Pair(session.listId, session.rankingMethod)
    }
    
    fun deleteSongList(songList: SongList) {
        viewModelScope.launch {
            repository.deleteSongList(songList)
        }
    }
}