package com.example.ranking.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import java.io.ByteArrayInputStream

class CsvReader {
    
    data class CsvSong(
        val trackNumber: Int = 0,
        val artist: String = "",
        val album: String = "",
        val name: String,
        val csvData: String? = null // JSON formatted tabular data
    )
    
    suspend fun readCsvFromUri(context: Context, uri: Uri): List<CsvSong> {
        val songs = mutableListOf<CsvSong>()
        var csvHeaders: List<String>? = null
        
        try {
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                
                // Read all bytes first to detect encoding and BOM
                val allBytes = inputStream.readBytes()
                
                // Detect and remove BOM if present
                val (cleanBytes, detectedCharset) = detectEncodingAndRemoveBOM(allBytes)
                
                // Create reader with detected charset
                val reader = BufferedReader(
                    InputStreamReader(ByteArrayInputStream(cleanBytes), detectedCharset)
                )
                
                reader.use { bufferedReader ->
                    var line: String?
                    var isFirstLine = true
                    var lineNumber = 0
                    
                    while (bufferedReader.readLine().also { line = it } != null) {
                        lineNumber++
                        line?.let { currentLine ->
                            
                            val normalizedLine = currentLine.trim().normalize()
                            
                            // Check if this is a header line - ALWAYS assume first line is header if it has separators
                            if (isFirstLine) {
                                if (currentLine.contains(",") || currentLine.contains(";") || currentLine.contains("\t")) {
                                    // First line has separators - assume it's a header
                                    csvHeaders = parseHeaderLine(normalizedLine)
                                    isFirstLine = false
                                    return@let
                                }
                            }
                            isFirstLine = false
                            
                            
                            val song = parseCsvLineWithHeaders(normalizedLine, csvHeaders)
                            if (song.name.isNotBlank()) {
                                songs.add(song)
                            } else {
                            }
                        }
                    }
                }
            } ?: throw Exception("Dosya açılamadı. Dosya erişim izni olmayabilir.")
            
            
        } catch (e: Exception) {
            throw Exception("CSV dosyası okunamadı: ${e.message}")
        }
        
        return songs
    }
    
    private fun parseHeaderLine(line: String): List<String> {
        if (line.isBlank()) return emptyList()
        
        // Handle different CSV separators (comma, semicolon, tab)
        val separator = when {
            line.contains(";") && line.count { it == ';' } > line.count { it == ',' } -> ";"
            line.contains("\t") -> "\t"
            else -> ","
        }
        
        // Split and clean header parts
        return line.split(separator).map { header ->
            header.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .trim()
                .normalize()
        }
    }
    
    private fun parseCsvLineWithHeaders(line: String, headers: List<String>?): CsvSong {
        if (line.isBlank()) return CsvSong(name = "")
        
        // Handle different CSV separators (comma, semicolon, tab)
        val separator = when {
            line.contains(";") && line.count { it == ';' } > line.count { it == ',' } -> ";"
            line.contains("\t") -> "\t"
            else -> ","
        }
        
        // Split and clean parts
        val parts = line.split(separator).map { part ->
            part.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .trim()
                .normalize()
        }
        
        
        // Create JSON data from headers and values
        val csvData = if (headers != null && headers.isNotEmpty() && parts.size >= headers.size) {
            val dataMap = mutableMapOf<String, String>()
            for (i in headers.indices) {
                if (i < parts.size) {
                    dataMap[headers[i]] = parts[i]
                }
            }
            // Convert to JSON string (simple manual JSON creation for this case)
            val jsonEntries = dataMap.map { "\"${it.key}\": \"${it.value.replace("\"", "\\\"")}\""  }
            "{${jsonEntries.joinToString(", ")}}"
        } else {
            null
        }
        
        
        // Use existing logic for parsing basic fields
        val baseSong = parseCsvLine(line)
        
        // Return CsvSong with CSV data
        return CsvSong(
            trackNumber = baseSong.trackNumber,
            artist = baseSong.artist,
            album = baseSong.album,
            name = baseSong.name,
            csvData = csvData
        )
    }
    
    private fun parseCsvLine(line: String): CsvSong {
        if (line.isBlank()) return CsvSong(name = "")
        
        // Handle different CSV separators (comma, semicolon, tab)
        val separator = when {
            line.contains(";") && line.count { it == ';' } > line.count { it == ',' } -> ";"
            line.contains("\t") -> "\t"
            else -> ","
        }
        
        
        // Split and clean parts
        val parts = line.split(separator).map { part ->
            part.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .trim()
                .normalize()
        }
        
        
        return when {
            parts.size >= 4 -> {
                // Four columns: A=numara, B=sanatçı, C=albüm, D=öğe
                val trackNumber = parts[0].toIntOrNull() ?: 0
                val artist = parts[1]
                val album = parts[2]
                val songName = parts[3]
                CsvSong(
                    trackNumber = trackNumber,
                    artist = artist,
                    album = album,
                    name = songName
                )
            }
            parts.size == 3 -> {
                // Three columns: assume sanatçı, albüm, öğe (no track number)
                val artist = parts[0]
                val album = parts[1]
                val songName = parts[2]
                CsvSong(
                    trackNumber = 0,
                    artist = artist,
                    album = album,
                    name = songName
                )
            }
            parts.size == 2 -> {
                // Two columns: assume sanatçı, öğe
                val artist = parts[0]
                val songName = parts[1]
                CsvSong(
                    trackNumber = 0,
                    artist = artist,
                    album = "",
                    name = songName
                )
            }
            parts.size == 1 -> {
                // Single column, check if it contains " - " separator
                val singleValue = parts[0]
                if (singleValue.contains(" - ")) {
                    val songParts = singleValue.split(" - ", limit = 2)
                    CsvSong(
                        trackNumber = 0,
                        artist = songParts[0].trim(),
                        album = "",
                        name = songParts[1].trim()
                    )
                } else {
                    CsvSong(
                        trackNumber = 0,
                        artist = "",
                        album = "",
                        name = singleValue
                    )
                }
            }
            else -> CsvSong(name = "")
        }
    }
    
    // Detect encoding and remove BOM if present
    private fun detectEncodingAndRemoveBOM(bytes: ByteArray): Pair<ByteArray, Charset> {
        // Check for UTF-8 BOM (EF BB BF)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Pair(bytes.sliceArray(3 until bytes.size), StandardCharsets.UTF_8)
        }
        
        // Check for UTF-16 BE BOM (FE FF)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Pair(bytes.sliceArray(2 until bytes.size), StandardCharsets.UTF_16BE)
        }
        
        // Check for UTF-16 LE BOM (FF FE)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Pair(bytes.sliceArray(2 until bytes.size), StandardCharsets.UTF_16LE)
        }
        
        // Try to detect Turkish characters to determine if we need Windows-1254
        val sampleText = String(bytes.take(1024).toByteArray(), StandardCharsets.UTF_8)
        
        // Also try with Windows-1254 for comparison
        try {
            val sampleWindows1254 = String(bytes.take(1024).toByteArray(), Charset.forName("windows-1254"))
        } catch (e: Exception) {
        }
        
        // If we see Turkish characters or common Turkish words, assume it's UTF-8
        if (sampleText.contains(Regex("[çğıöşüÇĞIÖŞÜ]")) || 
            sampleText.lowercase().contains(Regex("\\b(sanatçı|şarkı|albüm|öğe)\\b"))) {
            return Pair(bytes, StandardCharsets.UTF_8)
        }
        
        // Try Windows-1254 for Turkish files that might be encoded in that format
        try {
            val windows1254Text = String(bytes.take(1024).toByteArray(), Charset.forName("windows-1254"))
            if (windows1254Text.contains(Regex("[çğıöşüÇĞIÖŞÜ]"))) {
                return Pair(bytes, Charset.forName("windows-1254"))
            }
        } catch (e: Exception) {
        }
        
        // Default to UTF-8
        return Pair(bytes, StandardCharsets.UTF_8)
    }
    
    // Extension function for Unicode normalization to handle Turkish characters properly
    private fun String.normalize(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFC)
        
        // Additional cleanup for Turkish characters that might be corrupted
        return normalized
            .replace("Ä±", "ı")      // Common corruption: Ä± -> ı
            .replace("Å\u009F", "ş") // Common corruption: Åž -> ş  
            .replace("Ä\u009F", "ğ") // Common corruption: Äž -> ğ
            .replace("Ã§", "ç")      // Common corruption: Ã§ -> ç
            .replace("Ã¼", "ü")      // Common corruption: Ã¼ -> ü
            .replace("Ã¶", "ö")      // Common corruption: Ã¶ -> ö
            .replace("Ä°", "İ")      // Common corruption: Ä° -> İ
            .replace("Å\u009E", "Ş") // Common corruption: Åž -> Ş
            .replace("Ä\u009E", "Ğ") // Common corruption: Äž -> Ğ
            .replace("Ã\u0087", "Ç") // Common corruption: Ã‡ -> Ç
            .replace("Ãœ", "Ü")      // Common corruption: Ãœ -> Ü
            .replace("Ã\u0096", "Ö") // Common corruption: Ã– -> Ö
            // Additional double-encoding fixes
            .replace("\u00E2\u0080\u0099", "'")     // Smart apostrophe
            .replace("\u00E2\u0080\u009C", "\"")    // Smart quote start  
            .replace("\u00E2\u0080\u009D", "\"")    // Smart quote end
            .replace("\u00E2\u0080\u0094", "-")     // Em dash
            // Fix common encoding issues for Turkish characters
            .replace("\u00C3\u0083\u00C2\u00A7", "ç")
            .replace("\u00C3\u0083\u00C2\u00BC", "ü")
            .replace("\u00C3\u0083\u00C2\u00B6", "ö")
    }
}