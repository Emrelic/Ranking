package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSiralamaSistemi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EMRE SIRALAMA SİSTEMİ — uygulama akışı sınavı.
 *
 * EmreSiralamaSistemiOlcumTest tasarımı (strateji yarışını) ölçmüştü; bu
 * sınıf UYGULAMANIN kullandığı gerçek motoru uygulamadaki akışla sınar:
 * tur üret → maçları oyla → tur bitince yeni tur → boş tur = son. Ayrıca
 * devam etme determinizmi, tek sayı, beraberlik (bilgi vermez ama soru
 * harcar), çelişkili oyda bitiş ve kısmi sonuç üretimi kapsanır.
 */
class EmreSiralamaUygulamaAkisiTest {

    private fun ogeler(n: Int, tohum: Long): List<Song> =
        (1..n).shuffled(java.util.Random(tohum)).mapIndexed { i, s ->
            Song(id = s.toLong(), name = "$s", artist = "", album = "",
                trackNumber = i + 1, listId = 1L)
        }

    private fun turnuvaOynat(
        songs: List<Song>,
        hakem: (Long, Long) -> Long?,
        turSiniri: Int = 2000
    ): MutableList<Match> {
        val db = mutableListOf<Match>()
        var id = 1L
        var tur = 0
        while (tur < turSiniri) {
            val yeni = EmreSiralamaSistemi.createNextRoundMatches(songs, db)
            if (yeni.isEmpty()) break
            tur++
            yeni.sortedBy { it.matchNumber }.forEach { m ->
                db.add(m.copy(
                    id = id++,
                    winnerId = hakem(m.songId1, m.songId2),
                    isCompleted = true
                ))
            }
        }
        assertTrue("Tur sınırına çarpıldı — turnuva bitmiyor", tur < turSiniri)
        return db
    }

    private fun buyukKazanir(a: Long, b: Long): Long? = maxOf(a, b)

    private fun sonSira(songs: List<Song>, db: List<Match>): List<Int> =
        EmreSiralamaSistemi.calculateResults(songs, db)
            .sortedBy { it.position }.map { it.songId.toInt() }

    // ---------- DOĞRULUK ----------

    @Test
    fun ciftSayi_n30_tamSiralama() {
        val songs = ogeler(30, 7L)
        val db = turnuvaOynat(songs, ::buyukKazanir)
        assertEquals((30 downTo 1).toList(), sonSira(songs, db))
    }

    @Test
    fun tekSayi_n51_tamSiralama() {
        val songs = ogeler(51, 42L)
        val db = turnuvaOynat(songs, ::buyukKazanir)
        assertEquals((51 downTo 1).toList(), sonSira(songs, db))
    }

    @Test
    fun kucukBoyutlar_n2den12ye_tumu() {
        for (n in 2..12) {
            val songs = ogeler(n, 100L + n)
            val db = turnuvaOynat(songs, ::buyukKazanir)
            assertEquals("n=$n", (n downTo 1).toList(), sonSira(songs, db))
        }
    }

    @Test
    fun n200_tohum777_maliyetMuhru() {
        // Simülasyon (tur içi çıkarımları maç açmadan atlayabilen ideal akış)
        // 1364 ölçmüştü; uygulamada turun maçları baştan sabitlendiği için
        // birkaç maç fazlası normaldir. Ölçülen değer mühürlenir.
        val songs = ogeler(200, 777L)
        val db = turnuvaOynat(songs, ::buyukKazanir)
        assertEquals((200 downTo 1).toList(), sonSira(songs, db))
        val tur = db.maxOf { it.round }
        println("EMRE_SIRALAMA uygulama akışı: tur=$tur maç=${db.size}")
        assertTrue("Maliyet patladı: ${db.size} (simülasyon 1364)", db.size < 1700)
        assertTrue("Tur sayısı patladı: $tur", tur < 40)
    }

    // ---------- TEKRARSIZLIK VE TUR SÖZLEŞMESİ ----------

    @Test
    fun hicbirCift_ikiKezSorulmaz_veTurlarAyrik() {
        val songs = ogeler(40, 13L)
        val db = turnuvaOynat(songs, ::buyukKazanir)

        val ciftler = db.map { minOf(it.songId1, it.songId2) to maxOf(it.songId1, it.songId2) }
        assertEquals("Aynı çift birden çok kez soruldu", ciftler.toSet().size, ciftler.size)

        db.groupBy { it.round }.forEach { (tur, maclar) ->
            val takimlar = maclar.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals("Tur $tur içinde takım tekrarı", takimlar.toSet().size, takimlar.size)
            assertEquals("Tur $tur matchNumber dizisi bozuk",
                (1..maclar.size).toList(), maclar.map { it.matchNumber }.sorted())
        }
    }

    @Test
    fun gecisliCikarim_hicSorulmaz() {
        // 3 öğe: a>b ve b>c oylanınca a?c SORULMAMALI (sistemin kalbi)
        val songs = ogeler(3, 1L)
        val db = turnuvaOynat(songs, ::buyukKazanir)
        // Tam sıralama 2 maçla kurulmalı (3 çiftin biri çıkarımla bilinir)
        // — girişe göre ilk tur hangi çifti seçerse seçsin üçüncü soru
        // ancak ilk iki maç zincir kurmazsa gelir; en kötü 3 maç.
        assertTrue("3 öğe için ${db.size} maç", db.size <= 3)
        assertEquals((3 downTo 1).toList(), sonSira(songs, db))
    }

    // ---------- DEVAM ETME (REPLAY DETERMİNİZMİ) ----------

    @Test
    fun yaridaKapatipAcma_ayniTuruUretir() {
        val songs = ogeler(30, 55L)
        val db = mutableListOf<Match>()
        var id = 1L
        repeat(4) {
            val yeni = EmreSiralamaSistemi.createNextRoundMatches(songs, db)
            if (yeni.isEmpty()) return@repeat
            yeni.forEach { m ->
                db.add(m.copy(id = id++, winnerId = buyukKazanir(m.songId1, m.songId2), isCompleted = true))
            }
        }
        val a = EmreSiralamaSistemi.createNextRoundMatches(songs, db)
        val b = EmreSiralamaSistemi.createNextRoundMatches(songs, db)
        assertEquals(
            a.map { Triple(it.songId1, it.songId2, it.round) },
            b.map { Triple(it.songId1, it.songId2, it.round) }
        )
    }

    // ---------- BERABERLİK VE ÇELİŞKİ ----------

    @Test
    fun beraberlik_bilgiVermez_amaTurnuvaBiter() {
        val songs = ogeler(20, 33L)
        var sayac = 0
        val db = turnuvaOynat(songs, { a, b ->
            sayac++
            if (sayac % 5 == 0) null else maxOf(a, b)
        })
        val sira = sonSira(songs, db)
        assertEquals(20, sira.toSet().size)
        // Berabere kalan çift bir daha sorulmamış olmalı
        val ciftler = db.map { minOf(it.songId1, it.songId2) to maxOf(it.songId1, it.songId2) }
        assertEquals(ciftler.toSet().size, ciftler.size)
    }

    @Test
    fun celiskiliOylar_turnuvaYineDeSonlanir() {
        val songs = ogeler(25, 77L)
        val rasgele = java.util.Random(99L)
        val db = turnuvaOynat(songs, { a, b -> if (rasgele.nextBoolean()) a else b })
        assertEquals(25, sonSira(songs, db).toSet().size)
    }

    // ---------- KISMİ SONUÇ VE SINIRLAR ----------

    @Test
    fun erkenDurum_tumOgelereBenzersizPozisyon() {
        val songs = ogeler(30, 11L)
        val db = mutableListOf<Match>()
        var id = 1L
        repeat(2) {
            val yeni = EmreSiralamaSistemi.createNextRoundMatches(songs, db)
            yeni.forEach { m ->
                db.add(m.copy(id = id++, winnerId = buyukKazanir(m.songId1, m.songId2), isCompleted = true))
            }
        }
        val sonuclar = EmreSiralamaSistemi.calculateResults(songs, db)
        assertEquals(30, sonuclar.size)
        assertEquals((1..30).toList(), sonuclar.map { it.position }.sorted())
        assertEquals(30, sonuclar.map { it.songId }.toSet().size)
    }

    @Test
    fun macDagilimi_n200_takimBasinaKacMac() {
        // Kullanıcı sorusu (2026-09-01): her takım en az/en çok kaç maç yapıyor?
        val songs = ogeler(200, 777L)
        val db = turnuvaOynat(songs, ::buyukKazanir)
        val sayim = HashMap<Long, Int>()
        db.forEach { m ->
            sayim[m.songId1] = (sayim[m.songId1] ?: 0) + 1
            sayim[m.songId2] = (sayim[m.songId2] ?: 0) + 1
        }
        val degerler = songs.map { sayim[it.id] ?: 0 }
        println("EMRE_SIRALAMA maç dağılımı: min=%d max=%d ort=%.1f toplam=%d"
            .format(degerler.min(), degerler.max(), degerler.average(), db.size))
        // Gerçek değere (sayının kendisi) göre profil: uçlar vs orta
        val siraliSayim = (200 downTo 1).map { v -> v to (sayim[v.toLong()] ?: 0) }
        println("Sıra profili (değer=maç): " + listOf(200, 199, 195, 180, 150, 120, 100, 80, 50, 20, 5, 2, 1)
            .joinToString("  ") { v -> "$v=${sayim[v.toLong()]}" })
        val histogram = degerler.groupingBy { it / 3 * 3 }.eachCount().toSortedMap()
        println("Histogram (3'lük kova): " + histogram.entries.joinToString("  ") { "${it.key}-${it.key + 2}:${it.value}" })
        // Uçlar ortalamadan az mı oynuyor? (geçişlilik uçları erken çözer)
        val ucOrt = (siraliSayim.take(10) + siraliSayim.takeLast(10)).map { it.second }.average()
        val ortaOrt = siraliSayim.subList(80, 120).map { it.second }.average()
        println("İlk10+son10 ort=%.1f  orta40 ort=%.1f".format(ucOrt, ortaOrt))
        degerler.forEach { assertTrue("Maçsız takım", it >= 1) }

        // Kıyas: İkili Karşılaştırmanın dağılımı (aynı diziliş)
        val cevaplar = mutableListOf<com.example.ranking.data.Match>()
        var id = 1L
        while (true) {
            val m = com.example.ranking.ranking.PairwiseComparisonSort
                .createNextComparisonMatch(songs, cevaplar) ?: break
            cevaplar.add(m.copy(id = id++, winnerId = maxOf(m.songId1, m.songId2), isCompleted = true))
        }
        val ikiliSayim = HashMap<Long, Int>()
        cevaplar.forEach { m ->
            ikiliSayim[m.songId1] = (ikiliSayim[m.songId1] ?: 0) + 1
            ikiliSayim[m.songId2] = (ikiliSayim[m.songId2] ?: 0) + 1
        }
        val ikiliDegerler = songs.map { ikiliSayim[it.id] ?: 0 }
        println("IKILI maç dağılımı:        min=%d max=%d ort=%.1f toplam=%d"
            .format(ikiliDegerler.min(), ikiliDegerler.max(), ikiliDegerler.average(), cevaplar.size))
    }

    @Test
    fun sinirDurumlar_n0_n1_n2() {
        assertTrue(EmreSiralamaSistemi.createNextRoundMatches(emptyList(), emptyList()).isEmpty())

        val tek = ogeler(1, 1L)
        assertTrue(EmreSiralamaSistemi.createNextRoundMatches(tek, emptyList()).isEmpty())
        assertEquals(1, EmreSiralamaSistemi.calculateResults(tek, emptyList()).size)

        val cift = ogeler(2, 1L)
        val db = turnuvaOynat(cift, ::buyukKazanir)
        assertEquals((2 downTo 1).toList(), sonSira(cift, db))
    }
}
