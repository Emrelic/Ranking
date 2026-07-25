package com.example.ranking.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ranking.data.RankingDatabase
import com.example.ranking.data.Song
import com.example.ranking.repository.RankingRepository
import com.example.ranking.utils.CsvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = RankingDatabase.getDatabase(application)
    private val repository = RankingRepository(database)
    
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
                Log.d("CreateListViewModel", "Liste oluşturma başladı: $listName, option: $option")
                android.util.Log.d("CREATE_LIST_DEBUG", "createList çağırıldı - listName: '$listName', option: '$option'")
                
                if (listName.isBlank()) {
                    onError("Liste adı boş olamaz")
                    return@launch
                }
                
                if (repository == null) {
                    onError("Repository initialization hatası")
                    return@launch
                }
                val listId = repository.createSongList(listName)
                if (listId <= 0) {
                    onError("Liste oluşturulamadı - geçersiz ID")
                    return@launch
                }
                
                when (option) {
                    "manual" -> {
                        if (manualSongs.isBlank()) {
                            onError("Öğe listesi boş olamaz")
                            return@launch
                        }
                        
                        
                        // Check if input looks like CSV table (has headers and multiple rows with chosen delimiter)
                        val inputLines = manualSongs.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        val isCSVTable = inputLines.size >= 2 && 
                                         inputLines.first().contains(csvDelimiter) && 
                                         inputLines.drop(1).all { it.contains(csvDelimiter) }
                        
                        if (isCSVTable) {
                            
                            try {
                                val headers = inputLines.first().split(csvDelimiter).map { it.trim() }
                                val dataRows = inputLines.drop(1)
                                
                                
                                // Find the name column (first non-empty header, or first column)
                                val nameColumnIndex = 0 // Use first column as name
                                val nameColumnHeader = if (nameColumnIndex < headers.size) headers[nameColumnIndex] else "Name"

                                val songsToAdd = mutableListOf<Song>()
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
                                        
                                        
                                        // Extract artist/album from known headers or use defaults
                                        val artist = csvDataMap["Artist"] ?: csvDataMap["artist"] ?:
                                                   csvDataMap["Sanatçı"] ?: csvDataMap["sanatçı"] ?: ""
                                        val album = csvDataMap["Album"] ?: csvDataMap["album"] ?:
                                                  csvDataMap["Albüm"] ?: csvDataMap["albüm"] ?: ""

                                        songsToAdd.add(Song(name = songName, artist = artist, album = album, trackNumber = 0, listId = listId, csvData = csvDataJson))
                                    }
                                }
                                // Tüm satırlar tek transaction'da eklenir (N+1 önlemi)
                                repository.addSongsBulk(listId, songsToAdd)


                            } catch (e: Exception) {
                                onError("CSV tablo formatı işlenirken hata oluştu: ${e.message}")
                                return@launch
                            }
                            
                        } else {
                            // Original simple parsing logic for non-CSV inputs
                            
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
                            
                            val songsToAdd = lines.mapNotNull { line ->
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
                                    Song(name = songName, artist = artist, album = album, trackNumber = 0, listId = listId)
                                } else null
                            }
                            repository.addSongsBulk(listId, songsToAdd)
                        }
                    }
                    
                    "csv" -> {
                        if (csvUri == null) {
                            onError("CSV dosyası seçilmedi")
                            return@launch
                        }
                        
                        try {
                            
                            // CSV file'ı string olarak oku (disk/ContentProvider IO ana thread'de yapılmaz)
                            val csvContent = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(csvUri)?.use { inputStream ->
                                    inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                                        reader.readText()
                                    }
                                }
                            } ?: throw Exception("CSV dosyası okunamadı")
                            
                            
                            // Manuel ekleme ile AYNI mantığı kullan - csvDelimiter parametresi ile
                            processManualCsvContent(csvContent, csvDelimiter, displayMode, listId, repository)
                            
                            
                        } catch (e: Exception) {
                            throw Exception("CSV dosyası işlenemedi: ${e.message}")
                        }
                    }
                }
                
                android.util.Log.d("CREATE_LIST_DEBUG", "Liste başarıyla oluşturuldu - ID: $listId")
                onSuccess(listId)
                
            } catch (e: Exception) {
                android.util.Log.e("CREATE_LIST_DEBUG", "Liste oluşturma hatası: ${e.message}", e)
                android.util.Log.e("CREATE_LIST_DEBUG", "Stack trace: ${e.stackTraceToString()}")
                onError(e.message ?: "Bilinmeyen hata oluştu")
            }
        }
    }
    
    private suspend fun processManualCsvContent(
        csvContent: String,
        csvDelimiter: String,
        displayMode: String,
        listId: Long,
        repository: RankingRepository
    ) {
        
        // Clean and split lines
        val inputLines = csvContent.replace("\r\n", "\n").replace("\r", "\n")
            .split("\n").map { it.trim() }.filter { it.isNotBlank() }
        
        if (inputLines.isEmpty()) {
            throw Exception("CSV dosyası boş")
        }
        
        
        // SMART DELIMITER DETECTION - Kullanıcının seçimini öncelikle dene
        var detectedDelimiter = csvDelimiter
        val firstLine = inputLines[0]
        
        // Eğer kullanıcı delimiter'ı çalışmıyorsa otomatik algıla
        if (!firstLine.contains(csvDelimiter)) {
            
            val delimiters = listOf(",", ";", "\t", "|", ":")
            var bestDelimiter = ","
            var maxColumns = 0
            
            for (delimiter in delimiters) {
                val columns = firstLine.split(delimiter).size
                if (columns > maxColumns && firstLine.contains(delimiter)) {
                    maxColumns = columns
                    bestDelimiter = delimiter
                }
            }
            
            detectedDelimiter = bestDelimiter
        }
        
        // SINGLE ROW SPECIAL HANDLING - Tek satır varsa header yok demektir
        if (inputLines.size == 1) {
            val values = inputLines[0].split(detectedDelimiter).map { it.trim() }.filter { it.isNotBlank() }
            repository.addSongsBulk(
                listId,
                values.map { Song(name = it, artist = "", album = "", trackNumber = 0, listId = listId) }
            )
            return
        }
        
        // MULTI ROW PARSING - Headers + Data
        val headers = inputLines[0].split(detectedDelimiter).map { it.trim() }
        val dataRows = inputLines.drop(1)
        
        
        // Process each data row
        val songsToAdd = mutableListOf<Song>()
        dataRows.forEachIndexed { rowIndex, row ->
            try {
                val values = row.split(detectedDelimiter).map { it.trim() }

                if (values.isNotEmpty() && values[0].isNotBlank()) {
                    val songName = values[0]
                    
                    // Create comprehensive CSV data map
                    val csvDataMap = mutableMapOf<String, String>()
                    for (i in headers.indices) {
                        if (i < values.size && values[i].isNotBlank()) {
                            csvDataMap[headers[i]] = values[i]
                        }
                    }
                    
                    // Smart JSON creation
                    val csvDataJson = if (csvDataMap.size > 1 || displayMode == "table") {
                        csvDataMap["_displayMode"] = displayMode
                        val jsonEntries = csvDataMap.map { 
                            "\"${it.key.replace("\"", "\\\"")}\": \"${it.value.replace("\"", "\\\"")}\""
                        }
                        "{${jsonEntries.joinToString(", ")}}"
                    } else {
                        null
                    }
                    
                    // Smart artist/album extraction
                    val artist = listOf("Artist", "artist", "Sanatçı", "sanatçı", "Sanatci", "ARTIST")
                        .firstNotNullOfOrNull { csvDataMap[it] } ?: ""
                    val album = listOf("Album", "album", "Albüm", "albüm", "ALBUM")
                        .firstNotNullOfOrNull { csvDataMap[it] } ?: ""

                    songsToAdd.add(Song(name = songName, artist = artist, album = album, trackNumber = 0, listId = listId, csvData = csvDataJson))
                }
            } catch (e: Exception) {
                // Continue processing other rows
            }
        }

        // Tüm satırlar tek transaction'da eklenir (N+1 önlemi)
        repository.addSongsBulk(listId, songsToAdd)
    }
}