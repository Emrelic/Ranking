package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSiralamaSistemi
import com.example.ranking.ranking.HibritKanitSistemi
import com.example.ranking.ranking.PairwiseComparisonSort
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.system.measureNanoTime

/**
 * PERFORMANS ÖLÇÜMÜ — büyük listede tur/soru üretim hızı (görev: ranking-7d
 * koordinatör, işçi: SONNET HAZIR KITA 101, 2026-09-01).
 *
 * Ölçülen: EmreSiralamaSistemi.createNextRoundMatches ve
 * HibritKanitSistemi.createNextRoundMatches çağrılarının TUR BAŞINA süresi
 * (System.nanoTime); PairwiseComparisonSort.createNextComparisonMatch
 * çağrılarının SORU BAŞINA süresi — karşılaştırma temeli (baseline).
 * n=200/350/500/750 tam turnuva koşumunda, JVM ısınması ölçüme katılmadan.
 *
 * ⚠️ Bu JVM (masaüstü) ölçümüdür, Android/ART değil — mutlak süreler telefonda
 * farklı olur; burada aranan ALGORİTMİK BÜYÜME EĞİLİMİ (n arttıkça süre nasıl
 * patlıyor) ve motorlar arası GÖRECELİ kıyastır.
 *
 * Bu bir DOĞRULUK testi değil, ÖLÇÜM testidir: eşik/süre aşımı testi
 * KIRMIZI yapmaz (yalnız raporlanır ve konsola yazılır). Yalnız sıralamanın
 * hâlâ DOĞRU çıkıp çıkmadığı gerçek bir sağlık kontrolüdür — o bozulursa test
 * KIRMIZI olur.
 */
class PerformansOlcumTest {

    /** Görev tanımlı eşik: tek tur/soru üretimi bunu aşarsa raporda işaretlenir. */
    private val ESIK_NS = 2_000_000_000L

    /** Güvenlik sınırı: tek tur/soru bunu aşarsa o koşum yarıda kesilir (donmayı önler). */
    private val TUR_IPTAL_NS = 30_000_000_000L

    /** Test yöntemi başına toplam bütçe: bunu aşan boyutlar denenmeden atlanır. */
    private val BUTCE_NS = 6L * 60 * 1_000_000_000L

    private fun sarkilar(n: Int): List<Song> =
        (1..n).map { Song(id = it.toLong(), listId = 1L, name = "Oge-$it") }

    /** guc[idx] = songs[idx]'in gerçek gücü — hakem bunu kullanır (tutarlı, döngüsüz). */
    private fun guc(n: Int, tohum: Long): LongArray =
        (1..n).shuffled(Random(tohum)).map { it.toLong() }.toLongArray()

    private fun kazanan(g: LongArray, s1: Long, s2: Long): Long =
        if (g[(s1 - 1).toInt()] >= g[(s2 - 1).toInt()]) s1 else s2

    private data class Adim(val no: Int, val ns: Long, val mac: Int)

    private class Sonuc(val ad: String, val n: Int) {
        val adimlar = ArrayList<Adim>()
        var toplamMac = 0
        var kesildi = false
        var dogru: Boolean? = null
        val toplamNs get() = adimlar.sumOf { it.ns }
        val enYavas get() = adimlar.maxByOrNull { it.ns }
        val ortalamaNs get() = if (adimlar.isEmpty()) 0.0 else toplamNs.toDouble() / adimlar.size
    }

    private fun ms(ns: Long) = "%.1f".format(ns / 1_000_000.0)
    private fun ms(ns: Double) = "%.1f".format(ns / 1_000_000.0)

    /** EMRE_SIRALAMA / HIBRIT: bir tam turnuva, TUR bazlı ölçüm. */
    private fun turBazliKostur(
        ad: String, n: Int, tohum: Long,
        turUret: (List<Song>, List<Match>) -> List<Match>,
        finalSira: (List<Song>, List<Match>) -> List<Long>
    ): Sonuc {
        val sonuc = Sonuc(ad, n)
        val ss = sarkilar(n)
        val g = guc(n, tohum)
        var tamamlanan = listOf<Match>()
        var sonrakiId = 1L
        var tur = 0
        val baslangic = System.nanoTime()
        while (true) {
            if (System.nanoTime() - baslangic > BUTCE_NS) { sonuc.kesildi = true; break }
            var maclar: List<Match> = emptyList()
            val ns = measureNanoTime { maclar = turUret(ss, tamamlanan) }
            tur++
            sonuc.adimlar.add(Adim(tur, ns, maclar.size))
            if (maclar.isEmpty()) break
            val tamam = maclar.map {
                it.copy(id = sonrakiId++, winnerId = kazanan(g, it.songId1, it.songId2), isCompleted = true)
            }
            tamamlanan = tamamlanan + tamam
            sonuc.toplamMac += tamam.size
            if (ns > TUR_IPTAL_NS) { sonuc.kesildi = true; break }
            check(tur < 3000) { "$ad n=$n: tur patladı (sonsuz döngü şüphesi)" }
        }
        if (!sonuc.kesildi) {
            val sira = finalSira(ss, tamamlanan)
            val beklenen = (0 until n).sortedByDescending { g[it] }.map { ss[it].id }
            sonuc.dogru = sira == beklenen
        }
        return sonuc
    }

    /** MERGE_SORT: bir tam sıralama, SORU bazlı ölçüm (karşılaştırma temeli). */
    private fun soruBazliKostur(n: Int, tohum: Long): Sonuc {
        val sonuc = Sonuc("MERGE_SORT", n)
        val ss = sarkilar(n)
        val g = guc(n, tohum)
        var tamamlanan = listOf<Match>()
        var sonrakiId = 1L
        var soru = 0
        val baslangic = System.nanoTime()
        while (true) {
            if (System.nanoTime() - baslangic > BUTCE_NS) { sonuc.kesildi = true; break }
            var m: Match? = null
            val ns = measureNanoTime { m = PairwiseComparisonSort.createNextComparisonMatch(ss, tamamlanan) }
            soru++
            val mm = m
            sonuc.adimlar.add(Adim(soru, ns, if (mm != null) 1 else 0))
            if (mm == null) break
            val tamam = mm.copy(id = sonrakiId++, winnerId = kazanan(g, mm.songId1, mm.songId2), isCompleted = true)
            tamamlanan = tamamlanan + tamam
            sonuc.toplamMac++
            if (ns > TUR_IPTAL_NS) { sonuc.kesildi = true; break }
            check(soru < 200_000) { "MERGE_SORT n=$n: soru patladı (sonsuz döngü şüphesi)" }
        }
        if (!sonuc.kesildi) {
            val state = PairwiseComparisonSort.computeState(ss, tamamlanan)
            val beklenen = (0 until n).sortedByDescending { g[it] }.map { ss[it].id }
            sonuc.dogru = state.sortedIds == beklenen
        }
        return sonuc
    }

    /** JIT ısınması: küçük n ile üç motoru da bir kez koştur, ölçüme KATMA. */
    private fun isinmaKostur() {
        turBazliKostur("ISINMA-EMRE", 40, 1L,
            { s, c -> EmreSiralamaSistemi.createNextRoundMatches(s, c) },
            { s, c -> EmreSiralamaSistemi.calculateResults(s, c).sortedBy { it.position }.map { it.songId } })
        turBazliKostur("ISINMA-HIBRIT", 40, 1L,
            { s, c -> HibritKanitSistemi.createNextRoundMatches(s, c) },
            { s, c -> HibritKanitSistemi.calculateResults(s, c).sortedBy { it.position }.map { it.songId } })
        soruBazliKostur(40, 1L)
    }

    private fun raporSatiri(s: Sonuc): String {
        val birim = if (s.ad == "MERGE_SORT") "soru" else "tur"
        val esikAsan = s.adimlar.firstOrNull { it.ns > ESIK_NS }
        return buildString {
            append("n=${s.n} ${s.ad}: ${s.adimlar.size} $birim, ${s.toplamMac} maç, ")
            append("toplam ${ms(s.toplamNs)}ms, ort ${ms(s.ortalamaNs)}ms, ")
            append("en yavaş ${s.enYavas?.let { "#${it.no}=${ms(it.ns)}ms" } ?: "-"}")
            append(if (s.dogru == false) " | 🔴 SIRALAMA YANLIŞ" else "")
            append(if (s.kesildi) " | ⚠️ KESİLDİ (süre sınırı)" else "")
            append(if (esikAsan != null) " | 2sn eşiği $birim #${esikAsan.no}'de aşıldı" else " | eşik aşılmadı")
        }
    }

    @Test
    fun performansOlcumu() {
        isinmaKostur()

        val boyutlar = listOf(200, 350, 500, 750)
        val tohum = 12345L
        val testBaslangic = System.nanoTime()
        val hatasizlarBozuldu = ArrayList<String>()

        for (n in boyutlar) {
            if (System.nanoTime() - testBaslangic > BUTCE_NS) {
                println("n=$n: DENENMEDİ (test bütçesi doldu)")
                continue
            }
            val emre = turBazliKostur("EMRE_SIRALAMA", n, tohum,
                { s, c -> EmreSiralamaSistemi.createNextRoundMatches(s, c) },
                { s, c -> EmreSiralamaSistemi.calculateResults(s, c).sortedBy { it.position }.map { it.songId } })
            println(raporSatiri(emre))
            if (emre.dogru == false) hatasizlarBozuldu.add("EMRE_SIRALAMA n=$n")

            val hibrit = turBazliKostur("HIBRIT", n, tohum,
                { s, c -> HibritKanitSistemi.createNextRoundMatches(s, c) },
                { s, c -> HibritKanitSistemi.calculateResults(s, c).sortedBy { it.position }.map { it.songId } })
            println(raporSatiri(hibrit))
            if (hibrit.dogru == false) hatasizlarBozuldu.add("HIBRIT n=$n")

            val merge = soruBazliKostur(n, tohum)
            println(raporSatiri(merge))
            if (merge.dogru == false) hatasizlarBozuldu.add("MERGE_SORT n=$n")
        }

        assertTrue("Sıralama doğruluğu bozuldu: $hatasizlarBozuldu", hatasizlarBozuldu.isEmpty())
    }
}
