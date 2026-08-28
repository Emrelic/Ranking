package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EliminationSystem
import com.example.ranking.ranking.EliminationSystem.Mode
import org.junit.Assert.*
import org.junit.Test

/**
 * Eleme motoru testleri (oturumlar/ELEME-MOTORU.md).
 *
 * Odak: eski RankingEngine.kt'deki iki ölçülmüş kusurun regresyon testleri
 * (shuffled() ile grup üyeliğinin kayması, pozisyon çakışması) + sınır
 * durumları + üç kip.
 */
class EliminationSystemTest {

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Song$i", listId = 1L) }

    /**
     * `winnerPicker` her maç için galibi seçer (varsayılan: düşük id/güçlü seed kazanır).
     * Turnuva bitene ya da `maxRounds` guard'ına kadar tur tur oynatır.
     */
    private fun simulate(
        songs: List<Song>,
        mode: Mode,
        maxRounds: Int = 30,
        winnerPicker: (Match) -> Long? = { m -> minOf(m.songId1, m.songId2) }
    ): List<Match> {
        val completed = mutableListOf<Match>()
        var nextId = 1L
        var guard = 0
        while (guard++ < maxRounds) {
            val state = EliminationSystem.computeState(songs, completed, mode)
            if (state.isComplete) break
            val round = EliminationSystem.createNextRound(songs, completed, mode)
            if (round.matches.isEmpty()) {
                if (!round.canContinue) break else continue
            }
            round.matches.forEach { m ->
                val winner = if (m.songId1 == m.songId2) m.songId1 else winnerPicker(m)
                completed += m.copy(id = nextId++, winnerId = winner, isCompleted = true)
            }
        }
        return completed
    }

    // ─────────────────────────────────────────────────────────────────
    // SINIR DURUMLARI — SINGLE
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `n=0 katilimci cokmuyor`() {
        val state = EliminationSystem.computeState(emptyList(), emptyList(), Mode.SINGLE)
        assertTrue(state.isComplete)
        assertNull(state.championId)
        assertTrue(EliminationSystem.calculateResults(emptyList(), emptyList(), Mode.SINGLE).isEmpty())
        assertTrue(EliminationSystem.bracketStructure(emptyList(), emptyList(), Mode.SINGLE).isEmpty())
    }

    @Test
    fun `n=1 katilimci otomatik sampiyon`() {
        val songs = makeSongs(1)
        val state = EliminationSystem.computeState(songs, emptyList(), Mode.SINGLE)
        assertTrue(state.isComplete)
        assertEquals(1L, state.championId)
        val results = EliminationSystem.calculateResults(songs, emptyList(), Mode.SINGLE)
        assertEquals(1, results.size)
        assertEquals(1, results[0].position)
    }

    @Test
    fun `n=2 katilimci tek mac final`() {
        val songs = makeSongs(2)
        val completed = simulate(songs, Mode.SINGLE)
        assertEquals(1, completed.size)
        val state = EliminationSystem.computeState(songs, completed, Mode.SINGLE)
        assertTrue(state.isComplete)
        assertEquals(1L, state.championId)
    }

    @Test
    fun `n=3 katilimci on tur gerekir`() {
        val songs = makeSongs(3)
        val completed = simulate(songs, Mode.SINGLE)
        // targetSize=2, fazla=1: 1 on tur maci + 1 final = 2 mac
        assertEquals(2, completed.size)
        val state = EliminationSystem.computeState(songs, completed, Mode.SINGLE)
        assertTrue(state.isComplete)
        assertEquals(1L, state.championId)
    }

    @Test
    fun `n=4 katilimci tam guc-2`() {
        val songs = makeSongs(4)
        val completed = simulate(songs, Mode.SINGLE)
        assertEquals(3, completed.size) // 2 + 1
        val state = EliminationSystem.computeState(songs, completed, Mode.SINGLE)
        assertTrue(state.isComplete)
        assertEquals(1L, state.championId)
        val results = EliminationSystem.calculateResults(songs, completed, Mode.SINGLE)
        assertEquals((1..4).toList(), results.map { it.position }.sorted())
    }

    @Test
    fun `n=8 tam guc-2 - 7 mac 3 tur`() {
        val songs = makeSongs(8)
        val completed = simulate(songs, Mode.SINGLE)
        assertEquals(7, completed.size) // 4+2+1
        val state = EliminationSystem.computeState(songs, completed, Mode.SINGLE)
        assertTrue(state.isComplete)
        assertEquals(1L, state.championId)
    }

    @Test
    fun `n=12 on tur gerekir - dogru mac sayisi ve sampiyon`() {
        val songs = makeSongs(12)
        val completed = simulate(songs, Mode.SINGLE)
        // targetSize=8, fazla=4: 4(on tur)+4(1.tur)+2(2.tur)+1(final) = 11
        assertEquals(11, completed.size)
        val state = EliminationSystem.computeState(songs, completed, Mode.SINGLE)
        assertTrue(state.isComplete)
        assertEquals(1L, state.championId)
    }

    // ─────────────────────────────────────────────────────────────────
    // 🔴 n=16: POZİSYON 1..16 HER BİRİ TAM BİR KEZ (eski kodun çakışma kusuru)
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `n=16 tek eleme pozisyon 1-16 tam bir kez`() {
        val songs = makeSongs(16)
        val completed = simulate(songs, Mode.SINGLE)
        assertEquals(15, completed.size) // 8+4+2+1

        val results = EliminationSystem.calculateResults(songs, completed, Mode.SINGLE)
        assertEquals(16, results.size)
        assertEquals("Pozisyonlar 1..16 TAM BİR KEZ geçmeli, çakışma olmamalı",
            (1..16).toList(), results.map { it.position }.sorted())
        assertEquals("Her songId tam bir kez sonuçta olmalı",
            16, results.map { it.songId }.toSet().size)

        val champion = results.first { it.position == 1 }
        assertEquals(1L, champion.songId) // düşük id her turda kazanıyor -> en güçlü seed şampiyon
        val runnerUp = results.first { it.position == 2 }
        assertEquals(2L, runnerUp.songId) // final rakibi, ikinci güçlü hayatta kalan seed
    }

    @Test
    fun `n=16 bracket yapisi motor testleri disaridan da kullanabilsin`() {
        val songs = makeSongs(16)
        val completed = simulate(songs, Mode.SINGLE)
        val structure = EliminationSystem.bracketStructure(songs, completed, Mode.SINGLE)
        // 4 tur: 8,4,2,1 mac -> her round'da slot sayisi = 2*mac
        assertEquals(4, structure.size)
        assertEquals(16, structure[0].size) // round1: 8 mac x 2 slot
        assertEquals(8, structure[1].size)
        assertEquals(4, structure[2].size)
        assertEquals(2, structure[3].size)
    }

    // ─────────────────────────────────────────────────────────────────
    // ③ GRUP + ELEME — 🔴 shuffled() regresyon testi
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `grup kipi computeState iki kez cagrilinca AYNI gruplari verir`() {
        val songs = makeSongs(12)
        val next1 = EliminationSystem.createNextRound(songs, emptyList(), Mode.GROUP_THEN_KNOCKOUT)
        val next2 = EliminationSystem.createNextRound(songs, emptyList(), Mode.GROUP_THEN_KNOCKOUT)
        // Eski kod burada BAŞKA bir shuffled() ile farklı gruplar üretebiliyordu — bu artık olmamalı.
        assertEquals(
            next1.matches.map { Triple(it.groupId, it.songId1, it.songId2) },
            next2.matches.map { Triple(it.groupId, it.songId1, it.songId2) }
        )
    }

    @Test
    fun `grup kipi ayni girdi ile tekrar tekrar cagrilinca hep ayni sonucu verir`() {
        val songs = makeSongs(12)
        val completedA = simulate(songs, Mode.GROUP_THEN_KNOCKOUT)
        val completedB = simulate(songs, Mode.GROUP_THEN_KNOCKOUT)
        val resultsA = EliminationSystem.calculateResults(songs, completedA, Mode.GROUP_THEN_KNOCKOUT)
        val resultsB = EliminationSystem.calculateResults(songs, completedB, Mode.GROUP_THEN_KNOCKOUT)
        assertEquals(
            resultsA.map { it.songId to it.position }.sortedBy { it.first },
            resultsB.map { it.songId to it.position }.sortedBy { it.first }
        )
    }

    @Test
    fun `grup kipi tamamlanip sampiyon veriyor ve pozisyon tekrari yok`() {
        val songs = makeSongs(12)
        val completed = simulate(songs, Mode.GROUP_THEN_KNOCKOUT)
        val state = EliminationSystem.computeState(songs, completed, Mode.GROUP_THEN_KNOCKOUT)
        assertTrue(state.isComplete)
        assertNotNull(state.championId)

        val results = EliminationSystem.calculateResults(songs, completed, Mode.GROUP_THEN_KNOCKOUT)
        assertEquals(12, results.size)
        assertEquals((1..12).toList(), results.map { it.position }.sorted())
        assertEquals(12, results.map { it.songId }.toSet().size)
    }

    @Test
    fun `grup kipi kucuk n=3 icin tek grup gibi davranir`() {
        val songs = makeSongs(3)
        val completed = simulate(songs, Mode.GROUP_THEN_KNOCKOUT)
        val state = EliminationSystem.computeState(songs, completed, Mode.GROUP_THEN_KNOCKOUT)
        assertTrue(state.isComplete)
        assertNotNull(state.championId)
    }

    // ─────────────────────────────────────────────────────────────────
    // ② ÇİFT ELEME — 🔴 bracket reset testi
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `cift eleme normal akis - WB galibi finali de kazanirsa reset olmaz`() {
        val songs = makeSongs(4)
        // Her yerde düşük id kazanır -> WB şampiyonu (1) finali de kazanır, reset gerekmez.
        val completed = simulate(songs, Mode.DOUBLE)
        val state = EliminationSystem.computeState(songs, completed, Mode.DOUBLE)
        assertTrue(state.isComplete)
        assertEquals(1L, state.championId)
        assertFalse("Reset gerekmiyorsa round=1001 maçı ÜRETİLMEMELİ", completed.any { it.round == 1001 })
        assertTrue("Büyük final (round=1000) oynanmış olmalı", completed.any { it.round == 1000 })
    }

    @Test
    fun `cift eleme alt kol galibi finali kazanirsa bracket reset olusur`() {
        val songs = makeSongs(4)
        // WB 1. tur (round=1): id=1'in maçında YÜKSEK id kazansın (1 erken WB'den düşüp LB'ye
        // gitsin); diğer her yerde (LB dahil, final/reset dahil) düşük id kazansın — böylece
        // güçlü takım (1) LB'yi domine edip finalde WB şampiyonunu yener, reset tetiklenir.
        val completed = simulate(songs, Mode.DOUBLE, winnerPicker = { m ->
            if (m.round == 1 && (m.songId1 == 1L || m.songId2 == 1L)) maxOf(m.songId1, m.songId2)
            else minOf(m.songId1, m.songId2)
        })
        val state = EliminationSystem.computeState(songs, completed, Mode.DOUBLE)
        assertTrue(state.isComplete)
        assertTrue("LB galibi finali kazanınca bracket reset (round=1001) üretilmeli",
            completed.any { it.round == 1001 })
        assertEquals("Reset'i de kazanan (id=1, LB'den gelen) nihai şampiyon olmalı", 1L, state.championId)

        val results = EliminationSystem.calculateResults(songs, completed, Mode.DOUBLE)
        assertEquals(4, results.size)
        assertEquals((1..4).toList(), results.map { it.position }.sorted())
        assertEquals(4, results.map { it.songId }.toSet().size)
        assertEquals(1L, results.first { it.position == 1 }.songId)
    }

    @Test
    fun `cift eleme n=8 tamamlaniyor ve cokmuyor`() {
        val songs = makeSongs(8)
        val completed = simulate(songs, Mode.DOUBLE, maxRounds = 40)
        val state = EliminationSystem.computeState(songs, completed, Mode.DOUBLE)
        assertTrue(state.isComplete)
        assertNotNull(state.championId)
        val results = EliminationSystem.calculateResults(songs, completed, Mode.DOUBLE)
        assertEquals(8, results.size)
        assertEquals((1..8).toList(), results.map { it.position }.sorted())
    }

    // ─────────────────────────────────────────────────────────────────
    // ORTAK — yetim maç, hepsi berabere
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `yetim mac kaydi cokmuyor`() {
        val songs = makeSongs(4)
        // songId1=999 songs listesinde YOK (silinmiş öğe senaryosu).
        val orphan = Match(
            id = 1, listId = 1L, rankingMethod = "ELIMINATION",
            songId1 = 999L, songId2 = 1L, winnerId = 999L,
            round = 1, matchNumber = 1, isCompleted = true
        )
        // Hiçbiri çökmemeli — doğru sonuç vermesini değil, çökmemesini test ediyoruz.
        val state = EliminationSystem.computeState(songs, listOf(orphan), Mode.SINGLE)
        assertNotNull(state)
        val results = EliminationSystem.calculateResults(songs, listOf(orphan), Mode.SINGLE)
        assertNotNull(results)
        val next = EliminationSystem.createNextRound(songs, listOf(orphan), Mode.SINGLE)
        assertNotNull(next)
        val structure = EliminationSystem.bracketStructure(songs, listOf(orphan), Mode.SINGLE)
        assertNotNull(structure)
    }

    @Test
    fun `yetim mac kaydi grup ve cift elemede de cokmuyor`() {
        val songs = makeSongs(8)
        val orphan = Match(
            id = 1, listId = 1L, rankingMethod = "ELIMINATION",
            songId1 = 12345L, songId2 = 67890L, winnerId = 12345L,
            round = 0, groupId = 0, matchNumber = 1, isCompleted = true
        )
        assertNotNull(EliminationSystem.computeState(songs, listOf(orphan), Mode.GROUP_THEN_KNOCKOUT))
        assertNotNull(EliminationSystem.calculateResults(songs, listOf(orphan), Mode.GROUP_THEN_KNOCKOUT))
        assertNotNull(EliminationSystem.computeState(songs, listOf(orphan), Mode.DOUBLE))
        assertNotNull(EliminationSystem.calculateResults(songs, listOf(orphan), Mode.DOUBLE))
    }

    @Test
    fun `hepsi beraberlik deterministik sonuc verir - yuksek seed gecer`() {
        val songs = makeSongs(4)

        fun playAllDraws(): List<Match> {
            val completed = mutableListOf<Match>()
            var nextId = 1L
            var guard = 0
            while (guard++ < 10) {
                val state = EliminationSystem.computeState(songs, completed, Mode.SINGLE)
                if (state.isComplete) break
                val round = EliminationSystem.createNextRound(songs, completed, Mode.SINGLE)
                if (round.matches.isEmpty()) break
                round.matches.forEach { m ->
                    completed += m.copy(id = nextId++, winnerId = null, isCompleted = true)
                }
            }
            return completed
        }

        val runA = playAllDraws()
        val stateA = EliminationSystem.computeState(songs, runA, Mode.SINGLE)
        assertTrue(stateA.isComplete)
        assertEquals("Beraberlikte yüksek seed (düşük id) geçmeli", 1L, stateA.championId)

        val runB = playAllDraws()
        val stateB = EliminationSystem.computeState(songs, runB, Mode.SINGLE)
        assertEquals("Aynı senaryo tekrar oynatılınca AYNI sonuç çıkmalı (replay determinizmi)",
            stateA.championId, stateB.championId)

        val resultsA = EliminationSystem.calculateResults(songs, runA, Mode.SINGLE)
        val resultsB = EliminationSystem.calculateResults(songs, runB, Mode.SINGLE)
        assertEquals(
            resultsA.map { it.songId to it.position },
            resultsB.map { it.songId to it.position }
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // İki takım aynı turda birden fazla kez eşleşmez (genel sağlık kontrolü)
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `bir takim ayni turda birden fazla mac oynamaz`() {
        val songs = makeSongs(16)
        val completed = simulate(songs, Mode.SINGLE)
        completed.groupBy { it.round }.forEach { (_, roundMatches) ->
            val participants = roundMatches.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals("Bir turda bir takım birden fazla maçta olamaz",
                participants.size, participants.toSet().size)
        }
    }
}
