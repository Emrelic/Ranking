package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.SwissSystem
import org.junit.Assert.*
import org.junit.Test

/**
 * DÜZ İSVİÇRE MOTORU — UZMAN SINAVI (oturumlar/ESKI-MOTORLAR-SINAV-GOREV.md § B)
 *
 * `SwissSystemTest` (24 test) sınır durumlarını, n=7 bye rotasyonunu, n=8
 * tekrarsızlığı, yetim maçı, replay determinizmini ve n=64/128 performansını
 * zaten kapsıyor. BU dosya yalnız oradaki BOŞLUKLARI doldurur:
 *
 *  ① Bye adaleti UZUN koşumda ve KARARLI sonuçlarla: n=9, n=15 tükenene kadar
 *     (mevcut test n=7 ve hepsi-berabere; berabere olunca sıralama hiç
 *     değişmiyor, bye seçimi gerçek baskı altına girmiyor)
 *  ② Tükenme ölçümü: tekrarsız eşleştirme kaç turda imkânsızlaşıyor
 *  ③ recommendedRoundCount uç değerleri (0,1,2,3,4,5,8,9,16,17)
 *  ④ Geri izleme dürüstlüğü: ağır geçmiş altında ÜRETİLEN her eşleşme
 *     tekrarsız mı (sessiz yanlış eşleşme YOK)
 *  ⑤ computeState maç listesinin SIRASINDAN bağımsız mı
 *  ⑥ Bozuk veri üçlüsü: yabancı winnerId · mükerrer kayıt · mükerrer bye
 *  ⑦ UYGULAMA AKIŞI: turnuvanın 1. turu gerçekten SwissSystem'den geliyor mu
 *     (sınavın ilk turundaki kör nokta: motor ölçüldü, uygulamanın motoru
 *     NASIL ÇAĞIRDIĞI ölçülmedi — bkz. rapor § B10)
 *
 * ⚠️ `SwissSystem.kt` yalnız OKUNDU, yazılmadı.
 */
class EskiMotorSwissSinaviTest {

    private val listeId = 1L

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Team$i", listId = listeId) }

    private fun normalize(a: Long, b: Long) = if (a < b) Pair(a, b) else Pair(b, a)

    private fun mac(
        id: Long, s1: Long, s2: Long, winner: Long?,
        round: Int = 1, method: String = SwissSystem.METHOD
    ) = Match(
        id = id, listId = listeId, rankingMethod = method,
        songId1 = s1, songId2 = s2, winnerId = winner,
        round = round, matchNumber = 1, isCompleted = true
    )

    /**
     * Turnuvayı tur bütçesini ZORLAYARAK (maxRounds=999) tekrarsız eşleştirme
     * kurulamayana kadar oynatır. `SwissState` alanları public; createNextRound
     * yalnız verilen state'e baktığı için bu meşru bir izolasyon
     * (SwissSystemTest.testSevenTeamsByeFairRotation aynı yolu kullanıyor).
     *
     * Döner: (tüm tamamlanmış maçlar, oynanan tur sayısı, tur başına bye id'si)
     */
    private fun tukeneneKadarOynat(
        songs: List<Song>,
        turSiniri: Int,
        kazananSecici: (Match) -> Long?
    ): Triple<List<Match>, Int, List<Long>> {
        val tumu = mutableListOf<Match>()
        val byeSirasi = mutableListOf<Long>()
        var sonrakiId = 1L
        var tur = 0

        while (tur < turSiniri) {
            val dogal = SwissSystem.computeState(songs, tumu)
            val zorlanmis = dogal.copy(maxRounds = 999, isComplete = false)
            val esleme = SwissSystem.createNextRound(zorlanmis, tumu)
            if (!esleme.canContinue) break
            tur++

            esleme.byeTeam?.let { byeSirasi.add(it.id) }
            esleme.matches.forEach { m ->
                val bitmis = if (m.isCompleted) m.copy(id = sonrakiId++)
                else m.copy(id = sonrakiId++, winnerId = kazananSecici(m), isCompleted = true)
                tumu.add(bitmis)
            }
        }
        return Triple(tumu, tur, byeSirasi)
    }

    private fun kucukIdKazanir(m: Match): Long? = minOf(m.songId1, m.songId2)

    // ==========================================================
    // ① BYE ADALETİ — uzun koşum, KARARLI sonuçlar
    // ==========================================================

    private fun assertByeAdaleti(n: Int) {
        val songs = makeSongs(n)
        val (tumMaclar, tur, byeSirasi) = tukeneneKadarOynat(songs, turSiniri = 60) { kucukIdKazanir(it) }

        assertTrue("n=$n: en az 3 tur oynanabilmeliydi (oynanan: $tur)", tur >= 3)
        assertEquals("n=$n: tek takım sayısında her turda tam 1 bye olmalı", tur, byeSirasi.size)

        val byeSayaci = songs.associate { it.id to 0 }.toMutableMap()
        byeSirasi.forEach { byeSayaci[it] = (byeSayaci[it] ?: 0) + 1 }
        val enAz = byeSayaci.values.min()
        val enCok = byeSayaci.values.max()

        println(
            "[İSVİÇRE BYE ADALETİ] n=$n tur=$tur mac=${tumMaclar.count { it.songId1 != it.songId2 }} " +
                "byeMin=$enAz byeMax=$enCok dagilim=${byeSayaci.toSortedMap()}"
        )
        assertTrue(
            "n=$n: bye dağılımı adil değil — min=$enAz max=$enCok, tur=$tur, dağılım=$byeSayaci",
            enCok - enAz <= 1
        )
        assertEquals(
            "n=$n: bye toplamı tur sayısına eşit olmalı",
            tur, byeSayaci.values.sum()
        )

        // Aynı koşumda tekrar eşleşme OLMAMALI
        val gorulen = mutableSetOf<Pair<Long, Long>>()
        tumMaclar.filter { it.songId1 != it.songId2 }.forEach { m ->
            val k = normalize(m.songId1, m.songId2)
            assertFalse("n=$n: $k ikilisi İKİ KEZ eşleşti (kırmızı çizgi ihlali)", k in gorulen)
            gorulen.add(k)
        }
    }

    @Test
    fun byeAdaleti_uzunKosum_n9() = assertByeAdaleti(9)

    @Test
    fun byeAdaleti_uzunKosum_n15() = assertByeAdaleti(15)

    @Test
    fun byeAdaleti_uzunKosum_n11_beraberlikYok() {
        // Beraberliksiz koşumda puan grupları hızla ayrışır; bye seçiminin
        // "en alttaki, bye geçmemiş takım" kuralı en çok burada zorlanır.
        assertByeAdaleti(11)
    }

    @Test
    fun byeAdaleti_ilkTurdaEnAlttakiTakimaVerilir() {
        // Hiç maç oynanmamışken herkes 0 puan; standingsComparator son çare
        // olarak id ASC sıraladığı için "en alt" = en büyük id.
        val songs = makeSongs(5)
        val state = SwissSystem.computeState(songs, emptyList())
        val esleme = SwissSystem.createNextRound(state, emptyList())
        assertTrue(esleme.canContinue)
        assertEquals("İlk turda bye en alttaki (en büyük id) takıma gitmeli", 5L, esleme.byeTeam?.id)
    }

    // ==========================================================
    // ② TÜKENME ÖLÇÜMÜ — kaç tura kadar tekrarsız eşleştirme kurulabiliyor
    // ==========================================================

    /**
     * ÖLÇÜM: n takımlı bir İsviçre'de tekrarsız tam eşleştirmenin teorik üst
     * sınırı, tek n'de n turdur (herkes n-1 rakip + 1 bye). Motor greedy +
     * geri izlemeli olduğu için bu sınıra ULAŞMAK zorunda değil; ulaşılan tur
     * sayısını ÖLÇÜP kilitliyoruz ki ileride gerileme fark edilsin.
     */
    @Test
    fun tukenme_tekSayilardaUlasilanTurSayisi() {
        val olculen = mutableMapOf<Int, Int>()
        listOf(5, 7, 9, 11, 15).forEach { n ->
            val (_, tur, _) = tukeneneKadarOynat(makeSongs(n), turSiniri = 60) { kucukIdKazanir(it) }
            olculen[n] = tur
        }
        println("[İSVİÇRE TÜKENME] tek n -> ulaşılan tur: $olculen " +
            "(teorik üst sınır n; önerilen tur ceil(log2 n))")
        // Teorik üst sınır n; motorun altına düşmesi kusur değil ama ÖLÇÜLÜ olmalı.
        olculen.forEach { (n, tur) ->
            assertTrue("n=$n: teorik üst sınır $n turu aşıldı (ölçülen $tur) — imkânsız", tur <= n)
            assertTrue(
                "n=$n: yalnız $tur tur kurulabildi, recommendedRoundCount " +
                    "(${SwissSystem.recommendedRoundCount(n)}) bile karşılanamıyor. Ölçüm: $olculen",
                tur >= SwissSystem.recommendedRoundCount(n)
            )
        }
    }

    @Test
    fun tukenme_ciftSayilardaUlasilanTurSayisi() {
        val olculen = mutableMapOf<Int, Int>()
        listOf(4, 6, 8, 12, 16).forEach { n ->
            val (_, tur, byeSirasi) = tukeneneKadarOynat(makeSongs(n), turSiniri = 60) { kucukIdKazanir(it) }
            olculen[n] = tur
            println("[İSVİÇRE TÜKENME] çift n=$n -> tur=$tur (teorik üst sınır ${n - 1}, " +
                "önerilen ${SwissSystem.recommendedRoundCount(n)})")
            assertTrue("n=$n: çift takım sayısında bye ÜRETİLMEMELİ (üretilen: $byeSirasi)", byeSirasi.isEmpty())
            assertTrue("n=$n: teorik üst sınır ${n - 1} turu aşıldı (ölçülen $tur)", tur <= n - 1)
            assertTrue(
                "n=$n: yalnız $tur tur kurulabildi, önerilen tur " +
                    "${SwissSystem.recommendedRoundCount(n)} karşılanamıyor",
                tur >= SwissSystem.recommendedRoundCount(n)
            )
        }
    }

    // ==========================================================
    // ③ recommendedRoundCount UÇ DEĞERLERİ
    // ==========================================================

    @Test
    fun onerilenTurSayisi_ucDegerler() {
        assertEquals("n=0: oynanacak maç yok", 0, SwissSystem.recommendedRoundCount(0))
        assertEquals("n=1: oynanacak maç yok", 0, SwissSystem.recommendedRoundCount(1))
        assertEquals("n=2: tek maç, tek tur", 1, SwissSystem.recommendedRoundCount(2))
        assertEquals("n=3: ceil(log2 3)=2", 2, SwissSystem.recommendedRoundCount(3))
        assertEquals("n=4: tam kuvvet, ceil(log2 4)=2", 2, SwissSystem.recommendedRoundCount(4))
        assertEquals("n=5", 3, SwissSystem.recommendedRoundCount(5))
        assertEquals("n=8: tam kuvvet", 3, SwissSystem.recommendedRoundCount(8))
        assertEquals("n=9", 4, SwissSystem.recommendedRoundCount(9))
        assertEquals("n=16: tam kuvvet", 4, SwissSystem.recommendedRoundCount(16))
        assertEquals("n=17", 5, SwissSystem.recommendedRoundCount(17))
    }

    @Test
    fun onerilenTurSayisi_negatifGirdideCokmuyor() {
        assertEquals("Negatif takım sayısı 0 tur vermeli", 0, SwissSystem.recommendedRoundCount(-3))
    }

    /**
     * Tam 2 kuvvetlerinde kayan nokta yuvarlaması ceil'i bir fazlaya
     * itmemeli (motorda 1e-9 payı bunun için var). 2^1..2^12 taranıyor.
     */
    @Test
    fun onerilenTurSayisi_tamKuvvetlerdeYuvarlamaHatasiYok() {
        var n = 2
        var beklenen = 1
        while (n <= 4096) {
            assertEquals("n=$n tam kuvvetinde tur sayısı", beklenen, SwissSystem.recommendedRoundCount(n))
            n *= 2
            beklenen++
        }
    }

    // ==========================================================
    // ④ GERİ İZLEME DÜRÜSTLÜĞÜ — sessiz yanlış eşleşme YOK
    // ==========================================================

    /**
     * Ağır geçmiş altında (her takım rakiplerinin çoğuyla oynamış) motor ya
     * TEKRARSIZ tam eşleştirme döndürmeli ya da canContinue=false demeli.
     * "Bulamadım, ilk ikisini eşleştireyim" davranışı YASAK (kural 1).
     */
    @Test
    fun geriIzleme_agirGecmisAltindaAslaTekrarEslestirmiyor() {
        listOf(8, 10, 12, 16).forEach { n ->
            val songs = makeSongs(n)
            val tumu = mutableListOf<Match>()
            var sonrakiId = 1L
            var tur = 0

            while (tur < 40) {
                val dogal = SwissSystem.computeState(songs, tumu)
                val zorlanmis = dogal.copy(maxRounds = 999, isComplete = false)
                val gecmis = tumu.filter { it.songId1 != it.songId2 }
                    .map { normalize(it.songId1, it.songId2) }.toSet()

                val esleme = SwissSystem.createNextRound(zorlanmis, tumu)
                if (!esleme.canContinue) {
                    assertTrue("n=$n: dürüst bitişte gerekçe boş olmamalı", esleme.reason.isNotBlank())
                    assertTrue("n=$n: dürüst bitişte yarım maç üretilmemeli", esleme.matches.isEmpty())
                    break
                }
                tur++

                // ÜRETİLEN her eşleşme geçmişte OLMAMALI
                esleme.matches.filter { it.songId1 != it.songId2 }.forEach { m ->
                    assertFalse(
                        "n=$n tur=$tur: motor daha önce oynanmış ${normalize(m.songId1, m.songId2)} " +
                            "ikilisini SESSİZCE tekrar eşleştirdi",
                        normalize(m.songId1, m.songId2) in gecmis
                    )
                }
                // Tam eşleştirme: çift n'de herkes oynamalı
                val katilan = esleme.matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
                assertEquals("n=$n tur=$tur: tam eşleştirme kurulmalı", n, katilan.size)

                esleme.matches.forEach { m ->
                    tumu.add(m.copy(id = sonrakiId++, winnerId = kucukIdKazanir(m), isCompleted = true))
                }
            }
            println("[İSVİÇRE TEKRARSIZLIK] ağır geçmiş n=$n -> $tur tur kuruldu, sıfır tekrar eşleşme")
            assertTrue("n=$n: hiç tur oynanamadı", tur > 0)
        }
    }

    /**
     * Geri izleme bütçesi (MAX_BACKTRACK_ATTEMPTS = 50.000) makul sürede
     * kapanmalı: yapısal olarak imkânsız ve BÜYÜK bir senaryoda motor
     * takılıp kalmamalı, dürüstçe false dönmeli.
     */
    @Test
    fun geriIzleme_imkansizBuyukSenaryodaMakulSuredeDurustcaBitiyor() {
        val n = 14
        val songs = makeSongs(n)
        // 1 numaralı takım HERKESLE oynamış; kalan havuzda ona rakip yok.
        val gecmis = (2..n).mapIndexed { i, other ->
            mac(id = (i + 1).toLong(), s1 = 1L, s2 = other.toLong(), winner = 1L, round = 1)
        }
        val state = SwissSystem.computeState(songs, gecmis)
            .copy(maxRounds = 999, isComplete = false)

        val basla = System.nanoTime()
        val esleme = SwissSystem.createNextRound(state, gecmis)
        val gecenMs = (System.nanoTime() - basla) / 1_000_000

        println("[İSVİÇRE GERİ İZLEME] n=$n imkânsız senaryo -> canContinue=${esleme.canContinue} " +
            "sure=${gecenMs}ms (butce=50.000 aday)")
        assertFalse("Yapısal olarak imkânsız — canContinue=false olmalı", esleme.canContinue)
        assertTrue("Gerekçe boş olmamalı", esleme.reason.isNotBlank())
        assertTrue("Yarım/geçersiz maç üretilmemeli", esleme.matches.isEmpty())
        assertTrue("Geri izleme 5 sn'yi aşmamalı (ölçülen: ${gecenMs}ms)", gecenMs < 5000)
    }

    // ==========================================================
    // ⑤ computeState — maç listesinin SIRASINDAN bağımsızlık
    // ==========================================================

    @Test
    fun computeState_macSirasindanBagimsiz() {
        val songs = makeSongs(9)
        val (tumMaclar, tur, _) = tukeneneKadarOynat(songs, turSiniri = 20) { kucukIdKazanir(it) }
        assertTrue("Ölçüm için en az 3 tur gerekli", tur >= 3)

        val duz = SwissSystem.computeState(songs, tumMaclar)
        val ters = SwissSystem.computeState(songs, tumMaclar.reversed())
        val karisik = SwissSystem.computeState(songs, tumMaclar.sortedBy { it.songId2 })

        assertEquals("Maç listesi ters verilince state değişti", duz, ters)
        assertEquals("Maç listesi farklı sırayla verilince state değişti", duz, karisik)
    }

    @Test
    fun computeState_songsSirasindanBagimsizPuanUretiyor() {
        val songs = makeSongs(8)
        val (tumMaclar, _, _) = tukeneneKadarOynat(songs, turSiniri = 20) { kucukIdKazanir(it) }

        val duz = SwissSystem.computeState(songs, tumMaclar).teams.associate { it.id to it.points }
        val ters = SwissSystem.computeState(songs.reversed(), tumMaclar).teams.associate { it.id to it.points }
        assertEquals("songs sırası puanları değiştirdi", duz, ters)
    }

    // ==========================================================
    // ⑥ BOZUK VERİ ÜÇLÜSÜ
    // ==========================================================

    /**
     * ÖLÇÜM: yabancı winnerId'de puan/galibiyet UYDURULMUYOR (motorun kendi
     * yorumu böyle diyor) ama maç OYNANMIŞ sayılıyor: played artıyor ve ikili
     * tekrar eşleşme geçmişine yazılıyor. Bu davranış test edilmemişti.
     */
    @Test
    fun bozukVeri_yabanciKazanan_puanVermiyorAmaGecmiseYaziliyor() {
        val songs = makeSongs(4)
        val bozuk = listOf(mac(1, 1L, 2L, winner = 999L))
        val state = SwissSystem.computeState(songs, bozuk)

        val t1 = state.teams.first { it.id == 1L }
        val t2 = state.teams.first { it.id == 2L }
        assertEquals("Yabancı kazananda puan uydurulmamalı", 0.0, t1.points, 0.0001)
        assertEquals("Yabancı kazananda puan uydurulmamalı", 0.0, t2.points, 0.0001)
        assertEquals("Galibiyet uydurulmamalı", 0, t1.won)
        assertEquals("Mağlubiyet uydurulmamalı", 0, t1.lost)
        assertEquals("Beraberlik uydurulmamalı", 0, t1.drawn)
        assertEquals("Maç oynanmış sayılmalı", 1, t1.played)
        assertTrue("İkili tekrar eşleşme geçmişine yazılmalı", 2L in t1.opponentIds)

        // Bir sonraki turda 1-2 ikilisi TEKRAR eşleşmemeli
        val esleme = SwissSystem.createNextRound(state, bozuk)
        if (esleme.canContinue) {
            assertFalse(
                "Bozuk maç yüzünden 1-2 ikilisi tekrar eşleştirildi",
                esleme.matches.any { normalize(it.songId1, it.songId2) == Pair(1L, 2L) }
            )
        }
    }

    /**
     * ÖLÇÜM (bulgu): mükerrer maç kaydında puan ve `played` İKİ KEZ sayılıyor;
     * `opponentIds` bir Set olduğu için rakip listesi doğru kalıyor (tekrar
     * eşleşme yasağı bozulmuyor). Yani mükerrer kayıt sıralamayı bozar,
     * kırmızı çizgiyi bozmaz.
     */
    @Test
    fun bozukVeri_mukerrerKayit_puanIkiKezSayiliyor_belgelenmisKusur() {
        val songs = makeSongs(4)
        val cift = listOf(mac(1, 1L, 2L, 1L), mac(2, 1L, 2L, 1L))
        val state = SwissSystem.computeState(songs, cift)
        val t1 = state.teams.first { it.id == 1L }

        assertEquals(
            "BULGU DEĞİŞMİŞ: mükerrer kayıt artık tekilleştiriliyor — raporu güncelle",
            2.0, t1.points, 0.0001
        )
        assertEquals("played de iki kez sayılıyor", 2, t1.played)
        assertEquals("opponentIds Set olduğu için tekilleşmeli", setOf(2L), t1.opponentIds)
    }

    /**
     * ÖLÇÜM (bulgu): mükerrer BYE kaydında da bye puanı ve byeCount iki kez
     * sayılıyor. byeCount şişince bye rotasyonu o takımı haksız yere atlar.
     */
    @Test
    fun bozukVeri_mukerrerBye_byeCountIkiKezSayiliyor_belgelenmisKusur() {
        val songs = makeSongs(5)
        val ciftBye = listOf(mac(1, 5L, 5L, 5L), mac(2, 5L, 5L, 5L))
        val t5 = SwissSystem.computeState(songs, ciftBye).teams.first { it.id == 5L }

        assertEquals(
            "BULGU DEĞİŞMİŞ: mükerrer bye artık tekilleştiriliyor — raporu güncelle",
            2, t5.byeCount
        )
        assertEquals("Bye puanı da iki kez yazılıyor", 2.0, t5.points, 0.0001)
    }

    @Test
    fun bozukVeri_yabanciYontemMaciSayilmiyor() {
        // SwissSystem (lig motorunun AKSİNE) rankingMethod süzüyor — kilitle.
        val songs = makeSongs(4)
        val yabanci = listOf(mac(1, 1L, 2L, 1L, method = "LEAGUE"))
        val state = SwissSystem.computeState(songs, yabanci)
        state.teams.forEach {
            assertEquals("LEAGUE maçı SWISS durumuna sızdı", 0.0, it.points, 0.0001)
            assertEquals(0, it.played)
        }
    }

    @Test
    fun bozukVeri_ucuBirArada_cokmuyor() {
        val songs = makeSongs(6)
        val karisik = listOf(
            mac(1, 1L, 2L, winner = 999L),      // yabancı kazanan
            mac(2, 3L, 4L, 3L), mac(3, 3L, 4L, 3L), // mükerrer
            mac(4, 5L, 77L, 5L),                // yetim (77 silinmiş)
            mac(5, 88L, 99L, 88L),              // iki taraf da yetim
            mac(6, 6L, 6L, 6L), mac(7, 6L, 6L, 6L)  // mükerrer bye
        )
        val state = SwissSystem.computeState(songs, karisik)
        assertEquals(6, state.teams.size)

        val esleme = SwissSystem.createNextRound(state, karisik)
        // Ne olursa olsun: ya dürüst bitiş, ya tekrarsız geçerli eşleştirme
        if (esleme.canContinue) {
            val gecmis = setOf(Pair(1L, 2L), Pair(3L, 4L))
            esleme.matches.filter { it.songId1 != it.songId2 }.forEach {
                assertFalse(
                    "Bozuk veri altında tekrar eşleşme üretildi",
                    normalize(it.songId1, it.songId2) in gecmis
                )
            }
        } else {
            assertTrue(esleme.reason.isNotBlank())
        }

        val sonuc = SwissSystem.calculateResults(songs, karisik)
        assertEquals("Bozuk veri altında sonuç üretilemedi", 6, sonuc.size)
        assertEquals((1..6).toList(), sonuc.map { it.position }.sorted())
    }

    @Test
    fun sonuclar_yalnizByeOynanmisTurnuvadaCokmuyor() {
        val songs = makeSongs(3)
        val sadeceBye = listOf(mac(1, 3L, 3L, 3L))
        val sonuc = SwissSystem.calculateResults(songs, sadeceBye)
        assertEquals(3, sonuc.size)
        assertEquals("Bye alan takım 1 puanla birinci olmalı", 3L, sonuc.first { it.position == 1 }.songId)
        assertEquals(1.0, sonuc.first { it.songId == 3L }.score, 0.0001)
    }

    // ==========================================================
    // ⑦ UYGULAMA AKIŞI — 1. TUR GERÇEKTEN SwissSystem'DEN Mİ GELİYOR
    //
    // 🔴 Bu bölüm, sınavın ilk turundaki bir KÖR NOKTAYI kapatıyor.
    // Yukarıdaki bütün ölçümler SwissSystem'i DOĞRUDAN besleyerek yapıldı;
    // uygulamanın motoru NASIL çağırdığı ölçülmedi. Ölçüm doğruydu, ölçülen
    // şey eksikti. Bulunduğunda akış şöyleydi:
    //   · 1. tur:  RankingViewModel.initializeSwiss
    //              -> RankingEngine.createSwissMatches(songs, 1, emptyList())  ESKİ MOTOR
    //   · 2+ tur:  RankingViewModel.createNextSwissRound
    //              -> SwissSystem.computeState / createNextRound              YENİ MOTOR
    // Yani düz İsviçre turnuvasının İLK TURU menüden gizlenmiş eski motordan
    // geliyordu; tek sayıda öğede son öğe hiçbir çifte girmiyordu.
    // (Bulgu ranking-fd'den geldi, burada bağımsız doğrulandı.)
    //
    // ✅ DÜZELTİLDİ: initializeSwiss artık createNextSwissRound(1) üzerinden
    // SwissSystem'i kullanıyor (RankingViewModel:515). Aşağıdaki iki test
    // bunun REGRESYON BEKÇİSİ — eski yola dönülürse kırmızıya dönerler.
    // ==========================================================

    // ⚠️ MÜKERRER YAZMA: kusurun kendisi (tek sayıda bir öğenin düşmesi, bye
    // kaydının üretilmemesi, shuffled() yüzünden düşen öğenin her koşumda
    // değişmesi, çift sayıda kusurun olmaması, ikinci giriş noktası
    // `createSwissMatchesWithState`in aynı kusuru taşıması) commit `dd4fe79`
    // ile gelen `SwissBirinciTurGirisNoktasiTest.kt` dosyasında kapsandı.
    // ORTAK.md gereği tekrarlanmadı. BURADA yalnız o dosyada OLMAYAN iki
    // ölçüm var: matchNumber rejim ayrışması ve GERÇEK AKIŞ bye dağılımı.
    //
    // Bulgunun zinciri: `ranking-fd` (eleme denetimi) kod okumasıyla buldu ve
    // "testi yok" statüsüyle teslim etti → `ranking-07` doküman kaydına aldı →
    // üçüncü bir oturum testi yazdı. O dosyanın yazarı oturum adıyla bilinmiyor
    // (tüm commit'ler tek git kimliğinden atılıyor); kaydı burada dosya ve
    // commit numarası üzerinden tutuluyor.

    // ÖLÜ DAL TESTİ KALDIRILDI (koordinatör, 2026-09-02): sınadığı
    // `RankingEngine.createSwissMatches` / `createSwissMatchesWithState` /
    // `createSwissMatchesAdvanced` üçlüsü ana koddan SİLİNDİ (çağıranları
    // kalmamıştı; kusur kaydı RankingEngine.kt'deki kaldırma notunda ve
    // ESKI-MOTORLAR-SINAV-RAPOR.md'de yaşıyor). Aynı sebeple kusurun tam
    // kapsaması olan SwissBirinciTurGirisNoktasiTest.kt dosyası da silindi.

    /**
     * REGRESYON BEKÇİSİ — SWISS 1. TUR GİRİŞ NOKTASI.
     *
     * Bu sınavda ölçülen kusur (B10): uygulamanın 1. turu `SwissSystem`'den
     * DEĞİL eski motordan geliyordu; tek sayıda öğede son öğe hiçbir çifte
     * girmiyordu (maç yok, bye yok, puan yok), `shuffled()` yüzünden düşen öğe
     * her koşumda değişiyordu ve `matchNumber` hiç atanmıyordu.
     *
     * Düzeltme: `initializeSwiss` → `createNextSwissRound(1)` → `SwissSystem`.
     * Bu test artık uygulamanın gerçekte kullandığı 1. tur üreticisini sınıyor.
     */
    @Test
    fun uygulamaAkisi_ilkTurArtikSwissSystemden_regresyonBekcisi() {
        listOf(5, 8, 9, 15).forEach { n ->
            val songs = makeSongs(n)
            // createNextSwissRound(1)'in yaptığı: boş geçmişle computeState + createNextRound
            val esleme = SwissSystem.createNextRound(
                SwissSystem.computeState(songs, emptyList()), emptyList()
            )
            assertTrue("n=$n: 1. tur kurulabilmeli", esleme.canContinue)

            val katilan = esleme.matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
            assertEquals("n=$n: 1. turda HİÇBİR öğe düşmemeli", n, katilan.size)

            val gercekMaclar = esleme.matches.filter { it.songId1 != it.songId2 }
            if (n % 2 == 0) {
                assertNull("n=$n: çift sayıda bye olmamalı", esleme.byeTeam)
                assertEquals("n=$n: n/2 maç", n / 2, gercekMaclar.size)
            } else {
                assertNotNull("n=$n: tek sayıda bye ÜRETİLMELİ", esleme.byeTeam)
                assertEquals("n=$n: (n-1)/2 maç", (n - 1) / 2, gercekMaclar.size)
                assertEquals(
                    "n=$n: bye tamamlanmış bir öz-eşleşme kaydı olmalı",
                    1, esleme.matches.count { it.songId1 == it.songId2 && it.isCompleted }
                )
            }

            assertEquals(
                "n=$n: gerçek maçlara 1..N matchNumber atanmalı (kural 7)",
                (1..gercekMaclar.size).toList(),
                gercekMaclar.map { it.matchNumber }.sorted()
            )

            // Determinizm: aynı girdi iki çağrıda aynı eşleştirme (shuffled YOK)
            val ikinci = SwissSystem.createNextRound(
                SwissSystem.computeState(songs, emptyList()), emptyList()
            )
            assertEquals(
                "n=$n: 1. tur deterministik olmalı",
                esleme.matches.map { Triple(it.songId1, it.songId2, it.matchNumber) },
                ikinci.matches.map { Triple(it.songId1, it.songId2, it.matchNumber) }
            )
        }
    }

    /**
     * GERÇEK AKIŞ SİMÜLASYONU — düzeltme sonrası.
     * Artık 1. tur da `SwissSystem`'den geliyor, yani tek takım sayısında
     * HER turda bir bye olmalı: bye sayısı == tur sayısı.
     * (Düzeltmeden önce ölçülen: n=5 → 3 turda 2 bye, n=9 → 4 turda 3 bye,
     *  n=15 → 4 turda 3 bye; hep bir eksik.)
     */
    @Test
    fun uygulamaAkisi_gercekAkistaHerTurdaBye_regresyonBekcisi() {
        listOf(5, 9, 15).forEach { n ->
            val songs = makeSongs(n)
            val tumu = mutableListOf<Match>()
            var sonrakiId = 1L
            val maxTur = SwissSystem.recommendedRoundCount(n)
            var tur = 0

            while (tur < maxTur) {
                val state = SwissSystem.computeState(songs, tumu)
                val esleme = SwissSystem.createNextRound(state, tumu)
                if (!esleme.canContinue) break
                tur++
                esleme.matches.forEach { m ->
                    val bitmis = if (m.isCompleted) m.copy(id = sonrakiId++)
                    else m.copy(id = sonrakiId++, winnerId = kucukIdKazanir(m), isCompleted = true)
                    tumu.add(bitmis)
                }
            }

            val byeler = tumu.filter { it.songId1 == it.songId2 }.map { it.songId1 }
            println("[İSVİÇRE GERÇEK AKIŞ] n=$n tur=$tur byeSayisi=${byeler.size} byeler=$byeler")

            assertEquals("n=$n: tur bütçesi dolmalı", maxTur, tur)
            assertEquals(
                "REGRESYON: n=$n tek takım sayısında her turda bir bye olmalı " +
                    "(tur=$tur, bye=${byeler.size}) — 1. tur yine eski motora düşmüş olabilir",
                tur, byeler.size
            )
            // Hiçbir öğe hiçbir turda kayıtsız kalmamalı
            tumu.groupBy { it.round }.forEach { (round, turKayitlari) ->
                val gorunen = turKayitlari.flatMap { listOf(it.songId1, it.songId2) }.toSet()
                assertEquals("n=$n tur=$round: bir öğe kayıtsız kaldı", n, gorunen.size)
            }
        }
    }
}
