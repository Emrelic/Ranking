package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * ESKİ ELEME MOTORLARININ DENETİMİ — `RankingEngine.kt` (ELIMINATION + FULL_ELIMINATION).
 *
 * Bu dosya KUSUR DÜZELTMEZ. Menüden gizlenmiş iki eski motorun bugünkü
 * davranışını SAYIYLA sabitler ki, "yeni EliminationSystem motoruna geç"
 * kararı ölçüme dayansın. Yeni motorun testleri `EliminationSystemTest.kt`.
 *
 * 🔴 Buradaki `KUSUR` başlıklı testler YEŞİLDİR ve BOZUK davranışı doğrular.
 *    Kusur giderilirse bu testler KIRMIZIYA döner — istenen budur, düşen test
 *    "düzeltme geldi" demektir. Sözleşmenin doğru hâlini iddia eden KIRMIZI
 *    testler ayrı dosyada: `EskiElemeSozlesmeKirmiziTest.kt`.
 *
 * Ölçüm günü: 1 Eylül 2026 · dal: ileri-tusu-asagida-crash-fix
 */
class EskiElemeMotoruDenetimTest {

    private fun sarkilar(n: Int): List<Song> =
        (1..n).map { i -> Song(id = i.toLong(), name = "Takim $i", listId = 1L) }

    /** Düşük id kazanır — replay'i bozmayan deterministik hakem. */
    private fun oyna(matches: List<Match>): List<Match> =
        matches.map { it.copy(winnerId = minOf(it.songId1, it.songId2), isCompleted = true) }

    private fun hedefGuc(n: Int): Int = RankingEngine.getPreviousPowerOfTwo(n)

    // ---------------------------------------------------------------- ELIMINATION: grup dagitimi

    /**
     * KUSUR A1 — `calculateOptimalGroupConfig` 29 farklı kadroda gruptan çıkan
     * takım sayısını İKİNİN ÜSSÜ YAPMIYOR; bracket kurulamaz hâle geliyor.
     *
     * Sebep: ikinci dal `eliminationsPerGroup = 2`yi KOŞULSUZ döndürüyor
     * (RankingEngine.kt:230-250). `groupsEliminating2 / groupsEliminating1`
     * hesaplanıyor ama HİÇ KULLANILMIYOR — tek elemeli gruplar hiç kurulmuyor,
     * eleme sayısı tek olduğunda bir takım FAZLA eleniyor.
     */
    @Test
    fun `KUSUR A1 - grup yapilandirmasi 29 kadroda ikinin ussu olmayan bracket birakiyor`() {
        val bozuk = mutableListOf<Int>()
        for (n in 3..100) {
            val hedef = hedefGuc(n)
            if (n == hedef) continue
            val cfg = RankingEngine.calculateOptimalGroupConfig(n, n - hedef)
            val gruptanCikan = n - cfg.groupCount * cfg.eliminationsPerGroup
            if (gruptanCikan != hedef) bozuk.add(n)
        }

        val beklenen = listOf(
            7, 9, 13, 15, 17, 19, 25, 27, 29, 31, 33, 35, 37,
            49, 51, 53, 55, 57, 59, 61, 63, 65, 67, 69, 71, 73, 75, 97, 99
        )
        println("KUSUR A1 · bracket kurulamayan kadrolar (3..100): $bozuk")
        assertEquals("ölçülmüş bozuk kadro listesi değişti", beklenen, bozuk)
        assertEquals("93 kadronun 29'u bozuk", 29, bozuk.size)

        // Hepsinin ortak imzası: elenecek takım sayısı TEK.
        bozuk.forEach { n ->
            assertEquals("n=" + n + " bozuk ama elenecek sayı çift", 1, (n - hedefGuc(n)) % 2)
        }
    }

    /**
     * KUSUR A2 — "grup boyutu 3-6 olsun" niyeti tutulmuyor: elenecek takım
     * sayısı 1 olduğunda TEK grup kuruluyor ve o grup TÜM kadro oluyor.
     * n=33'te 528 maçlık tek lig, sonunda yine bozuk bracket (31 takım).
     */
    @Test
    fun `KUSUR A2 - tek elemeli kadroda tum liste tek gruba dusuyor`() {
        data class Olcum(val n: Int, val grup: Int, val boyut: Int, val mac: Int)
        val olcumler = listOf(9, 17, 33, 65).map { n ->
            val cfg = RankingEngine.calculateOptimalGroupConfig(n, n - hedefGuc(n))
            val boyut = cfg.baseGroupSize
            Olcum(n, cfg.groupCount, boyut, boyut * (boyut - 1) / 2)
        }
        olcumler.forEach { println("KUSUR A2 · n=" + it.n + " → " + it.grup + " grup × " + it.boyut + " takım = " + it.mac + " grup maçı") }

        assertEquals(listOf(1, 1, 1, 1), olcumler.map { it.grup })
        assertEquals(listOf(9, 17, 33, 65), olcumler.map { it.boyut })
        assertEquals("n=9: tek takım elemek için 36 maç", 36, olcumler[0].mac)
        assertEquals("n=33: iki takım elemek için 528 maç", 528, olcumler[2].mac)
        assertEquals("n=65: 2080 maç", 2080, olcumler[3].mac)
    }

    /**
     * A3 (SAĞLAM) — grup dağıtımındaki `shuffled()` kusuru GERÇEKTEN giderilmiş.
     * Fikstürü kuran dağıtım ile sonucu okuyan dağıtım artık aynı (song.id).
     * ANALIZ_RAPORU.md'nin "grup dağılımı hatalı" cümlesi BU kusur için artık
     * geçerli değil — regresyon kilidi olarak burada duruyor.
     */
    @Test
    fun `A3 - grup dagitimi deterministik - ayni girdi ayni fikstur`() {
        val songs = sarkilar(12)
        val a = RankingEngine.createEliminationMatches(songs)
        val b = RankingEngine.createEliminationMatches(songs)
        assertEquals(
            "aynı girdi farklı fikstür üretti",
            a.map { Triple(it.songId1, it.songId2, it.groupId) },
            b.map { Triple(it.songId1, it.songId2, it.groupId) }
        )

        // Her grup bitişik bir id dilimidir ve grup içi tam round-robin oynanır.
        val gruplar = a.groupBy { it.groupId!! }
        assertEquals(4, gruplar.size)
        gruplar.forEach { (gid, macs) ->
            val idler = macs.flatMap { listOf(it.songId1, it.songId2) }.distinct().sorted()
            assertEquals("grup " + gid + " 3 takım olmalı", 3, idler.size)
            assertEquals("grup " + gid + " bitişik dilim değil", (idler.first()..idler.last()).toList(), idler)
            assertEquals("grup " + gid + " tam lig değil", 3, macs.size)
        }
        // Gruplar örtüşmüyor: 12 takımın hepsi tam bir kez.
        val hepsi = a.flatMap { listOf(it.songId1, it.songId2) }.distinct()
        assertEquals(12, hepsi.size)
    }

    // ---------------------------------------------------------------- ELIMINATION: sonuc katmani

    /**
     * KUSUR A3 — EN AĞIRI: tam akış sonunda 12 öğenin YALNIZ 9'u sonuç alıyor,
     * 2·3·4 pozisyonları hiç dağıtılmıyor.
     *
     * Sebep zinciri: ViewModel (`createNextEliminationRound`, :1300) knockout'un
     * SADECE 1. turunu kuruyor; ikinci çağrıda `allMatches.none { round > 0 }`
     * artık yanlış olduğu için `completeRanking()`e düşüyor. Motor da yarım
     * bracket'i "bitti" sayıp `songs.find { it.id !in eliminated }` ile ayakta
     * kalan DÖRT takımdan yalnız BİRİNE 1. sırayı veriyor, kalan üçü sonuç
     * listesine hiç girmiyor.
     */
    @Test
    fun `KUSUR A3 - n=12 tam akista 12 ogeden 9'u sonuc aliyor`() {
        val songs = sarkilar(12)
        val grupMaclari = oyna(RankingEngine.createEliminationMatches(songs))
        val cfg = RankingEngine.calculateOptimalGroupConfig(12, 12 - 8)
        val gecenler = RankingEngine.getGroupQualifiers(songs, grupMaclari, cfg)
        assertEquals("n=12 doğru yapılandırılan kadrolardan: 8 takım geçmeli", 8, gecenler.size)

        // ViewModel'in yaptığı: knockout'un SADECE 1. turu kurulur.
        val knockout = oyna(RankingEngine.createEliminationKnockoutMatches(gecenler, 1))
        assertEquals(4, knockout.size)

        val sonuc = RankingEngine.calculateEliminationResults(songs, grupMaclari + knockout)
        val pozisyonlar = sonuc.map { it.position }.sorted()
        println("KUSUR A3 · sonuç sayısı=" + sonuc.size + "/12 · pozisyonlar=" + pozisyonlar)

        assertEquals("12 öğeden 9'u sonuç alıyor", 9, sonuc.size)
        assertEquals("eksik öğe sayısı", 3, songs.size - sonuc.size)
        assertEquals(listOf(1, 5, 6, 7, 8, 9, 10, 11, 12), pozisyonlar)
        listOf(2, 3, 4).forEach { p ->
            assertFalse(p.toString() + ". sıra dağıtılmamış olmalı (kusur)", pozisyonlar.contains(p))
        }
    }

    /**
     * KUSUR A4 — kadro zaten ikinin üssü olduğunda da aynı yarım-bracket:
     * n=8 → tek tur (4 maç) kurulup turnuva bitiyor, 8 öğenin 5'i sonuç alıyor.
     */
    @Test
    fun `KUSUR A4 - n=8 direkt elemede tek tur sonrasi turnuva bitiyor`() {
        val songs = sarkilar(8)
        val maclar = RankingEngine.createEliminationMatches(songs)
        assertEquals("ikinin üssü kadroda grup aşaması kurulmaz", 4, maclar.size)
        assertTrue("hepsi 1. tur olmalı", maclar.all { it.round == 1 })

        val sonuc = RankingEngine.calculateEliminationResults(songs, oyna(maclar))
        println("KUSUR A4 · n=8 tek tur → sonuç " + sonuc.size + "/8 · " + sonuc.map { it.position }.sorted())
        assertEquals(5, sonuc.size)
        assertEquals(listOf(1, 5, 6, 7, 8), sonuc.map { it.position }.sorted())
    }

    /**
     * KUSUR A5 — tek sayılı kadroda son takım SESSİZCE düşüyor: bye kaydı yok,
     * maçı yok, elenmiş de sayılmıyor. `createDirectEliminationMatches` (:259)
     * `i + 1 < songs.size` koşuluyla son öğeyi atlıyor.
     */
    @Test
    fun `KUSUR A5 - tek sayili bracket son takimi macsiz birakiyor`() {
        val songs = sarkilar(7)
        val maclar = RankingEngine.createDirectEliminationMatches(songs, 1)
        assertEquals(3, maclar.size)
        val oynayanlar = maclar.flatMap { listOf(it.songId1, it.songId2) }.toSet()
        assertEquals("6 takım oynuyor", 6, oynayanlar.size)
        assertFalse("7. takım hiçbir maçta yok (sessiz düşme)", oynayanlar.contains(7L))
    }

    /**
     * KUSUR A7 — n=7 TAM AKIŞ: KUSUR A1'deki 29 kadronun biri uçtan uca.
     * Grup aşaması 3 takım bırakıyor (hedef 4), knockout tek maça düşüyor,
     * gruptan çıkan üçüncü takım (id=5) bracket'te de sonuç listesinde de YOK.
     *
     * Ölçülen: 7 öğeden 6'sı sonuç alıyor, 2. sıra hiç dağıtılmıyor.
     */
    @Test
    fun `KUSUR A7 - n=7 tam akista gruptan 3 takim cikiyor ve biri kayboluyor`() {
        val songs = sarkilar(7)
        val cfg = RankingEngine.calculateOptimalGroupConfig(7, 7 - 4)
        val grupMaclari = oyna(RankingEngine.createEliminationMatches(songs))
        assertEquals("grup fikstürü: 4'lü + 3'lü = 6+3 maç", 9, grupMaclari.size)

        val gecenler = RankingEngine.getGroupQualifiers(songs, grupMaclari, cfg)
        println("KUSUR A7 · gruptan çıkan=" + gecenler.map { it.id } + " (hedef 4 takım)")
        assertEquals("hedef 4 iken 3 takım çıkıyor — bracket kurulamaz", 3, gecenler.size)

        val knockout = oyna(RankingEngine.createEliminationKnockoutMatches(gecenler, 1))
        assertEquals("3 takımdan yalnız 2'si eşleşiyor", 1, knockout.size)

        val sonuc = RankingEngine.calculateEliminationResults(songs, grupMaclari + knockout)
        val pozisyonlar = sonuc.map { it.position }.sorted()
        println("KUSUR A7 · sonuç " + sonuc.size + "/7 · " + sonuc.map { it.songId to it.position })
        assertEquals("7 öğeden 6'sı sonuç alıyor", 6, sonuc.size)
        assertEquals(listOf(1, 3, 4, 5, 6, 7), pozisyonlar)
        assertFalse("2. sıra dağıtılmamış", pozisyonlar.contains(2))
        assertFalse("gruptan çıkan 3. takım sonuç listesinde yok", sonuc.any { it.songId == 5L })
    }

    /**
     * KUSUR A8 — EN SİNSİSİ: sonuç katmanı OYNANAN FİKSTÜRE değil `songs.size`e
     * bakıyor. `calculateEliminationResults` grup yapılandırmasını n'den yeniden
     * türetiyor ve grup sıralamasını `round == 0` maçlarından okuyor; o maçlar
     * HİÇ YOKSA bile hata vermiyor, herkesin puanını 0 kabul edip id sırasına
     * göre dört takımı eliyor. Yani hiç oynanmamış bir grup aşamasından
     * "sonuç" üretiliyor.
     */
    @Test
    fun `KUSUR A8 - oynanmamis grup asamasindan sifir puanla sonuc uretiliyor`() {
        val songs = sarkilar(7)
        // Yalnız knockout maçları var; round 0 (grup) maçı YOK.
        val sadeceKnockout = oyna(RankingEngine.createDirectEliminationMatches(songs, 1))
        val sonuc = RankingEngine.calculateEliminationResults(songs, sadeceKnockout)
        println("KUSUR A8 · grup maçı yokken sonuç " + sonuc.size + "/7 · " + sonuc.map { it.songId to it.position })

        assertEquals(6, sonuc.size)
        // 3, 4, 6, 7 hiç grup maçı oynamadan "gruptan elendi" sayıldı, puanları 0.
        listOf(3L, 4L, 6L, 7L).forEach { id ->
            val r = sonuc.firstOrNull { it.songId == id }
            assertNotNull("id=" + id + " gruptan elenmiş sayılmalı (kusur)", r)
            assertEquals("oynanmamış grup aşamasından 0.0 puan", 0.0, r!!.score, 0.0001)
        }
        assertEquals("pozisyonlar", listOf(1, 3, 4, 5, 6, 7), sonuc.map { it.position }.sorted())
    }

    /**
     * KUSUR A6 — YABANCI kazanan id'si (üçüncü takım / silinmiş öğe) çökme
     * yapmıyor ✅ ama maç SESSİZCE yok sayılıyor: kimse elenmiyor, iki takım
     * da ayakta kalıyor, yalnız biri sonuç alıyor.
     */
    @Test
    fun `KUSUR A6 - yabanci kazanan id cokmuyor ama mac sessizce yutuluyor`() {
        val songs = sarkilar(2)
        val mac = Match(
            listId = 1L, rankingMethod = "ELIMINATION",
            songId1 = 1L, songId2 = 2L, winnerId = 999L, round = 1, isCompleted = true
        )
        val sonuc = RankingEngine.calculateEliminationResults(songs, listOf(mac))
        println("KUSUR A6 · yabancı id → sonuç " + sonuc.size + "/2 · " + sonuc.map { it.songId to it.position })
        assertEquals("çökmüyor ama tek sonuç dönüyor", 1, sonuc.size)
        assertEquals(1, sonuc.first().position)
    }

    // ---------------------------------------------------------------- FULL_ELIMINATION

    /**
     * B1 (SAĞLAM) — `getRemainingTeamsAfterRound` sözleşmesi tutuyor:
     * beraberlikte kimse elenmez, tamamlanmamış maç sayılmaz, verilen turdan
     * SONRAKİ turlar hesaba katılmaz.
     */
    @Test
    fun `B1 - getRemainingTeamsAfterRound sozlesmesi tutuyor`() {
        val songs = sarkilar(6)
        val maclar = listOf(
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 1, songId2 = 2, winnerId = 1, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 3, songId2 = 4, winnerId = null, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 5, songId2 = 6, winnerId = 5, round = 1, isCompleted = false),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 3, songId2 = 5, winnerId = 3, round = 2, isCompleted = true)
        )
        val turSonu1 = RankingEngine.getRemainingTeamsAfterRound(songs, maclar, 1).map { it.id }
        assertEquals("1. tur sonunda yalnız 2 elenmiş olmalı", listOf(1L, 3L, 4L, 5L, 6L), turSonu1)

        val turSonu2 = RankingEngine.getRemainingTeamsAfterRound(songs, maclar, 2).map { it.id }
        assertEquals(listOf(1L, 3L, 4L, 6L), turSonu2)
    }

    /**
     * KUSUR B2 — `getWinnersAndLosers` üçlü grup (lig usulü) dalını YALNIZCA
     * turda tam 3 takım ve tam 3 maç varken çalıştırıyor (:832). Gerçek bir
     * tam-eleme turunda tur, ikili maçlar + son üçlü grup KARIŞIMIDIR; o zaman
     * üçlü grup üç bağımsız ikili maç gibi işleniyor ve AYNI TAKIM hem kazanan
     * hem kaybeden listesine giriyor. ViewModel bu iki listeyle sonraki turu
     * kuruyor (`createNextFullEliminationRound`, :1374) — yani elenmiş sayılan
     * takım aynı anda kazanan havuzunda.
     */
    @Test
    fun `KUSUR B2 - karisik turda ayni takim hem kazanan hem kaybeden`() {
        val songs = sarkilar(5)
        // 1-2 ikili; 3-4-5 üçlü grupta DÖNGÜ: 3>4, 4>5, 5>3
        val maclar = listOf(
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 1, songId2 = 2, winnerId = 1, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 3, songId2 = 4, winnerId = 3, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 4, songId2 = 5, winnerId = 4, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 3, songId2 = 5, winnerId = 5, round = 1, isCompleted = true)
        )
        val (kazananlar, kaybedenler) = RankingEngine.getWinnersAndLosers(songs, maclar)
        val kesisim = kazananlar.map { it.id }.intersect(kaybedenler.map { it.id }.toSet())
        println("KUSUR B2 · kazananlar=" + kazananlar.map { it.id } + " kaybedenler=" + kaybedenler.map { it.id } + " kesişim=" + kesisim)

        assertEquals("üçlü grubun üç üyesi de iki listede birden", setOf(3L, 4L, 5L), kesisim)
    }

    /**
     * B3 (SAĞLAM) — üçlü grup dalı, tur SADECE 3 takım/3 maçtan ibaretse doğru
     * çalışıyor: lig usulü tek galip, iki kaybeden. Yani mantık VAR, tetikleyen
     * koşul YANLIŞ (bkz. KUSUR B2).
     */
    @Test
    fun `B3 - saf uclu turda lig usulu dogru calisiyor`() {
        val songs = sarkilar(3)
        val maclar = listOf(
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 1, songId2 = 2, winnerId = 1, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 1, songId2 = 3, winnerId = 1, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 2, songId2 = 3, winnerId = 2, round = 1, isCompleted = true)
        )
        val (kazananlar, kaybedenler) = RankingEngine.getWinnersAndLosers(songs, maclar)
        assertEquals(listOf(1L), kazananlar.map { it.id })
        assertEquals(setOf(2L, 3L), kaybedenler.map { it.id }.toSet())
    }

    /**
     * KUSUR B4 — tam eleme tek turda yarıda kalıyor: n=12'de ilk tur 6 takımı
     * eliyor, ama hedef yalnız 4 eleme. ViewModel `eliminatedSoFar >= teamsToEliminate`
     * görünce (:1362) ya finali kurar ya biter; kalan 6 takım hedef 8'e eşit
     * olmadığı için `completeRanking()` çağrılır ve AYAKTA KALAN 6 TAKIM HİÇ
     * SONUÇ ALMAZ. Motor katmanında ölçümü:
     */
    @Test
    fun `KUSUR B4 - tam eleme ilk tur sonrasi ayakta kalanlar sonucsuz`() {
        val songs = sarkilar(12)
        val ilkTur = oyna(RankingEngine.createFullEliminationMatches(songs))
        assertEquals("12 takım → 6 ikili maç", 6, ilkTur.size)

        val kalan = RankingEngine.getRemainingTeamsAfterRound(songs, ilkTur, 1)
        assertEquals("6 takım ayakta", 6, kalan.size)
        assertNotEquals(
            "hedef 8'e eşit değil → final bracket hiç kurulmaz",
            RankingEngine.getPreviousPowerOfTwo(12), kalan.size
        )

        val sonuc = RankingEngine.calculateFullEliminationResults(songs, ilkTur)
        println("KUSUR B4 · sonuç " + sonuc.size + "/12 · pozisyonlar=" + sonuc.map { it.position }.sorted())
        assertEquals("12 öğenin yalnız 6'sı sonuç alıyor", 6, sonuc.size)
        assertEquals(listOf(7, 8, 9, 10, 11, 12), sonuc.map { it.position }.sorted())
        val sonucAlan = sonuc.map { it.songId }.toSet()
        assertTrue("ayakta kalan hiçbir takım sonuç almamalı (kusur)", kalan.none { it.id in sonucAlan })
    }

    /**
     * KUSUR B5 — final aşaması (round ≥ 101) kurulabilse bile pozisyon aritmetiği
     * bozuk: `mergeAdvancedEliminationResults` (:1070) ön eleme pozisyonlarına
     * final sonuç SAYISINI ekliyor. n=8'de pozisyonlar 11'e kadar çıkıyor.
     */
    @Test
    fun `KUSUR B5 - final asamasi pozisyonlari kadro disina tasiyor`() {
        val songs = sarkilar(8)
        val onEleme = oyna(RankingEngine.createDirectEliminationMatches(songs, 1, "FULL_ELIMINATION"))
        val gecenler = RankingEngine.getRemainingTeamsAfterRound(songs, onEleme, 1)
        assertEquals(4, gecenler.size)
        val final = oyna(RankingEngine.createDirectEliminationMatches(gecenler, 101, "FULL_ELIMINATION"))

        val sonuc = RankingEngine.calculateFullEliminationResults(songs, onEleme + final)
        val pozisyonlar = sonuc.map { it.position }.sorted()
        println("KUSUR B5 · sonuç " + sonuc.size + "/8 · pozisyonlar=" + pozisyonlar)

        assertEquals(7, sonuc.size)
        assertEquals(listOf(1, 3, 4, 8, 9, 10, 11), pozisyonlar)
        assertTrue("kadro 8 kişi ama 8'den büyük pozisyon dağıtılmış", pozisyonlar.max() > songs.size)
    }

    /**
     * KUSUR B6 — `createFullEliminationMatches` `shuffled()` çağırıyor (:915).
     * Replay deseninin (ORTAK.md) tek kuralını çiğniyor: aynı girdi aynı
     * fikstürü vermiyor. Aşağıdaki ölçüm, 20 çağrının kaç farklı fikstür
     * ürettiğini SAYIYOR — sözleşme iddiası kırmızı dosyada.
     */
    @Test
    fun `KUSUR B6 - tam eleme fiksturu rastgele - ayni girdi farkli eslesme`() {
        val songs = sarkilar(33) // ikinin üssü değil ki ön eleme dalı çalışsın
        val imzalar = (1..20).map {
            RankingEngine.createFullEliminationMatches(songs).map { m -> m.songId1 to m.songId2 }
        }.toSet()
        println("KUSUR B6 · 20 çağrı → " + imzalar.size + " farklı fikstür")
        assertTrue("shuffled() kaldırılmışsa bu test kırmızıya döner (istenen)", imzalar.size > 1)
    }
}
