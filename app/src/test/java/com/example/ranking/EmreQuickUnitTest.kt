package com.example.ranking

import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import org.junit.Test
import org.junit.Assert.*

/**
 * Emre System Correct Unit Test
 */
class EmreQuickUnitTest {
    
    @Test
    fun testEmreSystemCorrectBasic() {
        println("🧪 EMRE USULÜ SİSTEM TESTİ (UNIT TEST)")
        
        // 6 takım oluştur
        val songs = listOf(
            Song(1L, "Team1", "", "", 1, 1L),
            Song(2L, "Team2", "", "", 2, 1L),
            Song(3L, "Team3", "", "", 3, 1L),
            Song(4L, "Team4", "", "", 4, 1L),
            Song(5L, "Team5", "", "", 5, 1L),
            Song(6L, "Team6", "", "", 6, 1L)
        )
        
        // Test 1: Sistem başlatma
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        assertEquals("Takım sayısı 6 olmalı", 6, state.teams.size)
        assertEquals("İlk tur 1 olmalı", 1, state.currentRound)
        
        // Test 2: İlk tur eşleştirmeler
        val round1 = EmreSystemCorrect.createNextRound(state)
        assertEquals("İlk turda 3 maç olmalı", 3, round1.matches.size)
        assertTrue("İlk turda aynı puanlı eşleşme olmalı", round1.hasSamePointMatch)
        assertTrue("Turnuva devam edebilmeli", round1.canContinue)
        
        // Test 3: 1-2 eşleştirmesi kontrolü
        val firstMatch = round1.matches.find { 
            (it.songId1 == 1L && it.songId2 == 2L) || (it.songId1 == 2L && it.songId2 == 1L)
        }
        assertNotNull("1-2 eşleştirmesi olmalı", firstMatch)
        
        // Test 4: İlk tur sonuçları (Team1, Team4, Team5 kazansın)
        val round1Results = round1.matches.mapIndexed { i, match ->
            val winnerId = when(i) {
                0 -> if (match.songId1 == 1L || match.songId1 == 2L) 1L else match.songId1 // Team1 kazansın
                1 -> if (match.songId1 == 3L || match.songId1 == 4L) 4L else match.songId2 // Team4 kazansın
                2 -> if (match.songId1 == 5L || match.songId1 == 6L) 5L else match.songId1 // Team5 kazansın
                else -> match.songId1
            }
            match.copy(winnerId = winnerId, isCompleted = true)
        }
        
        // Test 5: Sonuçları işleme
        state = EmreSystemCorrect.processRoundResults(state, round1Results, round1.byeTeam)
        assertEquals("2. tur olmalı", 2, state.currentRound)
        
        // Kazananların puanı 1 olmalı
        val team1Points = state.teams.find { it.id == 1L }?.points
        val team4Points = state.teams.find { it.id == 4L }?.points  
        val team5Points = state.teams.find { it.id == 5L }?.points
        assertEquals("Team1 puanı 1 olmalı", 1.0, team1Points!!, 0.01)
        assertEquals("Team4 puanı 1 olmalı", 1.0, team4Points!!, 0.01)
        assertEquals("Team5 puanı 1 olmalı", 1.0, team5Points!!, 0.01)
        
        // Kaybedenlerin puanı 0 olmalı
        val team2Points = state.teams.find { it.id == 2L }?.points
        val team3Points = state.teams.find { it.id == 3L }?.points
        val team6Points = state.teams.find { it.id == 6L }?.points
        assertEquals("Team2 puanı 0 olmalı", 0.0, team2Points!!, 0.01)
        assertEquals("Team3 puanı 0 olmalı", 0.0, team3Points!!, 0.01) 
        assertEquals("Team6 puanı 0 olmalı", 0.0, team6Points!!, 0.01)
        
        // Test 6: İkinci tur
        val round2 = EmreSystemCorrect.createNextRound(state)
        assertTrue("İkinci turda eşleşme olmalı", round2.matches.isNotEmpty())
        
        // Test 7: Final sonuçlar
        val finalResults = EmreSystemCorrect.calculateFinalResults(state)
        assertEquals("6 sonuç olmalı", 6, finalResults.size)
        
        val positions = finalResults.map { it.position }.sorted()
        assertEquals("Pozisyonlar 1-6 arası olmalı", (1..6).toList(), positions)
        
        println("✅ Tüm testler BAŞARILI!")
    }
}