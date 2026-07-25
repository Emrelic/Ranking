package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import org.junit.Assert.*
import org.junit.Test

/**
 * Faz 1-2 düzeltmelerinin regresyon testleri (ANALIZ_RAPORU.md):
 * - Alternating match numbering yönü: TOP KEAT -> 1, 2, 3...
 * - matchHistory'ye yalnızca tamamlanmış maçlar girer
 * - Tiebreaker zinciri TÜM maç geçmişini görür (yalnızca son turu değil)
 * - Bye rotasyonu adildir (herkes bye geçtiyse en az bye geçen seçilir)
 */
class EmreFixRegressionTest {

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Team$i", listId = 1L) }

    @Test
    fun testMatchNumberingTopKeatGetsOne() {
        // 8 takım, ilk tur: 1-2, 3-4, 5-6, 7-8 eşleşmeleri beklenir.
        // Alternating numbering: üstten seçilen KEAT küçük numarayı,
        // alttan seçilen KEAT büyük numarayı alır.
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(8))
        val round1 = EmreSystemCorrect.createHybridPairingSystem(state)
        assertEquals(4, round1.matches.size)

        fun numberOfMatchContaining(songId: Long): Int =
            round1.matches.first { it.songId1 == songId || it.songId2 == songId }.matchNumber

        assertEquals("En üst eşleşme (1-2) 1 numarayı almalı", 1, numberOfMatchContaining(1L))
        assertEquals("En alt eşleşme (7-8) son numarayı almalı", 4, numberOfMatchContaining(8L))
        assertEquals("İkinci üst eşleşme (3-4) 2 numarayı almalı", 2, numberOfMatchContaining(3L))
        assertEquals("İkinci alt eşleşme (5-6) 3 numarayı almalı", 3, numberOfMatchContaining(5L))
    }

    @Test
    fun testMatchHistoryOnlyCompletedMatches() {
        val state = EmreSystemCorrect.initializeEmreTournament(makeSongs(4))
        val round1 = EmreSystemCorrect.createHybridPairingSystem(state)
        assertEquals(2, round1.matches.size)

        // Yalnızca ilk maç tamamlanır; ikincisi yarım kalır
        val completed = round1.matches[0].copy(id = 1L, winnerId = round1.matches[0].songId1, isCompleted = true)
        val uncompleted = round1.matches[1].copy(id = 2L, winnerId = null, isCompleted = false)

        val newState = EmreSystemCorrect.processRoundResults(state, listOf(completed, uncompleted), null)

        assertEquals(
            "Yalnızca tamamlanmış maç history'ye girmeli",
            1, newState.matchHistory.size
        )
    }

    @Test
    fun testTiebreakerSeesFullMatchHistory() {
        // Senaryo: R1'de 1, 2'yi yener; 3, 4'ü yener.
        // R2'de 3, 1'i yener; 2, 4'ü yener.
        // Sonuç: 1 ve 2 birer puanda eşit. Head-to-head (R1: 1 > 2) TÜM
        // geçmişe bakıldığında 1'i öne koymalı. (Eski hata: tiebreaker
        // yalnızca son turun maçlarını gördüğü için H2H işlevsizdi ve
        // "son turda daha az mağlubiyet" kriteri 2'yi öne geçiriyordu.)
        var state = EmreSystemCorrect.initializeEmreTournament(makeSongs(4))

        val r1 = EmreSystemCorrect.createHybridPairingSystem(state)
        var id = 1L
        val r1Completed = r1.matches.map { m ->
            val winner = if (setOf(m.songId1, m.songId2) == setOf(1L, 2L)) 1L else 3L
            m.copy(id = id++, winnerId = winner, isCompleted = true)
        }
        state = EmreSystemCorrect.processRoundResults(state, r1Completed, null, r1Completed)

        val r2 = EmreSystemCorrect.createHybridPairingSystem(state)
        assertTrue("2. tur oynanabilmeli", r2.canContinue && r2.matches.isNotEmpty())
        val r2Completed = r2.matches.map { m ->
            val winner = when (setOf(m.songId1, m.songId2)) {
                setOf(1L, 3L) -> 3L
                setOf(2L, 4L) -> 2L
                else -> minOf(m.songId1, m.songId2)
            }
            m.copy(id = id++, winnerId = winner, isCompleted = true)
        }
        val allCompleted = r1Completed + r2Completed
        state = EmreSystemCorrect.processRoundResults(state, r2Completed, null, allCompleted)

        val team1 = state.teams.first { it.id == 1L }
        val team2 = state.teams.first { it.id == 2L }
        assertEquals("1 ve 2 aynı puanda olmalı", team1.points, team2.points, 0.001)
        assertTrue(
            "H2H galibi (1) sıralamada 2'nin üstünde olmalı " +
                "(1: ${team1.currentPosition}, 2: ${team2.currentPosition})",
            team1.currentPosition < team2.currentPosition
        )
    }

    @Test
    fun testByeRotationIsFair() {
        // 5 takımla uzun simülasyon: hiçbir takım, herkes 1 kez bye
        // geçmeden 2. bye'ını almamalı (rotasyon adaleti).
        var state = EmreSystemCorrect.initializeEmreTournament(makeSongs(5))
        val byeCounts = mutableMapOf<Long, Int>()
        var id = 1L
        var rounds = 0

        while (rounds < 15) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            rounds++

            pairing.byeTeam?.let { bye ->
                byeCounts[bye.song.id] = (byeCounts[bye.song.id] ?: 0) + 1
                val max = byeCounts.values.max()
                val teamsWithoutBye = (1L..5L).count { (byeCounts[it] ?: 0) == 0 }
                if (max >= 2) {
                    assertEquals(
                        "Tur $rounds: bir takım 2. bye'ını aldığında herkes en az 1 bye geçmiş olmalı ($byeCounts)",
                        0, teamsWithoutBye
                    )
                }
            }

            val completed = pairing.matches.map { m ->
                m.copy(id = id++, winnerId = minOf(m.songId1, m.songId2), isCompleted = true)
            }
            state = EmreSystemCorrect.processRoundResults(state, completed, pairing.byeTeam)
        }

        assertTrue("En az 3 tur oynanmalı", rounds >= 3)
    }
}
