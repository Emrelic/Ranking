package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.CriterionList
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.SongList
import com.example.ranking.data.Tournament
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NewTournamentViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    private val gson = Gson()
    
    private val _songLists = MutableStateFlow<List<SongList>>(emptyList())
    val songLists: StateFlow<List<SongList>> = _songLists.asStateFlow()
    
    private val _criterionLists = MutableStateFlow<List<CriterionList>>(emptyList())
    val criterionLists: StateFlow<List<CriterionList>> = _criterionLists.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            database.songListDao().getAllSongLists().collect { lists ->
                _songLists.value = lists
            }
        }
        
        viewModelScope.launch {
            database.criterionListDao().getAllActiveCriterionLists().collect { lists ->
                _criterionLists.value = lists
            }
        }
    }
    
    fun createTournament(
        songList: SongList,
        name: String,
        systemType: String,
        criterionList: CriterionList?,
        criteriaSettings: Map<String, Any>?,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                android.util.Log.d("NewTournamentViewModel", "Creating tournament: $name, system: $systemType")

                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                val tournament = Tournament(
                    name = name,
                    startDate = currentDate,
                    songListId = songList.id,
                    systemType = systemType,
                    criterionListId = criterionList?.id,
                    criteriaSettings = criteriaSettings?.let { gson.toJson(it) },
                    isCompleted = false
                )

                android.util.Log.d("NewTournamentViewModel", "Tournament object created: $tournament")

                val tournamentId = database.tournamentDao().insertTournament(tournament)
                android.util.Log.d("NewTournamentViewModel", "Tournament inserted with ID: $tournamentId")

                onSuccess(tournamentId)

            } catch (e: Exception) {
                android.util.Log.e("NewTournamentViewModel", "Failed to create tournament", e)
                onError("Turnuva oluşturulamadı: ${e.message}")
            }
        }
    }
}