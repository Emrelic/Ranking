package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Lig puanlaması testleri (Faz 2 standartlaştırması):
 * - Galibiyet 3, beraberlik 1, mağlubiyet 0
 * - Eşit puanda averaj (gol farkı), sonra atılan gol tiebreak'i
 */
class LeagueScoringTest {

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Team$i", listId = 1L) }

    private fun match(
        id: Long,
        songId1: Long,
        songId2: Long,
        winnerId: Long?,
        score1: Int? = null,
        score2: Int? = null
    ) = Match(
        id = id, listId = 1L, rankingMethod = "LEAGUE",
        songId1 = songId1, songId2 = songId2, winnerId = winnerId,
        score1 = score1, score2 = score2, round = 1, isCompleted = true
    )

    @Test
    fun testStandardPointScale() {
        val songs = makeSongs(2)
        val results = RankingEngine.calculateLeagueResults(
            songs,
            listOf(match(1, 1L, 2L, winnerId = 1L))
        )

        assertEquals(3.0, results.first { it.songId == 1L }.score, 0.001)
        assertEquals(0.0, results.first { it.songId == 2L }.score, 0.001)
        assertEquals(1, results.first { it.songId == 1L }.position)
    }

    @Test
    fun testDrawGivesOnePointEach() {
        val songs = makeSongs(2)
        val results = RankingEngine.calculateLeagueResults(
            songs,
            listOf(match(1, 1L, 2L, winnerId = null))
        )
        assertEquals(1.0, results.first { it.songId == 1L }.score, 0.001)
        assertEquals(1.0, results.first { it.songId == 2L }.score, 0.001)
    }

    @Test
    fun testGoalDifferenceBreaksTies() {
        // Üçlü kısır döngü: 1, 2'yi 5-0; 2, 3'ü 1-0; 3, 1'i 1-0 yener.
        // Herkes 3 puanda. Averaj: 1 -> +4, 3 -> 0, 2 -> -4.
        val songs = makeSongs(3)
        val results = RankingEngine.calculateLeagueResults(
            songs,
            listOf(
                match(1, 1L, 2L, winnerId = 1L, score1 = 5, score2 = 0),
                match(2, 2L, 3L, winnerId = 2L, score1 = 1, score2 = 0),
                match(3, 3L, 1L, winnerId = 3L, score1 = 1, score2 = 0)
            )
        )

        assertEquals("Herkes 3 puanda olmalı", listOf(3.0, 3.0, 3.0), results.map { it.score })
        assertEquals("Averaj lideri (Team1) 1. olmalı", 1L, results.first { it.position == 1 }.songId)
        assertEquals("Averajı sıfır olan (Team3) 2. olmalı", 3L, results.first { it.position == 2 }.songId)
        assertEquals("Averajı en kötü olan (Team2) 3. olmalı", 2L, results.first { it.position == 3 }.songId)
    }
}
