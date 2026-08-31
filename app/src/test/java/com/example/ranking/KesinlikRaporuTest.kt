package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keskinlik raporunun sınavı + "kaç turda ne keskinlik" eğrisi.
 *
 * Gerçek sıra bilinen senaryoda (1..100, büyük kazanır) tur tur ilerlenir;
 * her durakta hem raporun keskinlik yüzdesi hem GERÇEK sapma ölçülür.
 * Böylece raporun dürüst olup olmadığı da görülür: rapor yükselirken
 * gerçek sapma düşmeli.
 */
class KesinlikRaporuTest {

    private var mid = 1L
    private fun oynat(maclar: List<Match>) = maclar.map {
        it.copy(id = mid++, winnerId = maxOf(it.songId1, it.songId2), isCompleted = true)
    }

    private fun sarkilar(n: Int): List<Song> =
        (1..n).shuffled(java.util.Random(31L)).mapIndexed { i, s ->
            Song(id = s.toLong(), name = "$s", artist = "", album = "",
                trackNumber = i + 1, listId = 1L)
        }

    private fun gercekSapma(state: EmreSystemCorrect.EmreState): Double {
        val sira = RankingEngine.calculateCorrectEmreResults(state)
            .sortedBy { it.position }.map { it.songId.toInt() }
        val n = sira.size
        return sira.mapIndexed { i, s -> kotlin.math.abs(i - (n - s)) }.sum() / n.toDouble()
    }

    @Test
    fun turKeskinlikEgrisi_raporGercekleUyumlu() {
        val songs = sarkilar(100)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val hepsi = mutableListOf<Match>()
        val duraklar = setOf(5, 7, 10, 15, 20, 30, 40)
        val olcumler = mutableListOf<Triple<Int, Int, Double>>() // tur, keskinlik%, sapma

        var tur = 0
        while (tur < 60) {
            val p = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!p.canContinue || p.matches.isEmpty()) break
            tur++
            val o = oynat(p.matches.map { it.copy(round = tur) })
            hepsi.addAll(o)
            val bye = state.teams.find { t -> o.none { it.songId1 == t.song.id || it.songId2 == t.song.id } }
            state = RankingEngine.processCorrectEmreResults(state, o, bye, allCompletedMatches = hepsi)

            if (tur in duraklar) {
                val r = EmreSystemCorrect.kesinlikRaporu(state)
                olcumler.add(Triple(tur, r.genelYuzde, gercekSapma(state)))
                println("tur=%-3d keskinlik=%%%-4d (üst %%%d orta %%%d alt %%%d)  GERÇEK sapma=%.2f"
                    .format(tur, r.genelYuzde, r.ustYuzde, r.ortaYuzde, r.altYuzde, gercekSapma(state)))
            }
        }
        val son = EmreSystemCorrect.kesinlikRaporu(state)
        println("SON: tur=$tur keskinlik=%${son.genelYuzde} sapma=${"%.2f".format(gercekSapma(state))}")

        assertTrue("En az 4 durak ölçülmeli", olcumler.size >= 4)
        // Rapor artmalı, gerçek sapma azalmalı (ilk durak → son durak)
        assertTrue("Keskinlik artmalı: ${olcumler.map { it.second }}",
            olcumler.last().second > olcumler.first().second)
        assertTrue("Gerçek sapma azalmalı: ${olcumler.map { it.third }}",
            olcumler.last().third < olcumler.first().third)
    }

    @Test
    fun sinirDurumlar() {
        // n=1: rapor çökmemeli
        val tek = EmreSystemCorrect.initializeEmreTournament(sarkilar(1))
        assertEquals(100, EmreSystemCorrect.kesinlikRaporu(tek).genelYuzde)

        // Hiç maç oynanmadan: herkes 0 puan, hiç maç yok → keskinlik 0 olmalı
        val bos = EmreSystemCorrect.initializeEmreTournament(sarkilar(10))
        val r = EmreSystemCorrect.kesinlikRaporu(bos)
        assertEquals("Maçsız turnuvada hiçbir komşuluk kanıtlı olamaz", 0, r.genelYuzde)
        assertEquals(9, r.toplamSinir)
        assertEquals(4, r.onerilenTur) // ceil(log2 10)
    }
}
