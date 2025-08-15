package com.example.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.Match
import com.example.ranking.ranking.EmreSystem
import org.junit.Test
import org.junit.Assert.*

/**
 * Gelişmiş Emre Usulü Test Sınıfı
 */
class EmreSystemTest {
    
    private fun createTestSongs(count: Int): List<Song> {
        return (1..count).map { i ->
            Song(
                id = i.toLong(),
                name = "Song$i",
                artist = "Artist$i",
                album = "Album$i",
                trackNumber = i,
                listId = 1L
            )
        }
    }
    
    private fun simulateMatchResult(match: Match, team1WinProbability: Double = 0.5): Match {
        val random = Math.random()
        val winnerId = when {
            random < team1WinProbability -> match.songId1
            random < team1WinProbability + 0.4 -> match.songId2
            else -> null // Beraberlik
        }
        return match.copy(winnerId = winnerId)
    }
    
    @Test
    fun testBasicInitialization() {
        val songs = createTestSongs(8)
        val state = EmreSystem.initializeEmreTournament(songs)
        
        assertEquals(8, state.teams.size)
        assertEquals(1, state.currentRound)
        assertFalse(state.isComplete)
        
        // Tüm takımlar 0 puanla başlamalı
        state.teams.forEach { team ->
            assertEquals(0.0, team.points, 0.001)
            assertFalse(team.byePassed)
        }
    }
    
    @Test
    fun testByeHandling() {
        // Tek sayıda takım testi
        val songs = createTestSongs(9)
        val state = EmreSystem.initializeEmreTournament(songs)
        
        val pairingResult = EmreSystem.createNextRound(state)
        
        assertNotNull(pairingResult.byeTeam)
        assertEquals(4, pairingResult.matches.size) // 8 takım / 2 = 4 maç
        
        // Çift sayıda takım testi
        val evenSongs = createTestSongs(8)
        val evenState = EmreSystem.initializeEmreTournament(evenSongs)
        
        val evenPairingResult = EmreSystem.createNextRound(evenState)
        
        assertNull(evenPairingResult.byeTeam)
        assertEquals(4, evenPairingResult.matches.size) // 8 takım / 2 = 4 maç
    }
    
    @Test
    fun testFirstRoundPairings() {
        val songs = createTestSongs(8)
        val state = EmreSystem.initializeEmreTournament(songs)
        
        val pairingResult = EmreSystem.createNextRound(state)
        
        assertEquals(4, pairingResult.matches.size)
        assertTrue(pairingResult.canContinue)
        
        // İlk turda herkes 0 puanda, sıralı eşleşme olmalı
        val allTeamIds = mutableSetOf<Long>()
        pairingResult.matches.forEach { match ->
            allTeamIds.add(match.songId1)
            allTeamIds.add(match.songId2)
        }
        
        assertEquals(8, allTeamIds.size) // Tüm takımlar eşleşmiş olmalı
    }
    
    @Test
    fun testPointsCalculation() {
        val songs = createTestSongs(4)
        var state = EmreSystem.initializeEmreTournament(songs)
        
        // İlk tur
        val pairingResult = EmreSystem.createNextRound(state)
        assertEquals(2, pairingResult.matches.size)
        
        // Sonuçları simüle et: Team1 beats Team2, Team3 beats Team4
        val simulatedMatches = listOf(
            pairingResult.matches[0].copy(winnerId = pairingResult.matches[0].songId1), // İlk takım kazanır
            pairingResult.matches[1].copy(winnerId = pairingResult.matches[1].songId1)  // İlk takım kazanır
        )
        
        // Sonuçları işle
        state = EmreSystem.processRoundResults(state, simulatedMatches)
        
        // Puanları kontrol et
        val winners = state.teams.filter { it.points == 1.0 }
        val losers = state.teams.filter { it.points == 0.0 }
        
        assertEquals(2, winners.size)
        assertEquals(2, losers.size)
        assertEquals(2, state.currentRound)
    }
    
    @Test
    fun testDrawResult() {
        val songs = createTestSongs(2)
        var state = EmreSystem.initializeEmreTournament(songs)
        
        val pairingResult = EmreSystem.createNextRound(state)
        assertEquals(1, pairingResult.matches.size)
        
        // Beraberlik simüle et
        val drawMatch = pairingResult.matches[0].copy(winnerId = null)
        state = EmreSystem.processRoundResults(state, listOf(drawMatch))
        
        // Her iki takım da 0.5 puan almalı
        state.teams.forEach { team ->
            assertEquals(0.5, team.points, 0.001)
        }
    }
    
    @Test
    fun testTournamentCompletion() {
        val songs = createTestSongs(4)
        var state = EmreSystem.initializeEmreTournament(songs)
        var roundCount = 0
        val maxRounds = 10
        
        while (!state.isComplete && roundCount < maxRounds) {
            val pairingResult = EmreSystem.createNextRound(state)
            
            if (!pairingResult.canContinue) {
                break
            }
            
            // Rastgele sonuçlar
            val simulatedMatches = pairingResult.matches.map { match ->
                simulateMatchResult(match)
            }
            
            state = EmreSystem.processRoundResults(state, simulatedMatches, pairingResult.byeTeam)
            roundCount++
        }
        
        assertTrue("Turnuva $maxRounds tur içinde bitmeli", roundCount < maxRounds)
        
        // Final sonuçları
        val finalResults = EmreSystem.calculateFinalResults(state)
        assertEquals(4, finalResults.size)
        
        // Sonuçlar sıralı olmalı
        for (i in 0 until finalResults.size - 1) {
            assertTrue(
                "Sıralama yanlış: ${finalResults[i].position} >= ${finalResults[i + 1].position}", 
                finalResults[i].position < finalResults[i + 1].position
            )
        }
    }
    
    @Test
    fun testMatchHistoryTracking() {
        val songs = createTestSongs(4)
        var state = EmreSystem.initializeEmreTournament(songs)
        
        // İlk tur
        val firstRound = EmreSystem.createNextRound(state)
        val simulatedFirstRound = firstRound.matches.map { match ->
            simulateMatchResult(match)
        }
        
        state = EmreSystem.processRoundResults(state, simulatedFirstRound)
        
        // İkinci tur - aynı eşleşmeler tekrar olmamamalı
        val secondRound = EmreSystem.createNextRound(state)
        
        // İlk tur eşleşmeleri
        val firstRoundPairs = firstRound.matches.map { 
            setOf(it.songId1, it.songId2) 
        }.toSet()
        
        // İkinci tur eşleşmeleri
        val secondRoundPairs = secondRound.matches.map { 
            setOf(it.songId1, it.songId2) 
        }.toSet()
        
        // Hiçbir eşleşme tekrar etmemeli
        assertTrue("Aynı eşleşmeler tekrar oluşturulmamalı", 
            firstRoundPairs.intersect(secondRoundPairs).isEmpty())
    }
    
    @Test
    fun testLargeTournament() {
        val songs = createTestSongs(16)
        var state = EmreSystem.initializeEmreTournament(songs)
        var roundCount = 0
        val maxRounds = 20
        
        while (!state.isComplete && roundCount < maxRounds) {
            val pairingResult = EmreSystem.createNextRound(state)
            
            if (!pairingResult.canContinue) {
                break
            }
            
            assertTrue("Her turda en az 1 maç olmalı", pairingResult.matches.isNotEmpty())
            
            val simulatedMatches = pairingResult.matches.map { match ->
                simulateMatchResult(match)
            }
            
            state = EmreSystem.processRoundResults(state, simulatedMatches, pairingResult.byeTeam)
            roundCount++
        }
        
        println("16 takımlı turnuva $roundCount turda bitti")
        assertTrue("16 takımlı turnuva $maxRounds tur içinde bitmeli", roundCount < maxRounds)
        
        val finalResults = EmreSystem.calculateFinalResults(state)
        assertEquals(16, finalResults.size)
    }
}