package com.example.ranking

import com.example.ranking.utils.CsvReader
import org.junit.Assert.*
import org.junit.Test

/**
 * RFC-4180 CSV parser testleri — tırnaklı alanlar, kaçış, çok satırlı hücreler.
 */
class CsvReaderTest {

    private val reader = CsvReader()

    @Test
    fun testSimpleCommaSeparated() {
        val songs = reader.parseText("No,Sanatçı,Albüm,Şarkı\n1,Sezen Aksu,Gülümse,Firuze\n2,MFÖ,Ele Güne Karşı,Diday Diday Day")
        assertEquals(2, songs.size)
        assertEquals("Firuze", songs[0].name)
        assertEquals("Sezen Aksu", songs[0].artist)
        assertEquals(1, songs[0].trackNumber)
        assertEquals("Diday Diday Day", songs[1].name)
    }

    @Test
    fun testQuotedFieldWithComma() {
        // KRİTİK: "Beatles, The" içindeki virgül alan ayracı DEĞİL
        val songs = reader.parseText("No,Sanatçı,Albüm,Şarkı\n1,\"Beatles, The\",\"Abbey Road\",Come Together")
        assertEquals(1, songs.size)
        assertEquals("Beatles, The", songs[0].artist)
        assertEquals("Abbey Road", songs[0].album)
        assertEquals("Come Together", songs[0].name)
    }

    @Test
    fun testEscapedQuotes() {
        val songs = reader.parseText("Sanatçı,Şarkı\nQueen,\"A Kind of \"\"Magic\"\"\"")
        assertEquals(1, songs.size)
        assertEquals("A Kind of \"Magic\"", songs[0].name)
    }

    @Test
    fun testMultilineQuotedField() {
        val songs = reader.parseText("Sanatçı,Şarkı\n\"Pink\nFloyd\",Time")
        assertEquals(1, songs.size)
        assertEquals("Pink\nFloyd", songs[0].artist)
        assertEquals("Time", songs[0].name)
    }

    @Test
    fun testSemicolonSeparator() {
        val songs = reader.parseText("Sanatçı;Şarkı\nTarkan;Şımarık\nBarış Manço;Gülpembe")
        assertEquals(2, songs.size)
        assertEquals("Şımarık", songs[0].name)
        assertEquals("Barış Manço", songs[1].artist)
    }

    @Test
    fun testSingleColumnNoHeader() {
        // Tek sütunlu dosya: başlık yok sayılır, her satır bir öğe
        val songs = reader.parseText("Elma\nArmut\nKiraz")
        assertEquals(3, songs.size)
        assertEquals("Elma", songs[0].name)
        assertEquals("Kiraz", songs[2].name)
    }

    @Test
    fun testSingleColumnWithDashPattern() {
        val songs = reader.parseText("Sezen Aksu - Firuze\nTarkan - Kuzu Kuzu")
        assertEquals(2, songs.size)
        assertEquals("Sezen Aksu", songs[0].artist)
        assertEquals("Firuze", songs[0].name)
    }

    @Test
    fun testCsvDataJsonIsValid() {
        val songs = reader.parseText("No,Sanatçı,Şarkı\n1,\"AC\\DC \"\"Band\"\"\",Thunderstruck")
        assertEquals(1, songs.size)
        // Ters eğik çizgi ve tırnak içeren değer geçerli JSON üretmeli
        val json = org.json.JSONObject(songs[0].csvData!!)
        assertEquals("AC\\DC \"Band\"", json.getString("Sanatçı"))
    }

    @Test
    fun testCrLfLineEndings() {
        val songs = reader.parseText("Sanatçı,Şarkı\r\nDuman,Senden Daha Güzel\r\n")
        assertEquals(1, songs.size)
        assertEquals("Senden Daha Güzel", songs[0].name)
    }

    @Test
    fun testEmptyAndBlankInput() {
        assertTrue(reader.parseText("").isEmpty())
        assertTrue(reader.parseText("   \n  \n").isEmpty())
    }
}
