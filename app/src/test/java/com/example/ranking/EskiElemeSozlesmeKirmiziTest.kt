package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

/**
 * 🔴 ELEME SÖZLEŞMESİ — yeni motorun bitiş şartnamesi (BUGÜN TUTMUYOR).
 *
 * Buradaki her test, eski eleme motorlarının (ELIMINATION / FULL_ELIMINATION)
 * TUTMASI GEREKEN sözleşmeyi iddia eder. Bugün hepsi DÜŞER. Bu dosya bir kusur
 * listesi değil, **yeni motorun bitiş şartnamesidir**: `EliminationSystem`
 * bağlanıp @Ignore kaldırıldığında hepsi yeşilse eski sistemler UI'ya geri
 * açılabilir.
 *
 * KOORDİNATÖR KARARI GÜNCELLENDİ (2026-09-02): önce "bilerek kırmızı, @Ignore
 * yok" denmişti; dalga kapanış koşumunda kalıcı kırmızının bedeli ölçüldü —
 * süit hiç yeşil olamıyor ve gerçek bir gerileme kırmızısı bu beşin arasında
 * GÖRÜNMEZ OLUYOR. Şartname @Ignore ile korunur, sinyal kirliliği biter.
 *
 * Bugünkü davranışın SAYIYLA ölçümü: `EskiElemeMotoruDenetimTest.kt` (yeşil).
 * Rapor: `oturumlar/ELEME-DENETIM-RAPOR.md`.
 */
@Ignore("Yeni EliminationSystem bağlanınca @Ignore kaldırılacak — bkz. ELEME-DENETIM-RAPOR.md")
class EskiElemeSozlesmeKirmiziTest {

    private fun sarkilar(n: Int): List<Song> =
        (1..n).map { i -> Song(id = i.toLong(), name = "Takim $i", listId = 1L) }

    private fun oyna(matches: List<Match>): List<Match> =
        matches.map { it.copy(winnerId = minOf(it.songId1, it.songId2), isCompleted = true) }

    /**
     * 🔴 R1 — Grup aşaması, bracket'e TAM OLARAK ikinin üssü kadar takım bırakmalı.
     * Bugün 3..100 arasındaki 93 kadronun 29'unda bırakmıyor (elenecek sayı tek
     * olduğunda ikinci dal koşulsuz 2 eleme yazıyor).
     */
    @Test
    fun `R1 - grup asamasi her kadroda ikinin ussu kadar takim birakmali`() {
        val bozuk = mutableListOf<String>()
        for (n in 3..100) {
            val hedef = RankingEngine.getPreviousPowerOfTwo(n)
            if (n == hedef) continue
            val cfg = RankingEngine.calculateOptimalGroupConfig(n, n - hedef)
            val gruptanCikan = n - cfg.groupCount * cfg.eliminationsPerGroup
            if (gruptanCikan != hedef) bozuk.add("n=" + n + ": " + gruptanCikan + " (hedef " + hedef + ")")
        }
        assertEquals("bracket kurulamayan kadrolar: " + bozuk, 0, bozuk.size)
    }

    /**
     * 🔴 R2 — Grup boyutu 3-6 aralığında kalmalı (algoritmanın kendi niyeti).
     * Bugün n=9 → 9'luk tek grup (36 maç), n=33 → 33'lük tek grup (528 maç).
     */
    @Test
    fun `R2 - grup boyutu 3 ile 6 arasinda kalmali`() {
        val ihlal = mutableListOf<String>()
        for (n in 3..100) {
            val hedef = RankingEngine.getPreviousPowerOfTwo(n)
            if (n == hedef) continue
            val cfg = RankingEngine.calculateOptimalGroupConfig(n, n - hedef)
            val enBuyuk = if (cfg.remainderGroups > 0) cfg.baseGroupSize + 1 else cfg.baseGroupSize
            if (cfg.baseGroupSize < 3 || enBuyuk > 6) ihlal.add("n=" + n + ": " + cfg.baseGroupSize + ".." + enBuyuk)
        }
        assertEquals("3-6 aralığı dışına çıkan kadrolar: " + ihlal, 0, ihlal.size)
    }

    /**
     * 🔴 R3 — ELIMINATION turnuvası bittiğinde HER öğe tam bir sonuç almalı ve
     * pozisyonlar 1..n aralığını tekrarsız doldurmalı.
     * Bugün n=12 akışında 9 sonuç dönüyor; 2·3·4 sıraları boş.
     */
    @Test
    fun `R3 - eleme sonucunda her oge tam bir kez 1-n pozisyonu almali`() {
        val songs = sarkilar(12)
        val grupMaclari = oyna(RankingEngine.createEliminationMatches(songs))
        val cfg = RankingEngine.calculateOptimalGroupConfig(12, 12 - 8)
        val gecenler = RankingEngine.getGroupQualifiers(songs, grupMaclari, cfg)
        val knockout = oyna(RankingEngine.createEliminationKnockoutMatches(gecenler, 1))

        val sonuc = RankingEngine.calculateEliminationResults(songs, grupMaclari + knockout)
        assertEquals("her öğe sonuç almalı", songs.size, sonuc.size)
        assertEquals("pozisyonlar 1..n tekrarsız olmalı", (1..songs.size).toList(), sonuc.map { it.position }.sorted())
        assertEquals("her öğe tam bir kez", songs.size, sonuc.map { it.songId }.distinct().size)
    }

    /**
     * 🔴 R4 — Tek sayılı bracket'te hiçbir takım maçsız kalmamalı: ya bye kaydı
     * ya eşleşme. Bugün son takım sessizce düşüyor (ne maçı var, ne elenmiş).
     */
    @Test
    fun `R4 - tek sayili bracketta hicbir takim macsiz kalmamali`() {
        val songs = sarkilar(7)
        val maclar = RankingEngine.createDirectEliminationMatches(songs, 1)
        val oynayanlar = maclar.flatMap { listOf(it.songId1, it.songId2) }.toSet()
        val macsiz = songs.filter { it.id !in oynayanlar }.map { it.id }
        assertEquals("maçsız ve bye kaydı da olmayan takımlar: " + macsiz, 0, macsiz.size)
    }

    /**
     * 🔴 R5 — `getWinnersAndLosers`: bir takım aynı turda hem kazanan hem
     * kaybeden olamaz. Bugün ikili + üçlü karışık turda üçlü grubun üç üyesi de
     * iki listede birden çıkıyor.
     */
    @Test
    fun `R5 - bir takim ayni turda hem kazanan hem kaybeden olamaz`() {
        val songs = sarkilar(5)
        val maclar = listOf(
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 1, songId2 = 2, winnerId = 1, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 3, songId2 = 4, winnerId = 3, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 4, songId2 = 5, winnerId = 4, round = 1, isCompleted = true),
            Match(listId = 1, rankingMethod = "FULL_ELIMINATION", songId1 = 3, songId2 = 5, winnerId = 5, round = 1, isCompleted = true)
        )
        val (kazananlar, kaybedenler) = RankingEngine.getWinnersAndLosers(songs, maclar)
        val kesisim = kazananlar.map { it.id }.intersect(kaybedenler.map { it.id }.toSet())
        assertEquals("hem kazanan hem kaybeden sayılan takımlar: " + kesisim, emptySet<Long>(), kesisim)
    }

    /**
     * 🔴 R6 — FULL_ELIMINATION fikstürü DETERMİNİSTİK olmalı (ORTAK.md replay
     * kuralı): aynı girdi aynı eşleşmeleri vermeli. Bugün `shuffled()` var.
     */
    @Test
    fun `R6 - tam eleme fiksturu deterministik olmali`() {
        val songs = sarkilar(33)
        val ilk = RankingEngine.createFullEliminationMatches(songs).map { it.songId1 to it.songId2 }
        repeat(9) { tur ->
            val sonraki = RankingEngine.createFullEliminationMatches(songs).map { it.songId1 to it.songId2 }
            assertEquals("çağrı " + (tur + 2) + " farklı fikstür üretti (shuffled)", ilk, sonraki)
        }
    }

    /**
     * 🔴 R7 — FULL_ELIMINATION sonuç katmanı ayakta kalan takımları da
     * sıralamalı. Bugün n=12'de ilk tur sonrası 6 elenen sonuç alıyor, ayakta
     * kalan 6 takım sonuç listesinde HİÇ YOK.
     */
    @Test
    fun `R7 - tam eleme sonucunda ayakta kalanlar da siralanmali`() {
        val songs = sarkilar(12)
        val ilkTur = oyna(RankingEngine.createFullEliminationMatches(songs))
        val sonuc = RankingEngine.calculateFullEliminationResults(songs, ilkTur)
        val sonucAlan = sonuc.map { it.songId }.toSet()
        val eksik = songs.filter { it.id !in sonucAlan }.map { it.id }
        assertEquals("sonuç listesinde hiç yer almayan takımlar: " + eksik, 0, eksik.size)
    }
}
