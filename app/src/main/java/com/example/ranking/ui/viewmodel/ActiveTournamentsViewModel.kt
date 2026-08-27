package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.Tournament
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActiveTournamentsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = RankingDatabase.getDatabase(application)
    
    private val _activeTournaments = MutableStateFlow<List<Tournament>>(emptyList())
    val activeTournaments: StateFlow<List<Tournament>> = _activeTournaments.asStateFlow()

    private val _hata = MutableStateFlow<String?>(null)
    val hata: StateFlow<String?> = _hata.asStateFlow()
    
    init {
        loadActiveTournaments()
    }
    
    private fun loadActiveTournaments() {
        viewModelScope.launch {
            database.tournamentDao().getActiveTournaments().collect { tournaments ->
                _activeTournaments.value = tournaments
            }
        }
    }
    
    /**
     * Turnuvayı ve ona ait TÜM veriyi siler.
     *
     * Eskiden yalnız `tournaments` satırı siliniyordu; maçlar, sıralama
     * sonuçları ve oylama oturumu geride kalıyordu. Sonuç: aynı listeyle
     * aynı yönteme girildiğinde eski oturum "aktif" bulunuyor ve silinmiş
     * turnuva kaldığı yerden devam ediyordu. Üstelik turnuva kaydı artık
     * olmadığı için kriter listesi de kayboluyordu.
     *
     * Hepsi tek transaction'da silinir: yarım silme, tutarsız bir
     * veritabanından daha kötüdür.
     */
    fun deleteTournament(tournamentId: Long) {
        viewModelScope.launch {
            try {
                val tournament = database.tournamentDao().getTournamentById(tournamentId)
                    ?: return@launch

                database.withTransaction {
                    val listId = tournament.songListId
                    val method = tournament.systemType

                    // Maçlar ve sıralama sonuçları
                    database.matchDao().deleteMatches(listId, method)
                    database.rankingResultDao().deleteRankingResults(listId, method)

                    // Oylama oturumu (kalırsa "Devam Et" silinmiş turnuvayı açar)
                    database.votingSessionDao().getActiveSession(listId, method)
                        ?.let { database.votingSessionDao().deactivateSession(it.id) }

                    database.tournamentDao().deleteTournament(tournament)
                }
            } catch (e: Exception) {
                _hata.value = "Turnuva silinemedi: ${e.message}"
            }
        }
    }

    /** Silme hatası — ekranda kullanıcıya gösterilir. */
    fun hataTemizle() {
        _hata.value = null
    }
}