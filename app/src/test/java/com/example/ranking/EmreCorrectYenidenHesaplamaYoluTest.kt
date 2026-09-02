package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EMRE_CORRECT'in YENİDEN HESAPLAMA (replay) YOLUNUN sınavı.
 *
 * Motorun kendisi değil, motora giden YOL ölçülür. `RankingViewModel.completeRanking`
 * içinde `emreState == null` iken (süreç ölümü sonrası devam, oturum yeniden
 * kurulumu) nihai sıralama şu şekilde yeniden üretilir:
 *
 *   state = initializeEmreTournament(songs)
 *   for ((round, roundMatches) in allMatches.filter{isCompleted}.groupBy{round}.toSortedMap())
 *       state = processCorrectEmreResults(state, roundMatches,
 *                   findByeTeamFromMatches(state, roundMatches, songs), tumTamamlanan)
 *
 * `findByeTeamFromMatches` (RankingViewModel.kt:931-938) bye'ı ÇIKARIM ile bulur:
 *   - `songs.size % 2 == 0` ise peşinen null
 *   - değilse "bu turda hiçbir maçta görünmeyen İLK öğe" bye sayılır
 * Motorun kendi bye kaydı (EmreTeam.byeCount / byePassed) kullanılmaz.
 *
 * Bu dosya (a) sağlam yolu ASSERT ile korur, (b) çıkarımın kırıldığı iki gerçek
 * hâli ÖLÇER ve rapora taşır. ViewModel'in Room bağımlılığına girmemek için
 * çıkarım mantığı burada birebir kopyalanmıştır (EmreTurKatlanmasiTest'teki kalıp).
 *
 * Motor koduna ve ViewModel'e DOKUNULMAZ.
 */
class EmreCorrectYenidenHesaplamaYoluTest {

    /** RankingViewModel.kt:931-938'in birebir karşılığı. */
    private fun findByeTeamFromMatches(
        state: EmreSystemCorrect.EmreState,
        matches: List<Match>,
        songs: List<Song>
    ): EmreSystemCorrect.EmreTeam? {
        if (songs.size % 2 == 0) return null
        val playedTeamIds = matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
        val byeSong = songs.find { it.id !in playedTeamIds }
        return byeSong?.let { song -> state.teams.find { it.song.id == song.id } }
    }

    /** completeRanking'in fallback dalının birebir karşılığı. */
    private fun viewModelYoluylaYenidenHesapla(
        songs: List<Song>,
        allMatches: List<Match>
    ): EmreSystemCorrect.EmreState {
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val tamamlanan = allMatches.filter { it.isCompleted }
        tamamlanan.groupBy { it.round }.toSortedMap().forEach { (_, turunMaclari) ->
            val byeTeam = findByeTeamFromMatches(state, turunMaclari, songs)
            state = RankingEngine.processCorrectEmreResults(
                state, turunMaclari, byeTeam, allCompletedMatches = tamamlanan
            )
        }
        return state
    }

    private fun sarkilar(n: Int, tohum: Long = 4242L): List<Song> =
        (1..n).shuffled(java.util.Random(tohum)).mapIndexed { i, deger ->
            Song(
                id = deger.toLong(), name = "Sayi $deger", artist = "", album = "",
                trackNumber = i + 1, listId = 1L
            )
        }

    private data class Kosum(
        val songs: List<Song>,
        val state: EmreSystemCorrect.EmreState,
        val allMatches: List<Match>,
        /** tur -> gerçek bye geçen öğenin song id'si */
        val byeler: Map<Int, Long>
    )

    private fun kostur(n: Int, tohum: Long = 4242L): Kosum {
        val songs = sarkilar(n, tohum)
        var state = EmreSystemCorrect.initializeEmreTournament(songs)
        val hepsi = mutableListOf<Match>()
        val byeler = mutableMapOf<Int, Long>()
        var nextId = 1L
        var tur = 0
        while (tur < 200) {
            val pairing = EmreSystemCorrect.createHybridPairingSystem(state)
            if (!pairing.canContinue || pairing.matches.isEmpty()) break
            tur++
            pairing.byeTeam?.let { byeler[tur] = it.id }
            val turunMaclari = pairing.matches.map {
                it.copy(
                    id = nextId++, round = tur,
                    winnerId = maxOf(it.songId1, it.songId2), isCompleted = true
                )
            }
            hepsi.addAll(turunMaclari)
            state = RankingEngine.processCorrectEmreResults(
                state, turunMaclari, pairing.byeTeam, allCompletedMatches = hepsi.toList()
            )
        }
        return Kosum(songs, state, hepsi.toList(), byeler)
    }

    private fun sira(state: EmreSystemCorrect.EmreState): List<Long> =
        RankingEngine.calculateCorrectEmreResults(state).sortedBy { it.position }.map { it.songId }

    private fun puanlar(state: EmreSystemCorrect.EmreState): Map<Long, Double> =
        state.teams.associate { it.id to it.points }

    // ==========================================================
    // (a) SAĞLAM YOL — korunması gereken sözleşme
    // ==========================================================

    @Test
    fun tamKayitla_viewModelYoluCanliSonucuBirebirUretir() {
        listOf(9, 15, 41).forEach { n ->
            val k = kostur(n)
            val yeniden = viewModelYoluylaYenidenHesapla(k.songs, k.allMatches)

            // Çıkarılan bye'lar gerçek bye'larla aynı mı?
            var state = EmreSystemCorrect.initializeEmreTournament(k.songs)
            val tamamlanan = k.allMatches.filter { it.isCompleted }
            val cikarilan = mutableMapOf<Int, Long?>()
            tamamlanan.groupBy { it.round }.toSortedMap().forEach { (tur, maclar) ->
                val bye = findByeTeamFromMatches(state, maclar, k.songs)
                cikarilan[tur] = bye?.id
                state = RankingEngine.processCorrectEmreResults(
                    state, maclar, bye, allCompletedMatches = tamamlanan
                )
            }
            assertEquals(
                "n=$n: cikarim bye'lari gercek bye'lardan farkli — cikarilan=$cikarilan gercek=${k.byeler}",
                k.byeler, cikarilan.filterValues { it != null }.mapValues { it.value!! }
            )
            assertEquals("n=$n: yeniden hesaplanan puanlar canlidan farkli",
                puanlar(k.state), puanlar(yeniden))
            assertEquals("n=$n: yeniden hesaplanan SIRA canlidan farkli",
                sira(k.state), sira(yeniden))
            val turSayisi = k.allMatches.map { it.round }.distinct().size
            println("SAGLAM n=%-3d tur=%-3d mac=%-4d bye=%-3d -> replay birebir"
                .format(n, turSayisi, k.allMatches.size, k.byeler.size))
        }
    }

    // ==========================================================
    // (b) YARIM MAÇ — turda tamamlanmamış kayıt kalırsa
    // ==========================================================

    /**
     * Ağaçta yarım kalmış bir maç kaydı varsa (katlanma gerilemesinde görüldüğü
     * gibi bu mümkün), `filter { isCompleted }` o maçı süzer; o turda maçı olan
     * İKİ takım da "hiç oynamamış" görünür. Çıkarım "görünmeyen İLK öğeyi" bye
     * sayar — bu, gerçek bye yerine listede önce gelen takım olabilir.
     */
    @Test
    fun yarimMacVarsa_byeCikarimiYanlisTakimaPuanYazabilir() {
        val n = 9
        val k = kostur(n)
        val hedefTur = 2
        val yarimId = k.allMatches.first { it.round == hedefTur }.id
        val bozuk = k.allMatches.map {
            if (it.id == yarimId) it.copy(winnerId = null, isCompleted = false) else it
        }

        var state = EmreSystemCorrect.initializeEmreTournament(k.songs)
        val tamamlanan = bozuk.filter { it.isCompleted }
        var cikarilanBye: Long? = null
        tamamlanan.groupBy { it.round }.toSortedMap().forEach { (tur, maclar) ->
            val bye = findByeTeamFromMatches(state, maclar, k.songs)
            if (tur == hedefTur) cikarilanBye = bye?.id
            state = RankingEngine.processCorrectEmreResults(
                state, maclar, bye, allCompletedMatches = tamamlanan
            )
        }

        val gercekBye = k.byeler[hedefTur]
        val yarimMac = k.allMatches.first { it.round == hedefTur }
        println(
            "YARIM-MAC n=$n tur=$hedefTur: yarim birakilan mac=${yarimMac.songId1}-${yarimMac.songId2}, " +
                "gercek bye=$gercekBye, cikarilan bye=$cikarilanBye  " +
                (if (cikarilanBye != gercekBye) "=> YANLIS TAKIMA +1 PUAN" else "=> tesadufen dogru")
        )
        println("YARIM-MAC sira farki: canli=${sira(k.state).take(5)} replay=${sira(state).take(5)}")

        // Çerçeve: yol çökmemeli ve puan kasası kendi içinde tutarlı olmalı
        val beklenenToplam = tamamlanan.size + (if (n % 2 == 1) tamamlanan.groupBy { it.round }.size else 0)
        assertEquals(
            "Replay kasasi kendi kayitlariyla tutarsiz",
            beklenenToplam.toDouble(), state.teams.sumOf { it.points }, 1e-9
        )
    }

    // ==========================================================
    // (c) SİLİNMİŞ ÖĞE — tek/çift varsayımı
    // ==========================================================

    /**
     * Tek sayılı turnuvada bir öğe silinirse `songs.size` ÇİFT olur ve çıkarım
     * peşinen null döner: geçmişteki TÜM bye puanları yok olur. Motorun kendi
     * bye kaydı kullanılmadığı için bu sessizce olur.
     */
    @Test
    fun ogeSilinince_gecmisteki_byePuanlariKaybolabilir() {
        val n = 9
        val k = kostur(n)
        val silinen = k.songs.last()
        val kalanSongs = k.songs.filterNot { it.id == silinen.id }
        val kalanMaclar = k.allMatches.filterNot {
            it.songId1 == silinen.id || it.songId2 == silinen.id
        }

        val yeniden = viewModelYoluylaYenidenHesapla(kalanSongs, kalanMaclar)

        // Silinen öğe dışındaki gerçek bye sayısı
        val kalanByeSayisi = k.byeler.values.count { it != silinen.id }
        val replayToplam = yeniden.teams.sumOf { it.points }
        val macToplam = kalanMaclar.count { it.isCompleted }.toDouble()

        println(
            "SILME n=$n: silinen=${silinen.id} | kalan oge=${kalanSongs.size} (cift => cikarim null) | " +
                "kalan mac=${macToplam.toInt()} | hakedilen bye puani=$kalanByeSayisi | " +
                "replay toplam puan=$replayToplam | KAYIP=${(macToplam + kalanByeSayisi) - replayToplam}"
        )

        // Çerçeve: yol çökmemeli, kalan öğeler eksiksiz sıralanmalı
        val sonuc = RankingEngine.calculateCorrectEmreResults(yeniden)
        assertEquals("Silme sonrasi oge kaybi/tekrari", kalanSongs.size, sonuc.map { it.songId }.toSet().size)
        assertEquals("Pozisyonlar 1..n olmali",
            (1..kalanSongs.size).toList(), sonuc.map { it.position }.sorted())
        assertTrue("Replay toplam puani mac sayisindan kucuk olamaz", replayToplam >= macToplam)
    }
}
