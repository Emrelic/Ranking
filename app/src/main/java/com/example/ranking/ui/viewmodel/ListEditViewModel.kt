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
    private val repository = RankingRepository(database)
    
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

                    // Öğe adı yalnızca AÇIK ad sütunundan alınır. Eski davranış
                    // values.firstOrNull()'a düşüyordu; hazır listelerde ilk
                    // sütun "No" olduğu için kayıt sonrası tüm öğe adları sıra
                    // numarasına dönüşüyordu ("Kaydet çalışmıyor" algısının kaynağı).
                    val explicitName = listOf("Name", "Song", "Şarkı", "İsim", "Ad")
                        .firstNotNullOfOrNull { songData[it] }
                        ?.takeIf { it.isNotBlank() }
                    val artist = listOf("Artist", "artist", "Sanatçı", "sanatçı", "Sanatci")
                        .firstNotNullOfOrNull { songData[it] } ?: ""
                    val album = listOf("Album", "album", "Albüm", "albüm")
                        .firstNotNullOfOrNull { songData[it] } ?: ""

                    if (songId > 0) {
                        // Mevcut kayıt: açık ad sütunu yoksa mevcut ad korunur
                        repository.updateSongWithCsvData(songId, explicitName, artist, album, csvDataJson)
                    } else {
                        // Yeni satır (+Satır ile eklendi): yeni kayıt oluştur.
                        // Ad: açık ad sütunu > CSV sözleşmesindeki 4. sütun > ilk dolu hücre
                        val newName = explicitName
                            ?: headers.getOrNull(3)?.let { songData[it] }?.takeIf { it.isNotBlank() }
                            ?: songData.values.firstOrNull { it.isNotBlank() }
                            ?: "Yeni Öğe"
                        repository.addSongWithCsvData(listId, newName, artist, album, csvDataJson)
                    }
                }

                onSuccess()
                
            } catch (e: Exception) {
                onError("Değişiklikler kaydedilemedi: ${e.message}")
            }
        }
    }
}