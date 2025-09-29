package com.example.ranking.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.Song
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.launch
import org.json.JSONObject

class ListEditViewModel(application: Application) : AndroidViewModel(application) {
    
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
    
    fun loadListData(listId: Long, onResult: (List<Song>) -> Unit) {
        viewModelScope.launch {
            try {
                val songs = repository.getSongsByListIdSync(listId)
                onResult(songs)
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }
    
    fun updateSongData(
        songId: Long,
        updatedData: Map<String, String>,
        displayMode: String = "table",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                
                // Create JSON from updated data
                val csvDataMap = updatedData.toMutableMap()
                csvDataMap["_displayMode"] = displayMode
                
                val jsonEntries = csvDataMap.map { 
                    "\"${it.key.replace("\"", "\\\"")}\": \"${it.value.replace("\"", "\\\"")}\""
                }
                val csvDataJson = "{${jsonEntries.joinToString(", ")}}"
                
                // Extract basic fields
                val songName = updatedData["Name"] ?: updatedData["Song"] ?: updatedData.values.firstOrNull() ?: "Unknown"
                val artist = listOf("Artist", "artist", "Sanatçı", "sanatçı", "Sanatci")
                    .firstNotNullOfOrNull { updatedData[it] } ?: ""
                val album = listOf("Album", "album", "Albüm", "albüm")
                    .firstNotNullOfOrNull { updatedData[it] } ?: ""
                
                // Update song in database
                repository.updateSongWithCsvData(songId, songName, artist, album, csvDataJson)
                
                onSuccess()
                
            } catch (e: Exception) {
                onError("Şarkı güncellenemedi: ${e.message}")
            }
        }
    }
    
    fun addNewSong(
        listId: Long,
        songData: Map<String, String>,
        displayMode: String = "table",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                
                // Create JSON from song data
                val csvDataMap = songData.toMutableMap()
                csvDataMap["_displayMode"] = displayMode
                
                val jsonEntries = csvDataMap.map { 
                    "\"${it.key.replace("\"", "\\\"")}\": \"${it.value.replace("\"", "\\\"")}\""
                }
                val csvDataJson = "{${jsonEntries.joinToString(", ")}}"
                
                // Extract basic fields
                val songName = songData["Name"] ?: songData["Song"] ?: songData.values.firstOrNull() ?: "New Song"
                val artist = listOf("Artist", "artist", "Sanatçı", "sanatçı", "Sanatci")
                    .firstNotNullOfOrNull { songData[it] } ?: ""
                val album = listOf("Album", "album", "Albüm", "albüm")
                    .firstNotNullOfOrNull { songData[it] } ?: ""
                
                // Add song to database
                repository.addSongWithCsvData(listId, songName, artist, album, csvDataJson)
                
                onSuccess()
                
            } catch (e: Exception) {
                onError("Şarkı eklenemedi: ${e.message}")
            }
        }
    }
    
    fun deleteSong(
        songId: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.deleteSong(songId)
                onSuccess()
                
            } catch (e: Exception) {
                onError("Şarkı silinemedi: ${e.message}")
            }
        }
    }
    
    fun saveListChanges(
        listId: Long,
        updatedSongs: List<Pair<Long, Map<String, String>>>, // (songId, data) pairs
        headers: List<String> = emptyList(), // Sütun sırası bilgisi
        displayMode: String = "table",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                
                updatedSongs.forEach { (songId, songData) ->
                    // Create JSON from song data - HEADER SIRASI KORUNARAK
                    val csvDataMap = songData.toMutableMap()
                    csvDataMap["_displayMode"] = displayMode

                    // Header sırası varsa kullan, yoksa mevcut key sırasını kullan
                    val orderedKeys = if (headers.isNotEmpty()) {
                        headers + "_displayMode" // Önce header sırası, sonra displayMode
                    } else {
                        csvDataMap.keys.toList() // Fallback: mevcut key sırası
                    }

                    val jsonEntries = orderedKeys.mapNotNull { key ->
                        csvDataMap[key]?.let { value ->
                            "\"${key.replace("\"", "\\\"")}\": \"${value.replace("\"", "\\\"")}\""
                        }
                    }
                    val csvDataJson = "{${jsonEntries.joinToString(", ")}}"
                    
                    // Extract basic fields
                    val songName = songData["Name"] ?: songData["Song"] ?: songData.values.firstOrNull() ?: "Unknown"
                    val artist = listOf("Artist", "artist", "Sanatçı", "sanatçı", "Sanatci")
                        .firstNotNullOfOrNull { songData[it] } ?: ""
                    val album = listOf("Album", "album", "Albüm", "albüm")
                        .firstNotNullOfOrNull { songData[it] } ?: ""
                    
                    // Update song in database
                    repository.updateSongWithCsvData(songId, songName, artist, album, csvDataJson)
                }
                
                onSuccess()
                
            } catch (e: Exception) {
                onError("Değişiklikler kaydedilemedi: ${e.message}")
            }
        }
    }
}