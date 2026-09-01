package com.example.ranking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.BitSet

/**
 * EMRE SIRALAMA SİSTEMİ — tasarım ölçümü (kullanıcı fikri, 2026-09-01).
 *
 * Prensip: her maç sonucu bir ÜSTÜNLÜK AĞACINA (kısmi sıralama) işlenir ve
 * geçişlilik kuralı uygulanır (a>b, b>c ⇒ a>c — bu çift bir daha sorulmaz).
 * Her turda, "hangi eşleşme hangi olasılıkla ne kadar belirsizlik giderir"
 * analizi yapılır ve beklenen kazancı en yüksek AYRIK eşleşmeler oynatılır:
 *
 *   Kazanç(a>b) = (a'nın üstündekiler + a) × (b'nin altındakiler + b)
 *                 içinde HENÜZ bilinmeyen çift sayısı
 *   Beklenen    = p(a>b)·Kazanç(a>b) + p(b>a)·Kazanç(b>a)
 *
 * p, tahmini konumlardan (bilinen üst/alt sayılarının orta noktası) lojistik
 * modelle kestirilir. "Ya tutarsa" etkisi doğal çıkar: düşük olasılıklı ama
 * dev kazançlı eşleşmeler, beklenen değerde güvenli küçük eşleşmeleri
 * geçebilir. Bilinmeyen çift kalmayınca sıralama TAMDIR (tutarlı hakemle).
 *
 * Bilimsel akrabaları: Lewis Carroll 1883 (şampiyonun elediklerinden 2.lik),
 * Kislitsyn 1964 (2. sıra alt sınırı n+⌈log2 n⌉-2), Ford–Johnson 1959
 * (ilk tur = n/2 ikili maç), Fredman 1976 / Kahn–Saks 1984 (kısmi bilgiyle
 * sıralama, lineer uzantıyı en iyi bölen soruyu seçme ilkesi).
 */
class EmreSiralamaSistemiOlcumTest {

    /** Eşleşme seçim stratejisi: (pKazanma, kazançA, kazançB) → skor. */
    enum class Strateji { BEKLENEN, GARANTI, DENGE }

    /** Üstünlük ağacı + kazanç analizli eşleştirici. */
    private class EmreSiralama(val n: Int, val strateji: Strateji) {
        // ust[i] = i'den büyük OLDUĞU BİLİNEN öğelerin bit kümesi
        // alt[i] = i'nin büyük olduğu bilinenler
        val ust = Array(n) { BitSet(n) }
        val alt = Array(n) { BitSet(n) }
        var mac = 0
        var tur = 0

        fun biliniyor(i: Int, j: Int) = alt[i].get(j) || ust[i].get(j)

        /** w > l sonucunu geçişli kapanışıyla işler. */
        fun sonucIsle(w: Int, l: Int) {
            if (alt[w].get(l)) return
            val kazananlar = ust[w].clone() as BitSet; kazananlar.set(w)
            val kaybedenler = alt[l].clone() as BitSet; kaybedenler.set(l)
            var x = kazananlar.nextSetBit(0)
            while (x >= 0) {
                alt[x].or(kaybedenler); alt[x].clear(x)
                x = kazananlar.nextSetBit(x + 1)
            }
            var y = kaybedenler.nextSetBit(0)
            while (y >= 0) {
                ust[y].or(kazananlar); ust[y].clear(y)
                y = kaybedenler.nextSetBit(y + 1)
            }
        }

        fun bilinmeyenCiftSayisi(): Int {
            var s = 0
            for (i in 0 until n) s += (n - 1) - alt[i].cardinality() - ust[i].cardinality()
            return s / 2
        }

        /** a>b çıkarsa yeni öğrenilecek çift sayısı (tam hesap, bitset farkıyla). */
        fun kazanc(a: Int, b: Int): Int {
            val ustler = ust[a].clone() as BitSet; ustler.set(a)
            val altlar = alt[b].clone() as BitSet; altlar.set(b)
            var toplam = 0
            var x = ustler.nextSetBit(0)
            while (x >= 0) {
                val yeni = altlar.clone() as BitSet
                yeni.andNot(alt[x])   // zaten bilinenler düşülür
                yeni.clear(x)
                toplam += yeni.cardinality()
                x = ustler.nextSetBit(x + 1)
            }
            return toplam
        }

        /** Tahmini konum: bilinen üst sayısı ile (n - bilinen alt) aralığının ortası. */
        fun ortaNokta(i: Int): Double {
            val enIyi = ust[i].cardinality() + 1
            val enKotu = n - alt[i].cardinality()
            return (enIyi + enKotu) / 2.0
        }

        fun pKazanir(a: Int, b: Int): Double {
            // Küçük ortaNokta = daha iyi konum tahmini
            val fark = ortaNokta(b) - ortaNokta(a)
            return 1.0 / (1.0 + Math.exp(-fark / (n / 8.0)))
        }

        /**
         * Bir turun eşleşmeleri: bilinmeyen çiftler beklenen kazanca göre
         * sıralanır, ayrık olanlar üstten toplanır.
         */
        fun turEslesmeleri(): List<Pair<Int, Int>> {
            data class Aday(val a: Int, val b: Int, val beklenen: Double)
            val adaylar = ArrayList<Aday>()
            for (i in 0 until n) for (j in i + 1 until n) {
                if (biliniyor(i, j)) continue
                val p = pKazanir(i, j)
                val ga = kazanc(i, j).toDouble()
                val gb = kazanc(j, i).toDouble()
                val e = when (strateji) {
                    // "Ya tutarsa": düşük olasılıklı dev kazanç beklenen değeri şişirir
                    Strateji.BEKLENEN -> p * ga + (1 - p) * gb
                    // Kahn–Saks ruhu: iki sonucun da GARANTİLEDİĞİ kazanç
                    Strateji.GARANTI -> minOf(ga, gb)
                    // Cevabı belirsiz VE toplam potansiyeli yüksek soruları sever
                    Strateji.DENGE -> p * (1 - p) * (ga + gb)
                }
                adaylar.add(Aday(i, j, e))
            }
            if (adaylar.isEmpty()) return emptyList()
            adaylar.sortByDescending { it.beklenen }
            val kullanildi = BooleanArray(n)
            val secilen = ArrayList<Pair<Int, Int>>()
            for (aday in adaylar) {
                if (kullanildi[aday.a] || kullanildi[aday.b]) continue
                kullanildi[aday.a] = true; kullanildi[aday.b] = true
                secilen.add(aday.a to aday.b)
            }
            return secilen
        }

        /** Bilinmeyen çift kalmayana dek oynatır; hakem: gerçek değere göre. */
        fun kostur(deger: IntArray): Pair<Int, Int> {
            while (true) {
                val eslesmeler = turEslesmeleri()
                if (eslesmeler.isEmpty()) break
                tur++
                for ((a, b) in eslesmeler) {
                    if (biliniyor(a, b)) continue // aynı turun önceki maçı çözmüş olabilir
                    mac++
                    if (deger[a] > deger[b]) sonucIsle(a, b) else sonucIsle(b, a)
                }
                check(tur < 10_000) { "tur patladı" }
            }
            return tur to mac
        }

        /** Tam sıralama: altında bilinen öğe sayısı (0..n-1, hepsi farklı). */
        fun siralama(): List<Int> =
            (0 until n).sortedByDescending { alt[it].cardinality() }
    }

    private fun kosturVeOlc(
        n: Int, tohum: Long, strateji: Strateji, etiket: String
    ): Triple<Int, Int, Boolean> {
        val giris = (1..n).shuffled(java.util.Random(tohum))
        val deger = giris.toIntArray()
        val motor = EmreSiralama(n, strateji)
        val (tur, mac) = motor.kostur(deger)
        val sira = motor.siralama().map { deger[it] }
        val dogru = sira == (n downTo 1).toList()
        println("%-28s tur=%-4d maç=%-5d doğru=%s".format(etiket, tur, mac, dogru))
        return Triple(tur, mac, dogru)
    }

    @Test
    fun stratejiYarismasi_n200() {
        // Kıyas çıtaları (tohum 777): HİBRİT 1906 maç / MERGE_SORT 1265 soru
        for (s in Strateji.entries) {
            val (_, mac, dogru) = kosturVeOlc(200, 777L, s, "t777 $s")
            assertTrue("$s: sıralama tam değil", dogru)
            // Kazanan stratejinin maliyet mühürü (ölçüldü: 1364 maç / 18 tur;
            // HİBRİT 1906, MERGE_SORT 1265). Sapma tasarım bozulması demektir.
            if (s == Strateji.GARANTI) assertTrue("GARANTI maliyeti patladı: $mac", mac < 1600)
        }
        for (s in Strateji.entries) {
            val (_, mac, dogru) = kosturVeOlc(200, 200L, s, "t200 $s")
            assertTrue("$s: sıralama tam değil", dogru)
            if (s == Strateji.GARANTI) assertTrue("GARANTI maliyeti patladı: $mac", mac < 1600)
        }
    }

    @Test
    fun n100_ucTohum_garanti() {
        for (t in listOf(7L, 42L, 20260831L)) {
            val (_, _, dogru) = kosturVeOlc(100, t, Strateji.GARANTI, "n100 t$t GARANTI")
            assertTrue(dogru)
        }
        // MERGE_SORT kıyas: n=100 ≈ 573 soru
    }

    @Test
    fun kucukBoyutlar_dogruluk() {
        for (n in 2..12) {
            val (_, _, dogru) = kosturVeOlc(n, 50L + n, Strateji.GARANTI, "n=$n")
            assertTrue("n=$n yanlış", dogru)
        }
    }
}
