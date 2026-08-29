package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Turlar ileri aşamalarda bozuluyor" şikâyetinin ölçümü.
 *
 * Kullanıcı gözlemi: 80 şarkıda ilk iki tur 40'ar eşleşme geliyor, sonraki
 * turlarda eşleştirme penceresi tur ortasında tekrar tekrar açılıyor.
 *
 * Buradaki test motorun tur başına KAÇ maç ürettiğini sayar. ViewModel tur
 * kapanışını `songs.size / 2` sabitine göre karar veriyor; motor daha az
 * maç üretirse o sabit tutmaz ve tur hiç kapanmaz.
 */
class EmreTurBozulmasiTest {

    private fun sarkilar(n: Int): List<Song> =
        (1..n).map { Song(id = it.toLong(), name = "S$it", artist = "", album = "", trackNumber = it, listId = 1L) }

    /**
     * Deterministik sonuç: küçük id kazanır.
     *
     * ⚠️ Her maça BENZERSİZ id verilir. Motor aynı maçın iki kez işlenmesini
     * `match.id` ile engelliyor; hepsi 0 kalırsa turun ilk maçı dışındaki
     * hepsi "zaten işlendi" sanılıp atlanır ve maç geçmişi boş kalır.
     * Gerçek uygulamada id'ler veritabanından gelir.
     */
    private var sonrakiId = 1L
    private fun oyna(maclar: List<Match>): List<Match> =
        maclar.map {
            it.copy(
                id = sonrakiId++,
                winnerId = minOf(it.songId1, it.songId2),
                isCompleted = true
            )
        }

    @Test
    fun n80_herTurdaKacMacUretiliyor() {
        val songs = sarkilar(80)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val tumTamamlanan = mutableListOf<Match>()
        val turBasinaMac = mutableListOf<Int>()

        repeat(12) { i ->
            val sonuc = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!sonuc.canContinue || sonuc.matches.isEmpty()) return@repeat

            val turNo = i + 1
            val turMaclari = sonuc.matches.map { it.copy(round = turNo) }
            turBasinaMac.add(turMaclari.size)

            val oynanan = oyna(turMaclari)
            tumTamamlanan.addAll(oynanan)

            val byeTeam = state.teams.find { t ->
                oynanan.none { it.songId1 == t.song.id || it.songId2 == t.song.id }
            }
            state = RankingEngine.processCorrectEmreResults(
                state, oynanan, byeTeam, allCompletedMatches = tumTamamlanan
            )
        }

        println("n=80 tur başına maç sayıları: $turBasinaMac")

        assertTrue("En az 3 tur üretilmeli, üretilen: ${turBasinaMac.size}", turBasinaMac.size >= 3)
        val eksikTurlar = turBasinaMac.withIndex().filter { (_, adet) -> adet != 40 }
        assertTrue(
            "80 takımda her tur 40 maç olmalı. Sapan turlar (tur→maç): " +
                eksikTurlar.joinToString { "${it.index + 1}→${it.value}" } +
                " | tamamı: $turBasinaMac",
            eksikTurlar.isEmpty()
        )
    }

    @Test
    fun n80_ayniIkiliIkiKezEslesmemeli() {
        val songs = sarkilar(80)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val tumTamamlanan = mutableListOf<Match>()
        val gorulen = mutableSetOf<Pair<Long, Long>>()

        repeat(10) { i ->
            val sonuc = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!sonuc.canContinue || sonuc.matches.isEmpty()) return@repeat
            val turMaclari = sonuc.matches.map { it.copy(round = i + 1) }

            turMaclari.forEach { m ->
                val anahtar = minOf(m.songId1, m.songId2) to maxOf(m.songId1, m.songId2)
                assertTrue("TEKRAR EŞLEŞME (tur ${i + 1}): $anahtar", gorulen.add(anahtar))
            }

            val oynanan = oyna(turMaclari)
            tumTamamlanan.addAll(oynanan)
            val byeTeam = state.teams.find { t ->
                oynanan.none { it.songId1 == t.song.id || it.songId2 == t.song.id }
            }
            state = RankingEngine.processCorrectEmreResults(
                state, oynanan, byeTeam, allCompletedMatches = tumTamamlanan
            )
        }
    }

    @Test
    fun n80_herTakimHerTurdaTamBirMacOynamali() {
        val songs = sarkilar(80)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val tumTamamlanan = mutableListOf<Match>()

        repeat(6) { i ->
            val sonuc = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!sonuc.canContinue || sonuc.matches.isEmpty()) return@repeat
            val turMaclari = sonuc.matches.map { it.copy(round = i + 1) }

            val katilan = turMaclari.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals(
                "Tur ${i + 1}: bir takım aynı turda iki kez oynuyor",
                katilan.size, katilan.toSet().size
            )
            assertEquals(
                "Tur ${i + 1}: 80 takımın hepsi oynamalı, oynayan: ${katilan.size}",
                80, katilan.size
            )

            val oynanan = oyna(turMaclari)
            tumTamamlanan.addAll(oynanan)
            state = RankingEngine.processCorrectEmreResults(
                state, oynanan, null, allCompletedMatches = tumTamamlanan
            )
        }
    }

    /**
     * ViewModel tur kapanışını `songs.size / 2` sabitiyle, sıradaki turu da
     * `(tamamlanan / (n/2)) + 1` formülüyle hesaplıyor. Motorun maçlara
     * yazdığı `round` bu sayaçla AYNI ilerlemezse tur hiç kapanmaz:
     * loadNextMatch boş maç bulur, createNextEmreRound çağrılır, eşleştirme
     * penceresi tur ortasında tekrar açılır ve bir önceki tur İKİNCİ KEZ
     * puanlanır.
     */
    @Test
    fun n80_motorunYazdigiTurNumarasiSayacIleAyniIlerlemeli() {
        val songs = sarkilar(80)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val tumTamamlanan = mutableListOf<Match>()
        val sapmalar = mutableListOf<String>()

        for (beklenenTur in 1..8) {
            val sonuc = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!sonuc.canContinue || sonuc.matches.isEmpty()) break

            val motorunYazdigi = sonuc.matches.map { it.round }.toSet()
            if (motorunYazdigi != setOf(beklenenTur)) {
                sapmalar.add("beklenen tur $beklenenTur, motor $motorunYazdigi yazdi")
            }

            // ViewModel'in sıradaki tur formülü
            val tamamlanan = tumTamamlanan.size + sonuc.matches.size
            val vmSiradakiTur = (tamamlanan / (songs.size / 2)) + 1
            if (vmSiradakiTur != beklenenTur + 1) {
                sapmalar.add("tur $beklenenTur sonunda VM sıradakini $vmSiradakiTur sandı, ${beklenenTur + 1} olmalıydı")
            }

            val oynanan = oyna(sonuc.matches)
            tumTamamlanan.addAll(oynanan)
            state = RankingEngine.processCorrectEmreResults(
                state, oynanan, null, allCompletedMatches = tumTamamlanan
            )
        }

        assertTrue("Tur numarası sapmaları: " + sapmalar.joinToString(" | "), sapmalar.isEmpty())
    }

    /**
     * UÇTAN UCA SINAV (kullanıcı isteği): 1..100 sayıları karışık sırayla
     * girilir, her eşleşmede BÜYÜK SAYI kazanır. Turnuva bitince sıralama
     * büyük ölçüde 100'den 1'e doğru olmalı.
     *
     * Bu, motorun gerçekten "doğru olanı yukarı taşıyıp taşımadığının" tek
     * dürüst ölçüsü: kural ihlali olmasa bile sıralama yanlışsa sistem işe
     * yaramaz.
     */
    @Test
    fun uctanUca_1den100e_buyukSayiKazanir_sonucBuyuktenKucugeSiralanmali() {
        val karisik = (1..100).shuffled(java.util.Random(20260829))
        val songs = karisik.mapIndexed { index, sayi ->
            Song(
                id = sayi.toLong(), name = "$sayi", artist = "", album = "",
                trackNumber = index + 1, listId = 1L
            )
        }

        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val tumTamamlanan = mutableListOf<Match>()
        var turSayisi = 0

        repeat(40) {
            val sonuc = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!sonuc.canContinue || sonuc.matches.isEmpty()) return@repeat
            turSayisi++

            // BÜYÜK SAYI KAZANIR (song.id = sayının kendisi)
            val oynanan = sonuc.matches.map {
                it.copy(
                    id = sonrakiId++,
                    winnerId = maxOf(it.songId1, it.songId2),
                    isCompleted = true
                )
            }
            tumTamamlanan.addAll(oynanan)

            val byeTeam = state.teams.find { t ->
                oynanan.none { m -> m.songId1 == t.song.id || m.songId2 == t.song.id }
            }
            state = RankingEngine.processCorrectEmreResults(
                state, oynanan, byeTeam, allCompletedMatches = tumTamamlanan
            )
        }

        val sonuclar = RankingEngine.calculateCorrectEmreResults(state)
            .sortedBy { it.position }
        val siralananSayilar = sonuclar.map { it.songId.toInt() }

        println("Tur sayısı: $turSayisi, oynanan maç: ${tumTamamlanan.size}")
        println("İlk 10: ${siralananSayilar.take(10)}")
        println("Son 10: ${siralananSayilar.takeLast(10)}")

        assertEquals("100 öğenin hepsi sonuçta olmalı", 100, siralananSayilar.size)
        assertEquals("Her sayı tam bir kez geçmeli", 100, siralananSayilar.toSet().size)

        // Sıralama kalitesi: her öğenin ideal konumundan (100→1) sapması
        val toplamSapma = siralananSayilar.mapIndexed { index, sayi ->
            val idealKonum = 100 - sayi   // 100 → 0, 1 → 99
            kotlin.math.abs(index - idealKonum)
        }.sum()
        val ortalamaSapma = toplamSapma / 100.0
        println("Ortalama konum sapması: $ortalamaSapma")

        // Rastgele sıralamada beklenen ortalama sapma ~33; iyi bir sıralamada
        // birkaç basamak olmalı.
        assertTrue(
            "Sıralama büyükten küçüğe olmalı. Ortalama konum sapması $ortalamaSapma " +
                "(rastgelede ~33 olur). İlk 10: ${siralananSayilar.take(10)}",
            ortalamaSapma < 8.0
        )

        // Zirve ve dip ayrıca sınanır
        assertTrue(
            "İlk 10'da en büyük sayılardan en az 7'si olmalı, çıkan: ${siralananSayilar.take(10)}",
            siralananSayilar.take(10).count { it >= 90 } >= 7
        )
        assertTrue(
            "Son 10'da en küçük sayılardan en az 7'si olmalı, çıkan: ${siralananSayilar.takeLast(10)}",
            siralananSayilar.takeLast(10).count { it <= 10 } >= 7
        )
    }
}
