package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.PairwiseComparisonSort
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.ranking.SwissSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KARŞILAŞTIRMA ÖLÇÜMÜ — kullanıcının sorusu için:
 * "100+ takımı hakkaniyetle dizmek için Geliştirilmiş İsviçre iyi mi,
 *  yoksa düz İsviçre yeterli mi?"
 *
 * Aynı senaryo iki motora da koşturulur: 1..N sayıları karışık girilir,
 * her eşleşmede BÜYÜK SAYI kazanır (nesnel gerçek sıra bilinir). Sonuçta
 * her motorun ürettiği sıralamanın gerçekten sapması ölçülür.
 *
 * Ölçüler:
 *  - ortalama konum sapması (0 = kusursuz; rastgele dizilişte ~N/3)
 *  - tam yerinde olan öğe sayısı
 *  - ilk 10 / son 10 isabeti
 *  - tur ve maç sayısı (kullanıcının ödediği emek)
 */
class SiralamaKalitesiKarsilastirmaTest {

    private fun sarkilar(n: Int, tohum: Long): List<Song> {
        val karisik = (1..n).shuffled(java.util.Random(tohum))
        return karisik.mapIndexed { i, sayi ->
            Song(id = sayi.toLong(), name = "$sayi", artist = "", album = "",
                trackNumber = i + 1, listId = 1L)
        }
    }

    private var mid = 1L
    private fun oynat(maclar: List<Match>): List<Match> = maclar.map {
        it.copy(id = mid++, winnerId = maxOf(it.songId1, it.songId2), isCompleted = true)
    }

    data class Sonuc(
        val siralama: List<Int>, val turlar: Int, val maclar: Int
    ) {
        val n get() = siralama.size
        val ortSapma: Double get() = siralama.mapIndexed { i, s ->
            kotlin.math.abs(i - (n - s))
        }.sum() / n.toDouble()
        val tamYerinde: Int get() = siralama.withIndex().count { (i, s) -> i == n - s }
        fun ilk10Isabet(): Int = siralama.take(10).count { it > n - 10 }
        fun son10Isabet(): Int = siralama.takeLast(10).count { it <= 10 }
    }

    private fun emreKostur(n: Int, tohum: Long): Sonuc {
        val songs = sarkilar(n, tohum)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val hepsi = mutableListOf<Match>()
        var tur = 0
        while (tur < 200) {
            val p = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!p.canContinue || p.matches.isEmpty()) break
            tur++
            val oynanan = oynat(p.matches.map { it.copy(round = tur) })
            hepsi.addAll(oynanan)
            val bye = state.teams.find { t ->
                oynanan.none { it.songId1 == t.song.id || it.songId2 == t.song.id }
            }
            state = RankingEngine.processCorrectEmreResults(
                state, oynanan, bye, allCompletedMatches = hepsi
            )
        }
        val sira = RankingEngine.calculateCorrectEmreResults(state)
            .sortedBy { it.position }.map { it.songId.toInt() }
        return Sonuc(sira, tur, hepsi.size)
    }

    private fun swissKostur(n: Int, tohum: Long): Sonuc {
        val songs = sarkilar(n, tohum)
        val hepsi = mutableListOf<Match>()
        var tur = 0
        while (tur < SwissSystem.recommendedRoundCount(n)) {
            val state = SwissSystem.computeState(songs, hepsi)
            val p = SwissSystem.createNextRound(state, hepsi)
            if (!p.canContinue || p.matches.isEmpty()) break
            tur++
            hepsi.addAll(oynat(p.matches.map { it.copy(round = tur) }))
        }
        val sira = SwissSystem.calculateResults(songs, hepsi)
            .sortedBy { it.position }.map { it.songId.toInt() }
        return Sonuc(sira, tur, hepsi.size)
    }

    private fun rapor(ad: String, s: Sonuc) {
        println("%-22s tur=%-3d maç=%-5d ortSapma=%-6.2f tamYerinde=%d/%d ilk10=%d/10 son10=%d/10"
            .format(ad, s.turlar, s.maclar, s.ortSapma, s.tamYerinde, s.n,
                s.ilk10Isabet(), s.son10Isabet()))
    }

    @Test
    fun n100_emreVeSwiss_kaliteKarsilastirmasi() {
        // Üç ayrı tohumla — tek tohumun şansına hüküm kurulmaz
        val tohumlar = listOf(7L, 42L, 20260831L)
        val emreler = tohumlar.map { emreKostur(100, it) }
        val swissler = tohumlar.map { swissKostur(100, it) }

        println("=== n=100, üç tohum ===")
        emreler.forEachIndexed { i, s -> rapor("EMRE tohum=${tohumlar[i]}", s) }
        swissler.forEachIndexed { i, s -> rapor("SWISS tohum=${tohumlar[i]}", s) }

        // Bütünlük: her iki motor da tüm öğeleri tam bir kez sıralamalı
        (emreler + swissler).forEach {
            assertEquals(100, it.siralama.size)
            assertEquals(100, it.siralama.toSet().size)
        }

        val emreOrt = emreler.map { it.ortSapma }.average()
        val swissOrt = swissler.map { it.ortSapma }.average()
        println("ORTALAMA: emre=%.2f swiss=%.2f".format(emreOrt, swissOrt))

        // İkisi de rastgeleden (~33) belirgin iyi olmalı
        assertTrue("Emre sıralaması rastgeleden iyi değil: $emreOrt", emreOrt < 15.0)
        assertTrue("Swiss sıralaması rastgeleden iyi değil: $swissOrt", swissOrt < 25.0)
    }

    @Test
    fun n128_ikiMotor_veIkiliKarsilastirmaMaliyeti() {
        val e = emreKostur(128, 99L)
        val s = swissKostur(128, 99L)
        println("=== n=128 ===")
        rapor("EMRE", e)
        rapor("SWISS", s)
        println("MERGE_SORT tahmini soru: " +
            PairwiseComparisonSort.estimatedTotalComparisons(128))
        assertEquals(128, e.siralama.toSet().size)
        assertEquals(128, s.siralama.toSet().size)
    }
}
