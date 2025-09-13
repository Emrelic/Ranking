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
        csvDelimiter: String = ",",
        displayMode: String = "cards",
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
                        
                        Log.d("CreateListViewModel", "Raw manual input: '$manualSongs'")
                        
                        // Check if input looks like CSV table (has headers and multiple rows with chosen delimiter)
                        val inputLines = manualSongs.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        val isCSVTable = inputLines.size >= 2 && 
                                         inputLines.first().contains(csvDelimiter) && 
                                         inputLines.drop(1).all { it.contains(csvDelimiter) }
                        
                        if (isCSVTable) {
                            Log.d("CreateListViewModel", "Detected CSV table format - processing as structured data")
                            
                            try {
                                val headers = inputLines.first().split(csvDelimiter).map { it.trim() }
                                val dataRows = inputLines.drop(1)
                                
                                Log.d("CreateListViewModel", "CSV Headers: ${headers.joinToString(" | ")}")
                                Log.d("CreateListViewModel", "CSV Data rows: ${dataRows.size}")
                                Log.d("CreateListViewModel", "Using delimiter: '$csvDelimiter'")
                                
                                // Find the name column (first non-empty header, or first column)
                                val nameColumnIndex = 0 // Use first column as name
                                val nameColumnHeader = if (nameColumnIndex < headers.size) headers[nameColumnIndex] else "Name"
                                
                                dataRows.forEachIndexed { index, row ->
                                    val values = row.split(csvDelimiter).map { it.trim() }
                                    
                                    if (values.isNotEmpty() && values[0].isNotBlank()) {
                                        val songName = values[0] // First column as song name
                                        
                                        // Create CSV data JSON for additional columns
                                        val csvDataMap = mutableMapOf<String, String>()
                                        for (i in headers.indices) {
                                            if (i < values.size && values[i].isNotBlank()) {
                                                csvDataMap[headers[i]] = values[i]
                                            }
                                        }
                                        
                                        // Convert to JSON string with display mode metadata
                                        val csvDataJson = if (csvDataMap.size > 1) { // More than just name column
                                            csvDataMap["_displayMode"] = displayMode // Add metadata
                                            val jsonEntries = csvDataMap.map { "\"${it.key}\": \"${it.value.replace("\"", "\\\"")}\""  }
                                            "{${jsonEntries.joinToString(", ")}}"
                                        } else {
                                            // Even for single column, add displayMode if it's table format
                                            if (displayMode == "table") {
                                                csvDataMap["_displayMode"] = displayMode
                                                val jsonEntries = csvDataMap.map { "\"${it.key}\": \"${it.value.replace("\"", "\\\"")}\""  }
                                                "{${jsonEntries.joinToString(", ")}}"
                                            } else {
                                                null
                                            }
                                        }
                                        
                                        Log.d("CreateListViewModel", "Row $index: Name='$songName', CSV data='$csvDataJson'")
                                        
                                        // Extract artist/album from known headers or use defaults
                                        val artist = csvDataMap["Artist"] ?: csvDataMap["artist"] ?: 
                                                   csvDataMap["Sanatçı"] ?: csvDataMap["sanatçı"] ?: ""
                                        val album = csvDataMap["Album"] ?: csvDataMap["album"] ?: 
                                                  csvDataMap["Albüm"] ?: csvDataMap["albüm"] ?: ""
                                        
                                        repository.addSongWithCsvData(listId, songName, artist, album, csvDataJson)
                                    }
                                }
                                
                                Log.d("CreateListViewModel", "CSV table processing completed")
                                
                            } catch (e: Exception) {
                                Log.e("CreateListViewModel", "CSV table parsing error: ${e.message}", e)
                                onError("CSV tablo formatı işlenirken hata oluştu: ${e.message}")
                                return@launch
                            }
                            
                        } else {
                            // Original simple parsing logic for non-CSV inputs
                            Log.d("CreateListViewModel", "Using simple list parsing")
                            
                            val lines = when {
                                // Single line with comma separation: "Item1, Item2, Item3"
                                manualSongs.contains(",") && !manualSongs.contains("\n") -> {
                                    manualSongs.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                }
                                
                                // Tab separated (Excel copy-paste): "Item1	Item2	Item3"
                                manualSongs.contains("\t") -> {
                                    manualSongs.split("\t").map { it.trim() }.filter { it.isNotBlank() }
                                }
                                
                                // Default: line separated
                                else -> {
                                    manualSongs.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                                }
                            }
                            
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
                    }
                    
                    "csv" -> {
                        if (csvUri == null) {
                            onError("CSV dosyası seçilmedi")
                            return@launch
                        }
                        
                        try {
                            Log.d("CreateListViewModel", "CSV dosyası yükleniyor: $csvUri")
                            repository.importSongsFromCsvWithDisplayMode(context, listId, csvUri, displayMode)
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