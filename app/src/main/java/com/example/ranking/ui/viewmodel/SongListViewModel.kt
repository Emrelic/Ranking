package com.example.ranking.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.Song
import com.example.ranking.data.SongList
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SongListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(database)
    
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    
    private val _songList = MutableStateFlow<SongList?>(null)
    val songList: StateFlow<SongList?> = _songList.asStateFlow()
    
    /**
     * Tek hücreyi günceller ve VERİTABANINA yazar.
     *
     * csvData JSON'unda `sutun` anahtarı `deger` yapılır. Sütunun eski
     * değeri öğenin ADIYLA aynıysa (yani düzenlenen sütun ad sütunuysa)
     * `song.name` de birlikte güncellenir — aksi hâlde kartlarda ve
     * turnuvalarda eski ad görünmeye devam ederdi.
     *
     * Liste `getSongsByListId` Flow'u üzerinden izlendiği için kayıt
     * biter bitmez ekran kendiliğinden tazelenir.
     */
    fun hucreGuncelle(song: Song, sutun: String, deger: String, onHata: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val json = try {
                    org.json.JSONObject(song.csvData ?: "{}")
                } catch (e: Exception) {
                    org.json.JSONObject()
                }
                val eskiDeger = json.optString(sutun, "")
                json.put(sutun, deger)

                val adSutunuydu = eskiDeger.isNotBlank() &&
                    eskiDeger.trim().equals(song.name.trim(), ignoreCase = true)
                repository.updateSong(
                    song.copy(
                        name = if (adSutunuydu && deger.isNotBlank() && deger != "-")
                            deger.trim() else song.name,
                        csvData = json.toString()
                    )
                )
            } catch (e: Exception) {
                onHata(e.message ?: "Hücre kaydedilemedi")
            }
        }
    }

    fun loadSongs(listId: Long) {
        viewModelScope.launch {
            repository.getSongsByListId(listId).collect { songList ->
                _songs.value = songList
            }
        }
        
        viewModelScope.launch {
            _songList.value = repository.getSongListById(listId)
        }
    }
}