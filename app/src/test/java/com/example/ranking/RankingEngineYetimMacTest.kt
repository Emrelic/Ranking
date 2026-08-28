package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * RANKING ENGINE — YETİM MAÇ TARAMASI
 *
 * `RankingEngine.kt` içindeki `map[id]!!` desenleri 9f0b8f1'de `getOrDefault`
 * ile kapatıldı. Bu dosya o düzeltmeyi BAĞIMSIZ olarak sınar: düzeltmeyi yapan
 * oturumun kendi doğrulaması zayıf kanıttır.
 *
 * Her genel (public) giriş noktası, `songs` içinde OLMAYAN bir öğe id'si taşıyan
 * maç kayıtlarıyla çağrılır. Beklenen: çökme yok, hayattaki öğelerin sonucu bozulmaz.
 *
 * Yetim maç bu projede gerçek bir senaryo: öğe silinebiliyor, maç kayıtları kalıyor.
 *
 * ⚠️ RankingEngine.kt KOORDİNATÖRÜN dosyası — yalnız okundu, yazılmadı.
 */
class RankingEngineYetimMacTest {

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Takim$i", listId = 5L) }

    private fun match(
        id: Long, s1: Long, s2: Long, winner: Long?,
        round: Int = 1, groupId: Int? = null, method: String = "LEAGUE"
    ) = Match(
        id = id, listId = 5L, rankingMethod = method,
        songId1 = s1, songId2 = s2, winnerId = winner,
        round = round, groupId = groupId, isCompleted = true
    )

    /** Silinmiş öğelere ait maçlar: tek taraflı, çift taraflı ve beraberlik dalları. */
    private fun yetimMaclar(method: String = "LEAGUE", round: Int = 1, groupId: Int? = null) = listOf(
        match(90, 1L, 999L, 1L, round, groupId, method),      // sağ taraf silinmiş, sol kazandı
        match(91, 998L, 2L, 2L, round, groupId, method),      // sol taraf silinmiş, sağ kazandı
        match(92, 997L, 996L, 997L, round, groupId, method),  // iki taraf da silinmiş
        match(93, 3L, 995L, null, round, groupId, method),    // beraberlik dalı
        match(94, 994L, 993L, null, round, groupId, method)   // beraberlik, iki taraf da silinmiş
    )

    // ==========================================================
    // ① LİG (:110-124)
    // ==========================================================

    @Test
    fun lig_yetimMacCokmuyor() {
        val songs = makeSongs(4)
        val matches = yetimMaclar() + match(1, 1L, 2L, 1L)
        val results = RankingEngine.calculateLeagueResults(songs, matches)

        assertEquals("Sonuç listesi eksilmemeli", 4, results.size)
        assertEquals((1L..4L).toSet(), results.map { it.songId }.toSet())
        assertEquals("Yalnız geçerli maç puan üretmeli", 3.0, results.first { it.songId == 1L }.score, 0.0001)
        assertEquals("Yetim maçtan puan sızmamalı", 0.0, results.first { it.songId == 3L }.score, 0.0001)
        assertTrue("Silinmiş öğe sonuçlara sızmamalı", results.none { it.songId > 4L })
    }

    // ==========================================================
    // ② İSVİÇRE PUANI (:645) ve DURUM TABLOSU (:688)
    // ==========================================================

    @Test
    fun swissSonuclari_yetimMacCokmuyor() {
        // 🔴 TUTARSIZLIK: Lig tarafında yetim maç `if (p1 == null || p2 == null)
        // return@forEach` ile TAMAMEN atlanıyor (ne puan ne geçmiş).
        // İsviçre tarafında ise yalnız `!!` → `getOrDefault` yapıldı: çökme
        // gitti ama HAYALET PUAN kaldı — silinmiş bir öğeye karşı "galibiyet"
        // hayattaki takıma puan yazmaya devam ediyor.
        // Bu, EMRE_CORRECT'te KUSUR #1 olarak bildirilip düzeltilen davranışın
        // aynısı. İki motorun aynı olaya farklı cevap vermesi kendi başına kusur.
        val songs = makeSongs(4)
        val matches = yetimMaclar("SWISS") + match(1, 1L, 2L, 1L, method = "SWISS")
        val results = RankingEngine.calculateSwissResults(songs, matches)

        assertEquals(4, results.size)
        assertEquals((1L..4L).toSet(), results.map { it.songId }.toSet())
        assertEquals(
            "HAYALET PUAN: takımın tek geçerli maçı var (1-2), ama silinmiş 999'a " +
                "karşı kazandığı maç da puan üretiyor",
            1.0, results.first { it.songId == 1L }.score, 0.0001
        )
        assertEquals("Yetim maçtan puan sızmamalı", 0.0, results.first { it.songId == 3L }.score, 0.0001)
        assertEquals("Pozisyonlar 1..4 olmalı", (1..4).toList(), results.map { it.position }.sorted())
    }

    @Test
    fun swissPuani_yalnizYetimMaciOlanTakimSifirKalmali() {
        // Aynı tutarsızlığın en yalın hali: takımın HİÇ geçerli maçı yok,
        // yalnız silinmiş öğelere karşı maçları var → puanı 0 olmalı.
        val songs = makeSongs(4)
        val matches = listOf(
            match(1, 1L, 999L, 1L, method = "SWISS"),
            match(2, 998L, 1L, 1L, method = "SWISS")
        )
        val results = RankingEngine.calculateSwissResults(songs, matches)
        assertEquals(
            "Yalnız yetim maçı olan takım 0 puanda kalmalı (Lig motorundaki kural)",
            0.0, results.first { it.songId == 1L }.score, 0.0001
        )
    }

    @Test
    fun swissDurumTablosu_yetimMacCokmuyor() {
        val songs = makeSongs(4)
        val matches = yetimMaclar("SWISS") + match(1, 1L, 2L, 1L, method = "SWISS")
        val standings = RankingEngine.createSwissStandingsFromMatches(songs, matches)
        assertNotNull("Durum tablosu üretilmeli", standings)
    }

    @Test
    fun swissDurumTablosu_bosMaclaCokmuyor() {
        val standings = RankingEngine.createSwissStandingsFromMatches(makeSongs(4), emptyList())
        assertNotNull(standings)
    }

    // ==========================================================
    // ③ KAZANAN/KAYBEDEN AYIRMA (:806)
    // ==========================================================

    @Test
    fun kazananKaybeden_yetimMacCokmuyor() {
        val songs = makeSongs(4)
        val (winners, losers) = RankingEngine.getWinnersAndLosers(songs, yetimMaclar())

        assertTrue("Silinmiş öğe kazanan listesine sızmamalı", winners.none { it.id > 4L })
        assertTrue("Silinmiş öğe kaybeden listesine sızmamalı", losers.none { it.id > 4L })
        assertTrue("Aynı takım hem kazanan hem kaybeden olamaz",
            winners.map { it.id }.intersect(losers.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun kazananKaybeden_ucluGrupDaliYetimMaclaCokmuyor() {
        // Üçlü grup dalı (3 takım, 3 maç) — puan haritası bu dalda kuruluyor
        val songs = makeSongs(2) // 3. takım silinmiş
        val ucluGrup = listOf(
            match(1, 1L, 2L, 1L),
            match(2, 2L, 999L, 2L),
            match(3, 999L, 1L, 999L)
        )
        val (winners, losers) = RankingEngine.getWinnersAndLosers(songs, ucluGrup)
        assertTrue("Silinmiş öğe listelere sızmamalı",
            (winners + losers).none { it.id > 2L })
    }

    @Test
    fun kazananKaybeden_normalDalDogruCalisiyor() {
        val songs = makeSongs(4)
        val matches = listOf(match(1, 1L, 2L, 1L), match(2, 3L, 4L, 4L))
        val (winners, losers) = RankingEngine.getWinnersAndLosers(songs, matches)
        assertEquals("Kazananlar", setOf(1L, 4L), winners.map { it.id }.toSet())
        assertEquals("Kaybedenler", setOf(2L, 3L), losers.map { it.id }.toSet())
    }

    // ==========================================================
    // ④ GRUP SIRALAMASI (:319) ve ÜÇLÜ GRUP PUANI (:1159)
    // ==========================================================

    /*
     * ⚠️ KAPSANAMADI — grup yolunda HAYALET PUAN ölçülemiyor.
     * `calculateGroupStandings` (:319) ve `calculateTripleGroupPoints` (:1159)
     * de `getOrDefault` kullanıyor, yani İsviçre'deki hayalet puan sorunu orada
     * da olmalı. Ama tek genel giriş noktaları `getGroupQualifiers` /
     * `calculateEliminationResults`, onlar da `getGroupSongs`'un `shuffled()`
     * çağrısı yüzünden DETERMİNİSTİK DEĞİL — hangi takımın hangi grupta olduğu
     * her çağrıda değişiyor, dolayısıyla puan iddiası kurulamıyor.
     * shuffled() kaldırılınca bu testler yazılabilir (bkz. aşağıdaki iki test).
     */

    @Test
    fun grupElemeleri_yetimMacCokmuyor() {
        val songs = makeSongs(10)
        val config = RankingEngine.calculateOptimalGroupConfig(10, 2)
        val groupMatches = yetimMaclar(round = 0, groupId = 0) +
            match(1, 1L, 2L, 1L, round = 0, groupId = 0)

        val qualifiers = RankingEngine.getGroupQualifiers(songs, groupMatches, config)
        assertTrue("Silinmiş öğe gruptan çıkamaz", qualifiers.none { it.id > 10L })
    }

    @Test
    fun elemeSonuclari_yetimMacCokmuyor() {
        val songs = makeSongs(8) // 8 = 2^3 → doğrudan eleme yolu
        val matches = yetimMaclar(method = "ELIMINATION") +
            match(1, 1L, 2L, 1L, method = "ELIMINATION")
        val results = RankingEngine.calculateEliminationResults(songs, matches)
        assertTrue("Silinmiş öğe sonuçlara sızmamalı", results.none { it.songId > 8L })
    }

    @Test
    fun tamElemeSonuclari_yetimMacCokmuyor() {
        val songs = makeSongs(8)
        val matches = yetimMaclar(method = "FULL_ELIMINATION") +
            match(1, 1L, 2L, 1L, method = "FULL_ELIMINATION")
        val results = RankingEngine.calculateFullEliminationResults(songs, matches)
        assertTrue("Silinmiş öğe sonuçlara sızmamalı", results.none { it.songId > 8L })
    }

    @Test
    fun tamElemeSonuclari_ucluGrupYetimMaclaCokmuyor() {
        // Üçlü grup puanı (calculateTripleGroupPoints) yolunu zorlar
        val songs = makeSongs(6)
        val ucluGruplar = listOf(
            match(1, 1L, 2L, 1L, method = "FULL_ELIMINATION"),
            match(2, 2L, 3L, 2L, method = "FULL_ELIMINATION"),
            match(3, 3L, 1L, 3L, method = "FULL_ELIMINATION"),
            match(4, 4L, 999L, 4L, method = "FULL_ELIMINATION"),
            match(5, 999L, 998L, null, method = "FULL_ELIMINATION")
        )
        val results = RankingEngine.calculateFullEliminationResults(songs, ucluGruplar)
        assertTrue("Silinmiş öğe sonuçlara sızmamalı", results.none { it.songId > 6L })
    }

    // ==========================================================
    // ⑤ BOŞ / BOZUK GİRDİLER
    // ==========================================================

    @Test
    fun bosGirdilerCokmuyor() {
        assertTrue(RankingEngine.calculateLeagueResults(emptyList(), emptyList()).isEmpty())
        assertTrue(RankingEngine.calculateSwissResults(emptyList(), emptyList()).isEmpty())
        val (w, l) = RankingEngine.getWinnersAndLosers(emptyList(), emptyList())
        assertTrue(w.isEmpty() && l.isEmpty())
    }

    @Test
    fun tumMaclarYetimse_herkesSifirPuan() {
        val songs = makeSongs(4)
        val results = RankingEngine.calculateLeagueResults(songs, yetimMaclar())
        assertEquals(4, results.size)
        results.forEach { assertEquals("Yetim maçlardan puan üretilmemeli", 0.0, it.score, 0.0001) }
    }

    @Test
    fun tanimsizKazanan_puanUretmiyor() {
        // winnerId ne songId1 ne songId2 ne null — bozuk veri
        val songs = makeSongs(4)
        val bozuk = listOf(match(1, 1L, 2L, 777L))
        val results = RankingEngine.calculateLeagueResults(songs, bozuk)
        assertEquals(4, results.size)
        results.forEach {
            assertEquals("Tanımsız kazananda puan uydurulmamalı", 0.0, it.score, 0.0001)
        }
    }

    // ==========================================================
    // ⑥ GRUP DAĞITIMI — yetim maçtan bağımsız iki ayrı bulgu
    // ==========================================================

    @Test
    fun grupDagitimi_ayniGirdiIkiKez_ayniSonucVermeli() {
        // ORTAK.md: motorlar shuffled()/Random kullanmaz — replay'i kırar.
        // getGroupSongs `allSongs.shuffled()` çağırıyor.
        val songs = makeSongs(10)
        val config = RankingEngine.calculateOptimalGroupConfig(10, 2)
        val ilk = RankingEngine.getGroupQualifiers(songs, emptyList(), config).map { it.id }

        repeat(20) { deneme ->
            val tekrar = RankingEngine.getGroupQualifiers(songs, emptyList(), config).map { it.id }
            assertEquals(
                "Grup dağıtımı deterministik olmalı (deneme ${deneme + 1}): " +
                    "aynı girdi farklı sonuç verdi — getGroupSongs shuffled() kullanıyor",
                ilk, tekrar
            )
        }
    }

    @Test
    fun grupDagitimi_gruplarAyrikOlmali() {
        // Her takım tam bir gruba ait olmalı; aynı takım iki gruptan çıkamaz.
        val songs = makeSongs(10)
        val config = RankingEngine.calculateOptimalGroupConfig(10, 2)
        val qualifiers = RankingEngine.getGroupQualifiers(songs, emptyList(), config)

        assertEquals(
            "Aynı takım birden fazla gruptan çıkmış — gruplar örtüşüyor " +
                "(getGroupSongs her grup için listenin BAŞINDAN dilim alıyor, songIndex hiç ilerlemiyor)",
            qualifiers.size, qualifiers.map { it.id }.toSet().size
        )
    }
}
