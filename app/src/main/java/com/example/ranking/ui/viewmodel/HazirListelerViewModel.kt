package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.HazirListe
import com.example.ranking.data.RankingDatabase
import com.example.ranking.repository.RankingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HazirListelerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(database)

    sealed class ImportState {
        object Idle : ImportState()
        data class Importing(val ad: String) : ImportState()
        data class Success(val ad: String, val listId: Long, val ogeSayisi: Int) : ImportState()
        data class Error(val mesaj: String) : ImportState()
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun importList(liste: HazirListe) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing(liste.ad)
            try {
                val listId = repository.importPreparedList(
                    context = getApplication(),
                    assetFileName = liste.dosya,
                    listName = liste.ad
                )
                _importState.value = ImportState.Success(liste.ad, listId, liste.ogeSayisi)
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "İçe aktarma başarısız")
            }
        }
    }

    fun clearState() {
        _importState.value = ImportState.Idle
    }
}
