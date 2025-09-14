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
        csvReader = CsvReader()
    )
    
    fun loadListData(listId: Long, onResult: (List<Song>) -> Unit) {
        viewModelScope.launch {
            try {
                Log.d("ListEditViewModel", "🔄 Loading list data for ID: $listId")
                val songs = repository.getSongsByListIdSync(listId)
                Log.d("ListEditViewModel", "✅ Loaded ${songs.size} songs")
                onResult(songs)
            } catch (e: Exception) {
                Log.e("ListEditViewModel", "❌ Error loading list: ${e.message}", e)
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
                Log.d("ListEditViewModel", "🔄 Updating song ID: $songId")
                
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
                
                Log.d("ListEditViewModel", "✅ Song updated successfully")
                onSuccess()
                
            } catch (e: Exception) {
                Log.e("ListEditViewModel", "❌ Error updating song: ${e.message}", e)
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
                Log.d("ListEditViewModel", "🔄 Adding new song to list: $listId")
                
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
                
                Log.d("ListEditViewModel", "✅ New song added successfully")
                onSuccess()
                
            } catch (e: Exception) {
                Log.e("ListEditViewModel", "❌ Error adding song: ${e.message}", e)
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
                Log.d("ListEditViewModel", "🔄 Deleting song ID: $songId")
                repository.deleteSong(songId)
                Log.d("ListEditViewModel", "✅ Song deleted successfully")
                onSuccess()
                
            } catch (e: Exception) {
                Log.e("ListEditViewModel", "❌ Error deleting song: ${e.message}", e)
                onError("Şarkı silinemedi: ${e.message}")
            }
        }
    }
    
    fun saveListChanges(
        listId: Long,
        updatedSongs: List<Pair<Long, Map<String, String>>>, // (songId, data) pairs
        displayMode: String = "table",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d("ListEditViewModel", "🔄 Saving ${updatedSongs.size} song changes")
                
                updatedSongs.forEach { (songId, songData) ->
                    // Create JSON from song data
                    val csvDataMap = songData.toMutableMap()
                    csvDataMap["_displayMode"] = displayMode
                    
                    val jsonEntries = csvDataMap.map { 
                        "\"${it.key.replace("\"", "\\\"")}\": \"${it.value.replace("\"", "\\\"")}\""
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
                
                Log.d("ListEditViewModel", "✅ All changes saved successfully")
                onSuccess()
                
            } catch (e: Exception) {
                Log.e("ListEditViewModel", "❌ Error saving changes: ${e.message}", e)
                onError("Değişiklikler kaydedilemedi: ${e.message}")
            }
        }
    }
}