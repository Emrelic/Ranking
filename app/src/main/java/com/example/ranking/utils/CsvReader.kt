package com.example.ranking.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.Normalizer

/**
 * RFC-4180 uyumlu CSV okuyucu.
 * - Tırnaklı alanlar içindeki ayraçları ve satır sonlarını doğru işler
 *   ("Beatles, The" tek alan olarak kalır)
 * - "" ile kaçırılmış tırnakları çözer
 * - Ayraç tespiti (virgül / noktalı virgül / tab) tırnak DIŞI sayıma göre yapılır
 * - BOM ve Türkçe encoding (UTF-8 / windows-1254) tespiti korunmuştur
 */
class CsvReader {

    data class CsvSong(
        val trackNumber: Int = 0,
        val artist: String = "",
        val album: String = "",
        val name: String,
        val csvData: String? = null // JSON formatted tabular data
    )

    suspend fun readCsvFromUri(context: Context, uri: Uri): List<CsvSong> = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                bytesToText(inputStream.readBytes())
            } ?: throw Exception("Dosya açılamadı. Dosya erişim izni olmayabilir.")
        } catch (e: Exception) {
            throw Exception("CSV dosyası okunamadı: ${e.message}", e)
        }

        parseText(text)
    }

    /** Test edilebilirlik için ayrık: tam metni CsvSong listesine çevirir. */
    fun parseText(rawText: String): List<CsvSong> {
        if (rawText.isBlank()) return emptyList()

        // Baştaki BOM'u burada temizle. readCsvFromUri BOM'u BAYT düzeyinde
        // siliyor, ama gömülü hazır listeler RankingRepository içinde
        // `readBytes().toString(UTF_8)` ile okunuyor ve o yolda BOM kalıyordu:
        // BOM ilk başlık anahtarına yapışıp "No" yerine "﻿No" üretiyor,
        // tablo görünümünde ilk sütun kayboluyordu.
        val text = rawText.removePrefix("﻿")

        val separator = detectSeparator(text)
        val rows = parseRows(text, separator)
            .map { row -> row.map { it.trim().normalize() } }
            .filter { row -> row.any { it.isNotBlank() } }

        if (rows.isEmpty()) return emptyList()

        // Baslik tespiti.
        //
        // Eskiden ilk satir KOSULSUZ baslik sayiliyordu: baslıksiz cok sutunlu
        // bir dosyanin ilk ogesi sessizce yutuluyordu (50 satirlik dosyadan 49
        // oge geliyor, hicbir uyari cikmiyordu).
        //
        // Sezgi: baslik satirinin ilk hucresi metindir ("No", "Sira", "#").
        // Veri satirinin ilk hucresi ise sira numarasidir (1, 2, 3...).
        // Dolayisiyla ilk hucre TAM SAYIYSA o satir veridir, baslik degil.
        // 31 gomulu hazir listenin hepsinde ilk hucre "No" — davranislari
        // degismiyor.
        val ilkSatir = rows.first()
        val hasHeader = ilkSatir.size >= 2 && ilkSatir[0].toIntOrNull() == null
        val headers = if (hasHeader) rows.first() else null
        val dataRows = if (hasHeader) rows.drop(1) else rows

        return dataRows.mapNotNull { row ->
            val song = mapRowToSong(row, headers)
            if (song.name.isNotBlank()) song else null
        }
    }

    /** Tırnak dışı ayraç sayımına göre ayraç tespiti (ilk satır üzerinden). */
    private fun detectSeparator(text: String): Char {
        var inQuotes = false
        var commas = 0
        var semicolons = 0
        var tabs = 0
        for (ch in text) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == '\n' && !inQuotes -> break
                !inQuotes -> when (ch) {
                    ',' -> commas++
                    ';' -> semicolons++
                    '\t' -> tabs++
                }
            }
        }
        return when {
            semicolons > commas && semicolons >= tabs -> ';'
            tabs > commas && tabs > semicolons -> '\t'
            else -> ','
        }
    }

    /**
     * RFC-4180 durum makinesi: tırnaklı alanlar, "" kaçışı ve
     * tırnak içi satır sonları desteklenir.
     */
    private fun parseRows(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            currentRow.add(currentField.toString())
            currentField.clear()
        }

        fun endRow() {
            endField()
            rows.add(currentRow.toList())
            currentRow.clear()
        }

        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        currentField.append('"'); i++ // "" -> kaçırılmış tırnak
                    }
                    ch == '"' -> inQuotes = false
                    else -> currentField.append(ch)
                }
                ch == '"' && currentField.isBlank() -> {
                    currentField.clear() // Alan başındaki tırnak: tırnaklı alan başlat
                    inQuotes = true
                }
                ch == separator -> endField()
                ch == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                }
                ch == '\n' -> endRow()
                else -> currentField.append(ch)
            }
            i++
        }
        // Son satır (dosya newline ile bitmiyorsa)
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            endRow()
        }

        return rows
    }

    private fun mapRowToSong(parts: List<String>, headers: List<String>?): CsvSong {
        // Başlık varsa hücreleri JSON olarak sakla (tablo görünümü için)
        val csvData = if (headers != null && headers.isNotEmpty()) {
            val json = JSONObject()
            for (i in headers.indices) {
                if (i < parts.size && headers[i].isNotBlank()) {
                    json.put(headers[i], parts[i])
                }
            }
            if (json.length() > 0) json.toString() else null
        } else {
            null
        }

        return when {
            parts.size >= 4 -> CsvSong(
                // Dört sütun: A=numara, B=sanatçı, C=albüm, D=öğe
                trackNumber = parts[0].toIntOrNull() ?: 0,
                artist = parts[1],
                album = parts[2],
                name = parts[3],
                csvData = csvData
            )
            parts.size == 3 -> CsvSong(
                // Üç sütun: sanatçı, albüm, öğe
                artist = parts[0],
                album = parts[1],
                name = parts[2],
                csvData = csvData
            )
            parts.size == 2 -> CsvSong(
                // İki sütun: sanatçı, öğe
                artist = parts[0],
                name = parts[1],
                csvData = csvData
            )
            parts.size == 1 -> {
                // Tek sütun: "Sanatçı - Öğe" kalıbını dene
                val singleValue = parts[0]
                if (singleValue.contains(" - ")) {
                    val songParts = singleValue.split(" - ", limit = 2)
                    CsvSong(
                        artist = songParts[0].trim(),
                        name = songParts[1].trim(),
                        csvData = csvData
                    )
                } else {
                    CsvSong(name = singleValue, csvData = csvData)
                }
            }
            else -> CsvSong(name = "")
        }
    }

    /**
     * Ham baytlari metne cevirir: BOM tespiti + Turkce encoding fallback.
     *
     * Android'e bagimli degil ve `internal` — JVM birim testinden dogrudan
     * cagrilabilir. Eskiden bu mantik yalnizca `readCsvFromUri(Context, Uri)`
     * icinden erisilebiliyordu, yani windows-1254 fallback'i ve bayt duzeyi
     * BOM tespiti hic test edilemiyordu.
     */
    internal fun bytesToText(bytes: ByteArray): String {
        val (cleanBytes, charset) = detectEncodingAndRemoveBOM(bytes)
        return String(cleanBytes, charset)
    }

    // Detect encoding and remove BOM if present
    private fun detectEncodingAndRemoveBOM(bytes: ByteArray): Pair<ByteArray, Charset> {
        // UTF-8 BOM (EF BB BF)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Pair(bytes.sliceArray(3 until bytes.size), StandardCharsets.UTF_8)
        }
        // UTF-16 BE BOM (FE FF)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Pair(bytes.sliceArray(2 until bytes.size), StandardCharsets.UTF_16BE)
        }
        // UTF-16 LE BOM (FF FE)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Pair(bytes.sliceArray(2 until bytes.size), StandardCharsets.UTF_16LE)
        }

        val sample = bytes.take(4096).toByteArray()

        // UTF-8 olarak geçerli mi? Geçersiz baytlar U+FFFD (replacement) üretir.
        val utf8Text = String(sample, StandardCharsets.UTF_8)
        if (!utf8Text.contains('�')) {
            return Pair(bytes, StandardCharsets.UTF_8)
        }

        // UTF-8 geçersizse Türkçe dosyalar için windows-1254 dene
        return try {
            Pair(bytes, Charset.forName("windows-1254"))
        } catch (e: Exception) {
            Pair(bytes, StandardCharsets.UTF_8)
        }
    }

    // Unicode normalizasyonu + yaygin mojibake (cift kodlama) onarimlari
    private fun String.normalize(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFC)

        return normalized
            .replace("Ä±", "ı")            // A-umlaut+-  -> i (noktasiz)
            .replace("Å", "ş")            // s (sedilli)
            .replace("Ä", "ğ")            // g (yumusak)
            .replace("Ã§", "ç")            // c (sedilli)
            .replace("Ã¼", "ü")            // u (umlaut)
            .replace("Ã¶", "ö")            // o (umlaut)
            .replace("Ä°", "İ")            // I (noktali)
            .replace("Å", "Ş")            // S (sedilli)
            .replace("Ä", "Ğ")            // G (yumusak)
            .replace("Ã", "Ç")            // C (sedilli)
            .replace("Ã", "Ü")            // U (umlaut)
            .replace("Ã", "Ö")            // O (umlaut)
            // Akilli tirnak / tire cift-kodlama onarimlari
            .replace("â", "'")
            .replace("â", "\"")
            .replace("â", "\"")
            .replace("â", "-")
    }
}
