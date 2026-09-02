package com.example.ranking

import com.example.ranking.data.RankingResult
import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * DİREKT PUANLAMA — UZMAN SINAVI (oturumlar/ESKI-MOTORLAR-SINAV-GOREV.md § C)
 *
 * `RankingEngine.createDirectScoringResults` için TEK BİR test bile yoktu
 * (tüm test paketinde arandı: 0 sonuç). Bu dosya ilk kapsamı kuruyor:
 *
 *  ① Eşit puanlar: sıralama deterministik mi, neye dayanıyor
 *  ② Puan aralığı dışı değerler (negatif, >100, çok büyük, NaN)
 *  ③ Hiç puanlanmamış öğe sonuçta nerede duruyor
 *  ④ Sınır durumları: 0 öğe, 1 öğe, mükerrer id, sözlükte fazladan id
 *  ⑤ Pozisyon bütünlüğü: 1..n, tekrarsız, boşluksuz
 *
 * ⚠️ `RankingEngine.kt` KOORDİNATÖRÜN dosyası — yalnız OKUNDU, yazılmadı.
 */
class EskiMotorDirektPuanlamaSinaviTest {

    private val listeId = 4L

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Oge$i", listId = listeId) }

    private fun sira(sonuclar: List<RankingResult>): List<Long> =
        sonuclar.sortedBy { it.position }.map { it.songId }

    // ==========================================================
    // ① EŞİT PUANLAR — determinizm
    // ==========================================================

    @Test
    fun esitPuan_ayniGirdiIkiCagridaAyniSonuc() {
        val songs = makeSongs(6)
        val puanlar = songs.associate { it.id to 50.0 }
        val a = sira(RankingEngine.createDirectScoringResults(songs, puanlar))
        val b = sira(RankingEngine.createDirectScoringResults(songs, puanlar))
        assertEquals("Aynı girdi iki çağrıda farklı sıra verdi", a, b)
        assertEquals("Eşitlikte girdi sırası korunmalı (kararlı sıralama)", listOf(1L, 2L, 3L, 4L, 5L, 6L), a)
    }

    /**
     * REGRESYON BEKÇİSİ (eski B3). Bu sınavda ölçülen kusur: eşitlik bozucu
     * YOKTU, sıralama `sortedByDescending`in kararlılığına — yani `songs`
     * listesinin geliş sırasına — düşüyordu; songs ters verilince sonuç da
     * tersine dönüyordu (`[1,2,3,4]` → `[4,3,2,1]`).
     * Düzeltme: `sortedWith(compareByDescending { score }.thenBy { songId })`
     * (`RankingEngine.kt:29-31`). Lig'de aynı kusur B2'de kapatılmıştı.
     */
    @Test
    fun esitPuan_girdiSirasindanBagimsiz_regresyonBekcisi() {
        val songs = makeSongs(4)
        val puanlar = songs.associate { it.id to 7.0 }

        val duz = sira(RankingEngine.createDirectScoringResults(songs, puanlar))
        val ters = sira(RankingEngine.createDirectScoringResults(songs.reversed(), puanlar))

        assertEquals("Son çare songId olmalı", listOf(1L, 2L, 3L, 4L), duz)
        assertEquals(
            "REGRESYON: motor yine girdi sırasına bağımlı (.thenBy { it.songId } düşmüş olabilir)",
            duz, ters
        )
    }

    @Test
    fun esitPuan_kismiEsitlikteYuksekPuanUstte() {
        val songs = makeSongs(5)
        val puanlar = mapOf(1L to 10.0, 2L to 90.0, 3L to 10.0, 4L to 90.0, 5L to 50.0)
        val sonuc = RankingEngine.createDirectScoringResults(songs, puanlar)
        // 90'lar (2,4) → 50 (5) → 10'lar (1,3); eşitler girdi sırasında
        assertEquals(listOf(2L, 4L, 5L, 1L, 3L), sira(sonuc))
    }

    // ==========================================================
    // ② ARALIK DIŞI DEĞERLER
    // ==========================================================

    /**
     * ÖLÇÜM: motorda aralık kısıtı YOK. Negatif ve 100 üstü puanlar olduğu gibi
     * kabul edilip sıralanıyor, kırpılmıyor, hata verilmiyor. Doğrulama
     * (varsa) giriş ekranının işi; motor savunmasız.
     */
    @Test
    fun aralikDisi_negatifVeYuzUstuDegerlerOlduguGibiSiralaniyor() {
        val songs = makeSongs(4)
        val puanlar = mapOf(1L to -50.0, 2L to 250.0, 3L to 0.0, 4L to 100.0)
        val sonuc = RankingEngine.createDirectScoringResults(songs, puanlar)

        assertEquals(listOf(2L, 4L, 3L, 1L), sira(sonuc))
        assertEquals("Puan kırpılmamalı", 250.0, sonuc.first { it.songId == 2L }.score, 0.0001)
        assertEquals("Negatif puan kırpılmamalı", -50.0, sonuc.first { it.songId == 1L }.score, 0.0001)
    }

    /**
     * REGRESYON BEKÇİSİ (eski B4'ün ikinci yarısı). `MAX_VALUE` SONLU olduğu
     * için olduğu gibi kalır; `±Infinity` sonlu olmadığı için 0.0 sayılır.
     */
    @Test
    fun aralikDisi_sonsuzDegerlerSifirSayiliyor_regresyonBekcisi() {
        val songs = makeSongs(3)
        val puanlar = mapOf(
            1L to Double.MAX_VALUE,
            2L to Double.POSITIVE_INFINITY,
            3L to Double.NEGATIVE_INFINITY
        )
        val sonuc = RankingEngine.createDirectScoringResults(songs, puanlar)

        assertEquals(3, sonuc.size)
        assertEquals("MAX_VALUE sonlu, korunmalı", Double.MAX_VALUE, sonuc.first { it.songId == 1L }.score, 0.0)
        assertEquals("+Infinity 0.0 sayılmalı", 0.0, sonuc.first { it.songId == 2L }.score, 0.0)
        assertEquals("-Infinity 0.0 sayılmalı", 0.0, sonuc.first { it.songId == 3L }.score, 0.0)
        assertEquals("1 birinci; 2 ve 3 eşit (0.0), songId ile ayrılır", listOf(1L, 2L, 3L), sira(sonuc))
    }

    /**
     * REGRESYON BEKÇİSİ (eski B4). Bu sınavda ölçülen kusur: NaN puan
     * ELENMİYORDU ve Kotlin'in `Double` karşılaştırıcısı NaN'ı EN BÜYÜK
     * saydığı için NaN puanlı öğe sessizce BİRİNCİ çıkıyordu.
     * Düzeltme: `scores[song.id]?.takeIf { it.isFinite() } ?: 0.0`
     * (`RankingEngine.kt:17`).
     */
    @Test
    fun aralikDisi_nanPuanSifirSayiliyor_regresyonBekcisi() {
        val songs = makeSongs(3)
        val puanlar = mapOf(1L to 10.0, 2L to Double.NaN, 3L to 99.0)
        val sonuc = RankingEngine.createDirectScoringResults(songs, puanlar)

        assertEquals(3, sonuc.size)
        assertFalse(
            "REGRESYON: NaN puan yine taşınıyor",
            sonuc.first { it.songId == 2L }.score.isNaN()
        )
        assertEquals("NaN 0.0 sayılmalı", 0.0, sonuc.first { it.songId == 2L }.score, 0.0)
        assertEquals(
            "REGRESYON: NaN yine birinciye çıkmış",
            3L, sonuc.first { it.position == 1 }.songId
        )
        assertEquals("Sıra: 99 → 10 → 0 (NaN)", listOf(3L, 1L, 2L), sira(sonuc))
    }

    // ==========================================================
    // ③ PUANLANMAMIŞ ÖĞE
    // ==========================================================

    @Test
    fun puanlanmamisOge_sifirSayilipPozitiflerinAltinaDusuyor() {
        val songs = makeSongs(4)
        val puanlar = mapOf(1L to 30.0, 3L to 10.0) // 2 ve 4 hiç puanlanmadı
        val sonuc = RankingEngine.createDirectScoringResults(songs, puanlar)

        assertEquals(listOf(1L, 3L, 2L, 4L), sira(sonuc))
        assertEquals("Puanlanmamış öğe 0.0 almalı", 0.0, sonuc.first { it.songId == 2L }.score, 0.0001)
        assertEquals(0.0, sonuc.first { it.songId == 4L }.score, 0.0001)
    }

    /**
     * ÖLÇÜM (bulgu): puanlanmamış öğe 0.0 sayıldığı için NEGATİF puan almış
     * öğenin ÜSTÜNE çıkıyor. Yani "hiç değerlendirilmedi" ile "sıfır puan
     * verildi" ayırt edilmiyor; negatif puan kullanan bir listede
     * değerlendirilmemiş öğe haksız avantaj kazanıyor.
     */
    @Test
    fun puanlanmamisOge_negatifPuanlininUstunde_belgelenmisKusur() {
        val songs = makeSongs(2)
        val puanlar = mapOf(1L to -5.0) // 2 hiç puanlanmadı
        val sonuc = RankingEngine.createDirectScoringResults(songs, puanlar)
        assertEquals(
            "BULGU DEĞİŞMİŞ: puanlanmamış öğe artık ayrı ele alınıyor — raporu güncelle",
            2L, sonuc.first { it.position == 1 }.songId
        )
    }

    @Test
    fun puanlanmamisOge_hicbiriPuanlanmamissaGirdiSirasiKoruniyor() {
        val songs = makeSongs(5)
        val sonuc = RankingEngine.createDirectScoringResults(songs, emptyMap())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sira(sonuc))
        sonuc.forEach { assertEquals(0.0, it.score, 0.0001) }
    }

    // ==========================================================
    // ④ SINIR DURUMLARI
    // ==========================================================

    @Test
    fun sinir_bosListe() {
        assertTrue(RankingEngine.createDirectScoringResults(emptyList(), emptyMap()).isEmpty())
        assertTrue(RankingEngine.createDirectScoringResults(emptyList(), mapOf(1L to 5.0)).isEmpty())
    }

    @Test
    fun sinir_tekOge() {
        val songs = makeSongs(1)
        val sonuc = RankingEngine.createDirectScoringResults(songs, mapOf(1L to 42.0))
        assertEquals(1, sonuc.size)
        assertEquals(1, sonuc[0].position)
        assertEquals(42.0, sonuc[0].score, 0.0001)
        assertEquals("DIRECT_SCORING", sonuc[0].rankingMethod)
        assertEquals(listeId, sonuc[0].listId)
    }

    @Test
    fun sinir_sozluktekiFazladanIdYokSayiliyor() {
        val songs = makeSongs(2)
        val puanlar = mapOf(1L to 10.0, 2L to 20.0, 999L to 100.0) // 999 listede yok
        val sonuc = RankingEngine.createDirectScoringResults(songs, puanlar)
        assertEquals("Listede olmayan id sonuca sızmamalı", 2, sonuc.size)
        assertFalse(sonuc.any { it.songId == 999L })
        assertEquals(listOf(2L, 1L), sira(sonuc))
    }

    @Test
    fun sinir_mukerrerOgeCokmuyor() {
        // Aynı id iki kez (veri katmanı bozulması) — çökmemeli, iki satır üretmeli
        val songs = makeSongs(2) + makeSongs(1)
        val sonuc = RankingEngine.createDirectScoringResults(songs, mapOf(1L to 10.0, 2L to 5.0))
        assertEquals(3, sonuc.size)
        assertEquals(listOf(1, 2, 3), sonuc.map { it.position }.sorted())
        assertEquals("Aynı id iki kez, ikisi de 10 puan almalı", 2, sonuc.count { it.songId == 1L })
    }

    // ==========================================================
    // ⑤ POZİSYON BÜTÜNLÜĞÜ
    // ==========================================================

    @Test
    fun pozisyonlar_birdenNyeKadarTekrarsizVeBosluksuz() {
        val songs = makeSongs(50)
        // Deterministik ve çok sayıda eşitlik üreten dağılım
        val puanlar = songs.associate { it.id to ((it.id % 5) * 20.0) }
        val sonuc = RankingEngine.createDirectScoringResults(songs, puanlar)

        assertEquals(50, sonuc.size)
        assertEquals((1..50).toList(), sonuc.map { it.position }.sorted())
        assertEquals("Her öğe tam bir kez görünmeli", 50, sonuc.map { it.songId }.toSet().size)

        // Puan sırası bozulmamalı
        val puanSirasi = sonuc.sortedBy { it.position }.map { it.score }
        assertEquals("Puanlar azalan sırada olmalı", puanSirasi.sortedDescending(), puanSirasi)
    }

    @Test
    fun pozisyonlar_buyukListeDeterministik() {
        val songs = makeSongs(200)
        val puanlar = songs.associate { it.id to ((it.id * 7) % 13).toDouble() }
        val a = sira(RankingEngine.createDirectScoringResults(songs, puanlar))
        val b = sira(RankingEngine.createDirectScoringResults(songs, puanlar))
        assertEquals(a, b)
        assertEquals(200, a.size)
    }
}
