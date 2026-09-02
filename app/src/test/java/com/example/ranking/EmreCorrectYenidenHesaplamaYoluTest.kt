package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.EmreSystemCorrect
import com.example.ranking.ranking.RankingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /**
     * RankingViewModel.kt:932-937'nin birebir karşılığı (düzeltilmiş hâli).
     * Kural: o turda oynamamış öğe TAM OLARAK BİR ise bye odur; değilse bye yok.
     * Eski hâli "çift sayıda bye yok" + "oynamayan İLK öğe bye'dir" idi ve
     * iki sessiz hataya yol açıyordu (bu dosyanın (b) ve (c) vakaları).
     */
    private fun findByeTeamFromMatches(
        state: EmreSystemCorrect.EmreState,
        matches: List<Match>,
        songs: List<Song>
    ): EmreSystemCorrect.EmreTeam? {
        val playedTeamIds = matches.flatMap { listOf(it.songId1, it.songId2) }.toSet()
        val oynamayanlar = songs.filter { it.id !in playedTeamIds }
        if (oynamayanlar.size != 1) return null
        return state.teams.find { it.song.id == oynamayanlar[0].id }
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
     * İKİ takım da "hiç oynamamış" görünür — yani oynamayan sayısı 3 olur.
     *
     * ESKİ davranış: "görünmeyen İLK öğe" bye sayılıyordu → gerçek bye 5 iken
     * çıkarım 2 diyor, hak edilmeyen +1 puan yazılıyor, final sıralaması
     * değişiyordu (ölçüldü, a82a6ab).
     * YENİ kural ("tam olarak bir" şartı): o turda bye YAZILMAZ. Hayalet puan
     * yok; emin olunamayan turda eksik puan kalır — yanlış puandan iyidir.
     */
    @Test
    fun yarimMacVarsa_hayaletByePuaniYazilmaz() {
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
        var yazilanByeSayisi = 0
        tamamlanan.groupBy { it.round }.toSortedMap().forEach { (tur, maclar) ->
            val bye = findByeTeamFromMatches(state, maclar, k.songs)
            if (bye != null) yazilanByeSayisi++
            if (tur == hedefTur) cikarilanBye = bye?.id
            state = RankingEngine.processCorrectEmreResults(
                state, maclar, bye, allCompletedMatches = tamamlanan
            )
        }

        val gercekBye = k.byeler[hedefTur]
        val yarimMac = k.allMatches.first { it.round == hedefTur }
        println(
            "YARIM-MAC n=$n tur=$hedefTur: yarim birakilan mac=${yarimMac.songId1}-${yarimMac.songId2}, " +
                "gercek bye=$gercekBye, cikarilan bye=$cikarilanBye (null olmali) | " +
                "yazilan bye=$yazilanByeSayisi/${tamamlanan.groupBy { it.round }.size} tur"
        )
        println("YARIM-MAC sira: canli=${sira(k.state).take(5)} replay=${sira(state).take(5)}")

        // ① Yarım maçlı turda bye YAZILMAMALI (hayalet puan yasağı)
        assertNull(
            "Yarim macli turda bye cikarilamaz; cikarilirsa hak etmeyen takima +1 yazilir " +
                "(eski kusur: gercek bye $gercekBye iken cikarim 2 diyordu)",
            cikarilanBye
        )

        // ② Hiçbir takım maçlardan + yazılan bye'lardan fazlasını kazanmamalı
        val beklenenToplam = (tamamlanan.size + yazilanByeSayisi).toDouble()
        assertEquals(
            "Replay kasasi kendi kayitlariyla tutarsiz",
            beklenenToplam, state.teams.sumOf { it.points }, 1e-9
        )

        // ③ Yarım kalan maçın taraflarına o turdan puan sızmamalı
        val yarimTaraflar = setOf(yarimMac.songId1, yarimMac.songId2)
        val macPuani = HashMap<Long, Double>()
        tamamlanan.forEach { m ->
            val w = m.winnerId
            if (w == null) {
                macPuani.merge(m.songId1, 0.5, Double::plus); macPuani.merge(m.songId2, 0.5, Double::plus)
            } else macPuani.merge(w, 1.0, Double::plus)
        }
        yarimTaraflar.forEach { id ->
            val motor = state.teams.first { it.id == id }.points
            val kayit = macPuani[id] ?: 0.0
            assertTrue(
                "Yarim macin tarafi $id, mac kayitlarindan fazla puan almis (motor=$motor kayit=$kayit) " +
                    "— hayalet bye puani sizmis olmali",
                motor <= kayit + 1e-9
            )
        }
    }

    // ==========================================================
    // (c) SİLİNMİŞ ÖĞE — tek/çift varsayımı
    // ==========================================================

    /**
     * ESKİ davranış: tek sayılı turnuvada bir öğe silinince `songs.size` çift
     * olur, çıkarım peşinen null döner ve geçmişteki TÜM bye puanları yok olurdu
     * (ölçüldü: 13.0, hak edilen 16.0).
     * YENİ kural: tek/çift ön şartı yok. Silinen öğenin OYNAMADIĞI turlarda
     * oynamayan tam olarak bir kalır → hak edilen bye puanı yazılır. Silinen
     * öğenin oynadığı turlarda rakibi de "oynamamış" görüneceği için oynamayan
     * sayısı 2'dir → o turlarda bye yazılmaz (eksik puan, yanlış puan değil).
     *
     * Bu test yazılan her bye'ın GERÇEK bye olduğunu doğrular ve kalan boşluğu
     * (hâlâ yazılamayan hak edilmiş bye sayısını) rakamla rapora taşır.
     */
    @Test
    fun ogeSilinince_yazilanByelerGercek_hayaletYok() {
        val n = 9
        val k = kostur(n)
        val silinen = k.songs.last()
        val kalanSongs = k.songs.filterNot { it.id == silinen.id }
        val kalanMaclar = k.allMatches.filterNot {
            it.songId1 == silinen.id || it.songId2 == silinen.id
        }

        // Tur tur çıkarımı izle
        var state = EmreSystemCorrect.initializeEmreTournament(kalanSongs)
        val tamamlanan = kalanMaclar.filter { it.isCompleted }
        val yazilan = mutableMapOf<Int, Long>()
        tamamlanan.groupBy { it.round }.toSortedMap().forEach { (tur, maclar) ->
            val bye = findByeTeamFromMatches(state, maclar, kalanSongs)
            if (bye != null) yazilan[tur] = bye.id
            state = RankingEngine.processCorrectEmreResults(
                state, maclar, bye, allCompletedMatches = tamamlanan
            )
        }

        val hakEdilen = k.byeler.filterValues { it != silinen.id }   // silinenin byesi sayılmaz
        val macToplam = tamamlanan.size.toDouble()
        val replayToplam = state.teams.sumOf { it.points }

        println(
            "SILME n=$n: silinen=${silinen.id} | kalan oge=${kalanSongs.size} | kalan mac=${macToplam.toInt()} | " +
                "hak edilen bye=${hakEdilen.size} ${hakEdilen} | yazilan bye=${yazilan.size} ${yazilan} | " +
                "replay toplam=$replayToplam | hala yazilamayan=${hakEdilen.size - yazilan.size}"
        )

        // ① Yazılan her bye GERÇEK bye olmalı (hayalet puan yasağı)
        yazilan.forEach { (tur, id) ->
            assertEquals(
                "Tur $tur icin yanlis takima bye puani yazildi (gercek=${k.byeler[tur]})",
                k.byeler[tur], id
            )
        }
        // ② Kasa: maç + yazılan bye
        assertEquals("Replay kasasi tutmuyor",
            macToplam + yazilan.size, replayToplam, 1e-9)
        // ③ Fazla puan yazılamaz (üst sınır: hak edilen bye sayısı)
        assertTrue(
            "Yazilan bye sayisi hak edilenden fazla: ${yazilan.size} > ${hakEdilen.size}",
            yazilan.size <= hakEdilen.size
        )
        // ④ Yol çökmemeli, kalan öğeler eksiksiz sıralanmalı
        val sonuc = RankingEngine.calculateCorrectEmreResults(state)
        assertEquals("Silme sonrasi oge kaybi/tekrari", kalanSongs.size, sonuc.map { it.songId }.toSet().size)
        assertEquals("Pozisyonlar 1..n olmali",
            (1..kalanSongs.size).toList(), sonuc.map { it.position }.sorted())
    }
}
