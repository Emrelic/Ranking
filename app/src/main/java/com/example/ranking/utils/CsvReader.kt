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
        val name: String
    )
    
    suspend fun readCsvFromUri(context: Context, uri: Uri): List<CsvSong> {
        val songs = mutableListOf<CsvSong>()
        
        try {
            Log.d("CsvReader", "CSV dosyası açılıyor: $uri")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                Log.d("CsvReader", "Input stream başarıyla açıldı")
                
                // Read all bytes first to detect encoding and BOM
                val allBytes = inputStream.readBytes()
                Log.d("CsvReader", "Dosya boyutu: ${allBytes.size} bytes")
                
                // Detect and remove BOM if present
                val (cleanBytes, detectedCharset) = detectEncodingAndRemoveBOM(allBytes)
                Log.d("CsvReader", "Tespit edilen encoding: ${detectedCharset.name()}")
                
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
                            Log.d("CsvReader", "Satır $lineNumber (raw): $currentLine")
                            
                            // Log the raw bytes of this line for debugging
                            val lineBytes = currentLine.toByteArray(StandardCharsets.UTF_8)
                            Log.d("CsvReader", "Satır bytes: ${lineBytes.joinToString(" ") { "%02X".format(it) }}")
                            
                            // Skip header if it exists
                            if (isFirstLine && (currentLine.lowercase().contains("öğe") || 
                                              currentLine.lowercase().contains("şarkı") || 
                                              currentLine.lowercase().contains("song") ||
                                              currentLine.lowercase().contains("sanatçı") ||
                                              currentLine.lowercase().contains("artist") ||
                                              currentLine.lowercase().contains("albüm") ||
                                              currentLine.lowercase().contains("album") ||
                                              currentLine.lowercase().contains("numara"))) {
                                Log.d("CsvReader", "Header satırı atlandı: $currentLine")
                                isFirstLine = false
                                return@let
                            }
                            isFirstLine = false
                            
                            val normalizedLine = currentLine.trim().normalize()
                            Log.d("CsvReader", "Normalize edilmiş satır: $normalizedLine")
                            
                            val song = parseCsvLine(normalizedLine)
                            if (song.name.isNotBlank()) {
                                songs.add(song)
                                Log.d("CsvReader", "Öğe eklendi: ${song.name} - ${song.artist}")
                            } else {
                                Log.w("CsvReader", "Boş öğe adı, satır atlandı: $currentLine")
                            }
                        }
                    }
                }
            } ?: throw Exception("Dosya açılamadı. Dosya erişim izni olmayabilir.")
            
            Log.d("CsvReader", "Toplam ${songs.size} öğe okundu")
            
        } catch (e: Exception) {
            Log.e("CsvReader", "CSV okuma hatası: ${e.message}", e)
            throw Exception("CSV dosyası okunamadı: ${e.message}")
        }
        
        return songs
    }
    
    private fun parseCsvLine(line: String): CsvSong {
        if (line.isBlank()) return CsvSong(name = "")
        
        // Handle different CSV separators (comma, semicolon, tab)
        val separator = when {
            line.contains(";") && line.count { it == ';' } > line.count { it == ',' } -> ";"
            line.contains("\t") -> "\t"
            else -> ","
        }
        
        Log.d("CsvReader", "Kullanılan ayraç: '$separator'")
        
        // Split and clean parts
        val parts = line.split(separator).map { part ->
            part.trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
                .trim()
                .normalize()
        }
        
        Log.d("CsvReader", "Ayrıştırılan parçalar (${parts.size}): ${parts.joinToString(" | ")}")
        
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
            Log.d("CsvReader", "UTF-8 BOM tespit edildi, kaldırılıyor")
            return Pair(bytes.sliceArray(3 until bytes.size), StandardCharsets.UTF_8)
        }
        
        // Check for UTF-16 BE BOM (FE FF)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            Log.d("CsvReader", "UTF-16 BE BOM tespit edildi")
            return Pair(bytes.sliceArray(2 until bytes.size), StandardCharsets.UTF_16BE)
        }
        
        // Check for UTF-16 LE BOM (FF FE)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            Log.d("CsvReader", "UTF-16 LE BOM tespit edildi")
            return Pair(bytes.sliceArray(2 until bytes.size), StandardCharsets.UTF_16LE)
        }
        
        // Try to detect Turkish characters to determine if we need Windows-1254
        val sampleText = String(bytes.take(1024).toByteArray(), StandardCharsets.UTF_8)
        Log.d("CsvReader", "UTF-8 ile ilk 100 karakter: ${sampleText.take(100)}")
        
        // Also try with Windows-1254 for comparison
        try {
            val sampleWindows1254 = String(bytes.take(1024).toByteArray(), Charset.forName("windows-1254"))
            Log.d("CsvReader", "Windows-1254 ile ilk 100 karakter: ${sampleWindows1254.take(100)}")
        } catch (e: Exception) {
            Log.w("CsvReader", "Windows-1254 sample test hatası: ${e.message}")
        }
        
        // If we see Turkish characters or common Turkish words, assume it's UTF-8
        if (sampleText.contains(Regex("[çğıöşüÇĞIÖŞÜ]")) || 
            sampleText.lowercase().contains(Regex("\\b(sanatçı|şarkı|albüm|öğe)\\b"))) {
            Log.d("CsvReader", "Türkçe karakterler tespit edildi, UTF-8 kullanılıyor")
            return Pair(bytes, StandardCharsets.UTF_8)
        }
        
        // Try Windows-1254 for Turkish files that might be encoded in that format
        try {
            val windows1254Text = String(bytes.take(1024).toByteArray(), Charset.forName("windows-1254"))
            if (windows1254Text.contains(Regex("[çğıöşüÇĞIÖŞÜ]"))) {
                Log.d("CsvReader", "Windows-1254 encoding tespit edildi")
                return Pair(bytes, Charset.forName("windows-1254"))
            }
        } catch (e: Exception) {
            Log.w("CsvReader", "Windows-1254 encoding test hatası: ${e.message}")
        }
        
        // Default to UTF-8
        Log.d("CsvReader", "Varsayılan UTF-8 encoding kullanılıyor")
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