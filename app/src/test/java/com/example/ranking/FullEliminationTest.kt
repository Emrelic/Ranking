package com.example.ranking

import com.example.ranking.ranking.RankingEngine
import com.example.ranking.data.Song
import org.junit.Test
import org.junit.Assert.*

class FullEliminationTest {

    @Test
    fun testFullEliminationWith79Teams() {
        // 79 takım oluştur
        val songs = (1..79).map { i ->
            Song(
                id = i.toLong(),
                name = "Takım $i", 
                artist = "Artist",
                album = "Album",
                trackNumber = i,
                listId = 1L
            )
        }
        
        // İlk tur maçları oluştur
        val firstRoundMatches = RankingEngine.createFullEliminationMatches(songs)
        
        // Sonuç kontrolleri
        assertNotNull(firstRoundMatches)
        assertTrue("Maç sayısı 0'dan büyük olmalı", firstRoundMatches.size > 0)
        
        // 79 takım için: 38 ikili + 1 üçlü = 38*2 + 3*3 = 76+3 = 79 takım
        // Maç sayısı: 38 + 3 = 41 maç olmalı
        val expectedMatches = 38 + 3  // 38 ikili maç + 3 üçlü maç
        assertEquals("79 takım için $expectedMatches maç olmalı", expectedMatches, firstRoundMatches.size)
        
        println("✅ 79 takım testi başarılı!")
        println("Toplam maç sayısı: ${firstRoundMatches.size}")
    }
    
    @Test
    fun testFullEliminationWith64Teams() {
        // 64 takım oluştur (2'nin üssü)
        val songs = (1..64).map { i ->
            Song(
                id = i.toLong(),
                name = "Takım $i", 
                artist = "Artist",
                album = "Album", 
                trackNumber = i,
                listId = 1L
            )
        }
        
        // 64 takım direkt eleme olmalı
        val matches = RankingEngine.createFullEliminationMatches(songs)
        
        assertNotNull(matches)
        assertTrue("64 takım için maçlar oluşturulmalı", matches.size > 0)
        
        // 64 takım: 32 maç (ilk tur)
        assertEquals("64 takım için 32 maç olmalı", 32, matches.size)
        
        println("✅ 64 takım testi başarılı!")
        println("Toplam maç sayısı: ${matches.size}")
    }
    
    @Test 
    fun testPowerOfTwoCheck() {
        assertTrue("2 ikilik üssü olmalı", RankingEngine.run { isPowerOfTwo(2) })
        assertTrue("4 ikilik üssü olmalı", RankingEngine.run { isPowerOfTwo(4) })
        assertTrue("8 ikilik üssü olmalı", RankingEngine.run { isPowerOfTwo(8) })
        assertTrue("64 ikilik üssü olmalı", RankingEngine.run { isPowerOfTwo(64) })
        
        assertFalse("79 ikilik üssü olmamalı", RankingEngine.run { isPowerOfTwo(79) })
        assertFalse("100 ikilik üssü olmamalı", RankingEngine.run { isPowerOfTwo(100) })
        
        println("✅ 2'nin üssü kontrol testi başarılı!")
    }
}