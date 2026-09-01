package com.example.ranking

import com.example.ranking.data.Song
import com.example.ranking.data.SwissStandings
import com.example.ranking.ranking.RankingEngine
import com.example.ranking.ranking.SwissSystem
import org.junit.Assert.*
import org.junit.Test

/**
 * SWISS — 1. TUR GİRİŞ NOKTASI DENETİMİ
 *
 * Bulguyu `ranking-07` (doküman senkron kıtası) kod okumasıyla bildirdi, testi yoktu.
 * Bu dosya onu BAĞIMSIZ olarak sınar ve şiddetini ölçer.
 *
 * ## Sorun
 *
 * SWISS turnuvası 2026-08-28'de yeni `SwissSystem` motoruyla geri açıldı ve
 * kullanıcı tarafından SEÇİLEBİLİR (`NewTournamentScreen.kt` systemTypes listesi).
 * Ama turnuvanın 1. turu yeni motordan GELMİYOR:
 *
 *   RankingViewModel.initializeSwiss (:519)
 *     → RankingEngine.createSwissMatches(songs, 1, emptyList())   ← ESKİ motor
 *
 * Eski yolun 1. tur dalı (`RankingEngine.kt:505-526`):
 *
 *   val half = shuffledSongs.size / 2
 *   for (i in 0 until half) { ... shuffledSongs[i] vs shuffledSongs[i + half] }
 *
 * Tek sayıda öğede son eleman hiçbir çifte girmiyor: ne maç, ne bye, ne puan.
 * `shuffled()` kullanıldığı için DÜŞEN ÖĞE HER KOŞUMDA FARKLI.
 *
 * Bu tam olarak `NewTournamentScreen.kt:431`'deki yorumun "düzeltildi" dediği
 * kusur: "Eski yolda bye yoktu (tek takım sessizce turdan düşüyordu)". Yeni
 * motor gerçekten düzeltiyor (aşağıdaki karşılaştırma testleri kanıtlıyor),
 * ama 1. tur giriş noktası hâlâ eski yola gidiyor. SwissSystem'in 59 testi bunu
 * yakalayamıyor çünkü hepsi `SwissSystem`'i ölçüyor, giriş noktasını değil.
 *
 * ## Bu dosyanın duruşu
 *
 * `belgeleme_` önekli testler mevcut KUSURLU davranışı sabitler (evin idiomu);
 * yeşil olmaları "sorun yok" demek değil. `yeniMotor_` önekli testler karşıtlığı
 * kurar: aynı girdide yeni motorun kusuru YOK. İkisi birlikte kusurun giriş
 * noktasında olduğunu, motorda olmadığını kanıtlıyor.
 *
 * ⚠️ `RankingEngine.kt` ve `SwissSystem.kt` yalnız OKUNDU, yazılmadı.
 * Düzeltme kararı koordinatörde.
 */
class SwissBirinciTurGirisNoktasiTest {

    private val LISTE = 5L

    private fun songs(n: Int): List<Song> =
        (1..n).map { i -> Song(id = i.toLong(), name = "Oge$i", listId = LISTE) }

    /** 1. turda maçlara giren tüm öğe id'leri. */
    private fun kapsananlar(n: Int): Set<Long> {
        val matches = RankingEngine.createSwissMatches(songs(n), 1, emptyList())
        return matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
    }

    // ==========================================================
    // ① ESKİ GİRİŞ NOKTASI — TEK SAYIDA ÖĞE DÜŞÜYOR
    // ==========================================================

    @Test
    fun belgeleme_tekSayida_TAM_BIR_OGE_TURNUVADAN_DUSUYOR() {
        for (n in listOf(3, 5, 7, 9, 15, 41, 199)) {
            val tumu = songs(n).map { it.id }.toSet()
            val kapsanan = kapsananlar(n)
            val dusen = tumu - kapsanan

            assertEquals(
                "n=$n: eski 1. tur yolunda tam olarak 1 öğe dışarıda kalmalı (mevcut kusur)",
                1, dusen.size
            )
            assertEquals("n=$n: kapsanan öğe sayısı n-1 olmalı", n - 1, kapsanan.size)
        }
    }

    @Test
    fun belgeleme_dusenOgeIcin_BYE_MACI_URETILMIYOR() {
        // Bye, bu projede "kendisiyle eşleşme" ya da ayrı bir işaret olarak
        // tutulabilirdi; eski yolda HİÇBİRİ yok — öğe sessizce yok sayılıyor.
        val n = 9
        val matches = RankingEngine.createSwissMatches(songs(n), 1, emptyList())

        assertEquals("n=$n: yalnız n/2 maç üretiliyor", n / 2, matches.size)
        assertTrue(
            "Hiçbir maç kendisiyle eşleşme (bye işareti) değil",
            matches.none { it.songId1 == it.songId2 }
        )
        assertEquals(
            "Düşen öğe hiçbir maçta geçmiyor — ne rakip, ne bye",
            n - 1, matches.flatMap { listOf(it.songId1, it.songId2) }.toSet().size
        )
    }

    @Test
    fun belgeleme_dusenOge_HER_KOSUMDA_FARKLI_shuffledYuzunden() {
        // Kusurun en kötü yanı: deterministik değil. Aynı liste iki kez
        // başlatılınca farklı öğe düşüyor; kullanıcı "geçen sefer vardı" diyemiyor.
        val n = 9
        val tumu = songs(n).map { it.id }.toSet()
        val dusenler = mutableSetOf<Long>()

        repeat(60) { dusenler.addAll(tumu - kapsananlar(n)) }

        assertTrue(
            "60 koşumda birden fazla farklı öğe düşmeli (shuffled) — bulunan: $dusenler",
            dusenler.size > 1
        )
    }

    @Test
    fun ciftSayida_kapsamaTam_kusurYok() {
        for (n in listOf(2, 4, 8, 16, 40, 200)) {
            val kapsanan = kapsananlar(n)
            assertEquals("n=$n: çift sayıda tüm öğeler kapsanmalı", n, kapsanan.size)
            assertEquals(
                "n=$n: maç sayısı n/2 olmalı",
                n / 2, RankingEngine.createSwissMatches(songs(n), 1, emptyList()).size
            )
        }
    }

    @Test
    fun belgeleme_tekOgeliTurnuva_hicMacUretilmiyor() {
        assertTrue(
            "n=1: half=0, hiç maç yok — tek öğe hiçbir şeye girmiyor",
            RankingEngine.createSwissMatches(songs(1), 1, emptyList()).isEmpty()
        )
    }

    // ==========================================================
    // ② İKİNCİ ESKİ YOL — createSwissMatchesWithState AYNI KUSURU TAŞIYOR
    // ==========================================================

    @Test
    fun belgeleme_withStateYolu_da_ayniOgeyiDusuruyor() {
        // `createSwissMatchesWithState` → `createSwissMatchesAdvanced` (:571-590)
        // birebir aynı `half = size / 2` aritmetiğini kullanıyor. Yani düzeltme
        // TEK yerde değil, İKİ yerde gerekiyor.
        val n = 9
        val bosDurum = SwissStandings(
            standings = songs(n).associate { it.id to 0.0 },
            pairingHistory = emptySet(),
            roundHistory = emptyList()
        )
        val matches = RankingEngine.createSwissMatchesWithState(songs(n), bosDurum)

        assertEquals("withState yolu da yalnız n/2 maç üretiyor", n / 2, matches.size)
        assertEquals(
            "withState yolunda da bir öğe dışarıda",
            n - 1, matches.flatMap { listOf(it.songId1, it.songId2) }.toSet().size
        )
    }

    // ==========================================================
    // ③ KARŞITLIK — YENİ MOTORDA KUSUR YOK
    // ==========================================================

    /**
     * Yeni motorda bye, KENDİSİYLE EŞLEŞEN bir Match kaydıdır
     * (`SwissSystem.kt:205-215`): songId1 == songId2 == bye takımı,
     * winnerId = bye takımı, matchNumber = 0, isCompleted = true.
     *
     * Eski 1. tur yolunda böyle bir kayıt HİÇ üretilmiyor
     * (`belgeleme_dusenOgeIcin_BYE_MACI_URETILMIYOR`) — iki yolun farkı
     * tam olarak burada görünüyor.
     */
    private fun gercekMaclar(matches: List<com.example.ranking.data.Match>) =
        matches.filter { it.songId1 != it.songId2 }

    private fun byeKayitlari(matches: List<com.example.ranking.data.Match>) =
        matches.filter { it.songId1 == it.songId2 }

    @Test
    fun yeniMotor_byeyiKendisiyleEslesenMacOlarakKaydediyor() {
        // Sözleşme sabitleme: cihazda/veritabanında bye ARANACAKSA bu biçimde aranmalı.
        val n = 9
        val sonuc = SwissSystem.createNextRound(
            SwissSystem.computeState(songs(n), emptyList()), emptyList()
        )
        val bye = byeKayitlari(sonuc.matches)

        assertEquals("Tek sayıda tam 1 bye kaydı olmalı", 1, bye.size)
        assertEquals("Bye kaydı bye takımına ait", sonuc.byeTeam?.id, bye[0].songId1)
        assertEquals("Bye kaydında iki taraf da aynı öğe", bye[0].songId1, bye[0].songId2)
        assertEquals("Bye kazananı kendisi", bye[0].songId1, bye[0].winnerId)
        assertEquals("Bye matchNumber 0", 0, bye[0].matchNumber)
        assertTrue("Bye kaydı tamamlanmış gelir", bye[0].isCompleted)
    }

    @Test
    fun yeniMotor_tekSayida_hicKimseyiDusurmuyor_byeVeriyor() {
        for (n in listOf(3, 5, 7, 9, 15, 41)) {
            val liste = songs(n)
            val state = SwissSystem.computeState(liste, emptyList())
            val sonuc = SwissSystem.createNextRound(state, emptyList())

            val gercek = gercekMaclar(sonuc.matches)
            val macIdleri = gercek.flatMap { listOf(it.songId1, it.songId2) }.toSet()
            val byeId = sonuc.byeTeam?.id

            assertNotNull("n=$n: yeni motor tek sayıda BYE vermeli", byeId)
            assertEquals("n=$n: gerçek maç sayısı (n-1)/2 olmalı", (n - 1) / 2, gercek.size)
            assertEquals(
                "n=$n: maçlar + bye TÜM öğeleri kapsamalı — kimse düşmemeli",
                liste.map { it.id }.toSet(), macIdleri + byeId!!
            )
        }
    }

    @Test
    fun yeniMotor_ciftSayida_tamKapsama_byeYok() {
        for (n in listOf(4, 8, 16, 40)) {
            val liste = songs(n)
            val state = SwissSystem.computeState(liste, emptyList())
            val sonuc = SwissSystem.createNextRound(state, emptyList())

            assertNull("n=$n: çift sayıda bye olmamalı", sonuc.byeTeam)
            assertTrue("n=$n: çift sayıda bye kaydı da olmamalı", byeKayitlari(sonuc.matches).isEmpty())
            assertEquals(
                "n=$n: tüm öğeler eşleşmeli",
                liste.map { it.id }.toSet(),
                sonuc.matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
            )
        }
    }

    @Test
    fun kusurunOlcusu_eskiYolVeYeniMotorArasindakiFark() {
        // Tek satırda kusurun büyüklüğü: aynı girdi, iki farklı kapsama.
        for (n in listOf(3, 9, 41)) {
            val liste = songs(n)
            val eskiKapsama = kapsananlar(n).size

            val sonuc = SwissSystem.createNextRound(
                SwissSystem.computeState(liste, emptyList()), emptyList()
            )
            // Bye kaydı kendisiyle eşleşen bir Match olduğu için gerçek maçlardan
            // ayrılmalı; yoksa bye takımı iki kez sayılır.
            val yeniKapsama = (
                gercekMaclar(sonuc.matches).flatMap { listOf(it.songId1, it.songId2) }.toSet() +
                    setOfNotNull(sonuc.byeTeam?.id)
                ).size

            assertEquals("n=$n: eski yol n-1 kapsıyor", n - 1, eskiKapsama)
            assertEquals("n=$n: yeni motor n kapsıyor", n, yeniKapsama)
            assertEquals(
                "n=$n: fark tam olarak 1 öğe — turnuvadan sessizce düşen öğe",
                1, yeniKapsama - eskiKapsama
            )
        }
    }
}
