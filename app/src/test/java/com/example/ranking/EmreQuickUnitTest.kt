package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import org.junit.Assert.*
import org.junit.Test

/**
 * Geliştirilmiş İsviçre Sistemi (Emre Usulü) - Canlı hibrit motor testleri
 *
 * Test edilen kritik kurallar:
 * - İki takım birbiriyle en fazla 1 kez eşleşir (kırmızı çizgi)
 * - Her turda tam eşleştirme (çift: n/2 maç, tek: (n-1)/2 maç + 1 bye)
 * - Turnuva her senaryoda sonlanır (sonsuz döngü koruması)
 * - Puanlama: galibiyet 1, beraberlik 0.5, bye 1
 */
class EmreQuickUnitTest {

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Team$i", listId = 1L) }

    /**
     * Deterministik turnuva simülasyonu.
     * winnerPicker her maç için kazanan songId döndürür (null = beraberlik).
     */
    private fun simulateTournament(
        songCount: Int,
        maxRoundsGuard: Int,
        winnerPicker: (Match) -> Long?
    ): Triple<EmreSystemCorrect.EmreState, List<Match>, Int> {
        var state = EmreSystemCorrect.initializeEmreTournament(makeSongs(songCount))
        val allCompleted = mutableListOf<Match>()
        var nextMatchId = 1L
        var rounds = 0

        while (rounds < maxRoundsGuard) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            rounds++
            val completed = pairing.matches.map { m ->
                m.copy(id = nextMatchId++, winnerId = winnerPicker(m), isCompleted = true)
            }
            allCompleted.addAll(completed)
            state = EmreSystemCorrect.processRoundResults(state, completed, pairing.byeTeam)
        }
        return Triple(state, allCompleted, rounds)
    }

    @Test
    fun testInitializationAndFirstRound() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(6))
        assertEquals("Takım sayısı 6 olmalı", 6, state.teams.size)
        assertEquals("İlk tur 1 olmalı", 1, state.currentRound)

        val round1 = EmreSystemCorrect.createHybridPairingSystem(state)
        assertEquals("İlk turda 3 maç olmalı", 3, round1.matches.size)
        assertTrue("İlk tur her zaman oynanmalı", round1.canContinue)
        assertNull("Çift sayıda takımda bye olmamalı", round1.byeTeam)

        // 1-2 komşu eşleştirmesi ilk turda olmalı
        val firstMatch = round1.matches.find {
            (it.songId1 == 1L && it.songId2 == 2L) || (it.songId1 == 2L && it.songId2 == 1L)
        }
        assertNotNull("1-2 eşleştirmesi olmalı", firstMatch)

        // Her takım tam olarak 1 maçta yer almalı
        val participants = round1.matches.flatMap { listOf(it.songId1, it.songId2) }
        assertEquals("6 katılımcı olmalı", 6, participants.size)
        assertEquals("Hiçbir takım iki maçta olmamalı", 6, participants.toSet().size)
    }

    @Test
    fun testPointsAfterFirstRound() {
        val winners = setOf(1L, 4L, 5L)
        var state = EmreSystemCorrect.initializeEmreTournament(makeSongs(6))
        val round1 = EmreSystemCorrect.createHybridPairingSystem(state)

        var id = 1L
        val completed = round1.matches.map { m ->
            val winnerId = if (m.songId1 in winners) m.songId1 else if (m.songId2 in winners) m.songId2 else m.songId1
            m.copy(id = id++, winnerId = winnerId, isCompleted = true)
        }
        state = EmreSystemCorrect.processRoundResults(state, completed, round1.byeTeam)

        assertEquals("2. tur olmalı", 2, state.currentRound)
        winners.forEach { winnerId ->
            assertEquals("Kazanan $winnerId 1 puan almalı", 1.0, state.teams.first { it.id == winnerId }.points, 0.001)
        }
        state.teams.filter { it.id !in winners }.forEach { loser ->
            assertEquals("Kaybeden ${loser.id} 0 puanda kalmalı", 0.0, loser.points, 0.001)
        }
    }

    @Test
    fun testOddTeamCountByeHandling() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(7))
        val round1 = EmreSystemCorrect.createHybridPairingSystem(state)

        assertEquals("7 takımda 3 maç olmalı", 3, round1.matches.size)
        assertNotNull("Tek sayıda takımda 1 bye olmalı", round1.byeTeam)
        assertEquals("İlk turda en alttaki takım bye geçmeli", 7L, round1.byeTeam!!.song.id)

        // Bye geçen takım +1 puan almalı
        var id = 1L
        val completed = round1.matches.map { m -> m.copy(id = id++, winnerId = m.songId1, isCompleted = true) }
        val newState = EmreSystemCorrect.processRoundResults(state, completed, round1.byeTeam)
        assertEquals("Bye geçen takım 1 puan almalı", 1.0, newState.teams.first { it.id == 7L }.points, 0.001)
        assertTrue("Bye bilgisi işaretlenmeli", newState.teams.first { it.id == 7L }.byePassed)
    }

    @Test
    fun testNoRematchInvariant() {
        // 16 takım, deterministik sonuçlar: küçük ID her zaman kazanır
        val (_, allMatches, rounds) = simulateTournament(16, maxRoundsGuard = 40) { m ->
            minOf(m.songId1, m.songId2)
        }

        assertTrue("En az 2 tur oynanmalı", rounds >= 2)

        // KIRMIZI ÇİZGİ: Hiçbir ikili iki kez eşleşmemeli
        val pairCounts = allMatches.groupingBy { m ->
            if (m.songId1 < m.songId2) Pair(m.songId1, m.songId2) else Pair(m.songId2, m.songId1)
        }.eachCount()
        val duplicates = pairCounts.filter { it.value > 1 }
        assertTrue("Tekrar eşleşme olmamalı ama bulundu: $duplicates", duplicates.isEmpty())

        // Her turda eşit sayıda (n/2) maç oynanmalı
        allMatches.groupBy { it.round }.forEach { (round, matches) ->
            assertEquals("Tur $round: 8 maç olmalı", 8, matches.size)
        }
    }

    @Test
    fun testAllDrawsTerminates() {
        // KRİTİK REGRESYON TESTİ: Tüm maçlar berabere biterse herkes hep aynı
        // puanda kalır. Eski motor bu senaryoda sonsuz döngüye girebiliyordu.
        // Yeni kural: tekrarsız tam eşleştirme kurulamadığında turnuva biter.
        val guard = 20
        val (_, allMatches, rounds) = simulateTournament(8, maxRoundsGuard = guard) { null }

        assertTrue("Turnuva sonlanmalı (guard'a çarpmamalı)", rounds < guard)
        // 8 takımla round-robin üst sınırı 7 turdur
        assertTrue("Tur sayısı round-robin sınırını aşmamalı", rounds <= 7)

        // Sonlanana kadar da kırmızı çizgi korunmalı
        val pairCounts = allMatches.groupingBy { m ->
            if (m.songId1 < m.songId2) Pair(m.songId1, m.songId2) else Pair(m.songId2, m.songId1)
        }.eachCount()
        assertTrue("Beraberlik senaryosunda da tekrar eşleşme olmamalı", pairCounts.none { it.value > 1 })
    }

    @Test
    fun testMatchNumbersUniquePerRound() {
        val (_, allMatches, _) = simulateTournament(18, maxRoundsGuard = 30) { m ->
            minOf(m.songId1, m.songId2)
        }

        allMatches.groupBy { it.round }.forEach { (round, matches) ->
            val numbers = matches.map { it.matchNumber }
            assertEquals(
                "Tur $round: maç numaraları benzersiz olmalı (bulunan: $numbers)",
                numbers.size, numbers.toSet().size
            )
            assertTrue(
                "Tur $round: maç numaraları 1..${matches.size} aralığında olmalı",
                numbers.all { it in 1..matches.size }
            )
        }
    }

    @Test
    fun testFinalResultsCompleteRanking() {
        val (finalState, _, _) = simulateTournament(10, maxRoundsGuard = 30) { m ->
            minOf(m.songId1, m.songId2)
        }

        val results = EmreSystemCorrect.calculateFinalResults(finalState)
        assertEquals("10 sonuç olmalı", 10, results.size)
        assertEquals("Pozisyonlar 1-10 arası benzersiz olmalı", (1..10).toList(), results.map { it.position }.sorted())

        // Deterministik senaryoda (küçük ID hep kazanır) 1 numaralı takım şampiyon olmalı
        assertEquals("Team1 şampiyon olmalı", 1L, results.first { it.position == 1 }.songId)
    }
}
