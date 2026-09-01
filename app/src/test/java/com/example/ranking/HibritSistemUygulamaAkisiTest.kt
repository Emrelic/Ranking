package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.HibritKanitSistemi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HİBRİT İSVİÇRE — uygulama akışı sınavı.
 *
 * HibritKanitSistemiTest tasarımı simülasyonla ölçmüştü; bu sınıf UYGULAMANIN
 * kullandığı gerçek motoru (HibritKanitSistemi, replay tabanlı) uygulamadaki
 * akışla sınar: tur üret → maçları teker teker oyla → tur bitince yeni tur →
 * boş tur = turnuva sonu. Ayrıca yarıda devam etme determinizmi, tek sayıda
 * takım (bye yolu), beraberlik anlamı, çelişkili oyda bitiş garantisi ve
 * kısmi sonuç üretimi kapsanır.
 */
class HibritSistemUygulamaAkisiTest {

    private fun ogeler(n: Int, tohum: Long): List<Song> =
        (1..n).shuffled(java.util.Random(tohum)).mapIndexed { i, s ->
            Song(id = s.toLong(), name = "$s", artist = "", album = "",
                trackNumber = i + 1, listId = 1L)
        }

    /** Uygulama döngüsü: tur üret, maçları verilen hakemle oyla, biteneçek sür. */
    private fun turnuvaOynat(
        songs: List<Song>,
        hakem: (Long, Long) -> Long?,   // (üst, alt) -> kazanan id (null = beraberlik)
        turSiniri: Int = 5000
    ): MutableList<Match> {
        val db = mutableListOf<Match>()
        var id = 1L
        var tur = 0
        while (tur < turSiniri) {
            val yeni = HibritKanitSistemi.createNextRoundMatches(songs, db)
            if (yeni.isEmpty()) break
            tur++
            // Uygulamadaki gibi: maçlar kaydedilir, sonra sırayla oylanır
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
        HibritKanitSistemi.calculateResults(songs, db)
            .sortedBy { it.position }.map { it.songId.toInt() }

    // ---------- DOĞRULUK ----------

    @Test
    fun ciftSayi_n30_tamSiralama() {
        val songs = ogeler(30, 7L)
        val db = turnuvaOynat(songs, ::buyukKazanir)
        assertEquals((30 downTo 1).toList(), sonSira(songs, db))
    }

    @Test
    fun tekSayi_n51_byeYolu_tamSiralama() {
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
    fun n200_tohum777_simulasyonlaAyniMaliyet() {
        // Tasarım simülasyonu (AltiSistemVeriUretimiTest) aynı tohumda 1906
        // maç ölçmüştü; uygulama motoru aynı algoritmayı koştuğu için aynı
        // sayıyı vermeli. Sapma, motorla simülasyonun ayrıştığını gösterir.
        val songs = ogeler(200, 777L)
        val db = turnuvaOynat(songs, ::buyukKazanir)
        assertEquals((200 downTo 1).toList(), sonSira(songs, db))
        assertEquals("Maç sayısı simülasyondan saptı", 1906, db.size)
    }

    // ---------- TEKRARSIZLIK VE TUR SÖZLEŞMESİ ----------

    @Test
    fun hicbirCift_ikiKezEslesmez_veTurlarAyrik() {
        val songs = ogeler(40, 13L)
        val db = turnuvaOynat(songs, ::buyukKazanir)

        val ciftler = db.map { minOf(it.songId1, it.songId2) to maxOf(it.songId1, it.songId2) }
        assertEquals("Aynı çift birden çok kez eşleşti", ciftler.toSet().size, ciftler.size)

        // Bir turda hiçbir takım iki kez oynamaz
        db.groupBy { it.round }.forEach { (tur, maclar) ->
            val takimlar = maclar.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals("Tur $tur içinde takım tekrarı", takimlar.toSet().size, takimlar.size)
        }

        // matchNumber her turda 1'den başlar ve ardışıktır (faz 2 turları)
        db.filter { it.round > HibritKanitSistemi.FAZ1_TUR }
            .groupBy { it.round }.forEach { (tur, maclar) ->
                assertEquals("Tur $tur matchNumber dizisi bozuk",
                    (1..maclar.size).toList(), maclar.map { it.matchNumber }.sorted())
            }
    }

    // ---------- DEVAM ETME (REPLAY DETERMİNİZMİ) ----------

    @Test
    fun yaridaKapatipAcma_ayniTuruUretir() {
        val songs = ogeler(30, 55L)
        val db = mutableListOf<Match>()
        var id = 1L
        // 6 tur oyna
        repeat(6) {
            val yeni = HibritKanitSistemi.createNextRoundMatches(songs, db)
            if (yeni.isEmpty()) return@repeat
            yeni.forEach { m ->
                db.add(m.copy(id = id++, winnerId = buyukKazanir(m.songId1, m.songId2), isCompleted = true))
            }
        }
        // "Uygulama kapandı, açıldı": aynı kayıtlarla iki çağrı aynı turu vermeli
        val a = HibritKanitSistemi.createNextRoundMatches(songs, db)
        val b = HibritKanitSistemi.createNextRoundMatches(songs, db)
        assertEquals(
            a.map { Triple(it.songId1, it.songId2, it.round) },
            b.map { Triple(it.songId1, it.songId2, it.round) }
        )
    }

    @Test
    fun turYarimKaldiginda_kalanCiftlerSonrakiCagridaGelir() {
        val songs = ogeler(30, 91L)
        val db = mutableListOf<Match>()
        var id = 1L
        val ilkTur = HibritKanitSistemi.createNextRoundMatches(songs, db)
        // Turun yalnız YARISI oylanır (kalanlar veritabanında yok sayılır —
        // uygulamada loadNextMatch onları servis eder, motor çağrılmaz;
        // burada motorun kısmi kayıtla determinizmi sınanır)
        ilkTur.take(ilkTur.size / 2).forEach { m ->
            db.add(m.copy(id = id++, winnerId = buyukKazanir(m.songId1, m.songId2), isCompleted = true))
        }
        val devam = HibritKanitSistemi.createNextRoundMatches(songs, db)
        // Oylanmış çiftler bir daha istenmez
        val oylanan = db.map { minOf(it.songId1, it.songId2) to maxOf(it.songId1, it.songId2) }.toSet()
        devam.forEach { m ->
            val c = minOf(m.songId1, m.songId2) to maxOf(m.songId1, m.songId2)
            assertTrue("Oylanmış çift yeniden istendi: $c", c !in oylanan)
        }
    }

    // ---------- BERABERLİK VE ÇELİŞKİ ----------

    @Test
    fun beraberlik_ustTekiKorunur_veTurnuvaBiter() {
        val songs = ogeler(20, 33L)
        // Her 5. karşılaştırma berabere; kalanı büyük kazanır
        var sayac = 0
        val db = turnuvaOynat(songs, { a, b ->
            sayac++
            if (sayac % 5 == 0) null else maxOf(a, b)
        })
        val sira = sonSira(songs, db)
        assertEquals("Eksik/mükerrer öğe", 20, sira.toSet().size)
        // Her komşuluk ya kanıtlı (oynanmış) olmalı — beraberlikler dahil
        val oynanan = db.map { minOf(it.songId1, it.songId2) to maxOf(it.songId1, it.songId2) }.toSet()
        val kanitsiz = (0 until 19).count { i ->
            val c = minOf(sira[i], sira[i + 1]).toLong() to maxOf(sira[i], sira[i + 1]).toLong()
            c !in oynanan
        }
        assertEquals("Kanıtsız komşuluk kaldı", 0, kanitsiz)
    }

    @Test
    fun celiskiliOylar_turnuvaYineDeSonlanir() {
        // Rastgele (geçişsiz) hakem: A>B, B>C, C>A döngüleri kaçınılmaz.
        // Garanti edilen şey sıralamanın doğruluğu değil, BİTİŞTİR.
        val songs = ogeler(25, 77L)
        val rasgele = java.util.Random(99L)
        val db = turnuvaOynat(songs, { a, b -> if (rasgele.nextBoolean()) a else b })
        val sira = sonSira(songs, db)
        assertEquals(25, sira.toSet().size)
    }

    // ---------- KISMİ SONUÇ ----------

    @Test
    fun erkenDurum_calculateResults_tumOgelereBenzersizPozisyonVerir() {
        val songs = ogeler(30, 11L)
        val db = mutableListOf<Match>()
        var id = 1L
        // Yalnız 3 tur oyna (faz 1 bile bitmedi)
        repeat(3) {
            val yeni = HibritKanitSistemi.createNextRoundMatches(songs, db)
            yeni.forEach { m ->
                db.add(m.copy(id = id++, winnerId = buyukKazanir(m.songId1, m.songId2), isCompleted = true))
            }
        }
        val sonuclar = HibritKanitSistemi.calculateResults(songs, db)
        assertEquals(30, sonuclar.size)
        assertEquals((1..30).toList(), sonuclar.map { it.position }.sorted())
        assertEquals(30, sonuclar.map { it.songId }.toSet().size)
    }

    // ---------- SINIR DURUMLAR ----------

    @Test
    fun sinirDurumlar_n0_n1_n2() {
        assertTrue(HibritKanitSistemi.createNextRoundMatches(emptyList(), emptyList()).isEmpty())

        val tek = ogeler(1, 1L)
        assertTrue(HibritKanitSistemi.createNextRoundMatches(tek, emptyList()).isEmpty())
        assertEquals(1, HibritKanitSistemi.calculateResults(tek, emptyList()).size)

        val cift = ogeler(2, 1L)
        val db = turnuvaOynat(cift, ::buyukKazanir)
        assertEquals((2 downTo 1).toList(), sonSira(cift, db))
    }
}
