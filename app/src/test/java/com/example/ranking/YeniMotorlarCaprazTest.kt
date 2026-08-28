package com.example.ranking

import com.example.ranking.data.Match
import com.example.ranking.data.Song
import com.example.ranking.ranking.SwissSystem
import org.junit.Assert.*
import org.junit.Test

/**
 * YENİ MOTORLAR — ÇAPRAZ KIRMA TESTLERİ
 *
 * Yeni yazılan motorları, onları yazan oturumdan BAĞIMSIZ olarak kırmaya
 * çalışır. Motor dosyalarına dokunulmaz; kusur bulunursa koordinatöre bildirilir.
 *
 * Kapsanan: `SwissSystem` (ranking-37).
 * Bekleniyor: `EliminationSystem` — dosya henüz yok, geldiğinde bu dosyaya eklenecek.
 *
 * En kırmızı çizgiden başlanır:
 *  - aynı ikili İKİ KEZ eşleşiyor mu
 *  - tek takımda bye gerçekten veriliyor mu, rotasyon adil mi
 *  - her turda kimse düşüyor mu
 *  - replay: aynı maç listesi (sırası bozuk olsa da) aynı durumu veriyor mu
 *  - geri izleme bütçesi dolunca ne oluyor
 *  - geçişlilik, yetim maç, sınır durumları
 */
class YeniMotorlarCaprazTest {

    // ==========================================================
    // YARDIMCILAR
    // ==========================================================

    private fun makeSongs(count: Int): List<Song> =
        (1..count).map { i -> Song(id = i.toLong(), name = "Takim$i", listId = 11L) }

    private fun normalize(a: Long, b: Long) = if (a < b) Pair(a, b) else Pair(b, a)

    private data class RoundInfo(
        val round: Int,
        val stored: List<Match>,
        val byeTeamId: Long?
    ) {
        val realMatches: List<Match> get() = stored.filter { it.songId1 != it.songId2 }
        val byeMatches: List<Match> get() = stored.filter { it.songId1 == it.songId2 }
    }

    private data class Sim(
        val songs: List<Song>,
        val rounds: List<RoundInfo>,
        val all: List<Match>,
        val endReason: String
    )

    /** Deterministik turnuva simülasyonu (rastgelelik YOK). */
    private fun simulate(n: Int, winnerPicker: (Match) -> Long?): Sim {
        val songs = makeSongs(n)
        val all = mutableListOf<Match>()
        val rounds = mutableListOf<RoundInfo>()
        var nextId = 1L
        var reason = "tur bütçesi doldu"
        var guard = 0

        while (guard++ < 200) {
            val state = SwissSystem.computeState(songs, all)
            if (state.isComplete) break

            val pairing = SwissSystem.createNextRound(state, all)
            if (!pairing.canContinue) {
                reason = pairing.reason
                break
            }

            val stored = pairing.matches.map { m ->
                if (m.isCompleted) m.copy(id = nextId++)                      // bye kaydı
                else m.copy(id = nextId++, winnerId = winnerPicker(m), isCompleted = true)
            }
            rounds.add(RoundInfo(state.currentRound, stored, pairing.byeTeam?.id))
            all.addAll(stored)
        }
        assertTrue("Turnuva 200 turda bitmedi — sonsuz döngü", guard < 200)
        return Sim(songs, rounds, all, reason)
    }

    private val lowerIdWins: (Match) -> Long? = { m -> minOf(m.songId1, m.songId2) }
    private val higherIdWins: (Match) -> Long? = { m -> maxOf(m.songId1, m.songId2) }
    private val allDraws: (Match) -> Long? = { null }
    private val mixed: (Match) -> Long? = { m ->
        when ((m.songId1 * 7 + m.songId2 * 13 + m.round * 3) % 5) {
            0L, 1L -> m.songId1
            2L, 3L -> m.songId2
            else -> null
        }
    }

    // ==========================================================
    // ① KIRMIZI ÇİZGİ — aynı ikili yalnız bir kez
    // ==========================================================

    private fun assertNoRepeatPairing(n: Int, picker: (Match) -> Long?, label: String) {
        val sim = simulate(n, picker)
        assertTrue("n=$n ($label): hiç tur oynanmadı", sim.rounds.isNotEmpty())

        val seen = mutableSetOf<Pair<Long, Long>>()
        sim.rounds.forEach { r ->
            r.realMatches.forEach { m ->
                val pair = normalize(m.songId1, m.songId2)
                assertTrue(
                    "KIRMIZI ÇİZGİ İHLALİ n=$n ($label): ${pair.first}-${pair.second} " +
                        "ikilisi tur ${r.round}'de İKİNCİ kez eşleşti",
                    seen.add(pair)
                )
            }
        }
    }

    @Test
    fun kirmiziCizgi_n4() = assertNoRepeatPairing(4, lowerIdWins, "küçük id kazanır")

    @Test
    fun kirmiziCizgi_n8() = assertNoRepeatPairing(8, lowerIdWins, "küçük id kazanır")

    @Test
    fun kirmiziCizgi_n8_hepsiBerabere() = assertNoRepeatPairing(8, allDraws, "hepsi berabere")

    @Test
    fun kirmiziCizgi_n16() = assertNoRepeatPairing(16, mixed, "karışık")

    @Test
    fun kirmiziCizgi_n32() = assertNoRepeatPairing(32, mixed, "karışık")

    @Test
    fun kirmiziCizgi_n5_tekSayi() = assertNoRepeatPairing(5, mixed, "tek sayı")

    @Test
    fun kirmiziCizgi_n9_tekSayi() = assertNoRepeatPairing(9, allDraws, "tek sayı, hepsi berabere")

    @Test
    fun kirmiziCizgi_n17_tekSayi() = assertNoRepeatPairing(17, higherIdWins, "tek sayı, büyük id kazanır")

    /**
     * En zorlayıcı senaryo: hepsi berabere → herkes aynı puanda kalır, eşleştirme
     * baskısı en yüksek. Kural 1 asla çiğnenmemeli; çiğnemek yerine turnuva
     * erken bitmeli.
     */
    @Test
    fun kirmiziCizgi_hepsiBerabere_kucukKadrolar() {
        listOf(4, 5, 6, 7, 8).forEach { n ->
            assertNoRepeatPairing(n, allDraws, "hepsi berabere")
        }
    }

    // ==========================================================
    // ② TAM EŞLEŞTİRME VE BYE
    // ==========================================================

    private fun assertRoundIntegrity(n: Int, picker: (Match) -> Long?) {
        val sim = simulate(n, picker)
        val allIds = (1L..n.toLong()).toSet()

        sim.rounds.forEach { r ->
            val oyuncular = r.realMatches.flatMap { listOf(it.songId1, it.songId2) }
            assertEquals(
                "n=$n tur ${r.round}: bir takım aynı turda iki maçta",
                oyuncular.size, oyuncular.toSet().size
            )

            if (n % 2 == 0) {
                assertNull("n=$n tur ${r.round}: çift takımda bye olmamalı", r.byeTeamId)
                assertTrue("n=$n tur ${r.round}: çift takımda bye kaydı olmamalı", r.byeMatches.isEmpty())
                assertEquals("n=$n tur ${r.round}: maç sayısı", n / 2, r.realMatches.size)
            } else {
                assertNotNull("n=$n tur ${r.round}: TEK takımda bye VERİLMEDİ", r.byeTeamId)
                assertEquals("n=$n tur ${r.round}: tam olarak 1 bye kaydı olmalı", 1, r.byeMatches.size)
                assertEquals("n=$n tur ${r.round}: maç sayısı", (n - 1) / 2, r.realMatches.size)
            }

            val kapsanan = oyuncular.toMutableSet()
            r.byeTeamId?.let { kapsanan.add(it) }
            assertEquals(
                "n=$n tur ${r.round}: TURDAN DÜŞEN TAKIM VAR — ${allIds - kapsanan}",
                allIds, kapsanan
            )
            r.byeTeamId?.let { bye ->
                assertFalse("n=$n tur ${r.round}: bye geçen takım maç da oynadı", bye in oyuncular)
            }
        }
    }

    @Test
    fun turButunlugu_cift_n8() = assertRoundIntegrity(8, mixed)

    @Test
    fun turButunlugu_cift_n16() = assertRoundIntegrity(16, lowerIdWins)

    @Test
    fun turButunlugu_tek_n5() = assertRoundIntegrity(5, mixed)

    @Test
    fun turButunlugu_tek_n7() = assertRoundIntegrity(7, allDraws)

    @Test
    fun turButunlugu_tek_n15() = assertRoundIntegrity(15, mixed)

    @Test
    fun bye_kaydiKendiyleEslesmeImzasiTasiyor() {
        val sim = simulate(7, mixed)
        sim.rounds.forEach { r ->
            val bye = r.byeMatches.firstOrNull()
            assertNotNull("tur ${r.round}: bye kaydı yok", bye)
            if (bye != null) {
                assertEquals("bye kaydında iki taraf aynı id olmalı", bye.songId1, bye.songId2)
                assertEquals("bye kaydının kazananı bye takımı olmalı", bye.songId1, bye.winnerId)
                assertTrue("bye kaydı tamamlanmış gelmeli (oy istemez)", bye.isCompleted)
                assertEquals("bye takımı pairing sonucuyla tutmalı", r.byeTeamId, bye.songId1)
            }
        }
    }

    @Test
    fun bye_ilkTurdaEnAlttakiSiradakiTakimaGider() {
        val songs = makeSongs(5)
        val state = SwissSystem.computeState(songs, emptyList())
        val pairing = SwissSystem.createNextRound(state, emptyList())
        // Herkes 0 puan; sıralama id'ye düşer → en alttaki 5 numara
        assertEquals("İlk turda bye en alttaki takıma gitmeli", 5L, pairing.byeTeam?.id)
    }

    @Test
    fun bye_ayniTakimIkinciByeyiHerkesAlmadanALMAZ() {
        listOf(5, 7, 9).forEach { n ->
            val sim = simulate(n, mixed)
            val byeCounts = mutableMapOf<Long, Int>()
            sim.rounds.forEach { r ->
                val bye = r.byeTeamId ?: return@forEach
                val yeni = (byeCounts[bye] ?: 0) + 1
                if (yeni == 2) {
                    assertEquals(
                        "n=$n BYE ROTASYON İHLALİ: takım $bye ikinci byeyi aldı ama " +
                            "bye geçmemiş takımlar var: ${(1L..n.toLong()).toSet() - byeCounts.keys}",
                        n, byeCounts.size
                    )
                }
                byeCounts[bye] = yeni
            }
            val min = byeCounts.values.minOrNull() ?: 0
            val max = byeCounts.values.maxOrNull() ?: 0
            assertTrue("n=$n bye dağılımı adil değil: $byeCounts", max - min <= 1)
        }
    }

    @Test
    fun bye_kaydiSaklanmazsaAyniTakimTekrarByeGecer() {
        // TEHLİKE SÖZLEŞMESİ: bye bir Match satırı olarak saklanmazsa replay
        // bye geçmişini hatırlayamaz ve rotasyon bozulur.
        val songs = makeSongs(5)
        val state1 = SwissSystem.computeState(songs, emptyList())
        val r1 = SwissSystem.createNextRound(state1, emptyList())
        val byeId = r1.byeTeam?.id
        assertNotNull(byeId)

        // Bye kaydı BİLEREK atılıyor, yalnız gerçek maçlar saklanıyor
        val byesiz = r1.matches.filter { it.songId1 != it.songId2 }
            .mapIndexed { i, m -> m.copy(id = (i + 1).toLong(), winnerId = m.songId1, isCompleted = true) }

        val state2 = SwissSystem.computeState(songs, byesiz)
        val r2 = SwissSystem.createNextRound(state2, byesiz)
        assertEquals(
            "Bye kaydı saklanmazsa aynı takım yine bye geçer — kayıt ŞART",
            byeId, r2.byeTeam?.id
        )

        // Karşılaştırma: kaydedilirse rotasyon doğru çalışır
        val byeli = r1.matches.mapIndexed { i, m ->
            if (m.isCompleted) m.copy(id = (i + 1).toLong())
            else m.copy(id = (i + 1).toLong(), winnerId = m.songId1, isCompleted = true)
        }
        val r2Dogru = SwissSystem.createNextRound(SwissSystem.computeState(songs, byeli), byeli)
        assertNotEquals(
            "Bye kaydı saklanınca aynı takım tekrar bye geçmemeli",
            byeId, r2Dogru.byeTeam?.id
        )
    }

    // ==========================================================
    // ③ MAÇ NUMARALARI
    // ==========================================================

    @Test
    fun macNumaralari_gercekMaclar1denNye_tekrarsiz() {
        listOf(8, 9, 16, 17).forEach { n ->
            val sim = simulate(n, mixed)
            sim.rounds.forEach { r ->
                val numaralar = r.realMatches.map { it.matchNumber }.sorted()
                assertEquals(
                    "n=$n tur ${r.round}: maç numaraları 1..${r.realMatches.size} olmalı, bulunan $numaralar",
                    (1..r.realMatches.size).toList(), numaralar
                )
            }
        }
    }

    @Test
    fun macNumaralari_enUstEslesme1Numarayi_alir() {
        val songs = makeSongs(8)
        val pairing = SwissSystem.createNextRound(SwissSystem.computeState(songs, emptyList()), emptyList())
        val birinci = pairing.matches.first { it.matchNumber == 1 }
        assertTrue(
            "1 numaralı maç en üst sıralı takımı içermeli, bulunan ${birinci.songId1}-${birinci.songId2}",
            birinci.songId1 == 1L || birinci.songId2 == 1L
        )
    }

    // ==========================================================
    // ④ PUANLAMA
    // ==========================================================

    @Test
    fun puan_galibiyet1_beraberlik05_bye1() {
        val songs = makeSongs(5)
        val r1 = SwissSystem.createNextRound(SwissSystem.computeState(songs, emptyList()), emptyList())
        val gercek = r1.matches.filter { it.songId1 != it.songId2 }.sortedBy { it.matchNumber }
        val byeId = r1.byeTeam?.id

        val stored = mutableListOf<Match>()
        r1.matches.filter { it.songId1 == it.songId2 }.forEach { stored.add(it.copy(id = 100L)) }
        stored.add(gercek[0].copy(id = 1L, winnerId = gercek[0].songId1, isCompleted = true))
        stored.add(gercek[1].copy(id = 2L, winnerId = null, isCompleted = true))

        val state = SwissSystem.computeState(songs, stored)
        fun puan(id: Long?) = state.teams.firstOrNull { it.id == id }?.points

        assertEquals("Galibiyet 1 puan", 1.0, puan(gercek[0].songId1) ?: -1.0, 0.0001)
        assertEquals("Mağlubiyet 0 puan", 0.0, puan(gercek[0].songId2) ?: -1.0, 0.0001)
        assertEquals("Beraberlik 0.5 puan", 0.5, puan(gercek[1].songId1) ?: -1.0, 0.0001)
        assertEquals("Beraberlik 0.5 puan", 0.5, puan(gercek[1].songId2) ?: -1.0, 0.0001)
        assertEquals("Bye 1 puan", 1.0, puan(byeId) ?: -1.0, 0.0001)
    }

    @Test
    fun puan_turnuvaBoyuncaToplamTutuyor() {
        val n = 9
        val sim = simulate(n, mixed)
        val beklenen = mutableMapOf<Long, Double>()
        (1L..n.toLong()).forEach { beklenen[it] = 0.0 }

        sim.all.forEach { m ->
            if (m.songId1 == m.songId2) {
                beklenen[m.songId1] = (beklenen[m.songId1] ?: 0.0) + 1.0
                return@forEach
            }
            when (m.winnerId) {
                m.songId1 -> beklenen[m.songId1] = (beklenen[m.songId1] ?: 0.0) + 1.0
                m.songId2 -> beklenen[m.songId2] = (beklenen[m.songId2] ?: 0.0) + 1.0
                null -> {
                    beklenen[m.songId1] = (beklenen[m.songId1] ?: 0.0) + 0.5
                    beklenen[m.songId2] = (beklenen[m.songId2] ?: 0.0) + 0.5
                }
            }
        }

        val state = SwissSystem.computeState(sim.songs, sim.all)
        state.teams.forEach { t ->
            assertEquals(
                "Takım ${t.id} puanı tutmuyor",
                beklenen[t.id] ?: -1.0, t.points, 0.0001
            )
        }
    }

    // ==========================================================
    // ⑤ REPLAY VE DETERMİNİZM
    // ==========================================================

    @Test
    fun replay_ayniMacListesiIkiKez_ayniDurum() {
        val sim = simulate(8, higherIdWins)
        val a = SwissSystem.computeState(sim.songs, sim.all)
        val b = SwissSystem.computeState(sim.songs, sim.all)
        assertEquals("Aynı maç listesi aynı durumu vermeli", a, b)
        // createdAt karşılaştırma dışı (bkz. replay_macSirasiBozuksaBileAyniDurum)
        assertEquals(
            "Aynı maç listesi aynı sonucu vermeli",
            SwissSystem.calculateResults(sim.songs, sim.all).map { Triple(it.songId, it.score, it.position) },
            SwissSystem.calculateResults(sim.songs, sim.all).map { Triple(it.songId, it.score, it.position) }
        )
    }

    @Test
    fun replay_macSirasiBozuksaBileAyniDurum() {
        // Gerçek replay sınavı: DB kayıtları farklı sırada gelirse durum
        // DEĞİŞMEMELİ (id'ler aynı, yalnız liste sırası bozuk).
        val sim = simulate(9, mixed)
        val karisik = sim.all.sortedBy { (it.songId1 * 31 + it.songId2 * 17) % 97 }

        val duz = SwissSystem.computeState(sim.songs, sim.all)
        val bozuk = SwissSystem.computeState(sim.songs, karisik)
        assertEquals("Kayıt sırası değişince durum değişmemeli", duz, bozuk)
        // ⚠️ `createdAt` KARŞILAŞTIRMA DIŞI: RankingResult'ın varsayılanı
        // System.currentTimeMillis(). İki çağrı milisaniye sınırını aşarsa
        // nesneler eşit çıkmıyor ve test motoru suçlu gösteriyor — oysa
        // songId/score/position birebir aynı. (Aynı tuzak SwissSystemTest'te
        // de vardı, orada da böyle çözüldü.)
        assertEquals(
            "Kayıt sırası değişince sonuç değişmemeli",
            SwissSystem.calculateResults(sim.songs, sim.all).map { Triple(it.songId, it.score, it.position) },
            SwissSystem.calculateResults(sim.songs, karisik).map { Triple(it.songId, it.score, it.position) }
        )
    }

    @Test
    fun replay_ogeSirasiBozuksaBileAyniPuanlar() {
        // songs listesi farklı sırada gelirse puanlar değişmemeli
        val sim = simulate(8, mixed)
        val tersSongs = sim.songs.reversed()
        val duz = SwissSystem.computeState(sim.songs, sim.all).teams.associate { it.id to it.points }
        val ters = SwissSystem.computeState(tersSongs, sim.all).teams.associate { it.id to it.points }
        assertEquals("Öğe sırası puanları etkilememeli", duz, ters)
    }

    @Test
    fun determinizm_tumTurnuvaIkiKez_ayniSonuc() {
        val a = simulate(16, mixed)
        val b = simulate(16, mixed)
        assertEquals("Tur sayısı aynı olmalı", a.rounds.size, b.rounds.size)
        assertEquals(
            "Tüm eşleştirmeler aynı olmalı",
            a.all.map { Triple(it.songId1, it.songId2, it.round) },
            b.all.map { Triple(it.songId1, it.songId2, it.round) }
        )
    }

    @Test
    fun determinizm_ayniDurumdanIkiEslestirme_ayni() {
        val songs = makeSongs(16)
        val state = SwissSystem.computeState(songs, emptyList())
        val a = SwissSystem.createNextRound(state, emptyList())
        val b = SwissSystem.createNextRound(state, emptyList())
        assertEquals(
            a.matches.map { Triple(it.songId1, it.songId2, it.matchNumber) },
            b.matches.map { Triple(it.songId1, it.songId2, it.matchNumber) }
        )
    }

    // ==========================================================
    // ⑥ TUR BÜTÇESİ VE BİTİŞ
    // ==========================================================

    @Test
    fun turSayisi_ceilLog2() {
        assertEquals(0, SwissSystem.recommendedRoundCount(0))
        assertEquals(0, SwissSystem.recommendedRoundCount(1))
        assertEquals(1, SwissSystem.recommendedRoundCount(2))
        assertEquals(2, SwissSystem.recommendedRoundCount(3))
        assertEquals(2, SwissSystem.recommendedRoundCount(4))
        assertEquals(3, SwissSystem.recommendedRoundCount(5))
        assertEquals(3, SwissSystem.recommendedRoundCount(8))
        assertEquals(4, SwissSystem.recommendedRoundCount(9))
        assertEquals(4, SwissSystem.recommendedRoundCount(16))
        assertEquals(6, SwissSystem.recommendedRoundCount(64))
    }

    @Test
    fun bitis_turButcesiAsilmaz() {
        listOf(4, 8, 16, 32).forEach { n ->
            val sim = simulate(n, mixed)
            val butce = SwissSystem.recommendedRoundCount(n)
            assertTrue(
                "n=$n: $butce turluk bütçe aşıldı (${sim.rounds.size} tur oynandı)",
                sim.rounds.size <= butce
            )
        }
    }

    @Test
    fun bitis_butceDolunca_devamEtmiyor() {
        val songs = makeSongs(8)
        val sim = simulate(8, lowerIdWins)
        val son = SwissSystem.computeState(songs, sim.all)
        assertTrue("Bütçe dolunca durum tamamlanmış olmalı", son.isComplete)
        val pairing = SwissSystem.createNextRound(son, sim.all)
        assertFalse("Tamamlanmış turnuvada yeni tur üretilmemeli", pairing.canContinue)
        assertTrue("Bitiş sebebi bildirilmeli", pairing.reason.isNotBlank())
    }

    @Test
    fun bitis_tekrarsizEslestirmeKurulamazsa_tekrarEtmezBITIRIR() {
        // 4 takım, tüm ikililer oynanmış → yeni tur kurulamaz.
        // Motor kural 1'i çiğneyip tekrar eşleştirmemeli, dürüstçe bitirmeli.
        val songs = makeSongs(4)
        var id = 1L
        val hepsi = mutableListOf<Match>()
        for (i in 1..4) for (j in i + 1..4) {
            hepsi.add(
                Match(
                    id = id++, listId = 11L, rankingMethod = SwissSystem.METHOD,
                    songId1 = i.toLong(), songId2 = j.toLong(), winnerId = i.toLong(),
                    round = 1, isCompleted = true
                )
            )
        }
        // Turu 1 tutup bütçenin bitmesini engelliyoruz ki asıl sebep eşleştirme olsun
        val state = SwissSystem.computeState(songs, hepsi)
        val pairing = SwissSystem.createNextRound(state, hepsi)
        assertFalse("Tüm ikililer oynanmışken tur üretilemez", pairing.canContinue)
        assertTrue(
            "Bitiş sebebi eşleştirme kurulamaması olmalı, bulunan: '${pairing.reason}'",
            pairing.reason.contains("eşleştirme") || pairing.reason.contains("tamamland")
        )
        assertTrue("Tekrar eşleşme üretilmemeli", pairing.matches.isEmpty())
    }

    // ==========================================================
    // ⑦ SINIR DURUMLARI VE VERİ ANOMALİLERİ
    // ==========================================================

    @Test
    fun sinir_sifirOge() {
        val state = SwissSystem.computeState(emptyList(), emptyList())
        assertTrue("0 öğede turnuva tamamlanmış sayılmalı", state.isComplete)
        assertTrue(state.teams.isEmpty())
        val pairing = SwissSystem.createNextRound(state, emptyList())
        assertFalse(pairing.canContinue)
        assertTrue(pairing.matches.isEmpty())
        assertTrue(SwissSystem.calculateResults(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun sinir_tekOge() {
        val songs = makeSongs(1)
        val state = SwissSystem.computeState(songs, emptyList())
        assertTrue("1 öğede turnuva tamamlanmış sayılmalı", state.isComplete)
        val pairing = SwissSystem.createNextRound(state, emptyList())
        assertFalse(pairing.canContinue)
        val results = SwissSystem.calculateResults(songs, emptyList())
        assertEquals(1, results.size)
        assertEquals(1, results[0].position)
    }

    @Test
    fun sinir_ikiOge_tekTur() {
        val sim = simulate(2, lowerIdWins)
        assertEquals("2 öğede 1 tur oynanmalı", 1, sim.rounds.size)
        assertEquals("2 öğede 1 maç", 1, sim.all.count { it.songId1 != it.songId2 })
        val results = SwissSystem.calculateResults(sim.songs, sim.all)
        assertEquals(listOf(1, 2), results.map { it.position }.sorted())
        assertEquals("Kazanan 1. sırada olmalı", 1L, results.first { it.position == 1 }.songId)
    }

    @Test
    fun sinir_ucOge() {
        val sim = simulate(3, mixed)
        assertTrue("3 öğede en az 1 tur oynanmalı", sim.rounds.isNotEmpty())
        sim.rounds.forEach { r ->
            assertEquals("3 öğede turda 1 maç", 1, r.realMatches.size)
            assertNotNull("3 öğede her turda bye olmalı", r.byeTeamId)
        }
    }

    @Test
    fun yetimMac_silinmisOgeCokmezVePuanUretmez() {
        val songs = makeSongs(4)
        val matches = listOf(
            Match(id = 1L, listId = 11L, rankingMethod = SwissSystem.METHOD,
                songId1 = 1L, songId2 = 999L, winnerId = 1L, round = 1, isCompleted = true),
            Match(id = 2L, listId = 11L, rankingMethod = SwissSystem.METHOD,
                songId1 = 998L, songId2 = 997L, winnerId = 998L, round = 1, isCompleted = true),
            Match(id = 3L, listId = 11L, rankingMethod = SwissSystem.METHOD,
                songId1 = 2L, songId2 = 3L, winnerId = 2L, round = 1, isCompleted = true)
        )
        val state = SwissSystem.computeState(songs, matches)
        assertEquals("Takım sayısı değişmemeli", 4, state.teams.size)
        assertEquals(
            "Yetim maç puan üretmemeli",
            0.0, state.teams.first { it.id == 1L }.points, 0.0001
        )
        assertEquals(
            "Geçerli maç puan üretmeli",
            1.0, state.teams.first { it.id == 2L }.points, 0.0001
        )
        // Yetim maç 1-999'u "oynanmış" saymamalı; 1 hâlâ herkesle eşleşebilmeli
        val pairing = SwissSystem.createNextRound(state, matches)
        assertTrue("Yetim maçtan sonra tur kurulabilmeli", pairing.canContinue)
    }

    @Test
    fun baskaYontemeAitMaclarSayilmaz() {
        val songs = makeSongs(4)
        val yabanci = listOf(
            Match(id = 1L, listId = 11L, rankingMethod = "LEAGUE",
                songId1 = 1L, songId2 = 2L, winnerId = 1L, round = 1, isCompleted = true)
        )
        val state = SwissSystem.computeState(songs, yabanci)
        assertEquals(
            "Başka yönteme ait maç SWISS puanı üretmemeli",
            0.0, state.teams.first { it.id == 1L }.points, 0.0001
        )
        val pairing = SwissSystem.createNextRound(state, yabanci)
        assertTrue(
            "Başka yönteme ait maç eşleşmeyi yasaklamamalı",
            pairing.matches.any { normalize(it.songId1, it.songId2) == Pair(1L, 2L) }
        )
    }

    @Test
    fun tamamlanmamisMaclarSayilmaz() {
        val songs = makeSongs(4)
        val yarim = listOf(
            Match(id = 1L, listId = 11L, rankingMethod = SwissSystem.METHOD,
                songId1 = 1L, songId2 = 2L, winnerId = 1L, round = 1, isCompleted = false)
        )
        val state = SwissSystem.computeState(songs, yarim)
        assertEquals(0.0, state.teams.first { it.id == 1L }.points, 0.0001)
        assertEquals("Tamamlanmamış maç turu ilerletmemeli", 1, state.currentRound)
    }

    @Test
    fun belgeleme_ayniMacIkiKezKaydedilirsePuanIKIYEKATLANIR() {
        // TEHLİKE SÖZLEŞMESİ: replay tüm listeyi baştan oynatıyor; aynı maç
        // listede iki kez varsa puan iki kez sayılır. Tekilleştirme ÇAĞIRANDA.
        val songs = makeSongs(4)
        val m = Match(id = 1L, listId = 11L, rankingMethod = SwissSystem.METHOD,
            songId1 = 1L, songId2 = 2L, winnerId = 1L, round = 1, isCompleted = true)

        val tek = SwissSystem.computeState(songs, listOf(m))
        val cift = SwissSystem.computeState(songs, listOf(m, m.copy(id = 2L)))
        assertEquals("Tek kayıtta 1 puan", 1.0, tek.teams.first { it.id == 1L }.points, 0.0001)
        assertEquals(
            "Çift kayıtta 2 puan — motor tekilleştirme yapmıyor, koruma çağıranda",
            2.0, cift.teams.first { it.id == 1L }.points, 0.0001
        )
    }

    // ==========================================================
    // ⑧ SONUÇ BÜTÜNLÜĞÜ, GEÇİŞLİLİK, BAŞARIM
    // ==========================================================

    @Test
    fun sonuclar_pozisyonlar1denNyeTekrarsiz() {
        listOf(5, 8, 16).forEach { n ->
            val sim = simulate(n, mixed)
            val results = SwissSystem.calculateResults(sim.songs, sim.all)
            assertEquals("n=$n sonuç sayısı", n, results.size)
            assertEquals(
                "n=$n pozisyonlar 1..$n tekrarsız olmalı",
                (1..n).toList(), results.map { it.position }.sorted()
            )
            assertEquals(
                "n=$n hiçbir takım kaybolmamalı",
                (1L..n.toLong()).toSet(), results.map { it.songId }.toSet()
            )
        }
    }

    @Test
    fun gecisliik_buyukDongulSonuclar_cokmuyor() {
        // 64 takım, hepsi aynı puanda ve aralarında yoğun döngü:
        // sortedWith sözleşme ihlali atmamalı (EMRE_CORRECT'te bu ölçülmüştü).
        val n = 64
        val songs = makeSongs(n)
        val matches = mutableListOf<Match>()
        var id = 1L
        for (i in 1..n) {
            for (step in 1..(n - 1) / 2) {
                val j = (i - 1 + step) % n + 1
                matches.add(
                    Match(id = id++, listId = 11L, rankingMethod = SwissSystem.METHOD,
                        songId1 = i.toLong(), songId2 = j.toLong(), winnerId = i.toLong(),
                        round = 1, isCompleted = true)
                )
            }
        }
        val results = SwissSystem.calculateResults(songs, matches)
        assertEquals("Tüm takımlar sonuçta olmalı", n, results.size)
        assertEquals("Pozisyonlar tekrarsız olmalı", n, results.map { it.position }.toSet().size)
    }

    @Test
    fun basarim_n64_ilkTur2SaniyeAlti() {
        val songs = makeSongs(64)
        val state = SwissSystem.computeState(songs, emptyList())
        val start = System.nanoTime()
        val pairing = SwissSystem.createNextRound(state, emptyList())
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals("n=64 ilk turda 32 maç", 32, pairing.matches.size)
        assertTrue("n=64 ilk tur $ms ms sürdü (sınır 2000 ms)", ms < 2000)
    }

    @Test
    fun basarim_n64_tumTurnuva20SaniyeAlti() {
        val start = System.nanoTime()
        val sim = simulate(64, mixed)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals("n=64 turnuvası 6 tur oynamalı", 6, sim.rounds.size)
        assertTrue("n=64 tam turnuva $ms ms sürdü (sınır 20000 ms)", ms < 20000)
    }
}
