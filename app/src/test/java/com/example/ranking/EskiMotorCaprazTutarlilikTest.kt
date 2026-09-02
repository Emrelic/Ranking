package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * ÇAPRAZ TUTARLILIK — LİG: motor vs. canlı puan tablosu
 * (oturumlar/ESKI-MOTORLAR-SINAV-GOREV.md § D)
 *
 * ✅ KIRMIZI TEST KALMADI. Bu dosyada ölçülen ÜÇ ayrışmanın üçü de sınav
 *    sırasında koordinatör tarafından düzeltildi; testler artık REGRESYON
 *    BEKÇİSİ (`duzeltmeSonrasi_*`): düzeltme geri alınırsa kırmızıya dönerler.
 *
 * Lig sıralaması projede İKİ AYRI YERDE hesaplanıyor:
 *   (1) `RankingEngine.calculateLeagueResults` — final sonuç ekranı
 *   (2) `RankingViewModel.calculateCurrentStandings` LEAGUE dalı — turnuva
 *       sırasında görünen canlı "Puan Durumu" tablosu
 *
 * ViewModel dosyası KOORDİNATÖRÜN; yalnız OKUNDU. Formülü aşağıda
 * `viewModelLigSirasi` olarak yeniden yazıldı (kopyalama testi) ve iki hesabın
 * aynı sırayı verip vermediği ölçüldü.
 *
 * GEÇMİŞ — bu sınavın ilk turunda İKİ ayrışma ölçülmüştü, koordinatör ikisini
 * de düzeltti (aşağıdaki `duzeltmeSonrasi_*` testleri artık regresyon bekçisi):
 *   · tek taraflı skor: ViewModel boş skoru 0 sayıp olmayan averaj üretiyordu
 *     → motorun kuralına (iki skor da dolu olacak) çekildi
 *   · eşitlik zinciri: motorun zinciri atılan golde bitip girdi sırasına
 *     düşüyordu → ViewModel'in zincirine (… → galibiyet → song.id) çekildi
 *
 *   · yetim maç: ViewModel karşı tarafın silinmiş olup olmadığına bakmadan
 *     ayakta kalan takıma 3 puan yazıyordu → motorun kuralına çekildi
 *
 * KALAN AYRIŞMA YOK.
 */
class EskiMotorCaprazTutarlilikTest {

    private val listeId = 9L

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Takim$i", listId = listeId) }

    private fun completed(
        id: Long, s1: Long, s2: Long, winner: Long?,
        score1: Int? = null, score2: Int? = null, round: Int = 1
    ) = Match(
        id = id, listId = listeId, rankingMethod = "LEAGUE",
        songId1 = s1, songId2 = s2, winnerId = winner,
        score1 = score1, score2 = score2, round = round, isCompleted = true
    )

    private fun motorSirasi(songs: List<Song>, maclar: List<Match>): List<Long> =
        RankingEngine.calculateLeagueResults(songs, maclar)
            .sortedBy { it.position }.map { it.songId }

    /**
     * `RankingViewModel.calculateCurrentStandings` LEAGUE dalının BİREBİR
     * yeniden yazımı (ViewModel'e dokunulmadı, yalnız okundu —
     * RankingViewModel.kt:1986-2051). Dönen: 1. sıradan sonuncuya songId.
     *
     * YETİM MAÇ: rakip `songs`ta yoksa maç sayılmaz (RankingViewModel.kt:2018-2019).
     * Bu satır bu sınavda bulunan ayrışma üzerine eklendi; motor ve SwissSystem
     * zaten böyle davranıyordu.
     */
    private fun viewModelLigSirasi(songs: List<Song>, maclar: List<Match>): List<Long> {
        val ligPuani = 3.0
        val beraberlikPuani = 1.0
        val tamamlanan = maclar.filter { it.isCompleted }

        data class Satir(val song: Song, val puan: Double, val won: Int, val averaj: Int, val atilan: Int)

        return songs.map { song ->
            var puan = 0.0
            var won = 0
            var atilan = 0
            var yenilen = 0

            tamamlanan.forEach { match ->
                val birinci = match.songId1 == song.id
                val ikinci = match.songId2 == song.id
                if (!birinci && !ikinci) return@forEach

                // Yetim maç: rakip listede yoksa maç hiç sayılmaz (skor dâhil)
                val rakipId = if (birinci) match.songId2 else match.songId1
                if (songs.none { it.id == rakipId }) return@forEach

                // Bozuk kazanan: winnerId ne songId1 ne songId2 ne null ise
                // maç hiç sayılmaz (RankingViewModel.kt:2026-2028)
                val bozukKazanan = match.winnerId
                if (bozukKazanan != null && bozukKazanan != match.songId1 &&
                    bozukKazanan != match.songId2
                ) return@forEach

                val kendiSkor = if (birinci) match.score1 else match.score2
                val rakipSkor = if (birinci) match.score2 else match.score1
                if (kendiSkor != null && rakipSkor != null) {
                    atilan += kendiSkor
                    yenilen += rakipSkor
                }

                when (match.winnerId) {
                    song.id -> { won++; puan += ligPuani }
                    null -> puan += beraberlikPuani
                    else -> Unit
                }
            }
            Satir(song, puan, won, atilan - yenilen, atilan)
        }.sortedWith(
            compareByDescending<Satir> { it.puan }
                .thenByDescending { it.averaj }
                .thenByDescending { it.atilan }
                .thenByDescending { it.won }
                .thenBy { it.song.id }
        ).map { it.song.id }
    }

    // ==========================================================
    // ✅ UYUŞTUKLARI DURUMLAR
    // ==========================================================

    @Test
    fun uyusuyor_skorsuzDuzLig() {
        val songs = makeSongs(6)
        val maclar = RankingEngine.createLeagueMatches(songs).mapIndexed { i, m ->
            m.copy(
                id = (i + 1).toLong(),
                winnerId = when (i % 3) { 0 -> m.songId1; 1 -> m.songId2; else -> null },
                isCompleted = true
            )
        }
        assertEquals(
            "Skorsuz düz ligde iki hesap aynı sırayı vermeli",
            viewModelLigSirasi(songs, maclar), motorSirasi(songs, maclar)
        )
    }

    @Test
    fun uyusuyor_herMactaIkiSkorDolu() {
        val songs = makeSongs(8)
        val maclar = RankingEngine.createLeagueMatches(songs).mapIndexed { i, m ->
            val s1 = i % 4
            val s2 = (i + 2) % 4
            m.copy(
                id = (i + 1).toLong(),
                score1 = s1, score2 = s2,
                winnerId = when {
                    s1 > s2 -> m.songId1
                    s2 > s1 -> m.songId2
                    else -> null
                },
                isCompleted = true
            )
        }
        assertEquals(
            "İki skoru da dolu ligde iki hesap aynı sırayı vermeli",
            viewModelLigSirasi(songs, maclar), motorSirasi(songs, maclar)
        )
    }

    /**
     * Yabancı `winnerId` — SKORLU vaka. Eskiden ikisi de puan uydurmuyordu ama
     * ikisi de skoru averaja yazıyordu; B8 düzeltmesi maçı İKİ KATMANDA da
     * tümüyle atlıyor. Skorsuz bir vaka bu düzeltmeyi sınayamaz (fark averajda
     * doğuyor), o yüzden test skorlu kuruldu.
     */
    @Test
    fun uyusuyor_yabanciKazananVarken_skorlu() {
        val songs = makeSongs(4)
        val maclar = listOf(
            completed(1, 1L, 2L, winner = 999L, score1 = 7, score2 = 0), // bozuk + skorlu
            completed(2, 3L, 4L, winner = 3L)
        )
        val motor = motorSirasi(songs, maclar)

        assertEquals(
            "Yabancı kazananda iki hesap aynı sırayı vermeli",
            viewModelLigSirasi(songs, maclar), motor
        )
        assertEquals(
            "Bozuk maç averaja yazılmamalı: 3 galip, kalanlar 0 puanda id sırasında",
            listOf(3L, 1L, 2L, 4L), motor
        )
    }

    @Test
    fun uyusuyor_buyukTamLig_n16() {
        val songs = makeSongs(16)
        val maclar = RankingEngine.createLeagueMatches(songs).mapIndexed { i, m ->
            m.copy(
                id = (i + 1).toLong(),
                winnerId = when (i % 4) { 0, 1 -> m.songId1; 2 -> m.songId2; else -> null },
                isCompleted = true
            )
        }
        assertEquals(
            "16 takımlı tam ligde iki hesap aynı sırayı vermeli",
            viewModelLigSirasi(songs, maclar), motorSirasi(songs, maclar)
        )
    }

    // ==========================================================
    // ✅ DÜZELTME SONRASI REGRESYON BEKÇİLERİ
    // ==========================================================

    /**
     * ESKİ AYRIŞMA 1 (düzeltildi). Bir maçta yalnız bir skor girilmişse
     * (`score1=5, score2=null`) ViewModel boş tarafı 0 sayıp ±5 averaj
     * üretiyordu; motor ise maçı averaja hiç yazmıyordu.
     * Ölçülen: motor `[1,2,3,4]`, canlı tablo `[1,3,4,2]`.
     * Düzeltme: ViewModel motorun kuralına çekildi (iki skor da dolu olacak).
     */
    @Test
    fun duzeltmeSonrasi_tekTarafliSkor_ikiHesapAyniSira() {
        val songs = makeSongs(4)
        val maclar = listOf(
            completed(1, 1L, 2L, winner = null, score1 = 5, score2 = null),
            completed(2, 3L, 4L, winner = null)
        )

        val motor = motorSirasi(songs, maclar)
        val vm = viewModelLigSirasi(songs, maclar)
        assertEquals(
            "REGRESYON: tek taraflı skorda iki hesap yine ayrıştı " +
                "(motor $motor · canlı tablo $vm). Boş skor 0 SAYILMAMALI.",
            vm, motor
        )
        assertEquals("Tek taraflı skor averaj üretmemeli", listOf(1L, 2L, 3L, 4L), motor)
    }

    /**
     * ESKİ AYRIŞMA 2 (düzeltildi). 3/1/0 ölçeğinde 1 galibiyet ile 3 beraberlik
     * aynı puanı verir; ViewModel galibiyet sayısıyla ayırırken motorun zinciri
     * atılan golde bitip `songs` listesinin sırasına düşüyordu.
     * Ölçülen: motor `[4,1,2,3,5]`, canlı tablo `[4,2,1,3,5]`.
     * Düzeltme: motorun zinciri ViewModel'inkine çekildi (… → galibiyet → id).
     */
    @Test
    fun duzeltmeSonrasi_esitPuandaGalibiyetBozucusu_ikiHesapAyniSira() {
        val songs = makeSongs(5)
        val maclar = listOf(
            completed(1, 1L, 3L, winner = null),
            completed(2, 1L, 4L, winner = null),
            completed(3, 1L, 5L, winner = null),  // 1 -> 3 puan, 0 galibiyet
            completed(4, 2L, 3L, winner = 2L),    // 2 -> 3 puan, 1 galibiyet
            completed(5, 2L, 4L, winner = 4L)
        )

        val motor = motorSirasi(songs, maclar)
        val vm = viewModelLigSirasi(songs, maclar)
        assertEquals(
            "REGRESYON: eşit puanda iki hesap yine ayrıştı " +
                "(motor $motor · canlı tablo $vm). Zincir: puan→averaj→atılan→galibiyet→id.",
            vm, motor
        )
        assertEquals("Daha çok galibiyeti olan üstte olmalı", listOf(4L, 2L, 1L, 3L, 5L), motor)
    }

    /**
     * ESKİ BULGU B2 (düzeltildi). Motorun sonucu `songs` listesinin sırasına
     * bağlıydı; artık son çare `song.id`. İki hesap da girdi sırasından
     * bağımsız olmalı.
     */
    @Test
    fun duzeltmeSonrasi_ikiHesapDaGirdiSirasindanBagimsiz() {
        val songs = makeSongs(4)
        val maclar = listOf(
            completed(1, 1L, 2L, null),
            completed(2, 3L, 4L, null)
        )
        assertEquals(
            "REGRESYON: motor yine girdi sırasına bağımlı",
            motorSirasi(songs, maclar), motorSirasi(songs.reversed(), maclar)
        )
        assertEquals(
            "Canlı tablo girdi sırasından bağımsız olmalı",
            viewModelLigSirasi(songs, maclar), viewModelLigSirasi(songs.reversed(), maclar)
        )
    }

    // ==========================================================
    // ✅ YETİM MAÇ — düzeltme sonrası regresyon bekçisi
    // ==========================================================

    /**
     * ESKİ AYRIŞMA 3 (düzeltildi). Bir maçın karşı tarafı silinmişse (öğe
     * listeden çıkarılmış, maç kaydı duruyor — bu projede gerçek senaryo):
     *  · Motor `points[songId2]` null olduğu için maçı TÜMÜYLE atlıyordu.
     *  · ViewModel `songs` üzerinde döndüğü ve karşı tarafın var olup olmadığına
     *    bakmadığı için ayakta kalan takıma galibiyet + 3 puan yazıyordu.
     * Ölçülen: motor `[1,4,5,2,3]` (3 → 0 puan, sonuncu) ·
     *          canlı tablo `[1,3,4,5,2]` (3 → 3 puan, İKİNCİ).
     * Düzeltme: ViewModel motorun kuralına çekildi (RankingViewModel.kt:2018-2019);
     * bye kaydı `songId1 == songId2` olduğu için kontrolden etkilenmiyor.
     */
    @Test
    fun duzeltmeSonrasi_yetimMac_ikiHesapAyniSira() {
        val songs = makeSongs(5)
        val maclar = listOf(
            completed(1, 1L, 2L, 1L),
            completed(2, 3L, 77L, 3L),   // 77 silinmiş — yetim maç
            completed(3, 4L, 5L, null)
        )

        val motor = motorSirasi(songs, maclar)
        val vm = viewModelLigSirasi(songs, maclar)

        assertEquals(
            "REGRESYON: yetim maçta iki hesap yine ayrıştı " +
                "(motor $motor · canlı tablo $vm). Silinmiş rakibe karşı puan YAZILMAMALI.",
            vm, motor
        )
        assertEquals(
            "Yetim maçtan puan alan takım sıralamaya taşınmamalı",
            listOf(1L, 4L, 5L, 2L, 3L), motor
        )
    }

    /**
     * Yetim maç sayısı artınca da iki hesap ayrışmamalı: 1 numaralı takımın
     * TÜM maçları silinmiş rakiplere karşı — iki hesapta da 0 puan kalmalı.
     */
    @Test
    fun duzeltmeSonrasi_cokSayidaYetimMac_ikiHesapAyniSira() {
        val songs = makeSongs(6)
        val olcum = mutableListOf<String>()

        (1..4).forEach { yetimSayisi ->
            val maclar = (1..yetimSayisi).map { k ->
                completed(k.toLong(), 1L, (100 + k).toLong(), 1L) // hepsi 1'in yetim galibiyeti
            }
            val motorPuan = RankingEngine.calculateLeagueResults(songs, maclar)
                .first { it.songId == 1L }.score
            olcum.add("yetim=$yetimSayisi motorPuan=$motorPuan")

            assertEquals(
                "Motor yetim maçtan puan yazmamalı ($yetimSayisi yetim maç)",
                0.0, motorPuan, 0.0001
            )
            assertEquals(
                "REGRESYON: $yetimSayisi yetim maçta canlı tablo motordan ayrıştı",
                viewModelLigSirasi(songs, maclar), motorSirasi(songs, maclar)
            )
        }
        println("[ÇAPRAZ TUTARLILIK] yetim maç bekçisi: $olcum")
    }

    /**
     * Bye kaydı (`songId1 == songId2`) yetim maç kontrolüne TAKILMAMALI.
     * SWISS dalında bye bir Match satırı olarak tutuluyor; kontrol yanlış
     * yazılsaydı bye puanı sessizce kaybolurdu. Lig'de bye kaydı üretilmiyor
     * ama aynı kod yolu paylaşıldığı için burada kilitleniyor.
     */
    @Test
    fun duzeltmeSonrasi_byeKaydiYetimKontrolundenGecmeli() {
        val songs = makeSongs(3)
        val byeKaydi = completed(1, 3L, 3L, winner = 3L)

        // ViewModel kopyasında bye satırı elenmemeli: rakipId == song.id ve
        // song listede olduğu için kontrol geçmeli.
        val vm = viewModelLigSirasi(songs, listOf(byeKaydi))
        assertEquals(
            "Bye kaydı yetim sanılıp elendi — 3 numaralı takım puan almalıydı",
            3L, vm.first()
        )
    }
}
