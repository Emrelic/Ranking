package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.RankingResult
import com.example.ranking.data.Song
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(
        songDao = database.songDao(),
        songListDao = database.songListDao(),
        rankingResultDao = database.rankingResultDao(),
        matchDao = database.matchDao(),
        csvReader = CsvReader()
    )
    
    private val _results = MutableStateFlow<List<Pair<RankingResult, Song>>>(emptyList())
    val results: StateFlow<List<Pair<RankingResult, Song>>> = _results.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    fun loadResults(listId: Long, method: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            repository.getRankingResults(listId, method).collect { rankingResults ->
                val resultsWithSongs = mutableListOf<Pair<RankingResult, Song>>()
                
                for (result in rankingResults) {
                    val song = database.songDao().getSongById(result.songId)
                    if (song != null) {
                        resultsWithSongs.add(Pair(result, song))
                    }
                }
                
                _results.value = resultsWithSongs
                _isLoading.value = false
            }
        }
    }
}