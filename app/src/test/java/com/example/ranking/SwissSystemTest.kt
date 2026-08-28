package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.SwissSystem
import org.junit.Assert.*
import org.junit.Test

/**
 * SwissSystem (İsviçre Sistemi) motoru testleri.
 *
 * Test edilen kırmızı çizgiler:
 * - İki takım birbiriyle en fazla 1 kez eşleşir
 * - Her turda TAM eşleştirme: kimse düşmez (ya oynar ya bye geçer)
 * - Bye rotasyonu adil: herkes bye geçmeden aynı takım ikinci byeyi almaz
 * - matchNumber her zaman atanır (0 kalmaz), üstten alta ASC sıralı
 * - Turnuva her senaryoda sonlanır (sonsuz döngü / üstel patlama korumalı)
 * - Replay: aynı maç geçmişi → aynı durum (computeState iki kez çağrılınca eşit)
 */
class SwissSystemTest {

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Team$i", listId = 1L) }

    /**
     * Deterministik turnuva simülasyonu. winnerPicker gerçek (bye olmayan) her
     * maç için kazanan songId döndürür (null = beraberlik).
     */
    private fun simulateTournament(
        songs: List<Song>,
        maxRoundsGuard: Int,
        winnerPicker: (Match) -> Long?
    ): Triple<SwissSystem.SwissState, List<Match>, Int> {
        val allCompleted = mutableListOf<Match>()
        var nextMatchId = 1L
        var rounds = 0
        var state = SwissSystem.computeState(songs, allCompleted)

        while (rounds < maxRoundsGuard) {
            val pairing = SwissSystem.createNextRound(state, allCompleted)
            if (!pairing.canContinue) break
            rounds++

            pairing.matches.forEach { m ->
                val completed = if (m.isCompleted) {
                    // Bye maçı: motor zaten tamamlanmış üretti, olduğu gibi al.
                    m.copy(id = nextMatchId++)
                } else {
                    m.copy(id = nextMatchId++, winnerId = winnerPicker(m), isCompleted = true)
                }
                allCompleted.add(completed)
            }
            state = SwissSystem.computeState(songs, allCompleted)
        }
        return Triple(state, allCompleted, rounds)
    }

    private fun higherIdWins(m: Match): Long? = maxOf(m.songId1, m.songId2)

    // ---------------------------------------------------------------
    // Sınır durumları
    // ---------------------------------------------------------------

    @Test
    fun testEmptyList() {
        val state = SwissSystem.computeState(emptyList(), emptyList())
        assertTrue("0 takımda turnuva tamamlanmış sayılmalı", state.isComplete)
        assertEquals(0, state.teams.size)

        val pairing = SwissSystem.createNextRound(state, emptyList())
        assertFalse("0 takımda devam edilemez", pairing.canContinue)

        assertTrue(SwissSystem.calculateResults(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun testSingleTeam() {
        val songs = makeSongs(1)
        val state = SwissSystem.computeState(songs, emptyList())
        assertTrue("1 takımda turnuva tamamlanmış sayılmalı", state.isComplete)

        val pairing = SwissSystem.createNextRound(state, emptyList())
        assertFalse("1 takımla eşleşme kurulamaz", pairing.canContinue)

        val results = SwissSystem.calculateResults(songs, emptyList())
        assertEquals(1, results.size)
        assertEquals(1, results[0].position)
    }

    @Test
    fun testTwoTeams() {
        val songs = makeSongs(2)
        assertEquals(1, SwissSystem.recommendedRoundCount(2))

        val (finalState, matches, rounds) = simulateTournament(songs, maxRoundsGuard = 10) { higherIdWins(it) }
        assertEquals("2 takımda 1 tur oynanmalı", 1, rounds)
        assertEquals(1, matches.size)
        assertTrue(finalState.isComplete)

        val results = SwissSystem.calculateResults(songs, matches)
        assertEquals(2L, results[0].songId) // yüksek id kazanır
        assertEquals(1, results[0].position)
    }

    @Test
    fun testThreeTeamsByeRotates() {
        val songs = makeSongs(3)
        val (_, matches, rounds) = simulateTournament(songs, maxRoundsGuard = 10) { higherIdWins(it) }

        assertTrue("En az 1 tur oynanmalı", rounds >= 1)

        // Her turda tam olarak 1 bye olmalı (tek sayı takım)
        val byesByRound = matches.filter { it.songId1 == it.songId2 }.groupBy { it.round }
        byesByRound.forEach { (_, byes) -> assertEquals(1, byes.size) }
    }

    // ---------------------------------------------------------------
    // n=7 (tek -> bye) ve n=8 (çift) — kırmızı çizgiler
    // ---------------------------------------------------------------

    @Test
    fun testSevenTeamsNoOneDropsEachRound() {
        val songs = makeSongs(7)
        val (_, matches, rounds) = simulateTournament(songs, maxRoundsGuard = 10) { higherIdWins(it) }
        assertTrue("En az 1 tur oynanmalı", rounds >= 1)

        val matchesByRound = matches.groupBy { it.round }
        matchesByRound.forEach { (round, roundMatches) ->
            val participants = mutableSetOf<Long>()
            roundMatches.forEach { m ->
                if (m.songId1 == m.songId2) {
                    participants.add(m.songId1) // bye
                } else {
                    participants.add(m.songId1)
                    participants.add(m.songId2)
                }
            }
            assertEquals(
                "Tur $round: 7 takımın hepsi ya oynamalı ya bye geçmeli, kimse düşmemeli",
                7, participants.size
            )
            // Tam olarak 1 bye olmalı (tek sayı)
            assertEquals(1, roundMatches.count { it.songId1 == it.songId2 })
        }
    }

    @Test
    fun testSevenTeamsByeFairRotation() {
        // n=7'nin doğal tur sınırı (recommendedRoundCount) 3 - tam bir bye
        // rotasyon döngüsünü (7 farklı bye) gözlemlemek için maxRounds'ı test
        // amaçlı büyütüyoruz; SwissState alanları public, bu meşru bir izolasyon.
        // (createNextRound yalnız kendisine verilen `state` parametresine bakar.)
        val songs = makeSongs(7)
        val allCompleted = mutableListOf<Match>()
        var nextMatchId = 1L
        val byeSequence = mutableListOf<Long>()

        repeat(7) {
            val natural = SwissSystem.computeState(songs, allCompleted)
            val forced = natural.copy(maxRounds = 999, isComplete = false)
            val pairing = SwissSystem.createNextRound(forced, allCompleted)
            assertTrue("Eşleştirme kurulabilmeli", pairing.canContinue)
            assertNotNull("7 (tek) takımda bye olmalı", pairing.byeTeam)
            byeSequence.add(pairing.byeTeam!!.id)

            pairing.matches.forEach { m ->
                val completed = if (m.isCompleted) m.copy(id = nextMatchId++)
                else m.copy(id = nextMatchId++, winnerId = null, isCompleted = true)
                allCompleted.add(completed)
            }
        }

        val seen = mutableSetOf<Long>()
        byeSequence.forEachIndexed { idx, teamId ->
            if (idx < 7) {
                assertFalse(
                    "Herkes bir kez bye almadan $teamId ikinci kez bye aldı (sıra: $byeSequence)",
                    teamId in seen
                )
            }
            seen.add(teamId)
        }
        assertEquals("7 tur sonunda 7 farklı takım bye almış olmalı", 7, seen.size)
    }

    @Test
    fun testEightTeamsNoRematchAcrossAllRounds() {
        val songs = makeSongs(8)
        val (_, matches, rounds) = simulateTournament(songs, maxRoundsGuard = 10) { higherIdWins(it) }
        assertTrue("En az birkaç tur oynanmalı", rounds >= 3)

        val realMatches = matches.filter { it.songId1 != it.songId2 }
        val seenPairs = mutableSetOf<Pair<Long, Long>>()
        realMatches.forEach { m ->
            val key = if (m.songId1 < m.songId2) Pair(m.songId1, m.songId2) else Pair(m.songId2, m.songId1)
            assertFalse(
                "Çift ($key) İKİ KEZ eşleşti! (kırmızı çizgi ihlali)",
                key in seenPairs
            )
            seenPairs.add(key)
        }
    }

    @Test
    fun testEightTeamsEveryoneAlwaysPlays() {
        val songs = makeSongs(8)
        val (_, matches, _) = simulateTournament(songs, maxRoundsGuard = 10) { higherIdWins(it) }

        val matchesByRound = matches.groupBy { it.round }
        matchesByRound.forEach { (round, roundMatches) ->
            assertEquals("Tur $round: çift sayıda takımda bye olmamalı", 0, roundMatches.count { it.songId1 == it.songId2 })
            val participants = roundMatches.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals("Tur $round: 8 katılımcı olmalı", 8, participants.size)
            assertEquals("Tur $round: hiçbir takım iki maçta olmamalı", 8, participants.toSet().size)
        }
    }

    @Test
    fun testMatchNumberAlwaysAssignedAscendingFromTop() {
        val songs = makeSongs(8)
        val state = SwissSystem.computeState(songs, emptyList())
        val pairing = SwissSystem.createNextRound(state, emptyList())

        assertTrue(pairing.matches.isNotEmpty())
        pairing.matches.forEach { assertTrue("matchNumber 0 kalmamalı", it.matchNumber > 0) }

        val numbers = pairing.matches.map { it.matchNumber }.sorted()
        assertEquals("1..N ardışık olmalı", (1..pairing.matches.size).toList(), numbers)
    }

    // ---------------------------------------------------------------
    // Beraberlik senaryosu
    // ---------------------------------------------------------------

    @Test
    fun testAllDrawsScenarioDoesNotCrash() {
        val songs = makeSongs(6)
        val (finalState, matches, rounds) = simulateTournament(songs, maxRoundsGuard = 10) { null }
        assertTrue(rounds >= 1)
        // Herkes berabere kaldığı için tüm takımların puanı eşit olmalı (bye alanlar hariç +1)
        finalState.teams.forEach { team ->
            assertEquals(team.drawn.toDouble() * 0.5 + team.byeCount.toDouble(), team.points, 0.001)
        }
        assertTrue(matches.isNotEmpty())
    }

    // ---------------------------------------------------------------
    // Yetim maç (silinmiş öğe)
    // ---------------------------------------------------------------

    @Test
    fun testOrphanMatchDoesNotCrash() {
        val songs = makeSongs(4) // id 1..4
        val orphanMatch = Match(
            id = 999, listId = 1L, rankingMethod = SwissSystem.METHOD,
            songId1 = 1L, songId2 = 999L, // 999 artık songs içinde yok (silinmiş)
            winnerId = 1L, round = 1, matchNumber = 1, isCompleted = true
        )

        val state = SwissSystem.computeState(songs, listOf(orphanMatch))
        // Çökmedi ve yetim maç puan/geçmiş üretmedi
        val team1 = state.teams.first { it.id == 1L }
        assertEquals(0.0, team1.points, 0.001)
        assertEquals(0, team1.played)
        assertTrue(team1.opponentIds.isEmpty())

        val pairing = SwissSystem.createNextRound(state, listOf(orphanMatch))
        assertTrue("Yetim maça rağmen tur kurulabilmeli", pairing.canContinue)

        val results = SwissSystem.calculateResults(songs, listOf(orphanMatch))
        assertEquals(4, results.size) // çökmedi
    }

    @Test
    fun testOrphanByeMatchDoesNotCrash() {
        val songs = makeSongs(4)
        val orphanBye = Match(
            id = 1, listId = 1L, rankingMethod = SwissSystem.METHOD,
            songId1 = 999L, songId2 = 999L, winnerId = 999L,
            round = 1, matchNumber = 0, isCompleted = true
        )
        val state = SwissSystem.computeState(songs, listOf(orphanBye))
        assertEquals(4, state.teams.size)
        state.teams.forEach { assertEquals(0.0, it.points, 0.001) }
    }

    // ---------------------------------------------------------------
    // Replay: aynı maç listesi -> aynı sonuç
    // ---------------------------------------------------------------

    @Test
    fun testReplayIsDeterministic() {
        val songs = makeSongs(8)
        val (_, matches, _) = simulateTournament(songs, maxRoundsGuard = 10) { higherIdWins(it) }

        val state1 = SwissSystem.computeState(songs, matches)
        val state2 = SwissSystem.computeState(songs, matches)
        assertEquals(state1, state2)

        val results1 = SwissSystem.calculateResults(songs, matches)
        val results2 = SwissSystem.calculateResults(songs, matches)
        assertEquals(results1, results2)
    }

    // ---------------------------------------------------------------
    // Performans: n=64
    // ---------------------------------------------------------------

    @Test
    fun testSixtyFourTeamsPerformance() {
        val songs = makeSongs(64)
        var state = SwissSystem.computeState(songs, emptyList())
        val allCompleted = mutableListOf<Match>()
        var nextMatchId = 1L
        val maxRounds = SwissSystem.recommendedRoundCount(64)

        var rounds = 0
        while (rounds < maxRounds) {
            val start = System.nanoTime()
            val pairing = SwissSystem.createNextRound(state, allCompleted)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("createNextRound < 2sn olmalı (ölçülen: ${elapsedMs}ms, tur ${rounds + 1})", elapsedMs < 2000)

            if (!pairing.canContinue) break
            rounds++
            pairing.matches.forEach { m ->
                val completed = if (m.isCompleted) m.copy(id = nextMatchId++)
                else m.copy(id = nextMatchId++, winnerId = higherIdWins(m), isCompleted = true)
                allCompleted.add(completed)
            }
            state = SwissSystem.computeState(songs, allCompleted)
        }
        assertTrue("n=64 için en az birkaç tur oynanmalı", rounds >= 3)
    }
}
