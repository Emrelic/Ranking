package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.PairwiseComparisonSort
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASARIM DENEMESİ — kullanıcının sorusu (2026-08-31):
 * "Hem çok iyi sıralama yapan hem maliyeti az olan, turnuva havasında
 *  nasıl bir sistem tasarlayabiliriz?"
 *
 * Aday: "İsviçre + Kanıt Turları" hibriti.
 *  Faz 1: k tur Geliştirilmiş İsviçre (kaba sıralama ucuza çıkar).
 *  Faz 2: sıralamada YAN YANA duran ama aralarında maç OLMAYAN çiftler
 *         eşleştirilir (tek/çift pariteyle, turda ayrık çiftler).
 *         Kaybeden aşağı düşer; daha önce oynanmış maç bedava kanıt sayılır.
 *         Bütün komşuluklar kanıtlanınca biter → tutarlı hakemle sonuç
 *         GARANTİLİ tam sıralıdır (odd-even transposition'ın hedefli hali).
 *
 * Bu test farklı faz-1 uzunluklarında toplam maliyeti ölçer ve
 * MERGE_SORT (teorik en iyi ~1264) ile düz İsviçre'ye kıyaslar.
 */
class HibritKanitSistemiTest {

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

    data class HibritSonuc(
        val siralama: List<Int>,
        val faz1Tur: Int, val faz1Mac: Int,
        val faz2Tur: Int, val faz2Mac: Int,
        val bedavaKanit: Int
    ) {
        val toplamMac get() = faz1Mac + faz2Mac
        val n get() = siralama.size
        val ortSapma: Double get() = siralama.mapIndexed { i, s ->
            kotlin.math.abs(i - (n - s))
        }.sum() / n.toDouble()
    }

    private fun hibritKostur(
        n: Int, tohum: Long, faz1Hedef: Int,
        adimlar: List<Int> = listOf(1) // 1 = düz komşu; ör. [16,8,4,2,1] = Shell tarzı
    ): HibritSonuc {
        val songs = sarkilar(n, tohum)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val hepsi = mutableListOf<Match>()
        var tur = 0
        while (tur < faz1Hedef) {
            val p = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!p.canContinue || p.matches.isEmpty()) break
            tur++
            val o = oynat(p.matches.map { it.copy(round = tur) })
            hepsi.addAll(o)
            val bye = state.teams.find { t ->
                o.none { it.songId1 == t.song.id || it.songId2 == t.song.id }
            }
            state = RankingEngine.processCorrectEmreResults(
                state, o, bye, allCompletedMatches = hepsi
            )
        }

        // Faz 1 çıkışı: kaba sıralama + oynanmış maç arşivi (kanıt deposu)
        val sira: MutableList<Long> =
            if (faz1Hedef == 0) songs.map { it.id }.toMutableList()
            else RankingEngine.calculateCorrectEmreResults(state)
                .sortedBy { it.position }.map { it.songId }.toMutableList()
        val kanit = HashMap<Pair<Long, Long>, Long>() // (küçükId, büyükId) -> kazanan
        hepsi.forEach { m ->
            val w = m.winnerId ?: return@forEach
            kanit[minOf(m.songId1, m.songId2) to maxOf(m.songId1, m.songId2)] = w
        }

        // Faz 2: kanıtsız eşleştirme; her adım mesafesi kendi içinde
        // sabitlenene dek parite dönüşümlü koşulur. Son adım 1 olduğu için
        // bitişte her komşuluk kanıtlıdır → tutarlı hakemle tam sıralama.
        var faz2Tur = 0
        var faz2Mac = 0
        var bedava = 0
        for (adim in adimlar) {
            var degisti = true
            while (degisti) {
                degisti = false
                for (parite in 0 until 2 * adim) { // ayrık çiftler: i ≡ parite (mod 2·adım)
                    var turdaMac = 0
                    var i = parite
                    while (i + adim < n) {
                        val ust = sira[i]; val alt = sira[i + adim]
                        val anahtar = minOf(ust, alt) to maxOf(ust, alt)
                        val kayitli = kanit[anahtar]
                        val kazanan: Long
                        if (kayitli == null) {
                            kazanan = maxOf(ust, alt) // hakem: büyük sayı kazanır
                            kanit[anahtar] = kazanan
                            faz2Mac++; turdaMac++
                        } else {
                            kazanan = kayitli
                            if (kazanan == alt) bedava++ // maçsız düzeltme
                        }
                        if (kazanan == alt) {
                            sira[i] = alt; sira[i + adim] = ust; degisti = true
                        }
                        i += 2 * adim
                    }
                    if (turdaMac > 0) faz2Tur++
                }
            }
        }

        return HibritSonuc(
            siralama = sira.map { it.toInt() },
            faz1Tur = tur, faz1Mac = hepsi.size,
            faz2Tur = faz2Tur, faz2Mac = faz2Mac, bedavaKanit = bedava
        )
    }

    private fun rapor(etiket: String, s: HibritSonuc) {
        println(("%-14s faz1=%2d tur/%4d maç  faz2=%3d tur/%4d maç  " +
            "TOPLAM=%4d maç  sapma=%.2f  bedavaKanıt=%d")
            .format(etiket, s.faz1Tur, s.faz1Mac, s.faz2Tur, s.faz2Mac,
                s.toplamMac, s.ortSapma, s.bedavaKanit))
    }

    private val shell200 = listOf(32, 16, 8, 4, 2, 1)
    private val shell100 = listOf(16, 8, 4, 2, 1)

    @Test
    fun n200_faz1UzunluguTaramasi() {
        println("=== n=200, tohum=200 (cihazdaki 34 no'lu listeyle aynı diziliş) ===")
        println("MERGE_SORT kıyas çıtası: ~%d soru, sapma 0"
            .format(PairwiseComparisonSort.estimatedTotalComparisons(200)))
        for (faz1 in listOf(0, 4, 6, 8, 10, 12, 15)) {
            val duz = hibritKostur(200, 200L, faz1)
            val shell = hibritKostur(200, 200L, faz1, shell200)
            rapor("f$faz1 düz", duz)
            rapor("f$faz1 shell", shell)
            // Tasarımın asıl iddiası: sonuç HER ayarda kusursuz
            assertEquals("faz1=$faz1 düz: tam sıralı değil",
                (200 downTo 1).toList(), duz.siralama)
            assertEquals("faz1=$faz1 shell: tam sıralı değil",
                (200 downTo 1).toList(), shell.siralama)
        }
    }

    @Test
    fun n200_inceAyar_adimDizisiVeKisaFaz1() {
        println("=== n=200 ince ayar: kısa faz1 × adım dizisi ===")
        val diziler = mapOf(
            "ikili" to listOf(32, 16, 8, 4, 2, 1),
            "knuth" to listOf(121, 40, 13, 4, 1),
            "ciura" to listOf(102, 45, 20, 9, 4, 1),
            "kisa" to listOf(20, 6, 2, 1)
        )
        for (faz1 in listOf(0, 2, 3, 4, 5)) {
            for ((ad, dizi) in diziler) {
                val s = hibritKostur(200, 200L, faz1, dizi)
                rapor("f$faz1 $ad", s)
                assertEquals((200 downTo 1).toList(), s.siralama)
            }
        }
    }

    @Test
    fun n200_hibritMaliyetSiniri() {
        // İnce ayar taramasının kazananı: 4 tur Emre + adımlar 20,6,2,1
        // (n=200 tohum=200'de 1668 maç ölçüldü; MERGE_SORT 1264'ün ~1.3 katı)
        val s = hibritKostur(200, 200L, 4, listOf(20, 6, 2, 1))
        rapor("ÖNERİLEN", s)
        assertEquals((200 downTo 1).toList(), s.siralama)
        // Hüküm mühürü: hibrit, Emre'nin sonuna kadar oynanmışından (10300)
        // kat kat ucuz kalmalı — bu sınır aşılırsa tasarım bozulmuş demektir
        assertTrue("Hibrit maliyeti patladı: ${s.toplamMac}", s.toplamMac < 2500)
    }

    @Test
    fun n100_ucTohum_kararlilik() {
        println("=== n=100, üç tohum, faz1=7, Shell adımlı ===")
        for (tohum in listOf(7L, 42L, 20260831L)) {
            val s = hibritKostur(100, tohum, 7, shell100)
            rapor("tohum=$tohum", s)
            assertEquals((100 downTo 1).toList(), s.siralama)
        }
        println("MERGE_SORT kıyas: ~%d soru"
            .format(PairwiseComparisonSort.estimatedTotalComparisons(100)))
    }
}
