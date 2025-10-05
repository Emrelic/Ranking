package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.CriterionList
import com.example.ranking.data.RankingDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CriteriaViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    
    private val _criterionLists = MutableStateFlow<List<CriterionList>>(emptyList())
    val criterionLists: StateFlow<List<CriterionList>> = _criterionLists.asStateFlow()
    
    init {
        loadCriterionLists()
    }
    
    private fun loadCriterionLists() {
        viewModelScope.launch {
            try {
                database.criterionListDao().getAllActiveCriterionLists().collect { lists ->
                    _criterionLists.value = lists
                }
            } catch (e: Exception) {
                _criterionLists.value = emptyList()
            }
        }
    }
    
    fun deleteCriterionList(
        criterionList: CriterionList,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Check if any active tournaments are using this criterion list
                val activeTournamentsCount = database.tournamentDao()
                    .getTournamentsByCriterionList(criterionList.id)
                    .filter { !it.isCompleted }
                    .size
                
                if (activeTournamentsCount > 0) {
                    onError("Bu kriter listesi $activeTournamentsCount aktif turnuvada kullanılıyor. Önce turnuvaları tamamlayın.")
                    return@launch
                }
                
                // Safe to delete - deactivate instead of hard delete
                database.criterionListDao().deactivateCriterionList(criterionList.id)
                onSuccess()
            } catch (e: Exception) {
                onError("Silme işlemi başarısız: ${e.message}")
            }
        }
    }
}