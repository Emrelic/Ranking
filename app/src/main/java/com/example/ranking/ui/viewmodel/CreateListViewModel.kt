package com.example.ranking.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.launch

class CreateListViewModel(application: Application) : AndroidViewModel(application) {
    
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
    
    fun createList(
        context: Context,
        listName: String,
        option: String,
        manualSongs: String,
        csvUri: Uri?,
        onSuccess: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (listName.isBlank()) {
                    onError("Liste adı boş olamaz")
                    return@launch
                }
                
                val listId = repository.createSongList(listName)
                
                when (option) {
                    "manual" -> {
                        if (manualSongs.isBlank()) {
                            onError("Öğe listesi boş olamaz")
                            return@launch
                        }
                        
                        // Smart parsing: Handle different input formats
                        Log.d("CreateListViewModel", "Raw input: '$manualSongs'")
                        
                        val lines = when {
                            // CSV-style multi-line data (each line contains comma-separated values)
                            manualSongs.contains("\n") && manualSongs.contains(",") -> {
                                Log.d("CreateListViewModel", "Using CSV multi-line parsing - each line will be processed as comma-separated values")
                                manualSongs.split("\n")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .flatMap { line -> 
                                        // Process each line individually as comma-separated
                                        if (line.contains(",")) {
                                            line.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                        } else {
                                            listOf(line)
                                        }
                                    }
                            }
                            
                            // Single line with comma separation: "Item1, Item2, Item3"
                            manualSongs.contains(",") && !manualSongs.contains("\n") -> {
                                Log.d("CreateListViewModel", "Using comma separation")
                                manualSongs.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                            }
                            
                            // Tab separated (Excel copy-paste): "Item1	Item2	Item3"
                            manualSongs.contains("\t") -> {
                                Log.d("CreateListViewModel", "Using tab separation")
                                manualSongs.split("\t")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                            }
                            
                            // Default: line separated
                            else -> {
                                Log.d("CreateListViewModel", "Using line separation")
                                manualSongs.split("\n")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                            }
                        }
                        
                        Log.d("CreateListViewModel", "Parsed lines: ${lines.joinToString(" | ")}")
                        
                        if (lines.isEmpty()) {
                            onError("Geçerli öğe bulunamadı")
                            return@launch
                        }
                        
                        lines.forEach { line ->
                            val (songName, artist, album) = if (line.contains(" - ")) {
                                val parts = line.split(" - ", limit = 3)
                                when (parts.size) {
                                    3 -> Triple(parts[2].trim(), parts[0].trim(), parts[1].trim())
                                    2 -> Triple(parts[1].trim(), parts[0].trim(), "")
                                    else -> Triple(line, "", "")
                                }
                            } else {
                                Triple(line, "", "")
                            }
                            
                            if (songName.isNotBlank()) {
                                repository.addSong(listId, songName, artist, album)
                            }
                        }
                    }
                    
                    "csv" -> {
                        if (csvUri == null) {
                            onError("CSV dosyası seçilmedi")
                            return@launch
                        }
                        
                        try {
                            Log.d("CreateListViewModel", "CSV dosyası yükleniyor: $csvUri")
                            repository.importSongsFromCsv(context, listId, csvUri)
                            Log.d("CreateListViewModel", "CSV dosyası başarıyla yüklendi")
                        } catch (e: Exception) {
                            Log.e("CreateListViewModel", "CSV yükleme hatası: ${e.message}", e)
                            throw Exception("CSV dosyası yüklenemedi: ${e.message}")
                        }
                    }
                }
                
                onSuccess(listId)
                
            } catch (e: Exception) {
                onError(e.message ?: "Bilinmeyen hata oluştu")
            }
        }
    }
}