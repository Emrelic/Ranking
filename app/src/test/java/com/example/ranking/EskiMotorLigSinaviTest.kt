package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.RankingResult
import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * LİG MOTORU — UZMAN SINAVI (oturumlar/ESKI-MOTORLAR-SINAV-GOREV.md § A)
 *
 * `LeagueEngineDeepTest` fikstürün çift/tek temel doğruluğunu, 3/1/0 puanlamayı,
 * averajı ve yetim maçı zaten kapsıyor. BU dosya yalnız ORADA OLMAYAN boşlukları
 * doldurur:
 *
 *  ① Çift devre TEK takım sayısında (n=5,7,9) — mevcut testte yalnız çift n var
 *  ② Büyük fikstür (n=32 / n=33) — mevcut testte en büyük n=12
 *  ③ matchNumber sözleşmesi: motor hiç atamıyor mu, oylama sırası neye dayanıyor
 *  ④ Mükerrer maç kaydı (aynı eşleşme iki satır) — puan/averaj ne oluyor
 *  ⑤ Yabancı winnerId + skor: puan verilmiyor ama averaj yürüyor mu
 *  ⑥ rankingMethod süzgeci: motor kendisine verilen SWISS maçını da sayıyor mu
 *  ⑦ Beraberlik bozucu tükendiğinde sıralamanın girdi sırasına bağımlılığı
 *
 * ⚠️ `RankingEngine.kt` KOORDİNATÖRÜN dosyası — yalnız OKUNDU, yazılmadı.
 */
class EskiMotorLigSinaviTest {

    private val listeId = 7L

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Takim$i", listId = listeId) }

    private fun normalize(a: Long, b: Long) = if (a < b) Pair(a, b) else Pair(b, a)

    private fun completed(
        id: Long, s1: Long, s2: Long, winner: Long?,
        score1: Int? = null, score2: Int? = null,
        round: Int = 1, method: String = "LEAGUE"
    ) = Match(
        id = id, listId = listeId, rankingMethod = method,
        songId1 = s1, songId2 = s2, winnerId = winner,
        score1 = score1, score2 = score2, round = round, isCompleted = true
    )

    private fun sira(sonuclar: List<RankingResult>): List<Long> =
        sonuclar.sortedBy { it.position }.map { it.songId }

    // ==========================================================
    // ① ÇİFT DEVRE — TEK TAKIM SAYISI (mevcut testlerde yok)
    // ==========================================================

    private fun assertCiftDevre(n: Int) {
        val songs = makeSongs(n)
        val matches = RankingEngine.createLeagueMatches(songs, doubleRoundRobin = true)

        val sayac = mutableMapOf<Pair<Long, Long>, Int>()
        matches.forEach { m ->
            val k = normalize(m.songId1, m.songId2)
            sayac[k] = (sayac[k] ?: 0) + 1
        }
        assertEquals("n=$n: farklı ikili sayısı n*(n-1)/2 olmalı", n * (n - 1) / 2, sayac.size)
        sayac.forEach { (k, v) ->
            assertEquals("n=$n: ${k.first}-${k.second} ikilisi tam iki kez oynamalı", 2, v)
        }
        assertEquals("n=$n: toplam maç sayısı n*(n-1) olmalı", n * (n - 1), matches.size)

        assertFalse(
            "n=$n: BYE takımı (-1) gerçek maça girmiş",
            matches.any { it.songId1 == -1L || it.songId2 == -1L }
        )

        // Ev-deplasman: aynı ikilinin iki maçında songId1/songId2 yer değişmeli
        matches.groupBy { normalize(it.songId1, it.songId2) }.forEach { (k, list) ->
            assertEquals(2, list.size)
            assertNotEquals(
                "n=$n: ${k.first}-${k.second} iki devrede de aynı ev sahibiyle oynamış",
                list[0].songId1, list[1].songId1
            )
        }

        // Bir takım aynı turda iki kez oynamamalı (iki devre boyunca)
        matches.groupBy { it.round }.forEach { (round, turMaclari) ->
            val gorunen = turMaclari.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals(
                "n=$n tur=$round: bir takım aynı turda birden fazla maça girmiş",
                gorunen.size, gorunen.toSet().size
            )
        }

        // İki devrenin tur numaraları çakışmamalı
        val ilkYari = matches.take(matches.size / 2).map { it.round }.toSet()
        val ikinciYari = matches.drop(matches.size / 2).map { it.round }.toSet()
        assertTrue(
            "n=$n: iki devrenin tur numaraları çakışıyor",
            ilkYari.intersect(ikinciYari).isEmpty()
        )
    }

    @Test
    fun ciftDevre_tekSayi_n5() = assertCiftDevre(5)

    @Test
    fun ciftDevre_tekSayi_n7() = assertCiftDevre(7)

    @Test
    fun ciftDevre_tekSayi_n9() = assertCiftDevre(9)

    @Test
    fun ciftDevre_ciftSayi_n6() = assertCiftDevre(6)

    // ==========================================================
    // ② BÜYÜK FİKSTÜR — n=32 / n=33
    // ==========================================================

    private fun assertTekDevreFikstur(n: Int) {
        val songs = makeSongs(n)
        val matches = RankingEngine.createLeagueMatches(songs)

        val pairs = matches.map { normalize(it.songId1, it.songId2) }
        assertEquals("n=$n: aynı çift birden fazla kez eşleşti", pairs.size, pairs.toSet().size)
        assertEquals("n=$n: toplam maç n*(n-1)/2 olmalı", n * (n - 1) / 2, matches.size)

        val beklenenTur = if (n % 2 == 0) n - 1 else n
        assertEquals("n=$n: tur sayısı", beklenenTur, matches.map { it.round }.distinct().size)

        val beklenenMac = if (n % 2 == 0) n / 2 else (n - 1) / 2
        matches.groupBy { it.round }.forEach { (round, turMaclari) ->
            val gorunen = turMaclari.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals(
                "n=$n tur=$round: takım tekrarı var", gorunen.size, gorunen.toSet().size
            )
            assertEquals("n=$n tur=$round: tur başına maç sayısı", beklenenMac, turMaclari.size)
        }
    }

    @Test
    fun fikstur_buyukCift_n32() = assertTekDevreFikstur(32)

    @Test
    fun fikstur_buyukTek_n33() = assertTekDevreFikstur(33)

    @Test
    fun fikstur_enKucukGecerli_n2ve3() {
        assertTekDevreFikstur(2)
        assertTekDevreFikstur(3)
    }

    // ==========================================================
    // ③ matchNumber SÖZLEŞMESİ
    // ==========================================================

    /**
     * REGRESYON BEKÇİSİ (eski B9). Bu sınavda ölçülen kusur: `createLeagueMatches`
     * matchNumber ATAMIYORDU — ligin bütün maçları `matchNumber = 0` doğuyordu.
     * ORTAK.md "matchNumber: oylama sırası — 0 BIRAKMA" diyor; oylama sırasını
     * yalnız `MatchDao` sorgusunun son anahtarı `id ASC` kurtarıyordu.
     * Düzeltme: tur içi 1..N (`RankingEngine.kt:76`).
     */
    @Test
    fun matchNumber_turIcinde1denNyeAtaniyor_regresyonBekcisi() {
        listOf(4, 5, 6, 9, 12).forEach { n ->
            val matches = RankingEngine.createLeagueMatches(makeSongs(n))
            assertTrue("n=$n: lig maçları üretilmedi", matches.isNotEmpty())
            assertTrue(
                "REGRESYON: n=$n'de matchNumber=0 kalmış maç var",
                matches.none { it.matchNumber == 0 }
            )

            matches.groupBy { it.round }.forEach { (round, turMaclari) ->
                assertEquals(
                    "n=$n tur=$round: matchNumber tur içinde 1..${turMaclari.size} olmalı",
                    (1..turMaclari.size).toList(),
                    turMaclari.map { it.matchNumber }.sorted()
                )
            }
        }
    }

    /**
     * Çift devrede rövanş maçı, orijinalinin TUR İÇİ sırasını korumalı; her
     * turun kendi içinde yine 1..N tekrarsız olmalı (`RankingEngine.kt:99`).
     */
    @Test
    fun matchNumber_ciftDevredeTurIciSiraKoruniyor() {
        listOf(5, 6, 13).forEach { n ->
            val matches = RankingEngine.createLeagueMatches(makeSongs(n), doubleRoundRobin = true)

            matches.groupBy { it.round }.forEach { (round, turMaclari) ->
                assertEquals(
                    "n=$n tur=$round: matchNumber tur içinde tekrarsız 1..N olmalı",
                    (1..turMaclari.size).toList(),
                    turMaclari.map { it.matchNumber }.sorted()
                )
                // Ligde bir takım tur içinde yalnız bir maç oynar — bu varsayım
                // `sonucDuzenlenebilirMi`nin LEAGUE dalının dayanağı
                val gorunen = turMaclari.flatMap { listOf(it.songId1, it.songId2) }
                assertEquals(
                    "n=$n tur=$round: takım turda iki maça girmiş",
                    gorunen.size, gorunen.toSet().size
                )
            }
        }
    }

    // ==========================================================
    // ④ MÜKERRER MAÇ KAYDI
    // ==========================================================

    /**
     * ÖLÇÜM (bulgu): aynı eşleşmenin iki satırı varsa motor İKİ KEZ sayar —
     * galibiyet 6 puana çıkar. Motorda tekilleştirme yok; çökme de uyarı da yok,
     * puan tablosu sessizce yanlış olur.
     */
    @Test
    fun mukerrerKayit_puanIkiKezSayiliyor_belgelenmisKusur() {
        val songs = makeSongs(4)
        val tek = listOf(completed(1, 1L, 2L, 1L))
        val cift = listOf(completed(1, 1L, 2L, 1L), completed(2, 1L, 2L, 1L))

        assertEquals(
            3.0,
            RankingEngine.calculateLeagueResults(songs, tek).first { it.songId == 1L }.score,
            0.0001
        )
        assertEquals(
            "BULGU DEĞİŞMİŞ: mükerrer kayıt artık tekilleştiriliyor — raporu güncelle",
            6.0,
            RankingEngine.calculateLeagueResults(songs, cift).first { it.songId == 1L }.score,
            0.0001
        )
    }

    @Test
    fun mukerrerKayit_averajDaIkiKezSayiliyor() {
        val songs = makeSongs(4)
        val cift = listOf(
            completed(1, 1L, 2L, 1L, score1 = 3, score2 = 1),
            completed(2, 1L, 2L, 1L, score1 = 3, score2 = 1)
        )
        val sonuc = RankingEngine.calculateLeagueResults(songs, cift)
        // Averaj doğrudan okunamıyor, sıralamadan dolaylı doğrula:
        // 1 → 6 puan (birinci), 2 → 0 puan ve -4 averaj (sonuncu)
        assertEquals(1L, sira(sonuc).first())
        assertEquals(2L, sira(sonuc).last())
    }

    @Test
    fun mukerrerKayit_buyukListedeCokmuyor() {
        val songs = makeSongs(32)
        val oynanmis = RankingEngine.createLeagueMatches(songs).mapIndexed { i, m ->
            m.copy(id = (i + 1).toLong(), winnerId = m.songId1, isCompleted = true)
        }
        val mukerrer = oynanmis + oynanmis.map { it.copy(id = it.id + 10_000) }
        val sonuc = RankingEngine.calculateLeagueResults(songs, mukerrer)
        assertEquals(32, sonuc.size)
        assertEquals((1..32).toList(), sonuc.map { it.position }.sorted())
    }

    // ==========================================================
    // ⑤ YABANCI winnerId (üçüncü takım)
    // ==========================================================

    /**
     * REGRESYON BEKÇİSİ (eski B8). Bu sınavda ölçülen kusur: yabancı
     * `winnerId`de puan uydurulmuyordu AMA skorlar averaja yazılmaya devam
     * ediyordu — bozuk maç puanı değil SIRALAMAYI etkiliyordu (ölçülen sıra
     * `[1,3,2]`, çünkü 1'e +5, 2'ye −5 averaj yazılıyordu).
     * Düzeltme: bozuk kazananlı maç TÜMÜYLE atlanıyor (averaj dâhil), hem
     * motorda hem ViewModel'in canlı tablosunda.
     */
    @Test
    fun yabanciKazanan_macTumuyleAtlaniyor_regresyonBekcisi() {
        val songs = makeSongs(3)
        val bozuk = listOf(completed(1, 1L, 2L, winner = 999L, score1 = 5, score2 = 0))
        val sonuc = RankingEngine.calculateLeagueResults(songs, bozuk)

        sonuc.forEach { assertEquals("Puan uydurulmamalı", 0.0, it.score, 0.0001) }
        assertEquals(
            "REGRESYON: bozuk maçın skoru yine averaja yazılıyor — sıra id'ye düşmeliydi",
            listOf(1L, 2L, 3L), sira(sonuc)
        )
    }

    /**
     * Bozuk kazanan kontrolü BERABERLİĞİ (winnerId == null) yememeli:
     * beraberlik geçerli bir sonuçtur, 1'er puan yazılmalı.
     */
    @Test
    fun yabanciKazanan_kontrolu_beraberligiYemiyor() {
        val songs = makeSongs(4)
        val maclar = listOf(
            completed(1, 1L, 2L, winner = null, score1 = 2, score2 = 2), // geçerli beraberlik
            completed(2, 3L, 4L, winner = 999L)                          // bozuk
        )
        val sonuc = RankingEngine.calculateLeagueResults(songs, maclar)

        assertEquals("Beraberlik 1 puan yazmalı", 1.0, sonuc.first { it.songId == 1L }.score, 0.0001)
        assertEquals("Beraberlik 1 puan yazmalı", 1.0, sonuc.first { it.songId == 2L }.score, 0.0001)
        assertEquals("Bozuk maç puan yazmamalı", 0.0, sonuc.first { it.songId == 3L }.score, 0.0001)
        assertEquals("Bozuk maç puan yazmamalı", 0.0, sonuc.first { it.songId == 4L }.score, 0.0001)
    }

    @Test
    fun yabanciKazanan_yetimMaclaBirlikteCokmuyor() {
        val songs = makeSongs(4)
        val karisik = listOf(
            completed(1, 1L, 2L, winner = 999L),   // yabancı kazanan
            completed(2, 1L, 77L, winner = 1L),    // yetim: 77 silinmiş
            completed(3, 88L, 99L, winner = 88L),  // iki taraf da yetim
            completed(4, 3L, 4L, winner = null)    // geçerli beraberlik
        )
        val sonuc = RankingEngine.calculateLeagueResults(songs, karisik)
        assertEquals(4, sonuc.size)
        assertEquals(1.0, sonuc.first { it.songId == 3L }.score, 0.0001)
        assertEquals(1.0, sonuc.first { it.songId == 4L }.score, 0.0001)
        assertEquals(0.0, sonuc.first { it.songId == 1L }.score, 0.0001)
        assertEquals(0.0, sonuc.first { it.songId == 2L }.score, 0.0001)
    }

    // ==========================================================
    // ⑥ rankingMethod SÜZGECİ
    // ==========================================================

    /**
     * ÖLÇÜM (bulgu): `calculateLeagueResults` yalnız `isCompleted` süzüyor,
     * `rankingMethod` SÜZMÜYOR — kendisine verilen SWISS maçını da 3/1/0 ile
     * sayıyor. Bugün zararsız, çünkü tek çağıran (`completeRanking`) maçları
     * zaten yönteme göre çekiyor. Ama `SwissSystem.computeState` AKSİNE süzüyor;
     * iki motor aynı konuda farklı davranıyor.
     */
    @Test
    fun yontemSuzgeci_ligMotoruYabanciYontemiDeSayiyor_belgelenmisKusur() {
        val songs = makeSongs(4)
        val karisik = listOf(
            completed(1, 1L, 2L, 1L, method = "LEAGUE"),
            completed(2, 3L, 4L, 3L, method = "SWISS")
        )
        val sonuc = RankingEngine.calculateLeagueResults(songs, karisik)
        assertEquals(
            "BULGU DEĞİŞMİŞ: motor artık rankingMethod süzüyor — raporu güncelle",
            3.0, sonuc.first { it.songId == 3L }.score, 0.0001
        )
    }

    // ==========================================================
    // ⑦ EŞİTLİK BOZUCU TÜKENDİĞİNDE — girdi sırasına bağımlılık
    // ==========================================================

    /**
     * REGRESYON BEKÇİSİ. Bu sınavın ilk turunda ölçülen kusur: motorun zinciri
     * puan → averaj → atılan gol ile BİTİYORDU; sonrası `sortedWith`
     * kararlılığına, yani `songs` listesinin sırasına kalıyordu ve songs ters
     * verilince sonuç da tersine dönüyordu. Koordinatör zinciri ViewModel'inki
     * ile eşitledi (… → galibiyet → song.id). Artık girdi sırası sonucu
     * DEĞİŞTİRMEMELİ — kilitliyoruz.
     */
    @Test
    fun esitlik_girdiSirasindanBagimsiz_regresyonBekcisi() {
        val songs = makeSongs(4)
        val maclar = listOf(
            completed(1, 1L, 2L, null),
            completed(2, 3L, 4L, null)
        ) // herkes 1 puan, averaj 0, atılan 0, 0 galibiyet — son çare: song.id

        val duz = sira(RankingEngine.calculateLeagueResults(songs, maclar))
        val ters = sira(RankingEngine.calculateLeagueResults(songs.reversed(), maclar))

        assertEquals("Son çare song.id olmalı", listOf(1L, 2L, 3L, 4L), duz)
        assertEquals(
            "REGRESYON: motor yine girdi sırasına bağımlı hâle gelmiş " +
                "(zincirdeki .thenBy { it.id } düşmüş olabilir)",
            duz, ters
        )
    }

    /**
     * Eşit puanda "galibiyet sayısı" bozucusunun gerçekten işlediğini kilitler:
     * 1 numaralı takım 3 beraberlikle, 2 numaralı 1 galibiyetle 3'er puanda.
     * Galibiyet bozucusu olmasaydı sıra girdi sırasına düşüp 1'i üste alırdı.
     */
    @Test
    fun esitlik_galibiyetSayisiBozucusuIsliyor() {
        val songs = makeSongs(5)
        val maclar = listOf(
            completed(1, 1L, 3L, null),
            completed(2, 1L, 4L, null),
            completed(3, 1L, 5L, null),  // 1 -> 3 puan, 0 galibiyet
            completed(4, 2L, 3L, 2L),    // 2 -> 3 puan, 1 galibiyet
            completed(5, 2L, 4L, 4L)
        )
        val siralama = sira(RankingEngine.calculateLeagueResults(songs, maclar))
        assertEquals("En çok puanlı takım 4 olmalı (4 puan)", 4L, siralama[0])
        assertTrue(
            "Eşit puanda daha çok galibiyeti olan (2) önde olmalı, sıra: $siralama",
            siralama.indexOf(2L) < siralama.indexOf(1L)
        )
    }

    @Test
    fun esitlik_ayniGirdiSirasiylaTekrarlanabilir() {
        val songs = makeSongs(8)
        val maclar = songs.chunked(2).mapIndexed { i, ikili ->
            completed((i + 1).toLong(), ikili[0].id, ikili[1].id, null)
        }
        val a = sira(RankingEngine.calculateLeagueResults(songs, maclar))
        val b = sira(RankingEngine.calculateLeagueResults(songs, maclar))
        val c = sira(RankingEngine.calculateLeagueResults(songs, maclar.reversed()))
        assertEquals("Aynı girdi iki çağrıda farklı sonuç verdi", a, b)
        assertEquals("Maç listesinin sırası sonucu değiştirdi", a, c)
    }

    // ==========================================================
    // ⑧ TAM ÇİFT DEVRE SİMÜLASYONU — tek sayıda puan korunumu
    // ==========================================================

    @Test
    fun tamCiftDevreSimulasyonu_tekSayi_puanToplamiTutuyor() {
        val n = 9
        val songs = makeSongs(n)
        val oynanmis = RankingEngine.createLeagueMatches(songs, doubleRoundRobin = true)
            .mapIndexed { i, m ->
                m.copy(
                    id = (i + 1).toLong(),
                    winnerId = if (i % 3 == 0) null else m.songId1,
                    isCompleted = true
                )
            }
        val sonuc = RankingEngine.calculateLeagueResults(songs, oynanmis)

        val beraberlik = oynanmis.count { it.winnerId == null }
        val galibiyet = oynanmis.size - beraberlik

        assertEquals(n, sonuc.size)
        assertEquals(
            "Dağıtılan toplam puan galibiyet*3 + beraberlik*2 olmalı",
            galibiyet * 3.0 + beraberlik * 2.0, sonuc.sumOf { it.score }, 0.0001
        )
        assertEquals((1..n).toList(), sonuc.map { it.position }.sorted())
    }
}
